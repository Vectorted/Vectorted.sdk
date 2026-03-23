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
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueArray;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.vector.proxy.reference.mixin.ByteHandler;
import org.vector.proxy.reference.mixin.Callback;
import org.vector.proxy.reference.mixin.ConstructorHandler;
import org.vector.proxy.reference.mixin.CreateMethodHandler;
import org.vector.proxy.reference.mixin.DefineMethodHandler;

/**
 * The type Base dynamic object handler.
 *
 * @param <T> the type parameter
 * @since 0.4.0
 */
public abstract class BaseDynamicObjectHandler<T> implements AutoCloseable {
    /**
     * The constant CONSTRUCTOR_STRATEGY.
     *
     * @since 0.1.0
     */
    protected static final ConstructorStrategy CONSTRUCTOR_STRATEGY = ConstructorStrategy.Default.IMITATE_SUPER_CLASS;
    /**
     * The constant METHOD_CLOSE.
     *
     * @since 0.1.0
     */
    protected static final String METHOD_CLOSE = "close";
    /**
     * The constant SUPER.
     *
     * @since 0.4.0
     */
    protected static final String SUPER = "$super";
    /**
     * The Handle.
     *
     * @since 0.4.0
     */
    protected long handle;
    /**
     * The Type.
     *
     * @since 0.4.0
     */
    protected Class<T> type;
    /**
     * The V8 value object.
     *
     * @since 0.4.0
     */
    protected V8ValueObject v8ValueObject;

    /**
     * The inject subClass Method.
     * 
     */
    protected DefineMethodHandler defineMethod;

    /**
     * The methodId List.
     * 
     */
    protected ArrayList<String> methodId = new ArrayList<String>();

    /**
     * Instantiates a new Base dynamic object handler.
     *
     * @param handle        the handle
     * @param type          the type
     * @param v8ValueObject the V8 value object
     * @since 0.4.0
     */
    public BaseDynamicObjectHandler(long handle, Class<T> type, V8ValueObject v8ValueObject) {
        this.handle = handle;
        this.type = Objects.requireNonNull(type);
        this.v8ValueObject = v8ValueObject;
    }

