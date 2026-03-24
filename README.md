# Vectorted.Sdk

Implement IEC61850/IEC60870-104 protocol endpoints and embed them into the Node.js/V8 platform as an embedded SDK API.

### API
NodeRuntime: <a href="https://github.com/Vectorted/Vectorted.sdk/blob/main/api.md">@Vectorted Module</a>

### Author

@Vectorted

* Email-QQ: `3537099724@qq.com`
* Microsoft: `xtyygdd123@outlook.com`

### Currently Compiling

* `JDK21`
* `Kali Linux(2025.4)`
* `GLIBC 2.41+`

### Environment

* `JDK21+`
* `X86_64`
* `Linux | Kali | Ubuntu/Debian`
* `GLIBC 2.41+`

### Build

To adapt to GLIBC, you need to compile it yourself. Please run vectorted-shared.sh. Before that, you should first install the libiec61850/iec60870-104 library.

```
Tree 
├── README.md
├── LICENSE.txt
├── /node_modules/
├── Point.mjs
├── config.ini
├── Template.icd
├── libvectorted.so
└── #
```

### Terminal Instruction Set
* `reload <script-file>`
* `v8-exit`
* `clear`
* `quit | exit`

### Network Model Configuration

* Before starting, please configure the config.ini file. If using SdkAPI, please follow the annotated Service attribute config to subscribe to the model configuration file.
* If the IP-Mode is IPv4, the local loopback address is used; if it is IP, the value of the IP field attribute is used. If it is neither of these two attributes or is empty, the debug source-level annotated address attribute is enabled.
* If TYPE is MMS, the 61850 protocol is enabled; if SLAVE, the 60870-104 protocol is enabled.
* ICD is the file path of the 61850 protocol model.

```ini
# ================================================
# IEC 61850 MMS/Salve Server Configuration File
# ================================================
# Author/Copyright: Vectorted
# Github: https://github.com/Vectorted
# Description: Configuration for IEC 61850 MMS Server
# File format: INI (Key-Value with sections)
# ================================================

[Vectorted.Sdk.service.v3]
# Section indicating the software author and copyright holder.
# This section typically contains metadata about the configuration file.

[PROXY-SOCKET]

IP-MODE = IPv4
# Defines the network address resolution mode. Accepted values: IPv4 or IP.
#  - IPv4: Forces the server to bind to the local loopback address (127.0.0.1).
#  - IP: Instructs the server to use the specific address defined in the `IP` key.
#  - If the value is empty, invalid, or none of the above, the configuration will be overridden by the value of the `address` attribute from the `@Service` annotation in the source code. This annotation takes the highest precedence.

IP = 0.0.0.0
# Specifies the IP address for the server to bind to. This key is conditionally effective.
#  - It is only used when `IP-MODE` is explicitly set to "IP".
#  - If `IP-MODE` is set to "IPv4", this value is ignored, and 127.0.0.1 is used instead.
#  - A typical value for binding to all network interfaces is "0.0.0.0".

TYPE = MMS
# Specifies the protocol server type to be instantiated and started. Accepted values:
#  - MMS: Initializes an IEC 61850 Manufacturing Message Specification (MMS) server.
#  - SLAVE: Initializes an IEC 60870-5-104 (IEC 104) slave/server.

[IEC61850]

ICD = Template.icd
# Path to the IEC 61850 SCL (System Configuration Language) model file.
# This file contains the data model definition for the MMS server.
# Supports relative paths (relative to application working directory) or absolute paths.
# Expected file extensions: .icd, .scd, .cid (IEC 61850 configuration files)

IEC61850-PORT = 102
# TCP port number for the MMS (Manufacturing Message Specification) server.
# This port will be overridden by the value specified in the @Module annotation's config section.
# Standard IEC 61850 MMS port is 102, but can be customized via Java annotation configuration.
# Ensure the configured port is available and not blocked by firewall.

[IEC104]

IEC104-PORT = 2404
# TCP port number for the IEC 60870-5-104 (IEC 104) protocol server.
# This is the actual effective configuration that will be used by the IEC 104 server.
# Standard IEC 104 port is 2404 (IANA registered port for IEC 60870-5-104).
# Ensure this port is available, not used by other services, and accessible through firewall rules.
# Note: Unlike other IP/PORT configurations, this port value is NOT overridden by Java annotations
# and will take effect as configured in this file.
```

