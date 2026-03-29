/*
 * Copyright (c) 2026 Vectorted
 * All rights reserved.
 *
 * This software is the proprietary property of Vectorted.
 * Unauthorized copying, modification, distribution, or use of this software is strictly prohibited.
 * 
 * GitHub: https://github.com/Vectorted
 * 
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>
#include <pthread.h>
#include <stddef.h>
#include <unistd.h>
#include <math.h>
#include <sys/ptrace.h>

#include <libiec61850/iec61850_server.h>
#include <lib60870/cs104_slave.h>
#include <libiec61850/hal_thread.h>

#include "jni.h"
#include "libvector.h"

/**
 * IEC 61850 data model instance containing all configured logical devices and data points.
 * This object represents the complete SCL-configured substation model used by the MMS server.
 * Must be initialized with pointModel() before starting the server.
 */
IedModel* model;

/**
 * Main IEC 61850 MMS server instance handling client connections and data exchange.
 * Manages the protocol stack, client sessions, and provides access to the data model.
 * Created after model initialization and destroyed on server shutdown.
 */
IedServer service;

/**
 * IEC 60870-5-104 slave/server instance for legacy protocol compatibility.
 * Enables communication with SCADA systems using the telecontrol standard protocol.
 * Operates concurrently with the IEC 61850 MMS server on a separate port.
 */
CS104_Slave slave_service;

/**
 * Application layer parameters for IEC 60870-5-104 communication configuration.
 * Contains protocol-specific settings like ASDU addressing, timeouts, and data formats.
 * Must be configured before starting the 104 slave service.
 */
CS101_AppLayerParameters params;

/**
 * Thread execution state constant indicating the thread is active and running.
 * Used by both iec_thread and slave_thread structures to monitor thread lifecycle.
 */
#define THREAD_EXECUTION_STATE_ACTIVE 1

/**
 * Thread execution state constant indicating the thread has terminated.
 * Used by both iec_thread and slave_thread structures to monitor thread lifecycle.
 */
#define THREAD_EXECUTION_STATE_TERMINATED 0

/**
 * @brief Initial capacity for dynamic data structures.
 * @details Defines the starting size for arrays or collections that need to
 *          grow dynamically. Used to avoid frequent reallocations during
 *          initial population.
 */
#define INITIAL_CAPACITY 1000

/**
 * @brief Capacity increment factor for dynamic expansion.
 * @details Specifies the amount to increase capacity when a data structure
 *          reaches its limit. Used with dynamic arrays to control growth rate.
 */
#define CAPACITY_INCREMENT 500

/**
 * @brief Maximum direct child nodes per parent in hierarchical structures.
 * @details Limits the number of immediate children any parent element can have.
 *          Used in tree-like data models to control branching factor.
 */
#define MAX_CHILDREN_PER_POINT 100

/**
 * @brief Maximum length for descriptive text strings.
 * @details Defines the character buffer size for storing descriptive information
 *          such as labels, comments, or annotations for data points.
 */
#define MAX_POINT_DESC_LENGTH 256

/**
 * @brief Maximum length for hierarchical path strings.
 * @details Defines the character buffer size for storing full hierarchical paths
 *          (e.g., object references, file paths, or tree node identifiers).
 */
#define MAX_POINT_PATH_LENGTH 512

/**
 * @struct ChildNodeInfo
 * @brief Structure representing child node information
 * @details Stores the complete path of a child node
 */
typedef struct {
    char path[MAX_POINT_PATH_LENGTH];  /**< Complete path of the child node */
} ChildNodeInfo;

/**
 * @struct PointInfo
 * @brief Structure representing a data point with all its child nodes
 * @details Contains main path, description, type, and all child node paths
 */
typedef struct {
    char mainPath[MAX_POINT_PATH_LENGTH];     /**< Main point path at DO level */
    char name[256];                            /**< Point name */
    int type;                                  /**< Node type (0=LD, 1=LN, 2=DO, 3=DA) */
    char description[MAX_POINT_DESC_LENGTH];  /**< Description from dU attribute */
    ChildNodeInfo children[MAX_CHILDREN_PER_POINT];  /**< Array of child node paths */
    int childCount;                            /**< Number of child nodes */
} PointInfo;

/**
 * @struct Points
 * @brief Dynamic array structure for storing points with automatic expansion
 * @details Manages dynamic allocation and resizing of PointInfo array
 */
typedef struct {
    PointInfo* data;           /**< Pointer to array of PointInfo structures */
    int count;                 /**< Number of elements currently stored */
    int capacity;              /**< Current capacity of the array */
} Points;

/**
 * @struct SendTask
 * @brief Data transmission task for IEC 60870-5 master communication.
 *
 * @var SendTask::con      Master connection handle
 * @var SendTask::asdu     ASDU container with data to transmit
 * @var SendTask::start    Start index of information objects
 * @var SendTask::end      End index of information objects (inclusive)
 */
typedef struct {
    IMasterConnection con;
    CS101_ASDU asdu;
    int start;
    int end;
} SendTask;

/**
 * Check if a long integer is within [min, max] (inclusive).
 * 
 * @param value Value to check
 * @param min   Minimum value (inclusive)
 * @param max   Maximum value (inclusive)
 * @return      true if in range, false otherwise
 * 
 * @note       Assumes min <= max. Time: O(1), Space: O(1).
 * 
 * @example    range(5, 1, 10) returns true
 * @example    range(15, 1, 10) returns false
 */
bool range(long value, long min, long max) {
    return (value >= min && value <= max);
}

/**
 * @brief Thread function for synchronized general interrogation (GI) transmission.
 * 
 * Sends periodic ASDUs containing information objects in batches.
 * Data type determined by object address ranges:
 * - 1-4000: Single-point information (M_SP_NA_1)
 * - 4097-4098: Double-point information (M_DP_NA_1)
 * - 16385-22879: Measured values, normalized (M_ME_NA_1)
 * - 24577-24577: Single command destination addresses (C_SC_NA_1)
 * - 24578-24834: Double command destination addresses (C_DC_NA_1)
 * - 25089-25099: Setpoint command destination addresses (C_SE_NC_1)
 * - 25601-25602: Integrated totals (M_IT_NA_1)
 * 
 * @note Remote control and setpoint addresses (24577-25099) are typically
 *       DOWNLINK command destinations and wouldn't be reported in uplink
 *       transmission during general interrogation, unless they are configured
 *       as reportable points for status feedback.
 * 
 * @param arg Pointer to SendTask with connection and address range
 * @return void* Always NULL
 */
