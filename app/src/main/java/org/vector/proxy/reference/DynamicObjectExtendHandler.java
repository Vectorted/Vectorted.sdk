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

import com.caoccao.javet.values.reference.V8ValueObject;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * The type Dynamic object extend handler.
 *
 * @param <T> the type parameter
 * @since 0.4.0
 */
public class DynamicObjectExtendHandler<T> extends BaseDynamicObjectHandler<T> {
    /**
     * Instantiates a new Dynamic object extend handler.
     *
     * @param handle        the handle
     * @param type          the type
     * @param v8ValueObject the V8 value object
     * @since 0.4.0
     */
    public DynamicObjectExtendHandler(long handle, Class<T> type, V8ValueObject v8ValueObject) {
        super(handle, type, v8ValueObject);
    }

    @RuntimeType
    @Override
    public void close() throws Exception {
        super.close();
    }

    /**
     * Intercept method call.
     *
     * @param method      the method
     * @param arguments   the arguments
     * @param superObject the super object
     * @param thisObject  the this object
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
            @This Object thisObject,
            @SuperCall Callable<T> superCall) throws Exception {
        return superCall.call();
    }
}
