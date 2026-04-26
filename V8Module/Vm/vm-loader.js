/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

const vm = require('node:vm');
const fs = require('node:fs').promises;
const path = require('node:path');

/**
 * ESModule - A sandboxed ECMAScript module loader implementation using Node.js vm module
 * 
 * This class provides a secure environment for loading and executing ES modules
 * with isolated contexts, module resolution, and import/export handling.
 * 
 * @example
 * const loader = new ESModule({ console, setTimeout });
 * const result = await loader.runFile('./module.mjs');
 */
class ESModule {
	/**
	 * @private
	 * @type {Object}
	 * Predefined context variables that are automatically included in the VM context
	 * This provides a set of common Node.js globals to the sandboxed environment
	 */
	vmContext = {
		/**
		 * @type {Console}
		 * Console object for logging and debugging output
		 * Provides methods like console.log(), console.error(), console.warn()
		 */
		console,

		/**
		 * @type {Function}
		 * Schedules a function to be executed after a specified delay (in milliseconds)
		 * Returns a Timeout object that can be used with clearTimeout()
		 */
		setTimeout,

		/**
		 * @type {Function}
		 * Repeatedly executes a function with a fixed time delay between each call
		 * Returns an Interval object that can be used with clearInterval()
		 */
		setInterval,

		/**
		 * @type {Function}
		 * Cancels a timeout previously established by setTimeout()
		 * Prevents the scheduled function from executing
		 */
		clearTimeout,

		/**
		 * @type {Function}
		 * Cancels a repeated timed action previously established by setInterval()
		 * Stops the interval execution
		 */
		clearInterval,

		/**
		 * @type {Function}
		 * Schedules a function to be executed on the next iteration of the event loop
		 * Returns an Immediate object for use with clearImmediate()
		 */
		setImmediate,

		/**
		 * @type {Function}
		 * Cancels an Immediate object previously created by setImmediate()
		 */
		clearImmediate,

		/**
		 * @type {process.Process}
		 * Node.js process object providing information about and control over the current Node.js process
		 * Includes properties like process.env, process.argv, process.cwd()
		 */
		process,

		/**
		 * @type {Buffer}
		 * Node.js Buffer class for handling binary data
		 * Used for working with streams, file I/O, and network protocols
		 */
		Buffer,

		/**
		 * @type {URL}
		 * Web standard URL class for parsing, constructing, and manipulating URLs
		 * Provides URL parsing and manipulation capabilities
		 */
		URL,

		/**
		 * @type {URLSearchParams}
		 * Utility class for working with query strings in URLs
		 * Allows parsing, getting, setting, and appending query parameters
		 */
		URLSearchParams,

		/**
		 * @type {TextEncoder}
		 * Web standard API for encoding strings into UTF-8 byte sequences
		 * Converts JavaScript strings to Uint8Array
		 */
		TextEncoder,

		/**
		 * @type {TextDecoder}
		 * Web standard API for decoding UTF-8 byte sequences into strings
		 * Converts Uint8Array to JavaScript strings
		 */
		TextDecoder,

		/**
		 * @type {AbortController}
		 * Web standard API for aborting one or more Web requests
		 * Used to signal cancellation of fetch requests or other async operations
		 */
		AbortController,

		/**
		 * @type {AbortSignal}
		 * Signal object associated with an AbortController
		 * Passed to operations that can be aborted
		 */
		AbortSignal,

		/**
		 * @type {Function}
		 * Decodes a string of Base64-encoded data
		 * Converts Base64 string to original binary data (ASCII/binary string)
		 */
		atob,

		/**
		 * @type {Function}
		 * Encodes a string in Base64 format
		 * Converts binary data/string to Base64-encoded ASCII string
		 */
		btoa
	};

	/**
	 * @private
	 * @type {Function}
	 * Reference to the Node.js require function
	 * This allows for dynamic loading of CommonJS modules within the sandbox
	 */
	vmRequire;

