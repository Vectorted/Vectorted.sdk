/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import org.vector.util.Io;
import org.vector.util.Loggor;
import org.vector.util.Loggor.Color;
import org.vector.util.Loggor.Image;
import org.vector.worker.Handler;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.V8ValueBoolean;
import com.caoccao.javet.values.reference.V8ValueArray;

/**
 * IEC 61850/60870-104 MMS/Slave client service interface
 * <p>
 * Provides MMS server binding, data manipulation, and background service management capabilities
 * based on libiec61850 library, supporting real-time industrial communication protocol processing.
 * </p>
 */
public class VectortedModule {

    /**
     * Directory instance representing the local cache folder path.
     * Used to store and retrieve cached ICD model files for IEC 61850 MMS server operations.
     * Caching model files improves initialization performance by avoiding repeated SCL file parsing.
     */
    File CACHEDIR = new File("./cache/");

    static {
        Loggor.log(Image.CHECK + Color.SPACE + Color.GREEN + "JVM is starting to load shared libraries." + Color.RESET);

        Io.loadLibrary("vectorted");
        Io.loadLibrary("iec61850");
        Io.loadLibrary("iec60870");
    }

    /**
     * Binds the IEC 61850 ICD model file by generating a cached CFG configuration file.
     * Checks and creates the cache directory if it doesn't exist, then calls the native
     * model generator to convert the ICD file into an optimized CFG format for faster loading.
     * Returns the absolute path of the generated CFG file, or null if generation fails.
     *
     * @param model The path to the source ICD model file (IEC 61850 SCL format)
     * @return Absolute path of the generated CFG cache file, or null on failure
     */
    public String bindModel(String model) {
        if(!CACHEDIR.exists()) {
            CACHEDIR.mkdirs();
        }

        try {
            Io.closeInput();
            File outFile = new File(CACHEDIR.getPath(), new File(model).getName().replaceAll("icd", "cfg"));
            System.out.println(outFile.getPath());
            com.libiec61850.tools.DynamicModelGenerator.main(new String[]{model, outFile.getAbsolutePath()});
            Io.openInput();

            return outFile.getAbsolutePath();
        } catch (FileNotFoundException e) {}
        Io.openInput();
        return null;
    }

    /**
     * Attaches the current process to the target process.
     * 
     * @param pid Process ID of the target process
     * @return true if attachment is successful, false otherwise
     */
    public native boolean attachProcess(long pid);

    /**
     * Detaches from the currently attached process.
     * 
     * @param pid Process ID of the process to detach from
     * @return true if detachment is successful, false otherwise
     */
    public native boolean detachProcess(long pid);

    /**
     * Waits for attached process to stop.
     * Call after attachProcess() before memory operations.
     * 
     * @param pid Target process ID
     */
    public native void waitProcess(long pid);

    /**
     * Reads a value from the specified memory address in the attached process.
     * 
     * @param pid Process ID of the target process
     * @param attachMode Ptrace operation type for reading (e.g., AttachMode.PEEKDATA)
     * @param address Memory address to read from
     * @return The value read from the memory address
     */
    public native long attachGetLongAddress(long pid, int attachMode, long address);

    /**
     * Writes a value to the specified memory address in the attached process.
     * 
     * @param pid Process ID of the target process
     * @param attachMode Ptrace operation type for writing (e.g., AttachMode.POKEDATA)
     * @param address Memory address to write to
     * @param value Value to write
     */
    public native void attachSetValueFromAddress(long pid, int attachMode, long address, ByteBuffer value);

    /**
     * Calculate memory offset from base address
     * 
     * @param address the base memory address
     * @param offsize the byte offset to add
     * @return the resulting memory address (base + offset)
     * 
     * @see #attachGetAddress
     * @see #attachSetValueFromAddress
     */
    public native long offsetof(long address, long offsize);

    /**
     * Converts a memory address to a char.
     *
     * @param address memory address to convert
     * @return character representation of the address
     */
    public native char toChar(long address);

