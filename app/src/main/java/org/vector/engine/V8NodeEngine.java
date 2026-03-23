/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.engine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.vector.proxy.reference.JavetReflectionObjectFactory;
import org.vector.util.Io;
import org.vector.util.Loggor;
import org.vector.util.Loggor.Color;
import org.vector.util.Loggor.Image;

import com.caoccao.javet.enums.V8AwaitMode;
import com.caoccao.javet.enums.V8RuntimeTerminationMode;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interception.jvm.JavetJVMInterceptor;
import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.callback.IV8ModuleResolver;
import com.caoccao.javet.interop.callback.JavetBuiltInModuleResolver;
import com.caoccao.javet.interop.converters.JavetProxyConverter;
import com.caoccao.javet.interop.options.NodeFlags;
import com.caoccao.javet.interop.options.NodeRuntimeOptions;
import com.caoccao.javet.interop.proxy.IJavetReflectionObjectFactory;
import com.caoccao.javet.node.modules.NodeModuleModule;
import com.caoccao.javet.utils.JavetOSUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.IV8Module;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;

/**
 * V8 JavaScript engine wrapper for Node.js runtime with JVM interop capabilities.
 * Provides integration between JavaScript execution and Java Virtual Machine,
 * enabling seamless interaction between Node.js modules and Java classes.
 */
public class V8NodeEngine {

    /**
     * The V8Runtime instance that manages the JavaScript execution environment.
     * This runtime is used to execute JavaScript code and interact with the V8 engine.
     */
    volatile NodeRuntime v8Runtime;

    /**
     * Stores the mapping between thread IDs and their associated looper IDs.
     * Key: Thread ID
     * Value: Looper ID
     * 
     * Declared as volatile to ensure visibility across threads.
     */
    static volatile ConcurrentHashMap<Long, Long> LooperId = new ConcurrentHashMap<>();

    /**
     * Shared random number generator instance.
     * 
     * Declared as volatile to guarantee visibility in a multithreaded environment.
     */
    static volatile Random random = new Random();

    /**
     * An instance of JavetProxyConverter used to convert between Java and JavaScript objects.
     * This converter facilitates interoperability between Java and JavaScript by handling
     * the conversion of proxies, arrays, maps, and sets.
     */
    JavetProxyConverter javetProxyConverter = new JavetProxyConverter();

    /**
     * An instance of JavetJVMInterceptor that intercepts and handles interactions
     * between the JVM and the V8 runtime. This interceptor allows JavaScript code
     * to access Java objects and vice versa.
     */
    JavetJVMInterceptor jvmInterceptor;

    /** 
     * V8 JavaScript engine function representing Node.js native console object.
     * Provides access to console methods like log(), error(), warn() for JS debugging.
     * Bound to V8 context to enable native Node.js console functionality in Java.
     */
    V8ValueObject console;

    /**
     * A constant representing the version of Node.js that is integrated with the V8NodeEngine.
     * This version string is used for logging and informational purposes.
     */
    String NODE_VERSION = "v24.13.0";

    /**
     * A constant representing the version of the V8 JavaScript engine that is used by the Node.js runtime.
     * This version string is used for logging and informational purposes.
     */
    String V8_VERSION = "v14.4.258.16";

    /**
     * Flag indicating the current running state of the V8 engine's asynchronous hooks.
     * When true, the hooks are active and processing async operations; when false, they are idle.
     * Used to control lifecycle and prevent duplicate hook registration or premature shutdown.
     */
    volatile boolean RUNNING = false;

    /**
     * Dedicated handler thread for V8 JavaScript runtime event loop processing.
     * Manages asynchronous V8 operations and ensures continuous execution of
     * JavaScript tasks without blocking the main application thread.
     */
    Thread handler;

    /** 
     * JVM thread hook for monitoring Node.js process termination events.
     * Registers shutdown callback to synchronize Java/Node.js lifecycle management.
     * Ensures resource cleanup when Node.js process exits unexpectedly or normally.
     */
    V8ValueFunction hook;
    
