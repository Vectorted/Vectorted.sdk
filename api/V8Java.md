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
keepRunning(): Long
```

Keep Node.js in the event loop all the time, waiting for and processing tasks.
#### Returns
* loopId: BigInt | Long

  Returns the event loop ID, used for stopRunning.
