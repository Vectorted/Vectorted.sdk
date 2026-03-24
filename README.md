# Vectorted.Sdk

Implement IEC61850/IEC60870-104 protocol endpoints and embed them into the Node.js/V8 platform as an embedded SDK API.

### Author

@Vectorted

1. Email-QQ: 3537099724@qq.com
2. Microsoft: xtyygdd123@outlook.com

### Currently Compiling

1. JDK21
2. Kali Linux(2025.4)
3. GLIBC 2.41+

### Environment

1. JDK21+
2. X86_64
3. Linux | Kali | Ubuntu/Debian
4. GLIBC 2.41+

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
1. `reload <script-file>`
2. `v8-exit`
3. `clear`
4. `quit | exit`

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

/*const ioM = Array.from({length: 22879 - 16385}, (item, count) => 16385 + count);
const ioB = Array.from({length: 4001}, (item, count) => 1 + count);
const ioA = Array.from({length: 2}, (item, count) => 25601 + count);

setTimeout(() => {
    vectorted.setIoList(ioM, 19.22);
    vectorted.setIoList(ioB, true);
    vectorted.setIoList(ioA, 39);
}, 1000)*/

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