    /**
     * Constructs V8NodeEngine instance and initializes Node.js runtime with JVM interop.
     * Sets up V8 runtime, proxy converters, JVM interceptor, and module resolvers.
     * Enables JavaScript evaluation and loads essential Java packages into global scope.
     * Logs success message upon completion or failure notification if initialization fails.
     */
    V8NodeEngine(boolean log) {
        try {
            NodeFlags nodeFlags = NodeRuntimeOptions.NODE_FLAGS;
            nodeFlags.setExperimentalSqlite(true);
            nodeFlags.setCustomFlags(new String[]{"--experimental-vm-modules", "--trace-warnings"});

            V8Host host = V8Host.getNodeI18nInstance();
            host.enableGCNotification();

            this.v8Runtime = host.createV8Runtime();
            this.javetProxyConverter.getConfig().setProxyListEnabled(true).setProxyArrayEnabled(true).setProxyMapEnabled(true).setProxySetEnabled(true).setReflectionObjectFactory(new IJavetReflectionObjectFactory() {
                @Override
                public Object toObject(Class<?> type, V8Value v8Value) {
                    return v8Value;
                }
            });
            this.jvmInterceptor = new JavetJVMInterceptor(this.v8Runtime);

            JavetReflectionObjectFactory factory = JavetReflectionObjectFactory.getInstance();

            this.jvmInterceptor.addCallbackContexts(factory.getCallbackContexts(v8Runtime));
            this.jvmInterceptor.register(v8Runtime.getGlobalObject());

            this.v8Runtime.setConverter(javetProxyConverter);
            this.v8Runtime.allowEval(true);

            initializeModule();
            initializeEsModule();

            mixinModule();

            if(log) {
                Loggor.log(Image.CHECK + Color.SPACE + "Node.js has completed JVM virtualization." + Image.ARROW + Color.SPACE + this.NODE_VERSION);
            }
        } catch (JavetException e) {
            Loggor.log(Image.ERROR + Color.SPACE + "Node.js initialization failed, dynamic library loading failed.");
        }
    }

    /**
     * Generates a unique ID for tracking Node.js event loop lifecycle.
     * 
     * @return long Unique event loop identifier
     * @note Thread-safe with synchronized method
     */
    public synchronized static long createLooperId() {
        long id = random.nextLong();

        if(!LooperId.containsKey(id)) {
            LooperId.put(id, id);
            return id;
        }
        return createLooperId();
    }

    /**
     * Removes event loop ID when event loop terminates.
     * 
     * @param looperId ID of the event loop to remove
     * @note Safe to call with non-existent ID (no-op)
     */
    public synchronized static void removeLooperId(long looperId) {
        if(LooperId.isEmpty()) return;

        if(LooperId.containsKey(looperId)) LooperId.remove(looperId); 
    }

    /**
     * Factory method to create new V8NodeEngine instance.
     * Provides convenient way to obtain engine instance following singleton-like pattern.
     * @return new V8NodeEngine instance
     */
    public static V8NodeEngine getV8Runtime() {
        return new V8NodeEngine(true);
    }

    /**
     * Retrieves the underlying Node.js runtime instance.
     * Allows direct access to V8 runtime for advanced operations and configurations.
     * @return NodeRuntime instance for JavaScript execution and manipulation
     */
    public NodeRuntime getNodeRuntime() {
        return this.v8Runtime;
    }

    /**
     * Stops V8 runtime gracefully by setting stopping flag to true.
     * Allows orderly shutdown and resource cleanup.
     * 
     * @see #init()
     */
    @SuppressWarnings("removal")
    void close() {
        Io.closeInput();
        try {
            this.jvmInterceptor.unregister(this.v8Runtime.getGlobalObject());
            LooperId.clear();
            this.v8Runtime.lowMemoryNotification();
            this.v8Runtime.setStopping(true);
            
            this.v8Runtime = null;

            System.gc();
            System.runFinalization();
        } catch (JavetException e) {}
        Io.openInput();
    }