## Let's go! Start writing a Node.js module to enable interaction.
### Node.js Module

#### Point.mjs
```js
/**
 * IEC 61850 Data Point Initialization Script
 * Loads ICD file and initializes all data points with default values
 * 
 * @copyright © Vectorted 2026. All rights reserved.
 * @license protected under copyright law - reproduction/distribution prohibited
 * @author Vectorted
 * @version Node.js v24.13.0
 */

import { writeFile } from 'fs/promises';

/**
 * Default values configuration for different IEC 61850 data types.
 * These values are applied to data points based on their detected type patterns.
 * 
 * @constant {Object} VALUES
 * @property {number} FLOAT - Default float value for magnitude float measurements (0.00)
 * @property {number} INT - Default integer value for magnitude integer measurements (1)
 * @property {boolean} BOOLEAN - Default boolean value for status points (true)
 * 
 * @example
 * Applied values based on data point patterns:
 * LD1/MMXU1.TotW.mag.f' → 88.88 (FLOAT)
 * LD1/MMXU1.TotW.mag.i' → 1 (INT)
 * LD1/GGIO1.Ind1.stVal' → true (BOOLEAN)
 */
const VALUES = {
    init() {
        return {
            FLOAT: new Date().getMilliseconds(),
            INT: Math.floor(Math.random() * 3 + 1),
            BOOLEAN: /*Math.floor(Math.random() * 2) ? true : false*/true
        }
    }
}

/**
 * Pattern-to-handler mapping configuration for IEC 61850 data point type detection.
 * Each handler defines:
 * 1. Pattern matching for data point paths
 * 2. Value setting function for the corresponding data type
 * 3. Java type reference for type validation (if applicable)
 * 
 * @constant {Array<Object>} HANDLER
 * @property {string} module - String pattern to search in data point paths
 * @property {Function} handler - Function to execute with matched value
 * @property {java.lang.Class} type - Corresponding Java type for cross-language validation
 * 
 * @typedef {Object} HANDLER
 * @property {string} module - Pattern string (e.g., '.mag.f', '.mag.i', '.stVal')
 * @property {Function} handler - Value setting function: (value: string) => void
 * @property {java.lang.Class<?>} type - Java class reference for type checking
 * 
 * @example
 * Handler configuration examples:
 * { module: '.mag.f', handler: setFloatValue, type: java.lang.Float }
 * { module: '.mag.i', handler: setIntValue, type: java.lang.Integer }
 * { module: '.stVal', handler: setBoolValue, type: java.lang.Boolean }
 * { module: '.t', handler: syncClientTime, type: [java.lang.Long, Date] }
 */
const HANDLER = [
    {
        module: '.mag.f',
        handler: (value) => vectorted.setFloatValue(value, VALUES.init().FLOAT),
        type: java.lang.Float
    },
    {
        module: '.mag.i',
        handler: (value) => vectorted.setIntValue(value, VALUES.init().INT),
        type: java.lang.Integer
    },
    {
        module: '.stVal',
        handler: (value) => vectorted.setBoolValue(value, VALUES.init().BOOLEAN),
        type: java.lang.Boolean
    },
    {
        module: '.t',
        handler: (value) => vectorted.syncClientTime(value),
        type: [java.lang.Long, Date]
    }
];

/**
 * Main data point initialization routine.
 * 
 * This function:
 * 1. Retrieves the complete data point hierarchy from the MMS service
 * 2. Processes each hierarchical level to extract valid data point arrays
 * 3. Applies pattern matching to determine data point types
 * 4. Sets appropriate default values based on detected types
 * 5. Exports the complete data point structure to JSON for debugging/reference
 * 
 * @sync
 * @throws {Error} If service.getNodeList() fails or returns invalid data
 * @throws {Error} If file write operation fails
 * 
 * @example
 * Expected data structure from service.getNodeList():
 * Map<number, Array<Array<string>>> where:
 * - Key: Hierarchical level (0=root/LD, 1=LN, 2=DO, 3=DA, etc.)
 * - Value: Array of data point arrays [path, name, description, type]
 * 
 * Example entry:
 * [
 *    [0, [['LD1', 'LogicalDevice1', 'Primary device', 'LD']]],
 *    [1, [['LD1/LLN0', 'LLN0', 'Logical node zero', 'LN'],
 *         ['LD1/GGIO1', 'GGIO1', 'Generic I/O', 'LN']]],
 *    [2, [['LD1/GGIO1.Ind1', 'Ind1', 'Indication 1', 'DO']]]
 *  ]
 */
const points = vectorted.getNodeList();

/**
 * Data point processing loop.
 * 
 * Iterates through the hierarchical data structure returned by getNodeList(),
 * filtering for valid array-type data points and applying type-specific
 * value initialization based on pattern matching.
 * 
 * @param {Map<number, Array>} points - Hierarchical data point structure
 * 
 * @algorithm
 * 1. For each [level, array] in points:
 *    a. Filter array elements that are themselves arrays (valid data points)
 *    b. For each data point array in filtered results:
 *        i. For each value in data point array:
 *            - Find matching handler based on pattern inclusion
 *            - Execute corresponding handler function
 * 
 * @timeComplexity O(n × m × p) where:
 *                n = number of levels
 *                m = data points per level
 *                p = values per data point
 * 
 * @spaceComplexity O(1) - In-place processing with minimal additional memory
 */
for(const [level, array] of points) {
    /**
     * Filters the current level's array to extract only array-type elements.
     * This removes any non-data-point metadata or malformed entries.
     * 
     * @constant {Array<Array>} paths - Filtered array containing only valid data point arrays
     */
    const paths = array.filter(value => Array.isArray(value));

    /**
     * Processes each valid data point array at the current hierarchical level.
     * 
     * @param {Array} path - Individual data point array containing path and metadata
     */
    for(let path of paths) {
        /**
         * Processes each value within the data point array to detect type patterns
         * and apply corresponding value initialization.
         * 
         * @param {string} value - Individual value from data point array (typically path string)
         */
        path.filter(value => {
            /**
             * Finds the first handler whose pattern is contained within the value string.
             * Uses Array.prototype.find() for early exit on first match.
             * 
             * @constant {Handler|void} point - Matched handler or undefined
             */
            const point = HANDLER.find(select => value.includes(select.module));
            
            /**
             * Executes the handler function if a pattern match was found.
             * Handler functions are responsible for setting the appropriate
             * value type in the MMS service.
             * 
             * @condition {boolean} point - Truthy if pattern match was successful
             */
            if(point) {
                point.handler(value);
                ((point, value) => console.log(point['path'] = value, point))(point, value);
            }
        });
    }
}

/**
 * Data export to JSON file.
 * 
 * Serializes the complete data point hierarchy to JSON format and writes
 * to disk for debugging, analysis, or archival purposes.
 * 
 * @constant {string} outputPath - File path for JSON output ('./point.json')
 * @param {string} outputPath - Relative path for the output JSON file
 * @param {string} jsonData - Pretty-printed JSON string with tab indentation
 * 
 * @async
 * @returns {Promise<void>} Resolves when file write completes
 * @throws {Error} If file system write operation fails
 * 
 * @example Generated point.json structure:
 * {
 *   "0": [
 *     ["LD1", "LogicalDevice1", "Primary device", "LD"]
 *   ],
 *   "1": [
 *     ["LD1/LLN0", "LLN0", "Logical node zero", "LN"],
 *     ["LD1/GGIO1", "GGIO1", "Generic I/O", "LN"]
 *   ]
 * }
 */
await writeFile('./point.json', JSON.stringify(points, null, '\t'));
```

### Terminal command

```shell
java -jar Vectorted.Sdk.jar
  > reload Point.mjs
  > quit
```

### Terminal log output
```shell
✔ JVM is starting to load shared libraries.
✔ Node.js has completed JVM virtualization.➜ v24.13.0
✔ org.vector.Vectorted: MMS server has been started ➜ 0.0.0.0:102
```
