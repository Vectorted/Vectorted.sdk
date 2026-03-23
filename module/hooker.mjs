/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

/**
 * Vector debugging attachment mode constants
 * @type {object}
 * @property {number} PEEKDATA - Read data mode
 * @property {number} POKEDATA - Write data mode
 * @property {number} ATTACH_MODE_READ - Read mode constant
 * @property {number} ATTACH_MODE_WRITE - Write mode constant
 */
const AttachMode = org.vector.lang.AttachMode;

/**
 * Java Integer wrapper class
 * @type {class}
 * @property {function} valueOf - Converts string/primitive to Integer
 * @property {function} parseInt - Parses string to int primitive
 * @property {number} MAX_VALUE - Maximum integer value
 * @property {number} MIN_VALUE - Minimum integer value
 */
const Integer = java.lang.Integer;

/**
 * Java ByteBuffer class for direct memory operations
 * @type {class}
 * @property {function} allocateDirect - Allocates direct byte buffer
 * @property {function} allocate - Allocates heap byte buffer
 * @property {function} wrap - Wraps byte array
 */
const ByteBuffer = java.nio.ByteBuffer;

/**
 * Java ByteOrder class for specifying byte order
 * @type {class}
 * @property {ByteOrder} BIG_ENDIAN - Big-endian byte order
 * @property {ByteOrder} LITTLE_ENDIAN - Little-endian byte order
 * @property {function} nativeOrder - Gets native platform byte order
 */
const ByteOrder = java.nio.ByteOrder;

/**
 * Get the PID identifier of the specified process
 * @param {string} target - Target process name
 * @returns {number} Process PID
 */
const getProcess = (target) => org.vector.lang.Process.getProcess(target);

/**
 * Attach debugger to the process with the specified PID
 * @param {number} pid - Target process PID
 * @returns {object} Debugger object
 */
const attachProcess = (pid) => vectorted.attachProcess(pid);

// Process PID
const pid = getProcess('target');

// Target process memory address
const address = BigInt(0x55f70a4142a0);

// Debugger instance
let system = attachProcess(pid);

if(system) {
    /**
     * Wait for the target process to interrupt
     * @param {number} pid - Target process PID
     */
    vectorted.waitProcess(pid);

    // Get struct base address
    let struct = vectorted.attachGetLongAddress(pid, AttachMode.PEEKDATA, address);
    
    /**
     * Get struct offset pointer
     * @param {bigint} address - Struct base address
     * @param {bigint} offset - Offset value
     */
    let countPtr = vectorted.attachGetLongAddress(pid, AttachMode.PEEKDATA, vectorted.offsetof(address, 0n));

    /**
     * Get integer value at specified address
     * @param {bigint} address - Memory address
     * @returns {number} Integer value
     */
    let value = vectorted.attachGetLongAddress(pid, AttachMode.PEEKDATA, countPtr);

    let vPrt = vectorted.attachGetLongAddress(pid, AttachMode.PEEKDATA, vectorted.offsetof(address, 20n));

    /*const hookString = [];

    for(let i = 0; i <= 10n; i++) {
        hookString.push(vectorted.toChar(vectorted.attachGetLongAddress(pid, AttachMode.PEEKDATA, vectorted.offsetof(dValue, i))));
    }*/

    console.log(`count(int*) = ${vectorted.toInt(value)} , v(float) = ${vectorted.toFloat(vPrt)}`);

    /**
     * Set integer value at specified address
     * @param {bigint} address - Memory address
     * @param {number} value - Value to set
     */
    vectorted.attachSetValueFromAddress(
        pid, 
        AttachMode.POKEDATA, 
        countPtr, 
        ByteBuffer.allocateDirect(1024)
            .order(ByteOrder.nativeOrder())
            .putInt(0, 100)
            .flip()
    );
}