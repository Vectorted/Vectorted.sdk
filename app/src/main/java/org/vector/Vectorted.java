/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector;

import java.io.File;

import org.vector.api.bind.V8Java;
import org.vector.client.VectortedInitialize;
import org.vector.client.VectortedModule;
import org.vector.engine.V8NodeEngine;
import org.vector.net.JDK;
import org.vector.net.MMS;
import org.vector.net.Module;
import org.vector.net.Service;
import org.vector.util.Io;
import org.vector.util.Loggor;
import org.vector.util.Loggor.Color;
import org.vector.util.Loggor.Image;
import org.vector.util.block.V8Block;
import org.vector.util.config.Config;
import org.vector.worker.Handler;

/**
 * Dual-protocol industrial communication server (IEC 61850 MMS / IEC 60870-5-104 Slave).
 * Extends ClientInitialize for lifecycle management and implements Handler for worker operations.
 *
 * <p><strong>Configuration Logic:</strong></p>
 * <ul>
 *   <li><strong>Protocol Selection:</strong>
 *     <ul>
 *       <li>{@code enabled = true}: Starts the IEC 61850 MMS server.</li>
 *       <li>{@code enabled = false}: Starts the IEC 60870-5-104 Slave server.</li>
 *     </ul>
 *   </li>
 *   <li><strong>Network Configuration:</strong>
 *     <ul>
 *       <li>{@code net = true}: Automatically detects and uses the local IP address.</li>
 *       <li>{@code net = false}: Uses the explicit IP address specified by the {@code address} parameter.</li>
 *     </ul>
 *   </li>
 *   <li><strong>Port Selection:</strong>
 *     <ul>
 *       <li>{@code enabled = true (MMS Mode)}: Uses the `PORT` setting from the configuration file specified by {@code @Module(config = "config.ini")}.</li>
 *       <li>{@code enabled = false (104 Slave Mode)}: Uses the port specified by the {@code port} parameter in the {@code @Service} annotation by default.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><strong>Current Configuration:</strong> Auto IP, MMS Mode (enabled=true). The port will be determined by the `PORT` item in the configuration file `config.ini`.</p>
 *
 * @see VectortedInitialize
 * @see Handler
 * @see Module
 * @see JDK
 * @see MMS
 * @see Service
 */
@MMS
@JDK(version = 21)
@Module(config = "config.ini")
@Service(address = "0.0.0.0", port = 10240, net = true, enabled = true, initialize = true)
public final class Vectorted<T> extends VectortedInitialize implements Handler {

    /**
     * 
     * Client interface instance for MMS/Slave server communication and data model operations.
     * Provides methods for binding to network endpoints, starting/stopping services,
     * and setting/getting values in the IEC 61850 data model.
     */
    VectortedModule client;

    /**
     * Identifier of the worker thread assigned by the MMS service for this handler instance.
     * Used to distinguish between different handler executions and for service management operations.
     * Value is assigned when startService() is called during initialization.
     */
    long threadId = 0;

    /**
     * IP address or hostname where the MMS server is bound and listening for client connections.
     * Retrieved from @Service annotation and used for network binding and logging purposes.
     */
    String address;

    /**
     * TCP port number on which the MMS server listens for incoming client connections.
     * Retrieved from @Service annotation and used for network binding and logging purposes.
     */
    int port;

    /**
     * Unique process identifier based on the class name for logging and identification purposes.
     * Set during construction using getClassId() method and used in log messages to identify
     * which service instance is generating output.
     */
    String processId;

    /** 
     * Unique identifier for a JavaScript script in V8 execution context.
     * Used to track, manage and reference individual scripts for execution control. 
     */
    String scriptId;

    /**
     * V8 JavaScript engine instance for Node.js runtime integration.
     * Provides JavaScript execution environment with JVM interop capabilities,
     * enabling execution of JavaScript code and interaction with Java classes.
     */
    V8NodeEngine v8Runtime;

    /**
     * V8Block - V8 engine internal API structure for inter-module and memory scheduling.
     * Manages resource allocation and communication between V8 internal components.
     * This is an internal class not intended for direct external usage.
     */
    V8Block v8Block;

    /**
     * Dedicated handler thread for V8 JavaScript runtime event loop processing.
     * Manages asynchronous V8 operations and ensures continuous execution of
     * JavaScript tasks without blocking the main application thread.
     */
    Thread handler;

    /**
     * Constructs App instance with specified network address and port.
     * Initializes ClientInterface and binds to the specified network endpoint.
     * Sets process identifier using the class name for logging purposes.
     * @param address IP address or hostname for MMS server binding
     * @param port TCP port number for MMS server binding
     */
    protected Vectorted(Config config, String address, int port) {
        this.client = new VectortedModule();
        String icd = config.getValue("ICD");

        String path = this.client.bindModel(icd);

        if(path == null) {
            Loggor.log(Color.RED + "Model generation failed. There may be a syntax error, or the model file " + Color.GREEN + "(" + icd + ")" + Color.RED + " does not exist." + Color.RESET);
            System.exit(0);
            return;
        }

        this.client.pointModel(path);

        this.address = address;
        this.port = port;
        this.processId = getClassId();

        this.v8Runtime = V8NodeEngine.getV8Runtime();
        this.v8Block = new V8Block(this.v8Runtime);
    }

