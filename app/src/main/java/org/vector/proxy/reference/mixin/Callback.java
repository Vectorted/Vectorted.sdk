/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 *
 * GitHub: https://github.com/Vectorted
 */
package org.vector.proxy.reference.mixin;

/**
 * A generic callback interface primarily designed for reflective invocation mechanisms.
 * This interface is commonly used to handle callbacks, such as those from V8 JavaScript functions.
 */
public interface Callback {
    /**
     * Invokes the callback with the given arguments.
     * This method is intended for reflective calls, often used to execute a V8Function callback.
     *
     * @param varargs The variable arguments to pass to the callback function.
     * @return The result returned by the callback invocation.
     */
    Object call(Object... varargs);
}