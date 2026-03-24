# VectortedModule Class

## Methods

* <a href="#bindmodel">bindModel</a>
* <a href="#attachprocess">attachProcess</a>
* <a href="#detachprocess">detachProcess</a>
* <a href="#waitprocess">waitProcess</a>
* <a href="#attachgetlongaddress">attachGetLongAddress</a>
* <a href="#attachsetvaluefromaddress">attachSetValueFromAddress</a>
* <a href="#offsetof">offsetof</a>
* <a href="#tochar">toChar</a>
* <a href="#toint">toInt</a>
* <a href="#toshort">toShort</a>
* <a href="#tofloat">toFloat</a>
* <a href="#toboolean">toBoolean</a>
* <a href="#pointmodel">pointModel</a>
* <a href="#bind">bind</a>
* <a href="#bindslave">bindSlave</a>
* <a href="#startservice">startService</a>
* <a href="#stopservice">stopService</a>
* <a href="#startslaveservice">startSlaveService</a>
* <a href="#stopslaveservice">stopSlaveService</a>
* <a href="#sendslaveblock">sendSlaveBlock</a>
* <a href="#setiolist">setIoList</a>
* <a href="#setfloatvalue">setFloatValue</a>
* <a href="#setintvalue">setIntValue</a>
* <a href="#setlongvalue">setLongValue</a>
* <a href="#setboolvalue">setBoolValue</a>
* <a href="#syncclienttime">syncClientTime</a>
* <a href="#getnodelist">getNodeList</a>

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
* address: BigInt | long

  Target Address.

### attachSetValueFromAddress
```js
attachSetValueFromAddress(pid: Number | Long, attachMode: Number | Integer, address: BigInt | Long, value: Object | ByteBuffer | Any): void
```

Follow address changes in memory for the injected specified process.
#### Parameters
* pid: number | long

  Target Pid.
* attachMode: number | int

  Target AttachMode.
* address: BigInt | long

  Target Address.
* value: object | java.nio.ByteBuffer | any

  Set Value.

### offsetof
```js
offsetof(address: BigInt | Long, offsize: Bigint | Long): Long
```

Offset the specified address by the specified vector.
#### Parameters
* address: BigInt | long

  Target Address.
* offsize: BigInt | long

  Offset Target size.

### toChar
```js
toChar(address: BigInt | Long): Char
```

Convert address pointer to Char type.
#### Parameters
* address: BigInt | long

  Target Address.

### toInt
```js
toInt(address: BigInt | Long): Number | Integer
```

Convert address pointer to Integer type.
#### Parameters
* address: BigInt | long

  Target Address.

### toShort
```js
toShort(address: BigInt | Long): Number | Short
```

Convert address pointer to Short type.
#### Parameters
* address: BigInt | long

  Target Address.

### toFloat
```js
toFloat(address: BigInt | Long): Number | Float
```

Convert address pointer to Float type.
#### Parameters
* address: BigInt | long

  Target Address.

### toBoolean
```js
toBoolean(address: BigInt | Long): Boolean
```

Convert address pointer to Boolean type.
#### Parameters
* address: BigInt | long

  Target Address.

### pointModel
```js
pointModel(config: String): void
```

Configure and load the positioning model.
#### Parameters
* config: string

  model config path.

### bind
```js
bind(address: String, port: Number | Integer): void
```

Bind a specified IP port for MMS.
#### Parameters
* address: string

  bind address.
* port: number | long

  bind port.

### bindSlave
```js
bindSlave(address: String, port: Number | Integer): void
```

Bind a specified IP port for Slave.
#### Parameters
* address: string

  bind address.
* port: number | integer

  bind port.

### startService
```js
startService(handler: Handler | Any): Long
```

Start MMS service.
#### Parameters
* handler: any | org.vector.worker.Handler

  Java layer interface set, implements the Handler interface.
#### Returns
* threadId: BigInt | long

  Return threadId, it is needed to stop the service.

### stopService
```js
stopService(threadId: BigInt | Long): void
```

Stop MMS service.
#### Parameters
* threadId: BigInt | long

  Find the threadId to stop the corresponding service.

### startSlaveService
```js
startSlaveService(handler: Handler | Any): Long
```

Start Slave service.
#### Parameters
* handler: any | org.vector.worker.Handler

  Java layer interface set, implements the Handler interface.
#### Returns
* threadId: BigInt | long

  Return threadId, it is needed to stop the service.

### stopSlaveService
```js
stopSlaveService(threadId: BigInt | Long): void
```

Stop Slave service.
#### Parameters
* threadId: BigInt | long

  Find the threadId to stop the corresponding service.

### sendSlaveBlock
```js
sendSlaveBlock(modules: Array<Number> | int[], value: Number | Float): void
```

Send ASDU dataset command to the Slave server.
#### Parameters
* modules: Array | int[]

  Set module.
* value: number | float

  Set Value.

### setIoList
```js
setIoList(modules: Array<Number>, value: Number | Float): void
```

Send ASDU dataset command to the Slave server.
#### Parameters
* modules: Array

  Set module.
* value: number | float

  Set Value.

### setFloatValue
```js
setFloatValue(path: String, value: Number | Float): void
```

Send Float dataset command to the MMS server.
#### Parameters
* path: String

  point path.
* value: number | float

  Set Value.

### setIntValue
```js
setIntValue(path: String, value: Number | Integer): void
```

Send Integer dataset command to the MMS server.
#### Parameters
* path: String

  point path.
* value: number | integer

  Set Value.

### setLongValue
```js
setLongValue(path: String, value: Bigint | Long): void
```

Send Long dataset command to the MMS server.
#### Parameters
* path: String

  point path.
* value: bigint | long

  Set Value.

### setBoolValue
```js
setBoolValue(path: String, value: Number | Boolean): void
```

Send Boolean dataset command to the MMS server.
#### Parameters
* path: String

  point path.
* value: number | boolean

  Set Value.

### syncClientTime
```js
syncClientTime(path: String): void
```

Send Time dataset command to the MMS server.
#### Parameters
* path: String

  point path.

### getNodeList
```js
getNodeList(): Map<Number, Array<Any>> | HashMap<Integer, ArrayList<extends V8Object>>
```

Return all points of the MMS server model.
#### Returns
* DateSet: Map<Number, Array> | HashMap<Integer, ArrayList<V8Object>>
