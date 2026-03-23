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
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

/**
 * Constructor interceptor for dynamic class generation.
 * Intercepts parent class constructors and delegates to V8 JavaScript constructors.
 * Used with ByteBuddy to inject custom constructor logic.
 */
public class ConstructorHandler {

    /**
     * V8 JavaScript runtime instance.
     * Used to execute JavaScript code from Java.
     */
    V8Runtime v8Runtime;

    /**
     * V8 JavaScript object containing constructor function.
     * Holds the JavaScript constructor to be called during Java object instantiation.
     */
    V8ValueObject v8Object;

    /**
     * Initializes handler with V8 JavaScript object.
     * Extracts V8 runtime from the provided V8 object.
     * 
     * @param v8Object V8 JavaScript object with constructor definition
     */
    public ConstructorHandler(V8ValueObject v8Object) {
        try {
            this.v8Runtime = v8Object.checkV8Runtime();
            this.v8Object = v8Object;
        } catch(Exception e) {}
    }

    /**
     * ByteBuddy interceptor method for constructors.
     * Called when intercepted constructor is invoked. Delegates to V8 JavaScript constructor.
     * 
     * @param classObject The Java object being constructed (current instance)
     * @param arguments Constructor arguments passed from Java
     * 
     * @note This method intercepts parent class constructors. Parent constructor
     *       must be called separately via ByteBuddy's SuperMethodCall.INSTANCE.
     * @warning Ensure parent constructor is called before this interceptor
     *          to avoid JVM verification errors.
     */
    @RuntimeType
    public void invoke(
        @This Object classObject, 
        @AllArguments Object[] arguments) {
        try {
            V8ValueFunction constructor = this.v8Object.get("constructor");
            constructor.call(this.v8Object, arguments);
            
        } catch (JavetException e) {}
    }
}