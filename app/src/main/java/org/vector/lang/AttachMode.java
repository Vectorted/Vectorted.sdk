/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */
package org.vector.lang;

/**
 * PTrace operation types for process interaction (Linux).
 * Corresponds to the 'ptrace' request parameter.
 */
public class AttachMode {
    
    /** Attach to a process, making it a tracee (stops the target). */
    public static int ATTACH = 16; // PTRACE_ATTACH

    /** Detach from a process, allowing it to run freely. */
    public static int DETACH = 17; // PTRACE_DETACH

    /** Read a word from the tracee's memory at a given address. */
    public static int PEEKDATA = 2; // PTRACE_PEEKDATA

    /** Write a word to the tracee's memory at a given address. */
    public static int POKEDATA = 5; // PTRACE_POKEDATA
}