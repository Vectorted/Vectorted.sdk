/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

const vm = require('node:vm');
const Module = require('node:module');
const fsSync = require('fs');
const fs = require('node:fs').promises;
const path = require('node:path');

/**
 * ESMLoader - A sandboxed ECMAScript module loader implementation using Node.js vm module
 *
 * This class provides a secure environment for loading and executing ES modules
 * with isolated contexts, module resolution, and import/export handling.
 *
 * @example
 * const loader = new ESMLoader({ console, setTimeout });
 * const result = await loader.runModule('./module.mjs');
 */
class ESMLoader {
    /**
     * Reference to the Node.js require function.
     * This allows for dynamic loading of CommonJS modules within the sandbox.
     *
     * @type {Function}
     * @private
     */
    vmRequire;

    /**
     * The directory name of the current module.
     *
     * @type {string}
     * @private
     */
    vmDirname;

    /**
     * The file name of the current module.
     *
     * @type {string}
     * @private
     */
    vmFilename;

    /**
     * The VM execution context.
     *
     * @type {Object}
     * @private
     */
    context;

    /**
     * Cache for loaded modules to prevent redundant loading.
     * Key: module identifier, Value: loaded module instance.
     *
     * @type {Map<string, vm.Module>}
     * @private
     */
    moduleCache;

    /**
     * Identifier of the entry module (main module being executed).
     *
     * @type {string|null}
     * @private
     */
    entryIdentifier;

    /**
     * Creates an instance of ESMLoader.
     *
     * @param {Object} [sandbox={}] - Sandbox context object that will be exposed to VM globals
     */
    constructor(sandbox = {}) {
        this.vmRequire = require;
        this.vmDirname = __dirname;
        this.vmFilename = __filename;

        delete globalThis['require']
        delete globalThis['__dirname']
        delete globalThis['__filename']

        Object.assign(globalThis, sandbox);

        if (!vm.isContext(globalThis)) {
            vm.createContext(globalThis);
        }
        this.context = globalThis;

        this.moduleCache = new Map();
        this.entryIdentifier = null;
    }

    /**
     * Determines whether a file is a CommonJS module based on package.json.
     *
     * @param {string} filepath - The file path to check
     * @returns {boolean} True if the file is a CommonJS module, false otherwise
     */
    isCjsModule(filepath) {
        if (filepath.endsWith('.cjs')) return true;
        if (filepath.endsWith('.mjs')) return false;
        let dir = path.dirname(filepath);
        while (dir !== path.dirname(dir)) {
            const pkgPath = path.join(dir, 'package.json');
            if (fsSync.existsSync(pkgPath)) {
                try {
                    const pkg = JSON.parse(fsSync.readFileSync(pkgPath, 'utf8'));
                    if (pkg.type === 'module') return false;
                    return true;
                } catch (e) {
                    return true;
                }
            }
            dir = path.dirname(dir);
        }
        return true;
    }

    /**
     * Creates or updates the VM execution context using the current host globalThis.
     *
     * @param {Object} [cxt={}] - New context object to be exposed to VM globals
     * @returns {void}
     */
    createContext(cxt = {}) {
        Object.assign(globalThis, cxt);
        if (!vm.isContext(globalThis)) {
            vm.createContext(globalThis);
        }
        this.context = globalThis;
    }

    /**
     * Wraps a CommonJS/built-in module as a VM ESM SyntheticModule.
     *
     * @param {Object} modObj - The CommonJS module object to wrap
     * @returns {vm.SyntheticModule} Synthetic module with ESM exports
     * @private
     */
    wrapCjsModuleAsEsm(modObj) {
        const keys = Object.keys(modObj);
        const exportNames = keys.includes('default') ? keys : [...keys, 'default'];
        return new vm.SyntheticModule(
            exportNames,
            function() {
                for (const key of keys)
                    this.setExport(key, modObj[key]);
                if (!keys.includes('default'))
                    this.setExport('default', modObj);
            },
            { context: this.context }
        );
    }