    @Override
    public void close() throws Exception {
        if (v8ValueObject != null) {
            JavetResourceUtils.safeClose(v8ValueObject);
            v8ValueObject = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    /**
     * Gets handle.
     *
     * @return the handle
     * @since 0.4.0
     */
    public long getHandle() {
        return handle;
    }

    /**
     * Retrieves the V8 object's "package" property value representing the subclass name.
     * 
     * <p>This method accesses the "package" property of the V8 JavaScript object,
     * which contains the fully qualified class name of the generated subclass.</p>
     * 
     * @return String containing the subclass package/class name, or null if property
     *         doesn't exist or V8 access fails
     */
    public String getSubClass() {
        try {
            return this.v8ValueObject.getPropertyString("package");
        } catch (JavetException e) {
            return null;
        }
    }

    /**
     * Retrieves all property names (function names) defined on the V8 object.
     * 
     * <p>Returns the list of all own property names from the V8 JavaScript object.
     * These properties typically represent JavaScript functions that will be
     * mapped to Java methods in the generated subclass.</p>
     * 
     * @return List<String> containing all property/function names defined on the V8 object,
     *         or null if V8 access fails
     */
    public List<String> getFunctionList() {
        try {
            return this.v8ValueObject.getOwnPropertyNameStrings();
        } catch (JavetException e) {
           return null;
        }
    }

    /**
     * Checks if a method exists in the target subclass's method signature cache.
     * 
     * <p>This method performs two-phase checking:
     * <ol>
     *   <li>If method cache ({@code methodId}) is populated, checks cache directly</li>
     *   <li>Otherwise, loads all public methods from the subclass into cache and recurses</li>
     * </ol>
     * </p>
     * 
     * @param subClass The target Java class to check for method existence
     * @param method The method name to search for
     * @return true if the method exists in the subclass, false otherwise
     * 
     * @warning This method uses recursion and will infinite loop if cache remains empty
     */
    public boolean isSubClassMethod(Class<?> subClass, String method) {
        if(!this.methodId.isEmpty()) {
            return this.methodId.contains(method);
        }

        java.lang.reflect.Method[] methods = subClass.getMethods();

        for(java.lang.reflect.Method id : methods) {
            id.setAccessible(true);
            this.methodId.add(id.getName());
        }
        return isSubClassMethod(subClass, method);
    }

    /**
     * Retrieves interface classes specified in the V8 object's "implements" property.
     * 
     * <p>V8 JavaScript object can specify an "implements" property containing an array
     * of V8 values representing Java interface classes. This method extracts and
     * converts those V8 values to a Java array format.</p>
     * 
     * @return V8Value[] array containing interface class references, or null if
     *         property doesn't exist or V8 access fails
     */
    public V8Value[] getImpClasses() {
        try {
            V8ValueArray classes = this.v8ValueObject.get("implements");
            return classes.toArray();
        } catch (JavetException e) {
           return null;
        }
    }

    /**
     * Extends the ByteBuddy builder with interface implementations.
     * 
     * <p>Adds all interface classes specified in V8 object's "implements" property
     * to the generated subclass using ByteBuddy's {@code implement()} method.</p>
     * 
     * @param v8Values Array of V8 values representing interface classes
     * @param builder ByteBuddy builder to extend
     * @return Extended ByteBuddy builder with interfaces implemented
     * 
     * @param <T> The target class type
     */
    DynamicType.Builder<T> extendClass(V8Value[] v8Values, DynamicType.Builder<T> builder) {
        try {
            V8Runtime runtime = v8ValueObject.checkV8Runtime();
            DynamicType.Builder<T> genClass = builder;

            for(V8Value value : v8Values) {
                Class<?> classz = (Class<?>) runtime.toObject(value);
                genClass = genClass.implement(classz);
            }
            return genClass;
        } catch(Exception e) {}
        return builder;
    }

    /**
     * Injects constructor interception logic into the dynamically generated class.
     * 
     * Intercepts all constructors of the generated class and adds V8 JavaScript
     * constructor execution after the parent constructor call.
     * 
     * @param builder ByteBuddy builder for the dynamic class being generated
     * @param <T> Type parameter of the class being generated
     * @return Modified builder with constructor interception configured
     * 
     * @implNote Uses a two-phase interception chain:
     *           1. {@code SuperMethodCall.INSTANCE} - Calls parent class constructor
     *           2. {@code ConstructorHandler} - Executes V8 JavaScript constructor
     * 
     * @warning Constructor execution order:
     *          Parent constructor → V8 constructor handler
     * @see ConstructorHandler
     */
    DynamicType.Builder<T> injectConstructor(DynamicType.Builder<T> builder) {
        return builder.constructor(ElementMatchers.isConstructor()).intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.withDefaultConfiguration().to(new ConstructorHandler(v8ValueObject))));
    }

    /**
     * Injects a dynamic method creation handler for a specified method.
     * 
     * <p>Defines a new method in the generated subclass that delegates to
     * {@link CreateMethodHandler} for JavaScript function execution.</p>
     * 
     * @param methodName Name of the method to create
     * @param builder ByteBuddy builder to modify
     * @return Modified ByteBuddy builder with method injection
     * 
     * @param <T> The target class type
     */
    DynamicType.Builder<T> injectCreateMethod(String methodName, DynamicType.Builder<T> builder) {
        return builder.defineMethod(methodName, Object.class, Modifier.PUBLIC | Opcodes.ACC_VARARGS)
            .withParameter(V8Value[].class)
            .intercept(MethodDelegation.to(new CreateMethodHandler(v8ValueObject)));
    }

