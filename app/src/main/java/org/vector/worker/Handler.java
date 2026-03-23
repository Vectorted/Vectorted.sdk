/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.worker;

import org.vector.client.VectortedModule;

/**
 * Background service execution handler interface
 * 
 * <p>
 * Implementations of this interface are periodically invoked in a dedicated native thread
 * for executing real-time data processing tasks in IEC 61850 MMS server environments.
 * </p>
 */
public interface Handler {
    
    /**
     * Periodic callback method executed within thread context
     * 
     * <p><strong>Execution Environment:</strong></p>
     * <ul>
     *   <li>Runs in a native POSIX thread managed by libiec61850 MMS service</li>
     *   <li>Thread priority follows system scheduling policy, no real-time guarantees</li>
     *   <li>Possesses independent JNI environment context, isolated from main Java thread</li>
     * </ul>
     * 
     * <p><strong>Functional Responsibilities:</strong></p>
     * <ul>
     *   <li>Process measurement value updates in IEC 61850 data models</li>
     *   <li>Execute device status monitoring and control logic</li>
     *   <li>Maintain real-time data synchronization with field devices</li>
     * </ul>
     * 
     * <p><strong>Parameter Specification:</strong></p>
     * <ul>
     *   <li>{@code threadId} - Native thread identifier, useful for distinguishing execution contexts in multi-instance scenarios</li>
     * </ul>
     * 
     * <p><strong>Usage Constraints:</strong></p>
     * <ul>
     *   <li>Method execution must maintain thread safety, avoid blocking operations</li>
     *   <li>Long-duration sleeps or synchronous waits are prohibited within this method</li>
     *   <li>Exception handling should be cautious; uncaught exceptions may cause thread termination</li>
     *   <li>Recommended execution cycle should be under 100ms to ensure responsiveness</li>
     * </ul>
     * 
     * <p><strong>Typical Application:</strong></p>
     * <pre>{@code
     * client.setFloatValue("TEMPLATECTRL02/measGGIO1.AnIn6.mag.f", measurement);
     * }</pre>
     * 
     * @param threadId Native thread identifier executing this method
     * @see VectortedModule#setFloatValue(String, float)
     * @see VectortedModule#startService(Handler)
     */
    void runTickOnWorkerThread(long threadId);

    /**
     * Pauses the current thread execution for the specified duration.
     * <p>
     * This utility method provides a convenient way to introduce delays in thread execution
     * using {@link Thread#sleep(long)} with proper exception handling. Commonly used for
     * timing control, polling operations, or simulating processing delays in asynchronous workflows.
     * </p>
     *
     * <p><strong>Behavior:</strong></p>
     * <ul>
     *   <li>Suspends current thread for <strong>at least</strong> the specified time in milliseconds</li>
     *   <li>Actual suspension time may exceed requested duration due to system timer resolution</li>
     *   <li>Interruptions are caught and logged without propagating the exception</li>
     * </ul>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * delay(1000);  // Pause for 1 second
     * processNextStep();
     * 
     * delay(500);   // Pause for 500ms between retries
     * retryOperation();
     * }</pre>
     *
     * @param time the length of time to sleep in milliseconds
     * @throws IllegalArgumentException if {@code time} is negative
     * @see Thread#sleep(long)
     * @see Thread#interrupt()
     */
    default void delay(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            System.err.println("Failed to dispatch the command.");
        }
    }
}