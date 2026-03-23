/*
 * Copyright (c) 2024-2025. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vector.proxy.reference;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.values.reference.V8ValueObject;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * The type Dynamic object auto closeable invocation handler.
 *
 * @param <T> the type parameter
 * @since 0.1.0
 */
public class DynamicObjectInvocationHandler<T> extends BaseDynamicObjectHandler<T> {
    /**
     * The constant ARGS for constructor.
     *
     * @since 0.3.0
     */
    protected static final String ARGS = "$";
    /**
     * The Dynamic object.
     *
     * @since 0.4.0
     */
    protected Object dynamicObject;

    /**
     * Instantiates a new Dynamic object auto closeable invocation handler.
     *
     * @param handle        the handle
     * @param type          the type
     * @param v8ValueObject the V8 value object
     * @since 0.1.0
     */
    public DynamicObjectInvocationHandler(long handle, Class<T> type, V8ValueObject v8ValueObject) {
        super(handle, type, v8ValueObject);
        dynamicObject = null;
    }

    @RuntimeType
    @Override
    public void close() throws Exception {
        super.close();
        dynamicObject = null;
    }

    /**
     * Gets dynamic object.
     *
     * @return the dynamic object
     * @throws NoSuchMethodException     the no such method exception
     * @throws InvocationTargetException the invocation target exception
     * @throws InstantiationException    the instantiation exception
     * @throws IllegalAccessException    the illegal access exception
     * @throws JavetException            the javet exception
     * @since 0.1.0
     */
    public Object getDynamicObject()
            throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException, JavetException {
        return null;
    }

    /**
     * Intercept method call.
     *
     * @param method      the method
     * @param arguments   the arguments
     * @param superObject the super object
     * @param superCall   the super call
     * @return the object
     * @throws Exception the exception
     * @since 0.1.0
     */
    @RuntimeType
    public Object interceptMethod(
            @Origin Method method,
            @AllArguments Object[] arguments,
            @Super(strategy = Super.Instantiation.UNSAFE, proxyType = TargetType.class) Object superObject,
            @SuperCall Callable<T> superCall) throws Exception {
        return superCall.call();
    }
}