    /**
     * Converts a memory address to an int.
     *
     * @param address memory address to convert
     * @return integer representation of the address
     */
    public native int toInt(long address);

    /**
     * Converts a memory address to a short.
     * Extracts the lower 16 bits of the address.
     *
     * @param address memory address to convert
     * @return 16-bit short value from the address
     */
    public native short toShort(long address);

    /**
     * Converts a memory address to a float.
     *
     * @param address memory address to convert
     * @return floating-point representation of the address
     */
    public native float toFloat(long address);

    /**
     * Converts a memory address to a boolean.
     * Non-zero address returns true, zero returns false.
     *
     * @param address memory address to convert
     * @return boolean representation of the address
     */
    public native boolean toBoolean(long address);

    /**
     * Native method that loads the IEC 61850 configuration (CFG) file into the MMS server.
     * This JNI method calls the underlying C library to parse and activate the model configuration,
     * enabling the server to expose the data points defined in the IEC 61850 model.
     *
     * @param config Absolute path to the CFG configuration file generated by bindModel()
     */
    public native void pointModel(String config);
    
    /**
     * Binds MMS server to specified network endpoint
     * 
     * @param address IP address for service binding
     * @param port Service listening port number
     */
    public native void bind(String address, int port);

    /**
     * Binds CS104 slave service to specified network endpoint for IEC 60870-5-104 protocol.
     * Creates and configures slave service for receiving client connections from SCADA systems.
     * 
     * @param address IP address for slave service binding ("0.0.0.0" for all interfaces)
     * @param port TCP port for slave service (standard IEC 60870-5-104 port is 2404)
     * @throws IllegalStateException if slave service creation fails
     * @see #startSlaveService(Handler)
     */
    public native void bindSlave(String address, int port);

    /**
     * Starts CS104 slave service and creates worker thread for continuous operation.
     * Sets server mode, starts slave service, and creates detached thread that runs
     * until explicitly stopped. The handler callback can be used for periodic tasks.
     * 
     * @param handler Callback handler for worker thread operations (may be null if not needed)
     * @return Native thread handle for service control, or 0 on failure
     * @throws IllegalStateException if slave service is not properly initialized
     * @see #bindSlave(String, int)
     * @see #stopSlaveService(long)
     */
    public native long startSlaveService(Handler handler);

    /**
     * Stops CS104 slave service and cleans up associated resources.
     * Signals worker thread to terminate, stops slave service, and releases all allocated resources.
     * 
     * @param threadId Thread handle returned by {@link #startSlaveService(Handler)}
     * @throws IllegalArgumentException if threadId is invalid (0 or already stopped)
     * @see #startSlaveService(Handler)
     */
    public native void stopSlaveService(long threadId);

    /**
     * Sends scaled measured value to connected CS104 clients.
     * Creates and queues an IEC 60870-5-101/104 ASDU containing the specified value
     * for transmission to monitoring clients. Only accepts valid module addresses.
     * 
     * @param module IEC 60870 module address (object reference) in range [16385, 22879]
     * @param value Scaled integer value to send (typically 16-bit signed integer)
     * @throws IllegalArgumentException if module address is outside valid range
     * @note Uses CS101_COT_PERIODIC cause of transmission for regular updates
     * @note Quality is set to IEC60870_QUALITY_GOOD (all flags clear)
     */
    private final native void sendSlaveBlock(int[] module, float value);

    /**
     * Sets a value on the specified I/O list.
     * The method dispatches to either an integer or a float send operation
     * based on the runtime type of the provided value.
     *
     * @param <T> the type of the V8 value (constrained to V8Value)
     * @param ioList a V8 array containing the target I/O list indices as integers
     * @param value the V8 value to send (boolean or numeric string)
     * @throws JavetException if a V8 runtime error occurs
     * @throws NumberFormatException if value is a non-numeric string
     */
    @SuppressWarnings("unchecked")
    public <T extends V8Value> void setIoList(V8ValueArray ioList, V8Value value) throws JavetException {
        V8Runtime v8Runtime = ioList.checkV8Runtime();
        int[] list = ((ArrayList<Integer>)v8Runtime.toObject(ioList)).stream().mapToInt(Integer::intValue).toArray();

        if(value instanceof V8ValueBoolean) {
            this.sendSlaveBlock(list, ((V8ValueBoolean)value).asInt());
            return;
        }

        this.sendSlaveBlock(list, Float.parseFloat(value.asString()));
    }
    