	/**
	 * Creates an instance of ESModule
	 * 
	 * @param {Object} [sandbox={}] - Sandbox context object that will be exposed to VM globals
	 */
	constructor(sandbox = {}) {
		/**
		 * @private
		 * @type {vm.Context}
		 * The isolated execution context for the sandbox
		 * Contains user-provided sandbox, predefined VM context, and global references
		 */
		this.vmRequire = require;
		this.context = vm.createContext({
			...sandbox,
			...this.vmContext,
			global: this.deleteProxy(),
			globalThis: this.deleteProxy()
		});

		/**
		 * @private
		 * @type {Map<string, vm.Module>}
		 * Cache for loaded modules to prevent redundant loading
		 * Key: module identifier, Value: loaded module instance
		 */
		this.moduleCache = new Map();

		/**
		 * @private
		 * @type {string|null}
		 * Identifier of the entry module (main module being executed)
		 */
		this.entryIdentifier = null;
	}

	/**
	 * Cleans the globalThis object by removing certain properties
	 * This is used to create a clean global scope for the sandbox
	 * 
	 * @private
	 * @returns {Object} The cleaned globalThis object
	 */
	deleteProxy() {
		delete globalThis['require'];
		return globalThis;
	}

	/**
	 * Creates or updates the VM execution context
	 * Replaces the existing context with a new one
	 * Note: This does not clear the module cache, which may cause issues
	 * with modules that were loaded with the previous context
	 * 
	 * @param {Object} [cxt={}] - New context object to be exposed to VM globals
	 * @returns {void}
	 */
	createContext(cxt = {}) {
		this.context = vm.createContext({
			...cxt,
			...this.vmContext,
			global: this.deleteProxy(),
			globalThis: this.deleteProxy()
		});
	}

	/**
	 * Wraps a CommonJS/built-in module as a VM ESM SyntheticModule
	 * 
	 * @private
	 * @param {Object} modObj - The CommonJS module object to wrap
	 * @returns {vm.SyntheticModule} Synthetic module with ESM exports
	 */
	wrapCjsModuleAsEsm(modObj) {
		const keys = Object.keys(modObj);
		return new vm.SyntheticModule(
			[...keys, 'default'],
			function() {
				for (const key of keys) this.setExport(key, modObj[key]);
				this.setExport('default', modObj);
			}, {
				//context: this.context
			}
		);
	}

