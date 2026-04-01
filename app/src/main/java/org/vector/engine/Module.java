/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.engine;

/**
 * Module type enumeration for different JavaScript/Node.js module systems.
 * Provides utility method to detect module type from file name.
 */
public enum Module {

    /**
     * V8Runtime Vectorted SDK module.
     * Used for special Vectorted SDK modules (e.g., "@vectorted").
     * These modules provide proprietary SDK functionality within the Vectorted ecosystem.
     */
    NODE_VECTORTED_MODULE,

    /** Default module type when no extension or invalid extension found */
    NODE_MODULE,
    
    /** ECMAScript module (file extension: .mjs) */
    ESModule,

    /** Generic Node.js module for unknown extensions */
    NODE,

    /** CommonJS module (file extensions: .js, .cjs) */
    CJS;

    /**
     * Detects module type from file name based on extension.
     * 
     * @param module file name with extension (e.g., "app.mjs", "script.js")
     * @return Module type determined by file extension
     * @throws NullPointerException if module is null
     */
    public static Module getModule(String module) {
        int index = module.lastIndexOf('.');
        if(module.equals("@vectorted")) {
            return NODE_VECTORTED_MODULE;
        }

        if(index < 0 || index == module.length() - 1) {
            return NODE_MODULE;
        }
        String fix = module.substring(index + 1).toLowerCase();

        return switch (fix) {
            case "mjs" -> ESModule;
            case "js", "cjs" -> CJS;

            default -> NODE;
        };
    }
}