    /**
     * Injects a {@code getBytes} method into a dynamically generated class.
     * <p>
     * Dynamically creates a {@code public final byte[] getBytes()} method using ByteBuddy.
     * All invocations of this method are delegated to the provided {@link ByteHandler}.
     * The injected method is typically used to retrieve the bytecode representation of the current (enhanced) class at runtime.
     * </p>
     *
     * @param <T>         The original type being enhanced
     * @param builder     The current type builder for defining the new method
     * @param byteHandler The bytecode handler that processes {@code getBytes} calls.
     *                    Its {@link ByteHandler#invoke} method will be executed when {@code getBytes} is invoked.
     *
     * @return The type builder with the injected {@code getBytes} method, for chaining.
     *
     * @see ByteHandler#invoke
     */
    DynamicType.Builder<T> getBytes(DynamicType.Builder<T> builder, ByteHandler<T> byteHandler) {
        return builder.defineMethod("getBytes", byte[].class, Modifier.PUBLIC | Modifier.FINAL).intercept(MethodDelegation.to(byteHandler));
    }

    /**
     * Injects all V8 JavaScript functions as methods in the generated Java class.
     * 
     * <p>Processes all function properties from the V8 object and injects them
     * as Java methods in the generated subclass. For new methods, uses
     * {@link CreateMethodHandler}; for overriding existing methods, uses
     * {@link DefineMethodHandler}.</p>
     * 
     * @param methodList List of method/function names from V8 object
     * @param builder ByteBuddy builder to modify
     * @return Modified ByteBuddy builder with all methods injected
     * 
     * @param <T> The target class type
     */
    DynamicType.Builder<T> injectMethod(List<String> methodList, DynamicType.Builder<T> builder) {
        Iterator<String> iterator = methodList.iterator();
        DynamicType.Builder<T> genObject = builder;

        while(iterator.hasNext()) {
            String name = iterator.next();

            try {
                if((this.v8ValueObject.get(name) instanceof V8ValueFunction)) {
                    if(name.equals("constructor")) {
                        genObject = injectConstructor(genObject);
                        continue;
                    }
                    if(!isSubClassMethod(type, name)) {
                        genObject = injectCreateMethod(name, genObject);
                        continue;
                    }
                    genObject = genObject.method(ElementMatchers.named(name))
                        .intercept(MethodDelegation.withDefaultConfiguration()
                            .withBinders(Morph.Binder.install(Callback.class))
                            .to(defineMethod));
                }
            } catch (JavetException e) {}
        }
        this.methodId.clear();
        return genObject;
    }

    ClassLoader getClassLoader() {
        try {
            return (ClassLoader)this.v8ValueObject.checkV8Runtime().toObject(this.v8ValueObject.get("classLoader"));
        } catch (JavetException e) {
            return null;
        }
    }

    /**
     * Generates and loads the dynamic Java class from V8 JavaScript object definition.
     * 
     * <p>This is the main class generation method that orchestrates the entire
     * dynamic subclass creation process:
     * <ol>
     *   <li>Creates method handler for existing method overrides</li>
     *   <li>Defines subclass with specified name</li>
     *   <li>Implements all specified interfaces</li>
     *   <li>Injects all V8 functions as Java methods</li>
     *   <li>Loads the generated class into the JVM</li>
     * </ol>
     * Uses try-with-resources to ensure proper cleanup of ByteBuddy resources.</p>
     * 
     * @return Generated dynamic class implementing the V8 object's functionality
     * 
     * @param <T> The target class type
     * @since 0.3.0
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getObjectClass() {
        this.defineMethod = new DefineMethodHandler(v8ValueObject);

        DynamicType.Builder<T> builder = new ByteBuddy().subclass(type).name(getSubClass());
        builder = extendClass(getImpClasses(), builder);
        builder = injectMethod(getFunctionList(), builder);

        ByteHandler<T> byteHandler = new ByteHandler<T>(builder);
        builder = getBytes(builder, byteHandler);

        try (DynamicType.Unloaded<T> genTypeClass = builder.make()) {
            ClassLoader classLoader = getClassLoader();
            return (classLoader != null) ? (Class<T>) genTypeClass.load(classLoader).getLoaded() : (Class<T>) genTypeClass.load(getClass().getClassLoader()).getLoaded();
        }
    }

    /**
     * Gets type.
     *
     * @return the type
     * @since 0.4.0
     */
    public Class<T> getType() {
        return type;
    }
}