void* syncLockGi(void* arg) {
    SendTask* task = (SendTask*)arg;

    IMasterConnection con = task->con;
    const int start = task->start;
    const int end = task->end;
    const int batchSize = 20;

    for (int i = start; i < end; i += batchSize) {
        /* Create ASDU with periodic cause of transmission (COT_PERIODIC) */
        CS101_ASDU asdu =
            CS101_ASDU_create(
                IMasterConnection_getApplicationLayerParameters(con),
                false,                      /* Not sequence of information objects */
                CS101_COT_PERIODIC,         /* Periodic transmission */
                0,                          /* Originator address (0 for not used) */
                1,                          /* Common address (ASDU address) */
                false,                      /* No test flag */
                false);                     /* No negative confirmation */

        /* Process each point in the current batch */
        for (int j = 0; j < batchSize && (i + j) < end; j++) {
            int point = i + j;
            InformationObject io = NULL;

            /* Create information object based on point address range */
            if (range(point, 1, 4000)) {
                /* Single-point information (binary status) */
                io = (InformationObject)SinglePointInformation_create(
                    NULL, point, false, IEC60870_QUALITY_GOOD);
            }
            else if (range(point, 4097, 4098)) {
                /* Double-point information (dual-state equipment status) */
                io = (InformationObject)DoublePointInformation_create(
                    NULL, point, 0, IEC60870_QUALITY_GOOD);
            }
            else if (range(point, 16385, 22879)) {
                /* Measured value, short format (normalized analog value) */
                io = (InformationObject)MeasuredValueShort_create(
                    NULL, point, 0, IEC60870_QUALITY_GOOD);
            }
            else if (range(point, 24577, 24577)) {
                /* Single command destination (remote control point) */
                io = (InformationObject)SingleCommand_create(
                    NULL, point, 0, false, IEC60870_QUALITY_GOOD);
            }
            else if (range(point, 24578, 24834)) {
                /* Double command destination (dual-state remote control point) */
                io = (InformationObject)DoubleCommand_create(
                    NULL, point, 0, false, IEC60870_QUALITY_GOOD);
            }
            else if (range(point, 25089, 25099)) {
                /* Setpoint command destination (normalized analog setpoint) */
                io = (InformationObject)SetpointCommandNormalized_create(
                    NULL, point, 0.0f, false, IEC60870_QUALITY_GOOD);
            }
            else if (range(point, 25601, 25602)) {
                /* Integrated totals (counter/energy totals) */
                io = (InformationObject)IntegratedTotals_create(
                    NULL, point, 
                    BinaryCounterReading_create(NULL, 0, 0, false, false, false));
            }

            /* Add to ASDU if object created successfully */
            if (io != NULL) {
                CS101_ASDU_addInformationObject(asdu, io);
                InformationObject_destroy(io);
            }
        }
        
        /* Send ASDU to master connection */
        IMasterConnection_sendASDU(con, asdu);
        CS101_ASDU_destroy(asdu);

        /* Delay between batches to avoid overwhelming the communication channel */
        Thread_sleep(5);
    }
    free(task);

    return NULL;
}

/**
 * @brief Handles master connection state changes.
 *
 * @param parameter User context pointer
 * @param con Master connection handle
 * @param event Connection event type
 */
static void connectionEventHandler(void* parameter, IMasterConnection con, CS104_PeerConnectionEvent event) {
    switch (event) {
        case CS104_CON_EVENT_CONNECTION_OPENED:
            /*printf("net: %p\n", con);*/
            break;

        case CS104_CON_EVENT_CONNECTION_CLOSED:
            /*printf("close: %p\n", con);*/
            break;

        default:break;
    }
}

/**
 * @brief Handles general interrogation requests.
 * 
 * Starts multiple threads to batch transmit all configured data to the master station
 * in response to a general interrogation command. Each thread handles a specific
 * Information Object Address (IOA) range corresponding to a data type.
 * 
 * Transmission ranges and corresponding IEC 60870-5-104 data types:
 * 1. 1-4000: Single-point information (M_SP_NA_1) - Binary status points
 * 2. 4097-4098: Double-point information (M_DP_NA_1) - Dual-state equipment status
 * 3. 16385-22879: Measured values, normalized (M_ME_NA_1) - Analog measurements
 * 4. 24577-24577: Single command (C_SC_NA_1) destinations - Single-point remote control targets
 * 5. 24578-24834: Double command (C_DC_NA_1) destinations - Dual-point remote control targets
 * 6. 25089-25099: Setpoint command (C_SE_NC_1) destinations - Analog setpoint targets
 * 7. 25601-25602: Integrated totals (M_IT_NA_1) - Energy/counter totals
 * 
 * @note Remote control and setpoint addresses (ranges 4-6) are typically the destinations
 *       for DOWNLINK commands from master. They are included here for completeness
 *       but normally wouldn't be reported in uplink transmission unless specifically
 *       configured as reportable points.
 * 
 * @param parameter User context pointer
 * @param con Master connection handle
 * @param asdu Interrogation ASDU containing the request
 * @param qoi Qualifier of interrogation (should be 20 for general interrogation)
 * @return bool Always returns true (command accepted)
 */
static bool interrogationHandler(void* parameter, IMasterConnection con, CS101_ASDU asdu, uint8_t qoi) {
    /* Send activation confirmation to master station */
    IMasterConnection_sendACT_CON(con, asdu, false);

    /* Transmission tasks for all configured IOA ranges */
    struct {
        pthread_t thread;
        SendTask* task;
        int start;
        int end;
    } threads[] = {
        {0, NULL, 16385, 22880},  /* Measured values, short format (M_ME_NA_1) */
        {0, NULL, 1, 4001},       /* Single-point information (M_SP_NA_1) */
        {0, NULL, 25601, 25603},  /* Integrated totals (M_IT_NA_1) */
        {0, NULL, 4097, 4099},    /* Double-point information (M_DP_NA_1) */
        {0, NULL, 24577, 24578},  /* Single command destination addresses (C_SC_NA_1) */
        {0, NULL, 24578, 24835},  /* Double command destination addresses (C_DC_NA_1) */
        {0, NULL, 25089, 25100}   /* Setpoint command destination addresses (C_SE_NC_1) */
    };

    /* Create and execute transmission threads for each IOA range */
    for (int i = 0; i < sizeof(threads)/sizeof(threads[0]); i++) {
        /* Allocate memory for transmission task structure */
        threads[i].task = (SendTask*)malloc(sizeof(SendTask));
        if (threads[i].task == NULL) {
            /* Memory allocation failed - clean up previously allocated tasks */
            for (int j = 0; j < i; j++) {
                free(threads[j].task);
            }
            return true;
        }
        
        /* Configure task parameters with connection and IOA range */
        threads[i].task->con = con;
        threads[i].task->asdu = asdu;
        threads[i].task->start = threads[i].start;
        threads[i].task->end = threads[i].end;
        
        /* Create and manage transmission thread (detached, but joined to ensure serial execution) */
        pthread_create(&threads[i].thread, NULL, syncLockGi, threads[i].task);
        pthread_detach(threads[i].thread);
        pthread_join(threads[i].thread, NULL);
    }

    /* Send interrogation termination to complete the general interrogation sequence */
    IMasterConnection_sendACT_TERM(con, asdu);
    return true;
}

/**
 * @brief Creates a new dynamic array with initial capacity
 * @param[in] initialCapacity Initial capacity of the array
 * @return Pointer to newly created Points, or NULL on failure
 */
static Points* PointArray_create(int init) {
    Points* points = (Points*)malloc(sizeof(Points));
    if (points == NULL) {
        return NULL;
    }

    points->data = (PointInfo*)malloc(init * sizeof(PointInfo));
    if (points->data == NULL) {
        free(points);
        return NULL;
    }

    memset(points->data, 0, init * sizeof(PointInfo));
    points->count = 0;
    points->capacity = init;

    return points;
}

