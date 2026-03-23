/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.client;

import org.vector.util.Io;
import org.vector.util.Loggor;
import org.vector.util.Loggor.Color;
import org.vector.util.Loggor.Image;

/**
 * Abstract base class for client initialization and management.
 * Provides common functionality and defines lifecycle methods for client implementations.
 */
public abstract class VectortedInitialize {

    /**
     * Protected constructor that enforces the Vectorted.Sdk framework instantiation policy.
     * 
     * <p>This constructor belongs to an abstract interface class and ensures that all
     * subclasses inheriting from Vectorted.Sdk comply with the framework's instantiation
     * constraints. It performs a runtime check to verify that the class is being
     * instantiated through the proper framework mechanisms rather than via direct
     * manual instantiation.</p>
     * 
     * <p><b>Enforcement Logic:</b>
     * <ul>
     *   <li>Detects if the instance was created via reflection (framework-controlled)</li>
     *   <li>If manual instantiation is detected (non-reflective), logs an error message
     *       and terminates the application with {@code System.exit(0)}</li>
     *   <li>Enforces that all subclasses must be started using {@code runModule(Class)}</li>
     * </ul>
     * </p>
     * 
     * <p><b>Usage Requirement:</b>
     * All classes extending or implementing this abstract interface must adhere to the
     * following constraint:
     * <ul>
     *   <li>Never call {@code new ClassName()} directly</li>
     *   <li>Always use the framework-provided {@code runModule(Class)} method</li>
     *   <li>Rely on the framework's dependency injection and lifecycle management</li>
     * </ul>
     * </p>
     * 
     * <p><b>Error Message Components:</b>
     * <ul>
     *   <li>{@code Image.ERROR}: Error icon/symbol</li>
     *   <li>{@code Color.SPACE}: Spacing formatting</li>
     *   <li>{@code Color.RED/GREEN/RESET}: Terminal color codes for visual emphasis</li>
     *   <li>{@code getClassId()}: Unique identifier of the violating class</li>
     * </ul>
     * </p>
     * 
     * @throws SecurityException If instantiation is attempted outside of framework control
     * 
     * @see #runModule(Class)
     * @see Io#isReflection(Object)
     * @see Loggor#log(String)
     * 
     * @note This is a defensive programming pattern to ensure consistent framework usage
     * @warning Violation of this constraint results in immediate application termination
     * 
     * @since Vectorted.Sdk 1.30.12
     */
    protected VectortedInitialize() {
        if(!Io.isReflection(this)) {
            Loggor.log(Image.ERROR + Color.SPACE + Color.RED +"Class " + Color.GREEN + "(" + getClassId() + ")" + Color.RED + " inherits from Vectorted.Sdk and must be started using runModule(Class) manual instantiation is not allowed." + Color.RESET);
            System.exit(0);
        }
    }

    /**
     * Terminates the application immediately.
     * Exits the JVM with status code 0 indicating normal termination.
     * This method should be called when the client needs to shut down completely.
     */
    public void quit() {
        System.exit(0);
    }

    /**
     * Retrieves the fully qualified class name of the subclass.
     * Returns the runtime class name including package structure.
     * Useful for logging, debugging, and identifying which client implementation is being used.
     * @return the fully qualified class name as String
     */
    public String getClassId() {
        return this.getClass().getName();
    }

    /**
     * Abstract method for graceful exit procedure.
     * Implementations should perform cleanup operations such as closing connections,
     * releasing resources, saving state, and preparing for application shutdown.
     * This method is called during JVM shutdown sequence before termination.
     */
    public abstract void exit();

    /**
     * Abstract method for client initialization.
     * Implementations should set up initial state, establish connections,
     * configure settings, and prepare the client for operational use.
     * This method is called once during client startup sequence.
     */
    public abstract void initialize();

    /**
     * Entry point for the client application.
     * This method is invoked when the application is launched.
     * If the {@code enabled} attribute of the {@code @Service} annotation is set to false,
     * the application will execute this method directly without initializing the service.
     * This allows for standalone execution of the client for testing or debugging purposes.
     * 
     * <p>
     * Implementations should define the necessary steps to run the client in standalone mode,
     * such as setting up local configurations, performing test operations, or simulating interactions.
     * </p>
     * 
     */
    public abstract void initializeSlave();

    /**
     * Abstract method for handling command execution.
     * Implementations should parse and execute commands received from
     * external sources such as console input, network requests, or IPC calls.
     * Commands may include administrative operations, configuration changes,
     * or application-specific actions.
     * @param command the command string to be processed and executed
     */
    public abstract void onCommand(String[] command);
}