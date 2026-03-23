/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.util.block;

import org.vector.engine.V8NodeEngine;

import com.caoccao.javet.annotations.V8Function;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.values.reference.V8ValueObject;

/**
 * A utility class providing operations for V8/Node.js runtime interaction and object manipulation.
 */
public class V8Block {

    V8NodeEngine v8Runtime;

    /**
     * Constructs a V8Block instance associated with the given V8NodeEngine runtime.
     * @param v8Runtime The V8/Node.js runtime engine.
     */
    public V8Block(V8NodeEngine v8Runtime) {
        this.v8Runtime = v8Runtime;
    }

    /**
     * Selects the V8 JavaScript runtime environment for execution.
     * This method configures the V8 engine instance that will be used
     * for processing JavaScript code within the Java application.
     * 
     * @param v8Runtime V8 JavaScript runtime engine instance to be used
     */
    public void checkRuntime(V8NodeEngine v8Runtime) {
        this.v8Runtime = v8Runtime;
    }
    
    /**
     * Creates a deep copy of a V8 object using the underlying V8 API, allocating new memory.
     * @param v8Object The source V8 object to clone.
     * @return A new cloned V8ValueObject, or null if the source is closed.
     * @throws JavetException If a V8 runtime error occurs.
     */
    @V8Function(name = "clone")
    public V8ValueObject clone(V8ValueObject v8Object) throws JavetException {
        if(v8Object.isClosed()) return null;
        return v8Object.toClone(false);
    }

    /**
     * Converts a V8ValueObject to a standard Java Object.
     * @param v8Object The V8 object to convert.
     * @return The corresponding Java object representation.
     * @throws JavetException If a conversion error occurs.
     */
    @V8Function(name = "toObject")
    public Object toObject(V8ValueObject v8Object) throws JavetException {
        return v8Object.checkV8Runtime().toObject(v8Object);
    }

    /**
     * Exits the Node.js event loop, terminating synchronous code execution.
     * Unlike process.exit(), this method does not terminate the entire process.
     */
    @V8Function(name = "exit")
    public void exit() {
        this.v8Runtime.exitHandlerThread(this.v8Runtime.getHandler());
    }
}