    /**
     * Starts background data processing service
     * 
     * <p>Creates an independent native thread to execute the specified data processing routine.
     * This thread will:</p>
     * <ul>
     *   <li>Invoke {@link Handler#runTickOnWorkerThread(long)} method at fixed intervals</li>
     *   <li>Maintain real-time connection with IEC 61850 data models</li>
     *   <li>Provide thread-safe MMS data access capabilities</li>
     * </ul>
     * 
     * @param handler Data processing callback handler instance
     * @return Native thread control handle for subsequent service termination
     * @throws IllegalStateException when service startup fails
     */
    public native long startService(Handler handler);
    
    /**
     * Stops the specified background data processing service
     * 
     * @param threadId Thread handle returned by {@link #startService(Handler)}
     */
    public native void stopService(long threadId);
    
    /**
     * Sets floating-point data attribute value in IEC 61850 data model.
     * Updates the specified data attribute with the given floating-point value
     * in the MMS server's data model.
     * 
     * @param path IEC 61850 data object reference path (e.g., "TEMPLATECTRL02/AlmGGIO1.Ind9.stVal")
     * @param value Floating-point value to be set
     * @throws IllegalArgumentException if dataAttributePath is invalid or attribute not found
     * @throws IllegalStateException if MMS server is not initialized
     */
    public native void setFloatValue(String path, float value);

    /**
     * Set Int32 value to IEC 61850 server
     * 
     * @param path IEC 61850 object reference path (e.g., "DEVICE1/LD0/GGIO1.AnIn1.stVal")
     * @param value 32-bit integer value (-2,147,483,648 ~ 2,147,483,647)
     * @throws IllegalArgumentException if path is null/empty or value out of range
     * @throws RuntimeException if server operation fails
     * 
     */
    public native void setIntValue(String path, int value);

    /**
     * Set Int64 value to IEC 61850 server
     * 
     * @param path IEC 61850 object reference path (e.g., "DEVICE1/LD0/Clock.TimeStamp.t")
     * @param value 64-bit integer value (-9,223,372,036,854,775,808 ~ 9,223,372,036,854,775,807)
     * @throws IllegalArgumentException if path is null/empty or value out of range
     * @throws RuntimeException if server operation fails
     * 
     */
    public native void setLongValue(String path, long value);

    /**
     * Synchronize client time to specified timestamp attribute.
     * Sets current system time to IEC 61850 .t attribute for time synchronization.
     * 
     * @param path IEC 61850 object reference path ending with .t
     * @throws IllegalArgumentException if path is null or invalid
     * @throws RuntimeException if server operation fails
     * 
     */
    public native void clockLock(String path);

    /**
     * Sets boolean data attribute value in IEC 61850 data model.
     * Updates the specified data attribute with the given boolean value
     * in the MMS server's data model.
     * 
     * @param path IEC 61850 data object reference path (e.g., "TEMPLATECTRL02/AlmGGIO1.Ind9.stVal")
     * @param value Boolean value to be set
     * @throws IllegalArgumentException if dataAttributePath is invalid or attribute not found
     * @throws IllegalStateException if MMS server is not initialized
     */
    public native void setBoolValue(String path, boolean value);

    /**
     * Retrieves all nodes from the MMS server model.
     * 
     * <p>This method traverses the complete IEC 61850 data model and returns
     * all nodes organized by their hierarchical depth.</p>
     * 
     * @return A map where keys represent hierarchical depth and values are
     *         lists of {@link HashMap} objects at that depth.
     *         Returns an empty map if connection fails or no nodes found.
     * 
     */
    public native HashMap<Integer, ArrayList<Object>> getNodeList();
}