    /**
     * Exposes a Java object to JavaScript runtime as a global variable.
     * Makes the specified object accessible in V8 JavaScript code via the given identifier.
     * Useful for bridging Java-JavaScript interoperability in V8 runtime environment.
     * 
     * @param variable     Variable name to expose in JavaScript global scope
     * @param object Java object to make available in JavaScript context
     * @throws JavetException if object binding fails (silently caught in implementation)
     * 
     * @example javaToV8Runtime("javaUtils", new JavaUtils()); // JS: javaUtils.method()
     * @note Silently ignores binding failures - consider logging for debugging
     * @see V8Runtime#getGlobalObject()
     * @see V8Value#set(String, Object)
     */
    public V8NodeEngine javaToV8Runtime(String variable, Object object) {
        try {
            this.v8Runtime.getGlobalObject().set(variable, object);
        } catch (JavetException e) {}
        return this;
    }

    /**
     * Creates new V8 Node.js engine instance.
     * 
     * @param debugMode true=output Node.js version, false=no version output
     * @return New V8NodeEngine instance
     * 
     * @note Each call creates independent engine instance
     * @note Version output useful for debugging, disable for clean logs
     */
    public V8NodeEngine init() {
        return new V8NodeEngine(false);
    }

    /**
     * Executes JavaScript code snippet in the V8 runtime.
     * Runs the provided script with module mode enabled for ES module compatibility.
     * Silently ignores execution exceptions to prevent runtime crashes.
     * @param scriptCode JavaScript code string to be executed
     */
    public void runScript(String scriptCode) {
        try {
            this.v8Runtime.getExecutor(scriptCode).setModule(true).execute();
        } catch (JavetException e) {}
    }