	/**
	 * Extracts directory and filename from a file URL
	 * 
	 * @private
	 * @param {string} url - File URL or path
	 * @returns {{dirname: string, filename: string}} Object containing dirname and filename
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
	 * Creates an initializeImportMeta callback for SourceTextModule
	 * 
	 * @private
	 * @param {string} entryIdentifier - Entry module identifier
	 * @returns {Function} initializeImportMeta callback function
	 */
	createInitializeImportMeta(entryIdentifier) {
		const self = this;
		const rq = this.vmRequire;
		return function initializeImportMeta(meta, module) {
			let url = `file://${module.identifier}`;

			const {
				dirname,
				filename
			} = self.getDirnameFilename(url);
			meta.dirname = dirname;
			meta.filename = filename;
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
	 * Resolves a module specifier relative to the referencing module
	 * 
	 * @private
	 * @async
	 * @param {string} specifier - Module specifier to resolve
	 * @param {vm.Module} referencingModule - The module that is importing
	 * @returns {Promise<{module?: vm.Module, filename?: string}>} Resolved module info
	 */
	async resolveModuleSpecifier(specifier, referencingModule) {
		// 1. Node built-in modules / npm packages
		if (specifier.startsWith('node:') || /^[a-zA-Z0-9_\-@]+/.test(specifier)) {
			try {
				const mod = this.vmRequire(specifier);
				const m = this.wrapCjsModuleAsEsm(mod);
				await m.evaluate();
				return {
					module: m
				};
			} catch (e) {
				/* fallback to file path resolution */
			}
		}
		// 2. File paths (relative/absolute)
		let base = path.dirname(referencingModule.identifier);
		let filename = path.resolve(base, specifier);
		return {
			filename
		};
	}

	/**
	 * Loads and caches an ESM module from file or string
	 * 
	 * @private
	 * @async
	 * @param {string} modulePathOrCode - File path or source code string
	 * @param {Object} [options={}] - Loading options
	 * @param {string} [options.filename=null] - Filename for string modules
	 * @param {boolean} [options.isEntry=false] - Whether this is the entry module
	 * @param {boolean} [options.isString=false] - Whether modulePathOrCode is source code
	 * @returns {Promise<vm.Module>} Loaded module instance
	 */
	async loadModule(modulePathOrCode, {
		filename = null,
		isEntry = false,
		isString = false
	} = {}) {
		let moduleIdentifier, code;
		if (isString) {
			// In-memory string module with unique identifier
			moduleIdentifier = path.resolve(filename || `memory_${Math.random().toString(36).slice(2)}.mjs`);
			code = modulePathOrCode;
		} else {
			moduleIdentifier = path.resolve(modulePathOrCode);
			if (this.moduleCache.has(moduleIdentifier)) return this.moduleCache.get(moduleIdentifier);
			code = await fs.readFile(moduleIdentifier, 'utf8');
		}
		if (this.moduleCache.has(moduleIdentifier)) return this.moduleCache.get(moduleIdentifier);

		const m = new vm.SourceTextModule(code, {
			//context: this.context,
			identifier: moduleIdentifier,
			importModuleDynamically: this.importModuleDynamically.bind(this),
			initializeImportMeta: this.createInitializeImportMeta(this.entryIdentifier || moduleIdentifier)
		});

		this.moduleCache.set(moduleIdentifier, m);

		if (isEntry) this.entryIdentifier = moduleIdentifier;
		return m;
	}

	/**
	 * Ensures a module has a default export
	 * Creates a synthetic module with a default export if the original doesn't have one
	 * 
	 * @private
	 * @async
	 * @param {vm.Module} m - Module to check
	 * @returns {Promise<vm.Module>} Module with guaranteed default export
	 */
	async ensureDefaultExport(m) {
		await m.link(this.linker.bind(this));
		await m.evaluate();
		const ns = m.namespace;
		if ('default' in ns) return m;
		const keys = Object.getOwnPropertyNames(ns);
		const synthetic = new vm.SyntheticModule([...keys, 'default'], function() {
			for (const k of keys) this.setExport(k, ns[k]);
			this.setExport('default', ns);
		}, {
			//context: this.context
		});
		await synthetic.evaluate();
		return synthetic;
	}

	/**
	 * Module linker function for resolving dependencies
	 * Called during module linking phase to resolve import specifiers
	 * 
	 * @private
	 * @async
	 * @param {string} specifier - Module specifier to resolve
	 * @param {vm.Module} referencingModule - The importing module
	 * @returns {Promise<vm.Module>} Resolved module
	 */
	async linker(specifier, referencingModule) {
		const resolved = await this.resolveModuleSpecifier(specifier, referencingModule);
		if (resolved.module) return resolved.module;
		const m = await this.loadModule(resolved.filename);
		return this.ensureDefaultExport(m);
	}

	/**
	 * Dynamic import handler
	 * Handles dynamic import() calls from within modules
	 * 
	 * @private
	 * @async
	 * @param {string} specifier - Module specifier
	 * @param {vm.Module} referencingModule - Module that called import()
	 * @returns {Promise<vm.Module>} Resolved module
	 */
	async importModuleDynamically(specifier, referencingModule) {
		const resolved = await this.resolveModuleSpecifier(specifier, referencingModule);
		if (resolved.module) return resolved.module;
		const m = await this.loadModule(resolved.filename);
		return this.ensureDefaultExport(m);
	}

	/**
	 * Executes an ES module from source code string
	 * 
	 * @async
	 * @param {string} code - ES module source code
	 * @param {Object} [options={}] - Execution options
	 * @param {string} [options.filename='main.mjs'] - Virtual filename for the module
	 * @returns {Promise<object>} Module namespace export object
	 */
	async runModule(code, options = {}) {
		// Clear cache
		this.moduleCache.clear();
		this.entryIdentifier = null;

		const entryModule = await this.loadModule(code, {
			filename: options.filename || 'main.mjs',
			isEntry: true,
			isString: true
		});

		await entryModule.link(this.linker.bind(this));
		await entryModule.evaluate();
		return entryModule.namespace;
	}

	/**
	 * Executes an ES module from a file
	 * 
	 * @async
	 * @param {string} filename - Path to the module file
	 * @returns {Promise<object>} Module namespace export object
	 */
	async runFile(filename) {
		// Clear cache
		this.moduleCache.clear();
		this.entryIdentifier = null;

		const entryModule = await this.loadModule(filename, {
			isEntry: true,
			isString: false
		});

		await entryModule.link(this.linker.bind(this));
		await entryModule.evaluate();
		return entryModule.namespace;
	}
}
