#!/bin/bash

# ============================================
# Vectorted Library Compilation Script
# ============================================

# Compile command
clang -shared -fpic -o libvectorted.so -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux libvector.c -liec61850 -llib60870

# Check compilation result
if [ $? -eq 0 ]; then
    echo "libvector shared library has been successfully compiled."
else
    echo "Compilation failed. Please check:"
    echo "- JAVA_HOME environment variable setting"
    echo "- Whether necessary library files are installed (libiec61850, lib60870 related libraries)"
    echo "- Whether source files exist and have correct syntax"
    echo "- For detailed error information, please check gcc output"
fi