/**
 * @brief Expands the dynamic array capacity
 * @param[in,out] arr Pointer to DynamicPointArray to expand
 * @param[in] newCapacity New capacity size
 * @return 1 if successful, 0 if failed
 */
static int DynamicPointArray_expand(Points* arr, int newCapacity) {
    if (arr == NULL || newCapacity <= arr->capacity) {
        return 0;
    }

    PointInfo* newData = (PointInfo*)realloc(arr->data, newCapacity * sizeof(PointInfo));
    if (newData == NULL) {
        return 0;
    }

    memset(newData + arr->capacity, 0, (newCapacity - arr->capacity) * sizeof(PointInfo));
    arr->data = newData;
    arr->capacity = newCapacity;

    return 1;
}

/**
 * @brief Adds a point to the dynamic array with automatic expansion
 * @param[in,out] arr Pointer to DynamicPointArray
 * @param[in] point Pointer to PointInfo to add
 * @return 1 if successful, 0 if failed
 */
static int DynamicPointArray_add(Points* arr, const PointInfo* point) {
    if (arr == NULL || point == NULL) {
        return 0;
    }

    if (arr->count >= arr->capacity) {
        int newCapacity = arr->capacity + CAPACITY_INCREMENT;
        if (!DynamicPointArray_expand(arr, newCapacity)) {
            return 0;
        }
    }

    memcpy(&arr->data[arr->count], point, sizeof(PointInfo));
    arr->count++;

    return 1;
}

/**
 * @brief Gets a point from the dynamic array at specified index
 * @param[in] arr Pointer to DynamicPointArray
 * @param[in] index Index of the point to retrieve
 * @return Pointer to PointInfo at index, or NULL if out of bounds
 */
static PointInfo* DynamicPointArray_get(Points* arr, int index) {
    if (arr == NULL || index < 0 || index >= arr->count) {
        return NULL;
    }

    return &arr->data[index];
}

/**
 * @brief Gets the number of points in the dynamic array
 * @param[in] arr Pointer to DynamicPointArray
 * @return Number of points, or 0 if arr is NULL
 */
static int DynamicPointArray_size(Points* arr) {
    return arr != NULL ? arr->count : 0;
}

/**
 * @brief Destroys a dynamic array and frees all allocated memory
 * @param[in] arr Pointer to DynamicPointArray to destroy
 */
static void DynamicPointArray_destroy(Points* arr) {
    if (arr != NULL) {
        if (arr->data != NULL) {
            free(arr->data);
        }
        free(arr);
    }
}

/**
 * @brief Retrieves the string representation of a node type
 * @param[in] type The node type (0=LD, 1=LN, 2=DO, 3=DA)
 * @return Pointer to the string representation of the node type
 */
static const char* getType(int type) {
    switch (type) {
        case 0: return "LD";
        case 1: return "LN";
        case 2: return "DO";
        case 3: return "DA";
        default: return "UNKNOWN";
    }
}

/**
 * @brief Recursively collects all child node paths
 * @param[in] node Pointer to the current model node
 * @param[out] children Array to store child node information
 * @param[in] maxChildren Maximum number of children to collect
 * @param[in] currentCount Current count of collected children
 * @return Updated count of collected children
 */
static int collectChildPaths(ModelNode* node, ChildNodeInfo* children, int maxChildren, int currentCount) {
    if (node == NULL || currentCount >= maxChildren) {
        return currentCount;
    }

    char objectReference[MAX_POINT_PATH_LENGTH];
    char* objRef = ModelNode_getObjectReference(node, objectReference);
    
    if (objRef != NULL) {
        strncpy(children[currentCount].path, objRef, MAX_POINT_PATH_LENGTH - 1);
        children[currentCount].path[MAX_POINT_PATH_LENGTH - 1] = '\0';
        currentCount++;
    }

    ModelNode* child = node->firstChild;
    while (child != NULL) {
        currentCount = collectChildPaths(child, children, maxChildren, currentCount);
        child = child->sibling;
    }

    return currentCount;
}

/**
 * @brief Extracts and stores the description from the dU attribute
 * @param[in] doNode Pointer to the data object node
 * @param[out] description Buffer to store the description
 * @param[in] maxLength Maximum length of the description buffer
 */
static void extractDescription(ModelNode* doNode, char* description, int maxLength) {
    description[0] = '\0';
    
    ModelNode* child = doNode->firstChild;
    while (child != NULL) {
        if (ModelNode_getType(child) == DataAttributeModelType) {
            const char* daName = ModelNode_getName(child);
            if (daName != NULL && strcmp(daName, "dU") == 0) {
                DataAttribute* da = (DataAttribute*)child;
                if (da->mmsValue != NULL) {
                    const char* desc = MmsValue_toString(da->mmsValue);
                    if (desc != NULL) {
                        strncpy(description, desc, maxLength - 1);
                        description[maxLength - 1] = '\0';
                    }
                }
                return;
            }
        }
        child = child->sibling;
    }
}

/**
 * @brief Collects child node paths for a given data object
 * @param[in] doNode Pointer to the data object node
 * @param[out] children Array to store child node information
 * @param[in] maxChildren Maximum number of children to collect
 * @return Number of child nodes collected
 */
static int collectChildrenForPoint(ModelNode* doNode, ChildNodeInfo* children, int maxChildren) {
    int childCount = 0;
    
    ModelNode* child = doNode->firstChild;
    while (child != NULL && childCount < maxChildren) {
        childCount = collectChildPaths(child, children, maxChildren, childCount);
        child = child->sibling;
    }
    
    return childCount;
}

/**
 * @brief Processes a single data object node and stores its information
 * @param[in] doNode Pointer to the data object node
 * @param[out] currentPoint Pointer to PointInfo structure to fill
 * @return 1 if successful, 0 if failed
 */
static int processDataObject(ModelNode* doNode, PointInfo* currentPoint) {
    char objectReference[MAX_POINT_PATH_LENGTH];
    char* objRef = ModelNode_getObjectReference(doNode, objectReference);
    const char* nodeName = ModelNode_getName(doNode);
    
    if (objRef == NULL || nodeName == NULL) {
        return 0;
    }

    strncpy(currentPoint->mainPath, objRef, MAX_POINT_PATH_LENGTH - 1);
    currentPoint->mainPath[MAX_POINT_PATH_LENGTH - 1] = '\0';
    
    strncpy(currentPoint->name, nodeName, 255);
    currentPoint->name[255] = '\0';
    
    currentPoint->type = DataObjectModelType;
    currentPoint->childCount = 0;

    extractDescription(doNode, currentPoint->description, MAX_POINT_DESC_LENGTH);
    currentPoint->childCount = collectChildrenForPoint(doNode, currentPoint->children, MAX_CHILDREN_PER_POINT);

    return 1;
}

/**
 * @brief Collects all data points at DO level including their child nodes with dynamic expansion
 * @param[in] serviceModel Pointer to the IedModel
 * @param[out] pointArray Pointer to DynamicPointArray to store collected points
 * @return 1 if successful, 0 if failed
 */
