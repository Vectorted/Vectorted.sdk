/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

import { Buffer } from 'node:buffer';
import { writeFile } from 'node:fs/promises';

/**
 * Loads a Java class from a JAR file.
 * @type {classLoader}
 */
const classLoader = $java.loadJar("./api.jar");

/**
 * Finds a specific class using a custom class loader.
 * @type {Class}
 */
const api = $java.findClassByLoader(classLoader, 'com.tools.api.test');

/**
 * Finds a standard Java class (ArrayList) and logs it.
 */
console.log($java.findClass('java.util.ArrayList'));

/**
 * Gets a method reference via reflection.
 * @type {Method}
 */
let renameTo = java.io.File.getMethod('renameTo', java.io.File);
console.log(`${renameTo}`);

const promise = $java.promise(new Promise((resolve, reject) => {
    resolve('ok');
}));

promise.then(console.log).catch(console.error);

/**
 * JavaScript extension of Java's java.io.File class using Javet framework.
 * 
 * This code demonstrates how to create a JavaScript class that extends a Java class,
 * providing JavaScript-level polymorphism and inheritance over Java types.
 * 
 * @example
 * // Creates a JavaScript class extending java.io.File
 * const ChildFile = Java.extend(java.io.File, {
 *     // JavaScript implementation here
 * });
 * 
 * @function extend
 * @param {Class} java.io.File - The Java base class to extend
 * @param {Object} prototype - JavaScript prototype containing methods and properties
 * @returns {Function} Constructor for the extended JavaScript-Java hybrid class
 */
const ChildFile = $java.extend(java.io.File, {

    /**
     * Array of Java interfaces to implement.
     * This allows the JavaScript class to implement multiple Java interfaces
     * in addition to extending the base class.
     * 
     * @type {Array<Class>}
     * @example
     * implements: [java.lang.Runnable, java.io.Serializable]
     */
    implements: [],

    /**
     * Fully qualified Java package and class name for the generated proxy class.
     * This determines the Java class name that will be generated at runtime.
     * 
     * @type {string}
     * @package 'io.proxy.File'
     * @note The package name must be valid Java package naming convention
     */
    package: 'io.proxy.File',

    /**
     * Constructor function that matches Java overloaded constructor patterns.
     * Supports variable arguments (...varargs) to match any Java constructor signature.
     * 
     * This constructor is called when JavaScript 'new' operator is used,
     * and it maps to the appropriate Java constructor based on argument types.
     * 
     * @param {...*} varargs - Variable arguments matching Java constructor parameters
     * @construct ChildFile
     * @example
     * // Matches java.io.File(String pathname)
     * new ChildFile('/home/user');
     * 
     * // Matches java.io.File(String parent, String child)  
     * new ChildFile('/home', 'user');
     * 
     * // Matches java.io.File(URI uri)
     * new ChildFile(new java.net.URL('file:///home/').toURI());
     */
    constructor(...varargs) {
        /**
         * Constructor logging for debugging purposes.
         * Prints all constructor arguments to console.
         * 
         * @param {Array} varargs - Constructor arguments array
         */
        console.log(`varargs = ${varargs}`);
        
        // Note: The actual Java constructor invocation is handled automatically
        // by Javet framework. This JavaScript constructor runs after the
        // Java super constructor has been called.
    },

    /**
     * Custom JavaScript method that extends java.io.File functionality.
     * Creates directory if it doesn't exist, otherwise returns existing path.
     * 
     * This method demonstrates accessing the Java superclass ($super) from
     * JavaScript and calling its methods polymorphically.
     * 
     * @returns {string|boolean} 
     *   - Directory path if directory was created successfully
     *   - Existing directory path if directory already exists
     *   - false if directory creation failed
     * 
     * @see java.io.File#mkdirs()
     * @see java.io.File#getPath()
     * @see java.io.File#exists()
     * 
     * @example
     * const file = new ChildFile('/new/directory');
     * const result = file.createDir(); // Creates directory or returns path
     */
    createDir() {
        // Access Java superclass methods via $super proxy
        return $super.exists();
    }
});

/**
 * Demonstration of JavaScript-Java hybrid object instantiation and usage.
 * 
 * This example shows:
 * 1. Creating a URI from URL for cross-platform file path handling
 * 2. Instantiating the hybrid JavaScript-Java class
 * 3. Calling inherited Java methods from JavaScript
 * 
 * @type {ChildFile} Hybrid JavaScript-Java object instance
 * @example
 * // Creates File object from URI, handling platform-specific path formats
 * let childFile = new ChildFile(new java.net.URL('file:///home/').toURI());
 * 
 * // Calls inherited java.io.File.exists() method
 * console.log(childFile.exists()); // true or false
 */
let childFile = new ChildFile(new java.net.URL('file:///home/').toURI());

/**
 * Tests the existence of the file/directory using inherited Java method.
 * Demonstrates seamless interoperability between JavaScript and Java layers.
 * 
 * @returns {boolean} true if file/directory exists, false otherwise
 */

console.log(`${childFile}`);