    /**
     * Extracts directory and filename from a file URL.
     *
     * @param {string} url - File URL or path
     * @returns {{dirname: string, filename: string}} Object containing dirname and filename
     * @private
     */
    getDirnameFilename(url) {
        const file = url.startsWith('file://') ?
            decodeURIComponent(new URL(url).pathname) :
            url;
        return {
            dirname: path.dirname(file),
            filename: file,
        };
    }

    /**
     * Creates an initializeImportMeta callback for SourceTextModule.
     *
     * @param {string} entryIdentifier - Entry module identifier
     * @returns {Function} initializeImportMeta callback function
     * @private
     */
    createInitializeImportMeta(entryIdentifier) {
        const self = this;
        const rq = this.vmRequire;
        const dir = this.vmDirname;
        const file = this.vmFilename;
        return function initializeImportMeta(meta, module) {
            let url = `file://${module.identifier}`;

            const {
                dirname,
                filename
            } = self.getDirnameFilename(url);
            meta.dirname = dir;
            meta.filename = file;
            meta.main = (module.identifier === entryIdentifier);
            meta.resolve = function(specifier) {
                return rq.resolve(specifier, {
                    paths: [dirname]
                });
            };
            meta.url = url;
        };
    }

    /**
     * Resolves a module specifier relative to the referencing module.
     *
     * @param {string} specifier - Module specifier to resolve
     * @param {vm.Module} referencingModule - The module that is importing
     * @returns {Promise<{module?: vm.Module, filename?: string}>} Resolved module info
     * @private
     * @async
     */
    async resolveModuleSpecifier(specifier, referencingModule) {
        const BUILTIN_MODULES = new Set([
            ...Module.builtinModules,
            ...Module.builtinModules.map(name => 'node:' + name.replace(/^node:/, ''))
        ]);
        if (BUILTIN_MODULES.has(specifier)) {
            try {
                const mod = this.vmRequire(specifier);
                const m = this.wrapCjsModuleAsEsm(mod);
                await m.evaluate();
                return {
                    module: m
                };
            } catch (e) {
                // fallback
            }
        }

        const base = path.dirname(referencingModule.identifier);
        let filename;
        if (
            /^[\w@][\w\-_/@.]*$/.test(specifier) &&
            !specifier.startsWith('./') && !specifier.startsWith('../') && !specifier.startsWith('/')
        ) {
            try {
                filename = this.vmRequire.resolve(specifier, {
                    paths: [base, process.cwd()]
                });
            } catch (e) {
                throw new Error(`Cannot resolve npm module: ${specifier}\n  from: ${base}\n  search paths: ${[base, process.cwd()]}`);
            }
            if (this.isCjsModule(filename)) {
                const mod = this.vmRequire(filename);
                const m = this.wrapCjsModuleAsEsm(mod);
                await m.evaluate();
                return {
                    module: m
                };
            } else {
                return {
                    filename
                };
            }
        } else {
            filename = path.resolve(base, specifier);
            return {
                filename
            };
        }
    }

