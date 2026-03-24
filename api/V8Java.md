# V8Java Class

## Methods

* <a href="#keeprunning">keepRunning</a>
* <a href="#stoprunning">stopRunning</a>
* <a href="#promise">promise</a>
* <a href="#getclasess">getClasses</a>
* <a href="#tojavabytes">toJavaBytes</a>
* <a href="#tojavaintarray">toJavaIntArray</a>
* <a href="#extend">extend</a>
* <a href="#loadjar">loadJar</a>
* <a href="#findclassbyloader">findClassByLoader</a>
* <a href="#findclass">findClass</a>

### keepRunning
```js
keepRunning(): BigInt | Long
```

Keep Node.js in the event loop all the time, waiting for and processing tasks.
#### Returns
* loopId: BigInt | Long

  Returns the event loop ID, used for stopRunning.

### stopRunning
```js
stopRunning(loopId: BigInt | Long): void
```

Stop the event loop according to the specified event loop ID until there are no IDs, eventually stopping the Node.js event loop.
#### Parameters
* loopId: BigInt | Long

  Event loop ID to be terminated.

### promise
```js
promise(callback: () => Promise<T>): Promise<T>
```

Start an asynchronous loop Promise, but unlike a regular Promise, its state is absorbed, and Node.js will not exit the event loop before it is resolved or rejected.
#### Parameters
* callback: Promise

  Promise callback returned when the Promise is completed.

### getClasses
```js
getClasses(className: String): Class<T>
```
```js
getClasses(className: String, byteCode: int[]): Class<T>
```

Load bytecode based on class name.
#### Parameters
* className: String

  The name of the class to be loaded.
* byteCode: byte[]

  Bytecode array.

### toJavaBytes
```js
toJavaBytes(value: V8Array): byte[]
```

Convert V8 array to Java bytecode array.
#### Parameters
* value: V8Array

  V8 array to be converted to Java bytecode array.

### toJavaIntArray
```js
toJavaIntArray(value: V8Array): int[]
```

Convert V8 array to Java int array.
#### Parameters
* value: V8Array

  V8 array to be converted to Java int array.

### extend
```js
extend(value: V8Object, classz: Class<T>): Class<T>
```

Grant V8 objects the ability to inherit Java classes, and ultimately generate Java class objects. This method dynamically generates bytecode.
#### Parameters
* value: V8Object

  V8 object that will inherit a Java class.
* classz: Class

  Inherited Java class.

### loadJar
```js
loadJar(jar: String): ClassLoader
```

Load the specified jar package and return the class loader.
#### Parameters
* jar: String

  The jar package to be loaded.

### findClassByLoader
```js
findClassByLoader(classLoader: ClassLoader, className: String): Class<T>
```

Load the specified class according to the specified class loader.
#### Parameters
* classLoader: ClassLoader

  The class loader used.
* className: String

  Class to be loaded.

### findClass
```js
findClass(className: String): Class<T>
```

Use the class loader already loaded by the JVM to find and load the specified class.
#### Parameters
* className: String

  Class to be loaded.