static int collectAllDataPoints(IedModel* serviceModel, Points* pointArray) {
    if (serviceModel == NULL || pointArray == NULL) {
        return 0;
    }
    PointInfo pointInfo;

    for (LogicalDevice* ld = serviceModel->firstChild; ld != NULL; ld = (LogicalDevice*)ld->sibling) {
        for (LogicalNode* ln = (LogicalNode*)ld->firstChild; ln != NULL; ln = (LogicalNode*)ln->sibling) {
            
            for (ModelNode* doNode = ln->firstChild; doNode != NULL; doNode = doNode->sibling) {
                if (ModelNode_getType(doNode) == DataObjectModelType) {
                    memset(&pointInfo, 0, sizeof(PointInfo));
                    if (processDataObject(doNode, &pointInfo)) {
                        if (!DynamicPointArray_add(pointArray, &pointInfo)) {
                            return 0;
                        }
                    }
                }
            }
        }
    }

    return 1;
}

/**
 * IEC 61850 MMS Master Thread Control Structure
 * 
 * This structure encapsulates the control and state information for a native thread
 * that manages IEC 61850 MMS (Manufacturing Message Specification) server operations.
 * The thread periodically calls back into Java to perform data updates and state management.
 * 
 * @field thread_descriptor POSIX thread descriptor for the IEC 61850 master thread
 * @field iec_state Volatile flag indicating the current operational state of the IEC thread
 *                  (0=stopped, 1=running, 2=paused, 3=error)
 * @field java_vm Reference to the Java Virtual Machine instance for JNI callback operations
 * @field handler_ref Global reference to the Java handler object that receives tick callbacks
 * @field method_ref JNI method ID for the tick callback method in the Java handler
 * @field iec_status Current IEC 61850 protocol status code (standard IEC 61850 status values)
 */
typedef struct {
    pthread_t thread_descriptor;
    volatile int iec_state;
    JavaVM* java_vm;
    jobject handler_ref;
    jmethodID method_ref;
    int iec_status;
} iec_thread;

/**
 * IEC104 Slave Thread Control Structure
 * 
 * This structure manages a slave thread that handles IEC 61850 slave device simulations
 * or secondary communication channels. The slave thread operates independently from the
 * master thread and provides asynchronous callback capabilities to Java.
 * 
 * @field slave_thread_descriptor POSIX thread descriptor for the IEC 61850 slave thread
 * @field slave_state Volatile boolean indicating whether the slave thread is active (true=running)
 * @field java_vm Reference to the Java Virtual Machine instance for JNI slave callbacks
 * @field handler Global reference to the Java slave handler object
 * @field method JNI method ID for the slave callback method in the Java handler
 */
typedef struct {
    pthread_t slave_thread_descriptor;
    volatile bool slave_state;
    JavaVM* java_vm;
    jobject handler;
    jmethodID method;
} slave_thread;

/**
 * Native thread worker routine function prototype.
 * Declares the signature for the IEC 61850 master thread worker function.
 * This function will be executed in a separate POSIX thread for continuous operation.
 * 
 * @param param Pointer to the iec_thread control block structure
 * @return Thread return value (always NULL in this implementation)
 */
static void* worker(void* param);

/**
 * Native thread worker routine for IEC 60870-5-104 slave operations.
 * 
 * Attaches to the Java VM, continuously invokes the Java callback method,
 * and manages the slave thread lifecycle. Handles JNI exceptions and
 * ensures proper thread detachment on termination.
 * 
 * @param param Pointer to the slave_thread control block structure
 * @return Always returns NULL upon thread exit
 * 
 * @note The thread runs until slave_state becomes false or Java handler is garbage collected
 * @note Automatically attaches/detaches from Java VM for JNI callback operations
 * @note Clears any Java exceptions to prevent JNI crash on callback errors
 */
static void* slave_worker(void* param) {
    slave_thread* slave = (slave_thread*)param;

    JNIEnv* env;
    jint attach_result = (*slave->java_vm)->AttachCurrentThread(slave->java_vm, (void**)&env, NULL);
    if (attach_result != JNI_OK || env == NULL) {
        slave->slave_state = THREAD_EXECUTION_STATE_TERMINATED;
        return NULL;
    }
    
    if (slave->handler == NULL || slave->method == NULL) {
        slave->slave_state = THREAD_EXECUTION_STATE_TERMINATED;
        (*slave->java_vm)->DetachCurrentThread(slave->java_vm);
        return NULL;
    }
    
    while(slave->slave_state) {
        if ((*env)->IsSameObject(env, slave->handler, NULL)) {
            break;
        }
        
        (*env)->CallVoidMethod(env, slave->handler, slave->method, slave->slave_thread_descriptor);
        
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
    }
    slave->slave_state = false;
    (*slave->java_vm)->DetachCurrentThread(slave->java_vm);
    return NULL;
}

/**
 * Native thread worker routine for IEC 61850 MMS server operations.
 * 
 * Main worker thread for the IEC 61850 master server. Attaches to Java VM,
 * continuously calls back into Java for data updates, and monitors thread state.
 * Includes iteration counting and proper JNI exception handling.
 * 
 * @param param Pointer to the iec_thread control block structure
 * @return Always returns NULL upon thread exit
 * 
 * @note Thread runs while iec_state equals THREAD_EXECUTION_STATE_ACTIVE
 * @note Passes the POSIX thread descriptor as a jlong parameter to Java callbacks
 * @note Safely handles Java object garbage collection and JNI exceptions
 */
static void* worker(void* param) {
    iec_thread* iecThread = (iec_thread*)param;
    
    JNIEnv* env;
    jint attach_result = (*iecThread->java_vm)->AttachCurrentThread(iecThread->java_vm, (void**)&env, NULL);
    if (attach_result != JNI_OK || env == NULL) {
        iecThread->iec_state = THREAD_EXECUTION_STATE_TERMINATED;
        return NULL;
    }
    
    if (iecThread->handler_ref == NULL || iecThread->method_ref == NULL) {
        iecThread->iec_state = THREAD_EXECUTION_STATE_TERMINATED;
        (*iecThread->java_vm)->DetachCurrentThread(iecThread->java_vm);
        return NULL;
    }
    
    jlong thread_identifier = (jlong)iecThread->thread_descriptor;
    int iteration_counter = 0;
    
    while (iecThread->iec_state == THREAD_EXECUTION_STATE_ACTIVE) {
        iteration_counter++;
        
        if ((*env)->IsSameObject(env, iecThread->handler_ref, NULL)) {
            break;
        }
        
        (*env)->CallVoidMethod(env, iecThread->handler_ref, iecThread->method_ref, thread_identifier);
        
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
    }
    
    iecThread->iec_state = THREAD_EXECUTION_STATE_TERMINATED;
    (*iecThread->java_vm)->DetachCurrentThread(iecThread->java_vm);
    return NULL;
}

/**
 * JNI library initialization function called when the native library is loaded.
 * 
 * Invoked by the Java Virtual Machine when the shared library is loaded via System.loadLibrary().
 * Returns the JNI version required by this native library implementation.
 * 
 * @param vm Pointer to the Java Virtual Machine instance
 * @param reserved Reserved for future use, currently NULL
 * @return The JNI version required by this library (JNI_VERSION_21)
 * 
 * @note This function must be exported and named exactly JNI_OnLoad
 * @note Return value indicates compatibility with specific JNI features
 */
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_21;
}

