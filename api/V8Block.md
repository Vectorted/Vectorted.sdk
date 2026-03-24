# V8Block Class

## Methods

* <a href="#clone">clone</a>
* <a href="#toobject">toObject</a>
* <a href="#exit">exit</a>

### clone
```js
clone(value: Object | V8Object): V8Object
```

Clone any V8 object, completely allocate new space.
#### Parameters
* value: object | V8Object

  V8 object to be cloned.

### toObject
```js
toObject(value: V8Object | Any): Object
```

Convert a V8Object object to a native Java object.
#### Parameters
* value: V8Object | Any

  V8 object to be converted.

### exit
```js
exit(): void
```

* Exit the V8Runtime/NodeRuntime engine thread, but do not terminate the process.
* This method is different from Process.exit, as it internally controls the V8/Node event loop.