     /**
     * Executes JavaScript code snippet in the V8 runtime.
     * Runs the provided script with module mode enabled for ES module compatibility.
     * Silently ignores execution exceptions to prevent runtime crashes.
     * @param scriptFile JavaScript file.
     */
    @SuppressWarnings("null")
    public void runScript(File scriptFile) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(scriptFile.toURI()));
            this.v8Runtime.getNodeModule(NodeModuleModule.class).setRequireRootDirectory(new File(JavetOSUtils.WORKING_DIRECTORY, scriptFile.getParent()));

            this.v8Runtime.getExecutor(new String(bytes, "UTF-8")).setModule(true).execute();
        } catch (Exception e) {}
    }

    /**
     * Creates and starts dedicated handler thread for asynchronous V8 runtime operations.
     * The thread runs the provided runnable and enters continuous await loop to process
     * V8 tasks without blocking. Returns thread instance for later management.
     * @param runnable initial task to execute in the handler thread before entering await loop
     * @return Thread instance managing the V8 runtime event loop
     */
    public Thread handlerLooper(Runnable runnable) {
        Thread handlerThread = new Thread(() -> {
            runnable.run();
            RUNNING = true;

            while(!Thread.currentThread().isInterrupted()) {
                if(!v8Runtime.await(V8AwaitMode.RunNoWait) && LooperId.isEmpty()) {
                    v8Runtime.terminateExecution(V8RuntimeTerminationMode.Asynchronous);
                    break;
                }
            }
            close();
        });
        this.handler = handlerThread;

        handlerThread.start();
        return handlerThread;
    }

    /**
     * Sets thread hook to handle Node.js process exit notifications.
     * @param hook The thread hook to execute when Node.js process exits
     * Links provided hook for executing cleanup logic on process termination.
     * Facilitates cross-runtime resource management between Java and Node.js.
     * @throws JavetException 
     */
    public void hookHandlerExit(V8ValueFunction hook) throws JavetException {
        this.hook = hook.toClone();
    }

    /**
     * Gracefully terminates the handler thread and stops V8 runtime execution.
     * Calls terminateExecution on V8 runtime and interrupts the specified thread
     * to exit the await loop and clean up resources.
     * @param thread handler thread to be terminated
     */
    public void exitHandlerThread(Thread thread) {
        if(thread == null || !thread.isAlive()) {
            return;
        }
        thread.interrupt();

        if(!RUNNING) {
            v8Runtime.terminateExecution(V8RuntimeTerminationMode.Synchronous);
        }
    }

    /**
     * Retrieves the handler thread associated with the V8 runtime event loop.
     * This thread is responsible for asynchronously processing V8 tasks without blocking the main application thread.
     * 
     * <p>
     * The handler thread is created and managed internally by the V8NodeEngine. It runs continuously, executing tasks
     * dispatched by the V8 runtime. This allows for efficient handling of asynchronous operations such as JavaScript
     * execution and event processing.
     * </p>
     * 
     * @return The handler thread instance managing the V8 runtime event loop.
     */
    public Thread getHandler() {
        return this.handler;
    }

    /**
     * Injects the current instance of the class into the global scope of the V8 JavaScript runtime.
     * This allows JavaScript code to access and interact with the Java object by referencing it as "v8Mixin".
     * 
     * <p>
     * This method sets a global property named "v8Mixin" to the current instance of the class, effectively
     * exposing the Java object to JavaScript. This is useful for scenarios where JavaScript needs to utilize
     * functionalities provided by the Java side, enabling seamless integration between the two environments.
     * </p>
     * 
     * <p>
     * Note: It is recommended to include appropriate error handling to manage any exceptions that may occur
     * during the injection process.
     * </p>
     */
    void mixinModule() {
        try {
            this.v8Runtime.getGlobalObject().set("v8Mixin", this);
        } catch (JavetException e) {}
    }

    /**
     * Initializes global Java package aliases in V8 runtime global scope.
     * Exposes commonly used Java packages (java, org, com, javax) as global variables
     * for convenient access from JavaScript code via 'Packages' namespace.
     */
    void initializeModule() {
        try {
            console = this.v8Runtime.getGlobalObject().get("console");

            this.v8Runtime.getExecutor("process.on('uncaughtException', (errorType, origin) => {console.error(errorType);v8Mixin.exitHandlerThread(v8Mixin.getHandler())});").execute();
            this.v8Runtime.getExecutor("const Packages = javet.package;const java = Packages.java;const org = Packages.org;const com = Packages.com;const javax = Packages.javax;").execute();
        } catch (JavetException e) {}
    }

    /**
     * Configures ECMAScript module resolution for Node.js compatibility.
     * Sets custom V8ModuleResolver that automatically prefixes module IDs with 'node:'
     * when not already present, enabling proper resolution of built-in Node.js modules.
     */
    void initializeEsModule() {
        this.v8Runtime.setV8ModuleResolver(new IV8ModuleResolver() {

            JavetBuiltInModuleResolver resolver = new JavetBuiltInModuleResolver();

            /**
             * Resolves module identifiers for ECMAScript module imports.
             * Automatically adds 'node:' prefix to module IDs that don't already contain it,
             * ensuring proper resolution of Node.js built-in modules through JavetBuiltInModuleResolver.
             * @param v8 V8 runtime instance for module resolution context
             * @param id module identifier string to be resolved
             * @param module parent module reference for relative resolution
             * @return resolved IV8Module instance
             * @throws JavetException if module resolution fails
             */
            @Override
            public IV8Module resolve(V8Runtime v8Runtime, String resourceName, IV8Module module) throws JavetException {
                Module moduleType = Module.getModule(resourceName);
                V8ValueObject NodeModule = null;

                switch (moduleType) {
                    case NODE, NODE_MODULE:
                        try {
                            NodeModule = v8Runtime.getExecutor("require('node:" + (resourceName.startsWith("node:") ? resourceName.substring(5) : resourceName) + "');").execute();
                        } catch(Exception moduleLoad) {
                            
                        }

                        if(NodeModule != null) {
                            return resolver.resolve(v8Runtime, resourceName.contains("node:") ? resourceName : "node:" + resourceName, module);
                        }
                    case CJS, ESModule:
                        try {
                            V8ValueObject v8Module = v8Runtime.getExecutor("require('" + resourceName +"')").execute();
                            v8Module.set("default", v8Module);

                            return v8Runtime.createV8Module(resourceName, v8Module);
                        } catch(Exception load) {
                            console.invoke("error", load.getMessage());
                        }
                    default:
                        break;
                }
                return null;
            }
        });
    }
}