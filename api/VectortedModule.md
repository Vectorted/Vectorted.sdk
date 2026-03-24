# VectortedModule Class

## Methods

* <a href="#bindmodel">bindModel</a>
* <a href="#attachprocess">attachProcess</a>
* <a href="#detachprocess">detachProcess</a>
* <a href="#waitprocess">waitProcess</a>
* <a href="#attachgetlongaddress">attachGetLongAddress</a>
* <a href="#attachsetvaluefromaddress">attachSetValueFromAddress</a>
* <a href="">offsetof</a>
* <a href="">toChar</a>
* <a href="">toInt</a>
* <a href="">toShortf</a>
* <a href="">toFloat</a>
* <a href="">toBoolean</a>
* <a href="">pointModel</a>
* <a href="">bind</a>
* <a href="">bindSlave</a>
* <a href="">startService</a>
* <a href="">stopService</a>
* <a href="">startSlaveService</a>
* <a href="">stopSlaveService</a>
* <a href="">sendSlaveBlock</a>
* <a href="">setIoList</a>
* <a href="">setFloatValue</a>
* <a href="">setIntValue</a>
* <a href="">setLongValue</a>
* <a href="">setBoolValue</a>
* <a href="">syncClientTime</a>
* <a href="">getNodeList</a>

### bindModel
```js
bindModel(model: String): String
```

Locate the model file and generate the corresponding configuration file model.
#### Parameters
* model: string

  Preparing to parse the generated configuration model file path.

### attachProcess
```js
attachProcess(pid: Number | Long): Boolean
```

Inject into the specified process according to Pid.
#### Parameters
* pid: number | long

  Target Pid.

### detachProcess
```js
detachProcess(pid: Number | Long): Boolean
```

Exit the specified target process for injection.
#### Parameters
* pid: number | long

  Target Pid.

### waitProcess
```js
waitProcess(pid: Number | Long): void
```

Waiting for the process to be injected to pause.
#### Parameters
* pid: number | long

  Target Pid.

### attachGetLongAddress
```js
attachGetLongAddress(pid: Number | Long, attachMode: Number | Integer, address: BigInt | Long): Long
```

Start following the address to search memory for the injected process.
#### Parameters
* pid: number | long

  Target Pid.
* attachMode: number | int

  Target AttachMode.
* address: bigint | long

  Target Address.

### attachSetValueFromAddress
```js
attachSetValueFromAddress(pid: Number | Long, attachMode: Number | Integer, address: BigInt | Long, value: Object | ByteBuffer | Any): void
```

Follow address changes in memory for the injected specified process.
* pid: number | long

  Target Pid.
* attachMode: number | int

  Target AttachMode.
* address: bigint | long

  Target Address.
* value: object | java.nio.ByteBuffer | any

  Set Value.
