#!/bin/bash
# Script to run chunk performance benchmark tests
# This provides a simple way to execute the performance tests without typing the full Gradle command

set -e

echo "Building and running chunk performance tests..."
echo "This will compile main sources, test sources, and run the performance benchmarks."
echo ""

# Use a single Gradle invocation to ensure proper task dependency resolution
# The runChunkPerformanceTest task already depends on classes and compileTestJava,
# but we'll make it explicit to ensure proper order
echo "Compiling and running tests..."
./gradlew classes compileTestJava runChunkPerformanceTest

echo ""
echo "Performance tests completed!"
