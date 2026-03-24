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