    /**
     * Stops current V8 engine and reinitializes with fresh Node.js instance.
     * Hooks exit handler, closes existing runtime, and creates V8NodeEngine
     * to ensure clean state and release previous resources.
     * 
     * @see V8NodeEngine#close()
     * @see V8NodeEngine#exitHandlerThread(Thread)
     * @see V8NodeEngine#init()
     */
    void hookEngine() {
        this.v8Runtime.exitHandlerThread(this.handler);
        this.v8Runtime = this.v8Runtime.init();
        this.v8Block.checkRuntime(this.v8Runtime);

        this.v8Runtime.javaToV8Runtime("vectorted", this.client)
            .javaToV8Runtime("v8Runtime", this.v8Block)
            .javaToV8Runtime("$java", new V8Java<T>(v8Runtime.getNodeRuntime()));
            
    }

    /**
     * Starts CS104 slave service and populates all IEC 60870 addresses with test data.
     * Binds to specified endpoint, creates worker thread that continuously sends
     * address-value pairs (i→i) for all valid module addresses (16385-22878).
     * 
     * @param address IP address for slave binding (type: should be "address")
     * 
     * @param port TCP port for slave service (standard: 2404)
     * 
     * @note High CPU usage - iterates ~6500 addresses per tick continuously
     * @note For testing/demo only - not suitable for production
     * @note Self-referential mapping (address i = value i) for easy identification
     * 
     * @see VectortedModule#bindSlave(String, int)
     * @see VectortedModule#startSlaveService(Handler)
     */
    @Override
    public void initializeSlave() {
        Loggor.log(Image.CHECK + Color.SPACE + this.processId + ": Slave server has been started " + Image.ARROW + Color.SPACE + Color.BLUE + address + ":" + port + Color.RESET);

        this.client.bindSlave(this.address, this.port);
        this.client.startSlaveService(this);
    }

    /**
     * Initializes the MMS server service and starts background processing.
     * Starts the client service with this handler instance and logs successful startup.
     * Outputs startup confirmation message with process ID and server endpoint information.
     */
    @Override
    public void initialize() {
        this.client.bind(address, port);
        this.threadId = this.client.startService(this);
        Loggor.log(Image.CHECK + Color.SPACE + this.processId + ": MMS server has been started " + Image.ARROW + Color.SPACE + Color.BLUE + this.address + ":" + this.port + Color.RESET);
    }

    /**
     * Handles console command execution for service control.
     * Processes 'exit' and 'quit' commands by clearing MMS service cache and initiating shutdown.
     * Logs shutdown preparation message and calls quit() to terminate the application.
     * @param command the command string received from console input
     */
    @Override
    public void onCommand(String[] command) {
        switch (command[0]) {
            case "clear":
                Io.clear();
                break;

            case "v8-exit":
                hookEngine();
                Loggor.log(Image.CHECK + Color.SPACE + "Node.js engine has terminated the (" + Color.GREEN + this.scriptId + Color.RESET + ") script subthread.");

                break;

            case "reload":
                if(command.length < 2) {
                    Loggor.log(Image.ERROR + Color.SPACE + Color.RED + "Missing command parameter, correct command is reload [<file-path>]." + Color.RESET);
                    return;
                }

                File script = new File(Io.WORKER_DIR, command[1]);
                if(!script.exists()) {
                    Loggor.log(Image.ERROR + Color.SPACE + Color.RED + "Node.js failed to load the script, file does not exist." + Color.RESET);
                    return;
                }
                hookEngine();

                this.scriptId = script.getName();

                /**
                 * Starts V8 handler thread to execute script, enable Node.js event loop, and provide JVM access.
                 * The looper runs initial JavaScript code, maintains continuous V8/Node runtime execution for
                 * asynchronous operations, and enables JavaScript to interact with Java classes and objects
                 * through Javet JVM interception. Facilitates seamless integration between JavaScript and
                 * Java Virtual Machine for cross-language function ality.
                 */
                this.handler = this.v8Runtime.handlerLooper(new Runnable() {

                    @Override
                    public void run() {
                        Loggor.log(Image.CHECK + Color.SPACE + "Node.js engine is loading the (" + Color.GREEN + scriptId + Color.RESET + ") script and starting to initialize the components.");
                        v8Runtime.runScript(script);
                    }
                });
                break;
            case "exit", "quit":
                Loggor.log(Image.CHECK + Color.SPACE + this.processId + ": Clearing MMS/Slave service cache, the MMS server will be shut down shortly.");
                this.v8Runtime.exitHandlerThread(this.handler);

                quit();
            break;

            default:
                Loggor.print(!command[0].isEmpty() ? Image.ERROR + Color.SPACE + Color.RED + "Command" + Color.SPACE + Color.GREEN + "-" + command[0] + "-" + Color.SPACE + Color.RED + "please check if the " + Color.YELLOW + "(Vectorted)" + Color.RED + " has injected the command." + Color.RESET : null);
                break;
        }
    }

    /**
     * Executes periodic tick processing on worker thread.
     * Sets boolean value in MMS data model to trigger alarm indication.
     * Called automatically by the worker thread scheduler for real-time data updates.
     * @param threadId identifier of the worker thread executing this tick
     */
    @Override
    public void runTickOnWorkerThread(long threadId) {
        
    }

    /**
     * Gracefully stops the MMS/Slave server service and releases resources.
     * Stops the client service using the stored thread identifier.
     * Called during application shutdown sequence to ensure clean service termination.
     */
    @Override
    public void exit() {
        this.client.stopService(threadId);
        this.client.stopSlaveService(threadId);
    }
}