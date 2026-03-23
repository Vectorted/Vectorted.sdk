/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */
package org.vector.proxy.reference.mixin;

import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.Super;
import net.bytebuddy.implementation.bind.annotation.This;

/**
 * Bytecode handler for dynamically generated classes.
 * <p>
 * Intercepts method calls via ByteBuddy delegation and provides access to
 * the dynamically generated class's bytecode representation. Primarily used
 * for runtime class enhancement and bytecode manipulation scenarios.
 * </p>
 */
public class ByteHandler<T> {

    /**
     * The ByteBuddy type builder for the dynamically generated class.
     * Used to retrieve the final bytecode representation via {@link DynamicType#make()}.
     */
    DynamicType.Builder<T> builder;

    /**
     * Constructs a new ByteHandler with the specified type builder.
     *
     * @param builder the ByteBuddy type builder for the dynamic class.
     *                Must not be {@code null}.
     * @throws NullPointerException if {@code builder} is {@code null}.
     */
    public ByteHandler(DynamicType.Builder<T> builder) {
        this.builder = builder;
    }
    
    /**
     * ByteBuddy interceptor for dynamic method calls.
     * <p>
     * When the dynamically generated class's {@code getBytes()} method is invoked,
     * this interceptor is triggered. It retrieves and returns the bytecode
     * representation of the dynamically built class.
     * </p>
     * 
     * @param superObject Parent class instance via ByteBuddy proxy (unsafe instantiation).
     * @param extendClass Extended class instance (the dynamically generated proxy object).
     * @param method Original Java method being intercepted (expected to be {@code getBytes()}).
     * @return The bytecode array representing the dynamically generated class.
     *         Never returns {@code null}.
     */
    @RuntimeType
    public final byte[] invoke(
        @Super(strategy = Super.Instantiation.UNSAFE, proxyType = TargetType.class) Object superObject, 
        @This Object extendClass,
        @Origin java.lang.reflect.Method method) {
            return this.builder.make().getBytes();
    }
}