    /**
     * Loads and caches an ESM module from file or string.
     *
     * @param {string} modulePathOrCode - File path or source code string
     * @param {Object} [options={}] - Loading options
     * @param {string} [options.filename=null] - Filename for string modules
     * @param {boolean} [options.isEntry=false] - Whether this is the entry module
     * @param {boolean} [options.isString=false] - Whether modulePathOrCode is source code
     * @returns {Promise<vm.Module>} Loaded module instance
     * @private
     * @async
     */
    async loadModule(modulePathOrCode, {
        filename = null,
        isEntry = false,
        isString = false
    } = {}) {
        let moduleIdentifier, code;

        if (isString) {
            moduleIdentifier = path.resolve(filename || `memory_${Math.random().toString(36).slice(2)}.mjs`);
            code = modulePathOrCode;
        } else {
            moduleIdentifier = path.resolve(modulePathOrCode);

            if (this.moduleCache.has(moduleIdentifier)) {
                return this.moduleCache.get(moduleIdentifier);
            }
            code = await fs.readFile(moduleIdentifier, 'utf8');
        }

        if (this.moduleCache.has(moduleIdentifier)) {
            return this.moduleCache.get(moduleIdentifier);
        }

        const m = new vm.SourceTextModule(code, {
            identifier: moduleIdentifier,
            importModuleDynamically: this.importModuleDynamically.bind(this),
            initializeImportMeta: this.createInitializeImportMeta(this.entryIdentifier || moduleIdentifier),
            context: this.context
        });

        this.moduleCache.set(moduleIdentifier, m);

        if (isEntry) this.entryIdentifier = moduleIdentifier;
        return m;
    }

    /**
     * Module linker function for resolving dependencies.
     *
     * @param {string} specifier - Module specifier to resolve
     * @param {vm.Module} referencingModule - The importing module
     * @returns {Promise<vm.Module>} Resolved module
     * @private
     * @async
     */
    async linker(specifier, referencingModule) {
        const resolved = await this.resolveModuleSpecifier(specifier, referencingModule);

        if (resolved.module) {
            if (resolved.module.status === 'unlinked') {
                await resolved.module.link(this.linker.bind(this));
            }
            if (resolved.module.status === 'linked') {
                await resolved.module.evaluate();
            }
            return resolved.module;
        }

        const m = await this.loadModule(resolved.filename);
        return m;
    }

    /**
     * Dynamic import handler.
     *
     * @param {string} specifier - Module specifier
     * @param {vm.Module} referencingModule - Module that called import()
     * @returns {Promise<vm.Module>} Resolved module
     * @private
     * @async
     */
    async importModuleDynamically(specifier, referencingModule) {
        const resolved = await this.resolveModuleSpecifier(specifier, referencingModule);

        if (resolved.module) {
            if (resolved.module.status === 'unlinked') {
                await resolved.module.link(this.linker.bind(this));
            }
            if (resolved.module.status === 'linked') {
                await resolved.module.evaluate();
            }
            return resolved.module;
        }

        const m = await this.loadModule(resolved.filename);
        if (m.status === 'unlinked') {
            await m.link(this.linker.bind(this));
        }
        if (m.status === 'linked') {
            await m.evaluate();
        }
        return m;
    }

    /**
     * Executes an ES module from source code string or a file path.
     *
     * @param {string} codeOrPath - ES module source code OR path to the module file
     * @param {Object} [options={}] - Execution options
     * @param {string} [options.filename='main.mjs'] - Virtual filename for the module (if string execution)
     * @param {boolean} [options.isString=false] - Force treat input as code string
     * @returns {Promise<object>} Module namespace export object
     * @async
     */
    async runModule(codeOrPath, options = {}) {
        this.moduleCache.clear();
        this.entryIdentifier = null;

        let isString = false;
        let filename = null;

        if (options && typeof options === 'object') {
            isString = !!options.isString;
            filename = options.filename;
        }

        if (options.isString === undefined) {
            const isMaybePath = typeof codeOrPath === 'string' &&
                !codeOrPath.includes('\n') &&
                !codeOrPath.includes(' ') &&
                (codeOrPath.startsWith('.') || codeOrPath.startsWith('/') || path.isAbsolute(codeOrPath) || fsSync.existsSync(codeOrPath));
            isString = !isMaybePath;
        }

        const entryModule = await this.loadModule(codeOrPath, {
            filename: isString ? (filename || 'main.mjs') : null,
            isEntry: true,
            isString: isString
        });

        await entryModule.link(this.linker.bind(this));
        await entryModule.evaluate();
        return entryModule.namespace;
    }
}

new ESMLoader({});
