/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */
package org.vector.proxy.classLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * A custom class loader that extends {@link ClassLoader} and implements {@link AutoCloseable}.
 * <p>
 * This loader supports dynamic class loading from byte arrays in memory as well as from
 * class files on the filesystem. It provides mechanisms to define classes at runtime
 * and manages the associated bytecode resources.
 */
public class VectortedClassLoader extends ClassLoader implements AutoCloseable {

    /** The parent class loader used for delegation. */
    ClassLoader classLoader;

    /** The bytecode array representing the class to be loaded. */
    byte[] byteCode;

    /**
     * Constructs a new {@code VectortedClassLoader} with a specified parent class loader.
     *
     * @param classLoader the parent class loader for delegation
     */
    public VectortedClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
    /**
     * Factory method to create a new instance of {@code VectortedClassLoader}.
     *
     * @param classLoader the parent class loader to associate with the new loader
     * @return a new {@code VectortedClassLoader} instance
     */
    public static VectortedClassLoader getVectortedLoader(ClassLoader classLoader) {
        return new VectortedClassLoader(classLoader);
    }

    /**
     * Loads a class by its fully qualified name using the current bytecode cache.
     *
     * @param name the fully qualified name of the class
     * @return the loaded {@link Class} object, or {@code null} if loading fails
     */
    public Class<?> getClasses(String name) {
        return findClass(name);
    }

    /**
     * Loads a class from the provided bytecode array in memory.
     * <p>
     * The provided bytecode is cached internally and used to define the class.
     *
     * @param className the fully qualified name of the class
     * @param byteCode  the byte array containing the class bytecode
     * @return the defined {@link Class} object, or {@code null} if definition fails
     */
    public Class<?> getClasses(String className, byte[] byteCode) {
        this.byteCode = byteCode;
        return findClass(className);
    }

    /**
     * Defines a class by searching in the bytecode cache or reading from a .class file.
     * <p>
     * This method first checks for a cached bytecode array; if present, it defines the class
     * from that array. Otherwise, it attempts to read a .class file from the filesystem
     * using the provided name as a file path.
     *
     * @param name the class name or file path of the .class file
     * @return the defined {@link Class} object, or {@code null} if the operation fails
     */
    @Override
    public Class<?> findClass(String name) {
        if(byteCode != null) {
            return defineClass(name, this.byteCode, 0, this.byteCode.length);
        }

        File classFile = new File(name);
        FileInputStream file;
        try {
            file = new FileInputStream(name);

            byte[] byteCode = file.readAllBytes();
            return defineClass(classFile.getName().replaceAll(".class", ""), byteCode, 0, byteCode.length);
        } catch (IOException e) {}
        return null;
    }

    /**
     * Releases resources associated with this class loader by clearing the cached bytecode.
     * <p>
     * This method implements the {@link AutoCloseable} interface, allowing the loader
     * to be used in try-with-resources statements.
     */
    @Override
    public void close() {
        this.byteCode = null;
    }
}