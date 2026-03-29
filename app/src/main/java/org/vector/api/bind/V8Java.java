/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */
package org.vector.api.bind;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.vector.engine.V8NodeEngine;
import org.vector.proxy.classLoader.VectortedClassLoader;
import org.vector.proxy.reference.JavetReflectionObjectFactory;

import com.caoccao.javet.annotations.V8Function;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.IV8ValuePromise.IListener;
import com.caoccao.javet.values.reference.V8ValueObject;
import com.caoccao.javet.values.reference.V8ValuePromise;

/**
 * V8Java bridging class.
 * This class serves as a bridge between Java and the V8 JavaScript runtime, providing two core capabilities:
 * 1. Allows JavaScript objects to inherit Java class behaviors (ultimately generating ByteCode).
 * 2. Manages additional class loaders for dynamically loading JAR files into the current JVM.
 *
 * @param <T> The type of Java class to be extended or operated upon.
 */
public final class V8Java<T> {

    /** Associated V8 JavaScript runtime environment. */
    V8Runtime v8Runtime;

    /** Singleton instance of Javet reflection object factory for handling conversions and inheritance between Java and JavaScript objects. */
    JavetReflectionObjectFactory factory = JavetReflectionObjectFactory.getInstance();

    /** Thread-safe map for caching URLClassLoader instances loaded via {@link #loadJar(String)}.
     * Key: ClassLoader hash code, Value: Corresponding ClassLoader.
     */
    ConcurrentHashMap<Integer, URLClassLoader> ClassLoaders = new ConcurrentHashMap<>();

    /**
     * Retrieves an instance of the custom {@link VectortedClassLoader} class loader.
     * <p>
     * This initializes and returns a custom class loader that extends the provided parent loader.
     * It is typically used for dynamic class loading scenarios, such as loading classes from memory.
     *
     * @return an instance of the {@link VectortedClassLoader} configured with the current class's parent loader
     */
    VectortedClassLoader vectortedClassLoader = VectortedClassLoader.getVectortedLoader(getClass().getClassLoader());

    /**
     * Constructor.
     *
     * @param v8Runtime The V8 runtime environment associated with this bridge.
     */
    public V8Java(V8Runtime v8Runtime) {
        this.v8Runtime = v8Runtime;
    }

    /**
     * Creates a LooperId to reinforce the Node.js event loop, preventing exit.
     * @return The newly generated LooperId.
     */
    @V8Function(name = "keepRunning")
    public synchronized long keepRunning() {
        return V8NodeEngine.createLooperId();
    }

    /**
     * Removes a LooperId. The event loop will stop when all IDs are removed and the task queue is empty.
     * @param loopId The LooperId to remove.
     */
    @V8Function(name = "stopRunning")
    public synchronized void stopRunning(long loopId) {
        V8NodeEngine.removeLooperId(loopId);
    }

    /**
     * Wraps a Promise to monitor Java async APIs. Keeps the event loop alive until the Promise settles.
     * @param promiseIo The Promise instance to monitor.
     * @return The input Promise with listeners attached.
     * @throws JavetException If a V8 engine error occurs.
     */
    @V8Function(name = "promise")
    public synchronized V8ValuePromise promise(V8ValuePromise promiseIo) throws JavetException {
        long looper = keepRunning();
        promiseIo.register(new IListener() {

            @Override
            public void onCatch(V8Value v8Value) {
                stopRunning(looper);
            }

            @Override
            public void onFulfilled(V8Value v8Value) {
                stopRunning(looper);
            }

            @Override
            public void onRejected(V8Value v8Value) {
                stopRunning(looper);
            }
            
        });
        return promiseIo;
    }

    /**
     * Retrieves a class by its name using a custom class loader.
     * The custom loader loads and provides the bytecode of the class.
     *
     * @param name the fully qualified name of the class to load
     * @return the loaded {@code Class} object, or null if not found
     * @see VectortedClassLoader#getVectortedLoader(ClassLoader)
     * @see VectortedClassLoader#getClasses(String)
     */
    @V8Function(name = "getClasses")
    public Class<?> getClasses(String name) {
        return vectortedClassLoader.getClasses(name);
    }

