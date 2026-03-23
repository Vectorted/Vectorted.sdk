/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */
package org.vector.proxy.reference.mixin;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;

import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.Super;

/**
 * ByteBuddy method handler for overriding existing parent class methods.
 * Intercepts Java method calls and delegates to V8 JavaScript implementations.
 */
public class DefineMethodHandler {

    /**
     * V8 JavaScript runtime for executing callback functions.
     */
    V8Runtime v8Runtime;

    /**
     * V8 JavaScript object containing method override implementations.
     */
    V8ValueObject v8Object;

    /**
     * Initializes handler with V8 object for method overrides.
     * 
     * @param v8Object V8 JavaScript object with override implementations
     */
    public DefineMethodHandler(V8ValueObject v8Object) {
        if(v8Object.isClosed()) return;

        try {
            this.v8Runtime = v8Object.checkV8Runtime();
            this.v8Object = v8Object;
        } catch(Exception init) {}
    }
    
    /**
     * ByteBuddy interceptor for overriding existing parent methods.
     * Invokes corresponding V8 JavaScript function to replace Java method behavior.
     * 
     * @param superObject Parent class instance via ByteBuddy proxy
     * @param extendClass Extended class instance
     * @param args Method arguments as array
     * @param method Original Java method being overridden
     * @param callback ByteBuddy morphing callback for super method invocation
     * @return JavaScript function return value (converted to Java object)
     * @throws JavetException If V8 execution fails
     */
    @RuntimeType
    public Object invoke(
        @Super(strategy = Super.Instantiation.UNSAFE, proxyType = TargetType.class) Object superObject, 
        @This Object extendClass, 
        @AllArguments Object[] args, 
        @Origin java.lang.reflect.Method method, 
        @Morph Callback callback) throws JavetException {
            V8ValueFunction function = this.v8Object.get(method.getName());
            this.v8Runtime.getGlobalObject().set("$super", superObject);

            V8Value rValue = function.call(v8Object, args);
            this.v8Runtime.getGlobalObject().delete("$super");

        return (!rValue.isNullOrUndefined()) ? v8Runtime.toObject(rValue) : null;
    }
}