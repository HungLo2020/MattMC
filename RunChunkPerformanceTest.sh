#!/bin/bash
# Script to run chunk performance benchmark tests
# This provides a simple way to execute the performance tests without typing the full Gradle command

set -e

echo "Building and running chunk performance tests..."
echo "This will compile main sources, test sources, and run the performance benchmarks."
echo ""

# Check if --clean flag is provided
if [ "$1" = "--clean" ] || [ "$1" = "-c" ]; then
    echo "Cleaning build directory first..."
    ./gradlew clean
    echo ""
fi

# Check for --debug flag for troubleshooting
DEBUG_FLAG=""
if [ "$1" = "--debug" ] || [ "$2" = "--debug" ]; then
    DEBUG_FLAG="--info"
    echo "Running in debug mode with detailed output..."
    echo ""
fi

# Use a single Gradle invocation with explicit task ordering
# Force compileJava to run first, then compileTestJava, then the test runner
echo "Compiling and running tests..."
./gradlew compileJava compileTestJava runChunkPerformanceTest $DEBUG_FLAG

echo ""
echo "Performance tests completed!"
echo ""
echo "If you encountered compilation errors, try:"
echo "  ./RunChunkPerformanceTest.sh --clean"
echo "  ./RunChunkPerformanceTest.sh --clean --debug  (for detailed output)"