    /**
     * Loads a class dynamically from the provided byte array in memory.
     *
     * @param className the fully qualified name of the class to load
     * @param byteCode  the byte array containing the class's bytecode definition
     * @return the loaded {@link Class} object corresponding to the specified name
     */
    @V8Function(name = "getClasses")
    public Class<?> getClasses(String className, byte[] byteCode) {
        return vectortedClassLoader.getClasses(className, byteCode);
    }

    /**
     * Provides JavaScript Objects with the ability to inherit from a specified Java class.
     * This method internally calls {@link JavetReflectionObjectFactory#extend}, with the final implementation generating corresponding ByteCode.
     * This method is exposed to JavaScript via the {@link V8Function} annotation and can be called in JS as `v8Java.extend(...)`.
     *
     * @param classz The Java class to be inherited by the JavaScript object.
     * @param v8Object The JavaScript-side object (or constructor) that will inherit the specified Java class features.
     * @return The inherited Java class.
     */
    @V8Function(name = "extend")
    public Class<T> extend(Class<T> classz, V8ValueObject v8Object) {
        return this.factory.extend(classz, v8Object);
    }

    /**
     * Loads a JAR file from the specified path into the current JVM and returns a new class loader for accessing classes within that JAR.
     * The loaded class loader is cached to prevent the same JAR from being loaded multiple times.
     * This method is exposed to JavaScript via the {@link V8Function} annotation.
     *
     * @param jar The path string to the JAR file.
     * @return The {@link URLClassLoader} instance for loading the specified JAR.
     * @throws Exception If the specified JAR file does not exist, an exception with the message "FileNotFound: Jar not found." is thrown.
     */
    @V8Function(name = "loadJar")
    public ClassLoader loadJar(String jar) throws Exception {
        File file = new File(jar);
        if(!file.exists()) throw new Exception("FileNotFound: Jar not found.");

        URLClassLoader classLoader = new URLClassLoader(new URL[]{file.toURI().toURL()});

        if(!this.ClassLoaders.containsKey(classLoader.hashCode())) {
            this.ClassLoaders.put(classLoader.hashCode(), classLoader);
        }
        return classLoader;
    }

    /**
     * Directly loads a class using the specified {@link ClassLoader}.
     * This approach bypasses the parent delegation chain scan, improving lookup efficiency.
     *
     * @param classLoader the class loader instance to use for loading
     * @param className   the fully qualified name of the desired class
     * @return the {@link Class} object for the specified name, or {@code null} if not found
     * @implNote This method catches {@link ClassNotFoundException} and returns {@code null}
     *           instead of throwing an exception. Caller must handle the {@code null} case.
     */
    @V8Function(name = "findClassByLoader")
    public Class<?> findClass(ClassLoader classLoader, String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Searches for a class with the specified name among all classes currently loaded in the virtual machine.
     * Search order:
     * 1. First attempts to use the class loader of the current class (V8Java), typically the system class loader or application class loader.
     * 2. If not found, iterates through all class loaders loaded and cached via the {@link #loadJar(String)} method (stored in {@link #ClassLoaders}).
     *
     * @param className The fully qualified name of the class to search for.
     * @return The found {@link Class} object; returns {@code null} if not found in any class loader.
     */
    @V8Function(name = "findClass")
    public Class<?> findClass(String className) {
        try {
            Class<?> classz = getClass().getClassLoader().loadClass(className);
            return classz;
        } catch(Exception loadClass) {
            Set<Entry<Integer, URLClassLoader>> entrys = this.ClassLoaders.entrySet();

            Iterator<Entry<Integer, URLClassLoader>> iterator = entrys.iterator();
            while(iterator.hasNext()) {
                Entry<Integer, URLClassLoader> entry = iterator.next();
                try {
                    return entry.getValue().loadClass(className);
                } catch (ClassNotFoundException e) {}
            }
        }
        return null;
    }
}