/**
 * Attaches to target process for memory debugging operations.
 * Uses ptrace PTRACE_ATTACH to attach to the specified process.
 * 
 * @param env JNI environment pointer
 * @param object Java object instance
 * @param processPid Process ID of the target process
 * @return JNI_TRUE if attachment successful, JNI_FALSE if failed
 */
JNIEXPORT jboolean JNICALL Java_org_vector_client_VectortedModule_attachProcess(
    JNIEnv* env, jobject object, jlong processPid) {
    long system = ptrace(PTRACE_ATTACH, (int)processPid, NULL, NULL);

    if(system == 0) {
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

/**
 * Waits for the attached process to stop execution.
 * Blocks until the target process is suspended after attachment.
 * 
 * @param env JNI environment pointer
 * @param object Java object instance
 * @param processPid Process ID of the attached target process
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_waitProcess(
    JNIEnv* env, jobject object, jlong processPid) {
    waitpid((int)processPid, NULL, 0);
}

/**
 * Detaches from the target process after debugging operations.
 * Uses ptrace PTRACE_DETACH to release the attached process.
 * 
 * @param env JNI environment pointer
 * @param object Java object instance
 * @param processPid Process ID of the process to detach from
 * @return JNI_TRUE if detachment successful, JNI_FALSE if failed
 */
JNIEXPORT jboolean JNICALL Java_org_vector_client_VectortedModule_detachProcess(
    JNIEnv* env, jobject object, jlong processPid) {
    long exit = ptrace(PTRACE_DETACH, (int)processPid, NULL, NULL);

    if(exit == 0) {
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

/**
 * Reads a value from the specified memory address in the attached process.
 * 
 * @param env JNI environment pointer
 * @param object Java object instance
 * @param processPid Process ID of the attached target process
 * @param attachMode Ptrace operation mode (PTRACE_PEEKDATA, PTRACE_PEEKTEXT, etc.)
 * @param address Memory address to read from
 * @return The value read from the specified memory address
 */
JNIEXPORT jlong JNICALL Java_org_vector_client_VectortedModule_attachGetLongAddress(
    JNIEnv* env, jobject object, jlong processPid, jint attachMode, jlong address) {
    return (jlong)ptrace(attachMode, (int)processPid, (void*)address, NULL);
}

/**
 * Converts a memory address (jlong) to a jchar.
 * 
 * @param env JNI interface pointer
 * @param object calling Java object
 * @param address memory address to convert
 * @return character representation of the address
 */
JNIEXPORT jchar JNICALL Java_org_vector_client_VectortedModule_toChar(
    JNIEnv* env, jobject object, jlong address) {
        return (char)address;
}

/**
 * Converts a memory address (jlong) to a jint.
 * 
 * @param env JNI interface pointer
 * @param object calling Java object
 * @param address memory address to convert
 * @return integer representation of the address
 */
JNIEXPORT jint JNICALL Java_org_vector_client_VectortedModule_toInt(
    JNIEnv* env, jobject object, jlong address) {
        return (int)(address & 0xFFFFFFFF);
}

/**
 * Converts a memory address (jlong) to a jshort.
 * Extracts the lower 16 bits of the address.
 * 
 * @param env JNI interface pointer
 * @param object calling Java object
 * @param address memory address to convert
 * @return 16-bit short value from the address
 */
JNIEXPORT jshort JNICALL Java_org_vector_client_VectortedModule_toShort(
    JNIEnv* env, jobject object, jlong address) {
        return (jshort)(address & 0xFFFF);
}

/**
 * Converts a memory address (jlong) to a jfloat.
 * 
 * @param env JNI interface pointer
 * @param object calling Java object
 * @param address memory address to convert
 * @return floating-point representation of the address
 */
JNIEXPORT jfloat JNICALL Java_org_vector_client_VectortedModule_toFloat(
    JNIEnv* env, jobject object, jlong address) {
        float value;
        memcpy(&value, &address, sizeof(float));

        return value;
}

/**
 * Converts a memory address (jlong) to a jboolean.
 * Non-zero address returns true, zero returns false.
 * 
 * @param env JNI interface pointer
 * @param object calling Java object
 * @param address memory address to convert
 * @return boolean representation of the address
 */
JNIEXPORT jboolean JNICALL Java_org_vector_client_VectortedModule_toBoolean(
    JNIEnv* env, jobject object, jlong address) {
        return ((jint)(address & 0xFFFFFFFF) != 0) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Writes a value to the specified memory address in the attached process.
 * 
 * @param env JNI environment pointer
 * @param object Java object instance
 * @param processPid Process ID of the attached target process
 * @param attachMode Ptrace operation mode (PTRACE_POKEDATA, PTRACE_POKETEXT, etc.)
 * @param address Memory address to write to
 * @param value Value to write to the memory address
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_attachSetValueFromAddress(
    JNIEnv* env, jobject object, jlong processPid, jint attachMode, 
    jlong address, jobject buffer) {

        void* ptr = (*env)->GetDirectBufferAddress(env, buffer);
        jvalue* iptr = (jvalue*)ptr;
        
    ptrace(attachMode, (int)processPid, (void*)address, iptr[0]);
}

/**
 * Calculate memory offset from base address
 * @param {jlong} address - Base memory address
 * @param {jlong} offsize - Offset value in bytes
 * @returns {jlong} New address after offset calculation
 */
JNIEXPORT jlong JNICALL Java_org_vector_client_VectortedModule_offsetof(
    JNIEnv* env, jobject object, jlong address, jlong offsize) {

        size_t size = (size_t)offsize;
        return (uintptr_t)(address + size);
}

/**
 * Updates float attribute value in IEC 61850 data model.
 * @param env JNI environment
 * @param object Java object instance
 * @param address Data attribute object reference path
 * @param value Float value to set
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_setFloatValue(JNIEnv* env, jobject object, jstring address, jfloat value) {
    const char* node = (*env)->GetStringUTFChars(env, address, NULL);

    DataAttribute* attr = (DataAttribute*)IedModel_getModelNodeByObjectReference(model, node);
    IedServer_updateFloatAttributeValue(
        service,
        attr,
        value
    );
    (*env)->ReleaseStringUTFChars(env, address, node);
}

/**
 * Sets an Int32 (32-bit integer) value to a specified data attribute in the IED model
 * 
 * This function is called from Java code to update a 32-bit integer attribute value
 * in the IEC 61850 server model. It converts the Java string address to C string,
 * finds the corresponding data attribute node, and updates its value.
 * 
 * @param env        JNI environment pointer (provides JNI function table)
 * @param object     Reference to the Java object that called this native method
 * @param address    Java string containing the object reference path (e.g., "TEMPLATECTRL01/measGGIO1.AnIn14.stVal")
 * @param value      Java int value (32-bit signed integer) to set
 * 
 * @note Memory Management: Automatically releases allocated UTF string characters
 * @warning Ensure the object reference path exists in the IED model to avoid null pointer dereference
 * 
 * @see IedModel_getModelNodeByObjectReference()
 * @see IedServer_updateInt32AttributeValue()
 * 
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_setIntValue(JNIEnv* env, jobject object, jstring address, jint value) {
    const char* node = (*env)->GetStringUTFChars(env, address, NULL);

    DataAttribute* attr = (DataAttribute*)IedModel_getModelNodeByObjectReference(model, node);
    IedServer_updateInt32AttributeValue(
        service,
        attr,
        value
    );
    (*env)->ReleaseStringUTFChars(env, address, node);
}

/**
 * Sets a Long (64-bit integer) value to a specified data attribute in the IED model
 * 
 * This function is called from Java code to update a 64-bit integer attribute value
 * in the IEC 61850 server model. Similar to setIntValue but handles larger numeric
 * ranges suitable for timestamps, counters, and other high-precision integer data.
 * 
 * @param env        JNI environment pointer (provides JNI function table)
 * @param object     Reference to the Java object that called this native method
 * @param address    Java string containing the object reference path (e.g., "TEMPLATECTRL01/measGGIO1.AnIn14.t")
 * @param value      Java long value (64-bit signed integer) to set
 * 
 * @note Memory Management: Automatically releases allocated UTF string characters
 * @note Data Type Mapping: Java long (int64) maps to IEC 61850 Int64 type
 * @warning Ensure the object reference path exists in the IED model to avoid null pointer dereference
 * 
 * @see IedModel_getModelNodeByObjectReference()
 * @see IedServer_updateInt64AttributeValue()
 * @see Java_org_vector_client_ClientInterface_setIntValue() (for Int32 equivalent)
 * 
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_setLongValue(JNIEnv* env, jobject object, jstring address, jlong value) {
    const char* node = (*env)->GetStringUTFChars(env, address, NULL);

    DataAttribute* attr = (DataAttribute*)IedModel_getModelNodeByObjectReference(model, node);
    IedServer_updateInt64AttributeValue(
        service,
        attr,
        value
    );
    (*env)->ReleaseStringUTFChars(env, address, node);
}

/**
 * Synchronizes client time to IEC 61850 timestamp attribute.
 * Gets current UTC time and updates specified .t attribute.
 * @param env JNI environment
 * @param object Java object instance  
 * @param address Data attribute object reference path
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_syncClientTime(JNIEnv* env, jobject object, jstring address) {
    const char* node = (*env)->GetStringUTFChars(env, address, NULL);

    DataAttribute* attr = (DataAttribute*)IedModel_getModelNodeByObjectReference(model, node);
    IedServer_updateUTCTimeAttributeValue(
        service,
        attr,
        Hal_getTimeInMs()
    );
    (*env)->ReleaseStringUTFChars(env, address, node);
}

/**
 * Sets boolean attribute value in IEC 61850 data model.
 * @param env JNI environment
 * @param object Java object instance
 * @param address Data attribute object reference path
 * @param value Boolean value to set
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_setBoolValue(JNIEnv* env, jobject object, jstring address, jboolean value) {
    const char* node = (*env)->GetStringUTFChars(env, address, NULL);

    DataAttribute* attr = (DataAttribute*)IedModel_getModelNodeByObjectReference(model, node);
    IedServer_updateBooleanAttributeValue(
        service,
        attr,
        value
    );
    (*env)->ReleaseStringUTFChars(env, address, node);
}

/**
 * Binds MMS server instance to specified network endpoint.
 * @param env JNI execution environment
 * @param object Java object instance
 * @param ip Network interface address
 * @param port Service listening port
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_bind(JNIEnv* env, jobject object, jstring ip, jint port) {
    const char* address = (*env)->GetStringUTFChars(env, ip, NULL);
    if(address == NULL) {
        return;
    }

    service = IedServer_create(model);
    IedServer_setLocalIpAddress(service, address);
    IedServer_start(service, port);

    (*env)->ReleaseStringUTFChars(env, ip, address);
}

/**
 * @brief JNI method to retrieve all node list as HashMap
 * @details Returns a HashMap where keys are point indices and values are ArrayLists
 *          containing [mainPath, description, type, childrenArray]
 * @param[in] env JNI environment pointer
 * @param[in] obj Java object reference
 * @return HashMap<Integer, ArrayList<Object>> containing node information
 */
JNIEXPORT jobject JNICALL Java_org_vector_client_VectortedModule_getNodeList(JNIEnv *env, jobject obj) {
    
    if (model == NULL) {
        return NULL;
    }

    Points* pointArray = PointArray_create(INITIAL_CAPACITY);
    if (pointArray == NULL) {
        return NULL;
    }

    if (!collectAllDataPoints(model, pointArray)) {
        return NULL;
    }

    int pointCount = DynamicPointArray_size(pointArray);

    jclass hashMapClass = (*env)->FindClass(env, "java/util/HashMap");
    jmethodID hashMapConstructor = (*env)->GetMethodID(env, hashMapClass, "<init>", "()V");
    jmethodID hashMapPut = (*env)->GetMethodID(env, hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jclass arrayListClass = (*env)->FindClass(env, "java/util/ArrayList");
    jmethodID arrayListConstructor = (*env)->GetMethodID(env, arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = (*env)->GetMethodID(env, arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jclass integerClass = (*env)->FindClass(env, "java/lang/Integer");
    jmethodID integerConstructor = (*env)->GetMethodID(env, integerClass, "<init>", "(I)V");

    jobject resultHashMap = (*env)->NewObject(env, hashMapClass, hashMapConstructor);

    for (int i = 0; i < pointCount; i++) {
        PointInfo* point = DynamicPointArray_get(pointArray, i);
        if (point == NULL) {
            continue;
        }
        jobject pointDataList = (*env)->NewObject(env, arrayListClass, arrayListConstructor);

        jstring mainPath = (*env)->NewStringUTF(env, point->mainPath);
        (*env)->CallBooleanMethod(env, pointDataList, arrayListAdd, mainPath);
        (*env)->DeleteLocalRef(env, mainPath);

        jstring desc = (*env)->NewStringUTF(env, point->description[0] != '\0' ? point->description : "void");
        (*env)->CallBooleanMethod(env, pointDataList, arrayListAdd, desc);
        (*env)->DeleteLocalRef(env, desc);

        jstring typeStr = (*env)->NewStringUTF(env, getType(point->type));
        (*env)->CallBooleanMethod(env, pointDataList, arrayListAdd, typeStr);
        (*env)->DeleteLocalRef(env, typeStr);

        jobject childrenList = (*env)->NewObject(env, arrayListClass, arrayListConstructor);
        for (int j = 0; j < point->childCount; j++) {
            jstring childPath = (*env)->NewStringUTF(env, point->children[j].path);
            (*env)->CallBooleanMethod(env, childrenList, arrayListAdd, childPath);
            (*env)->DeleteLocalRef(env, childPath);
        }
        (*env)->CallBooleanMethod(env, pointDataList, arrayListAdd, childrenList);
        (*env)->DeleteLocalRef(env, childrenList);

        jobject key = (*env)->NewObject(env, integerClass, integerConstructor, i);

        (*env)->CallObjectMethod(env, resultHashMap, hashMapPut, key, pointDataList);

        (*env)->DeleteLocalRef(env, key);
        (*env)->DeleteLocalRef(env, pointDataList);
    }

    DynamicPointArray_destroy(pointArray);
    (*env)->DeleteLocalRef(env, hashMapClass);
    (*env)->DeleteLocalRef(env, arrayListClass);
    (*env)->DeleteLocalRef(env, integerClass);

    return resultHashMap;
}

/**
 * Native implementation of the pointModel method.
 * Loads an IEC 61850 configuration (CFG) file into the MMS server's memory model.
 * 
 * Converts the Java string path to a C string, then uses the IEC 61850 library's
 * parser to create and store a runtime data model from the configuration file.
 * Properly releases the allocated string resources after processing.
 *
 * @param env    JNI environment pointer
 * @param object Java object instance (unused)
 * @param path   Java string containing the absolute path to the CFG configuration file
 * 
 * @note The created model (iedModel) is stored globally for subsequent MMS operations.
 * @note Returns early if the path string conversion fails.
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_pointModel(JNIEnv* env, jobject object, jstring path) {
    const char* config = (*env)->GetStringUTFChars(env, path, NULL);
    if(config == NULL) {
        return;
    }

    model = ConfigFileParser_createModelFromConfigFileEx(config);
    (*env)->ReleaseStringUTFChars(env, path, config);
}

/**
 * Binds CS104 slave service to network endpoint.
 * Creates slave service and sets local IP address and port for IEC 60870-5-104 communication.
 * 
 * @param env JNI environment
 * @param object Java object instance
 * @param ip IP address to bind ("0.0.0.0" for all interfaces)
 * @param port TCP port to listen on (standard: 2404)
 * 
 * @note Overwrites existing slave_service - call stopSlaveService first to avoid leaks
 * @note Not thread-safe due to global slave_service variable
 * 
 * @see #startSlaveService(JNIEnv*, jobject)
 * @see #stopSlaveService(JNIEnv*, jobject, jlong)
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_bindSlave(JNIEnv* env, jobject object, jstring ip, jint port) {
    const char* address = (*env)->GetStringUTFChars(env, ip, NULL);
    if(address == NULL) {
        return;
    }

    slave_service = CS104_Slave_create(100, 100);

    CS104_Slave_setLocalAddress(slave_service, address);
    CS104_Slave_setLocalPort(slave_service, port);
    CS104_Slave_setMaxOpenConnections(slave_service, 1);

    CS104_Slave_setServerMode(slave_service, CS104_MODE_SINGLE_REDUNDANCY_GROUP);

    CS104_Slave_setConnectionEventHandler(slave_service, connectionEventHandler, NULL);
    CS104_Slave_setInterrogationHandler(slave_service, interrogationHandler, NULL);

    (*env)->ReleaseStringUTFChars(env, ip, address);
}
/**
 * Starts CS104 slave service and creates worker thread.
 * Sets server mode, starts slave service, and creates detached thread for continuous operation.
 * 
 * @param env JNI environment
 * @param object Java object instance
 * @param handler Callback handler for worker thread (unused in current implementation)
 * @return Thread handle as jlong, or 0 on failure
 * 
 * @note Requires prior call to bindSlave() - checks slave_service != NULL
 * @note Creates detached thread that runs until slave_state becomes false
 * @note Always returns valid handle on success, 0 on any failure
 * 
 * @see #bindSlave(JNIEnv*, jobject, jstring, jint)
 * @see #stopSlaveService(JNIEnv*, jobject, jlong)
 * @see #slave_worker(void*)
 */
JNIEXPORT jlong JNICALL Java_org_vector_client_VectortedModule_startSlaveService(JNIEnv* env, jobject object, jobject handler) {
    if(handler == NULL) {
        return 0;
    }

    jclass handler_class = (*env)->GetObjectClass(env, handler);
    if (handler_class == NULL) {
        return 0;
    }

    if (slave_service == NULL) {
        (*env)->DeleteLocalRef(env, handler_class);
        return 0;
    }
    params = CS104_Slave_getAppLayerParameters(slave_service);

    CS104_Slave_start(slave_service);

    slave_thread* slaveHandler = malloc(sizeof(slave_thread));
    if (slaveHandler == NULL) {
        (*env)->DeleteLocalRef(env, handler_class);
        return 0;
    }

    memset(slaveHandler, 0, sizeof(slave_thread));

    slaveHandler->handler = (*env)->NewGlobalRef(env, handler);
    if (slaveHandler->handler == NULL) {
        free(slaveHandler);
        (*env)->DeleteLocalRef(env, handler_class);
        return 0;
    }

    jint vm_result = (*env)->GetJavaVM(env, &slaveHandler->java_vm);
    if (vm_result != JNI_OK || slaveHandler->java_vm == NULL) {
        (*env)->DeleteGlobalRef(env, slaveHandler->handler);
        free(slaveHandler);
        (*env)->DeleteLocalRef(env, handler_class);
        return 0;
    }

    slaveHandler->method = (*env)->GetMethodID(env, handler_class, "runTickOnWorkerThread", "(J)V");
    (*env)->DeleteLocalRef(env, handler_class);
    
    if (slaveHandler->method == NULL) {
        (*env)->DeleteGlobalRef(env, slaveHandler->handler);
        free(slaveHandler);
        return 0;
    }

    slaveHandler->slave_state = true;

    if (pthread_create(&slaveHandler->slave_thread_descriptor, NULL, slave_worker, slaveHandler) != 0) {
        (*env)->DeleteGlobalRef(env, slaveHandler->handler);
        free(slaveHandler);
        return 0;
    }

    pthread_detach(slaveHandler->slave_thread_descriptor);
    return (jlong)(uintptr_t)slaveHandler;
}

/**
 * Stops CS104 slave service and cleans up resources.
 * Signals worker thread to stop, waits briefly, then stops and destroys slave service.
 * Also cleans up JNI global references to prevent memory leaks.
 * 
 * @param env JNI environment
 * @param object Java object instance
 * @param threadId Thread handle returned from startSlaveService()
 * 
 * @note Does nothing if threadId is 0 (invalid handle)
 * @note Uses usleep() to give worker thread time to terminate
 * @note Safely handles NULL slave_service and handler references
 * @note Sets slave_service to NULL after destruction to prevent double-free
 * 
 * @see #startSlaveService(JNIEnv*, jobject, jobject)
 * @see #bindSlave(JNIEnv*, jobject, jstring, jint)
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_stopSlaveService(JNIEnv* env, jobject object, jlong threadId) {
    if (threadId == 0) {
        return;
    }
    slave_thread* handler = (slave_thread*)(uintptr_t)threadId;
    handler->slave_state = false;

    if(slave_service != NULL) {
        CS104_Slave_stop(slave_service);
        CS104_Slave_destroy(slave_service);
        slave_service = NULL;
    }
    if (handler->handler != NULL) {
        (*env)->DeleteGlobalRef(env, handler->handler);
        handler->handler = NULL;
    }
}

/**
 * @brief Sends batch telecontrol data to multiple modules via IEC 60870-5-104 slave service.
 * 
 * This JNI function creates and enqueues ASDUs containing various information object types
 * based on the module address ranges. Data is batched in groups of up to 20 points per ASDU.
 * 
 * @param env JNI environment pointer
 * @param object Java object reference
 * @param modules Java int array containing module addresses (Information Object Addresses)
 * @param value Common value for all points in the batch transmission
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_sendSlaveBlock(
    JNIEnv* env, jobject object, jintArray modules, jfloat value) {
    
    /* Get native array from Java int array */
    jint* box = (*env)->GetIntArrayElements(env, modules, NULL);
    if (box == NULL) {
        return; /* Failed to get array elements */
    }

    /* Calculate batch configuration */
    int maxBatchSize = 20; /* Maximum points per ASDU */
    int totalPoints = (*env)->GetArrayLength(env, modules);
    int numBatches = (int)ceil((double)totalPoints / maxBatchSize);

    /* Allocate memory for ASDU pointers */
    CS101_ASDU* asdus = malloc(sizeof(CS101_ASDU) * numBatches);
    if (asdus == NULL) {
        (*env)->ReleaseIntArrayElements(env, modules, box, 0);
        return; /* Memory allocation failed */
    }

    int startIdx = 0;
    int endIdx = startIdx + maxBatchSize;

    /* Process each batch */
    for (int batchIdx = 0; batchIdx < numBatches; batchIdx++) {
        /* Create ASDU for this batch with periodic cause of transmission */
        asdus[batchIdx] = CS101_ASDU_create(
            params,                     /* Application layer parameters */
            false,                      /* Not sequence of information objects */
            CS101_COT_PERIODIC,         /* Periodic transmission */
            0,                          /* Originator address (0 for not used) */
            1,                          /* Common address (ASDU address) */
            false,                      /* No test flag */
            false                       /* No negative confirmation */
        );

        /* Process each module in current batch */
        for (int pointIdx = startIdx; pointIdx < endIdx && pointIdx < totalPoints; pointIdx++) {
            int moduleAddr = box[pointIdx];
            InformationObject io = NULL;

            /* Create appropriate information object based on module address */
            if (range(moduleAddr, 1, 4000)) {
                /* Single-point information (binary status) - M_SP_NA_1 */
                io = (InformationObject)SinglePointInformation_create(
                    NULL, moduleAddr, (bool)value, IEC60870_QUALITY_GOOD);
            }
            else if (range(moduleAddr, 4097, 4098)) {
                /* Double-point information (dual-state equipment status) - M_DP_NA_1 */
                io = (InformationObject)DoublePointInformation_create(
                    NULL, moduleAddr, (value >= 0.5) ? IEC60870_DOUBLE_POINT_ON : IEC60870_DOUBLE_POINT_OFF, IEC60870_QUALITY_GOOD);
            }
            else if (range(moduleAddr, 16385, 22879)) {
                /* Measured value, short format (normalized analog value) - M_ME_NA_1 */
                io = (InformationObject)MeasuredValueShort_create(
                    NULL, moduleAddr, value, IEC60870_QUALITY_GOOD);
            }
            else if (range(moduleAddr, 24577, 24577)) {
                /* WARNING: Single command is DOWNLINK type, not typically used for uplink */
                io = (InformationObject)SingleCommand_create(
                    NULL, moduleAddr, 0, false, IEC60870_QUALITY_GOOD);
            }
            else if (range(moduleAddr, 24578, 24834)) {
                /* WARNING: Double command is DOWNLINK type, not typically used for uplink */
                io = (InformationObject)DoubleCommand_create(
                    NULL, moduleAddr, 0, false, IEC60870_QUALITY_GOOD);
            }
            else if (range(moduleAddr, 25089, 25099)) {
                /* WARNING: Setpoint command is DOWNLINK type, not typically used for uplink */
                io = (InformationObject)SetpointCommandNormalized_create(
                    NULL, moduleAddr, value, false, IEC60870_QUALITY_GOOD);
            }
            else if (range(moduleAddr, 25601, 25602)) {
                /* Integrated totals (counter/energy totals) - M_IT_NA_1 */
                io = (InformationObject)IntegratedTotals_create(
                    NULL, moduleAddr, 
                    BinaryCounterReading_create(NULL, (int32_t)value, 0, false, false, false));
            }

            /* Add to ASDU and clean up */
            if (io != NULL) {
                CS101_ASDU_addInformationObject(asdus[batchIdx], io);
                InformationObject_destroy(io);
            }
        }

        /* Update indices for next batch */
        startIdx = endIdx;
        endIdx = startIdx + maxBatchSize;

        /* Enqueue ASDU to slave service for transmission */
        CS104_Slave_enqueueASDU(slave_service, asdus[batchIdx]);
        CS101_ASDU_destroy(asdus[batchIdx]);

        /* Small delay to prevent overwhelming the communication channel */
        Thread_sleep(5);
    }

    /* Clean up resources */
    (*env)->ReleaseIntArrayElements(env, modules, box, 0);
    free(asdus);
}

/**
 * Initiates background service with handler callback execution.
 * @param env JNI execution environment  
 * @param obj Java object instance
 * @param handler Callback interface implementation
 * @return Native thread handle or zero on failure
 */
JNIEXPORT jlong JNICALL Java_org_vector_client_VectortedModule_startService(JNIEnv* env, jobject obj, jobject handler) {
    if (handler == NULL) {
        return 0;
    }
    
    iec_thread* threadEntity = malloc(sizeof(iec_thread));
    if (threadEntity == NULL) {
        return 0;
    }
    
    memset(threadEntity, 0, sizeof(iec_thread));
    threadEntity->iec_state = THREAD_EXECUTION_STATE_ACTIVE;
    
    jint vm_result = (*env)->GetJavaVM(env, &threadEntity->java_vm);
    if (vm_result != JNI_OK || threadEntity->java_vm == NULL) {
        free(threadEntity);
        return 0;
    }
    
    threadEntity->handler_ref = (*env)->NewGlobalRef(env, handler);
    if (threadEntity->handler_ref == NULL) {
        free(threadEntity);
        return 0;
    }
    
    jclass handler_class = (*env)->GetObjectClass(env, handler);
    if (handler_class == NULL) {
        goto cleanup_resources;
    }
    
    threadEntity->method_ref = (*env)->GetMethodID(env, handler_class, "runTickOnWorkerThread", "(J)V");
    (*env)->DeleteLocalRef(env, handler_class);
    
    if (threadEntity->method_ref == NULL) {
        goto cleanup_resources;
    }
    
    threadEntity->iec_status = 1;
    
    if (pthread_create(&threadEntity->thread_descriptor, NULL, worker, threadEntity) != 0) {
        goto cleanup_resources;
    }
    
    pthread_detach(threadEntity->thread_descriptor);
    return (jlong)threadEntity;

    cleanup_resources:
        if (threadEntity->handler_ref != NULL) {
            (*env)->DeleteGlobalRef(env, threadEntity->handler_ref);
        }
        free(threadEntity);
    return 0;
}

/**
 * Terminates background service execution gracefully.
 * @param env JNI execution environment
 * @param obj Java object instance  
 * @param thread_handle Native thread identifier
 */
JNIEXPORT void JNICALL Java_org_vector_client_VectortedModule_stopService(JNIEnv* env, jobject obj, jlong thread_handle) {
    if (thread_handle == 0) {
        return;
    }
    
    iec_thread* threadEntity = (iec_thread*)thread_handle;
    threadEntity->iec_state = THREAD_EXECUTION_STATE_TERMINATED;

    if(service != NULL) {
        IedServer_stop(service);
        IedServer_destroy(service);

        return;
    }
}
