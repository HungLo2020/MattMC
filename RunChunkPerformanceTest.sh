#!/bin/bash
# Script to run chunk performance benchmark tests
# This provides a simple way to execute the performance tests without typing the full Gradle command

set -e

echo "Building and running chunk performance tests..."
echo "This will:"
echo "  1. Compile the main source code"
echo "  2. Compile the test code"
echo "  3. Run the chunk save/load performance benchmarks"
echo ""

# Run the Gradle task
./gradlew runChunkPerformanceTest --no-daemon

echo ""
echo "Performance tests completed!"
