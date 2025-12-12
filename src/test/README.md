# MattMC Test Directory

This directory contains all tests for the MattMC project.

## Structure

- **`misc/`** - Unit tests and integration tests (pass/fail tests)
- **`performance/`** - Performance benchmarks using JMH
- **`resources/`** - Test-specific resources (test data, fixtures, configs)

## Quick Start

### Running Tests

```bash
# Run all unit/integration tests (excludes performance tests)
./gradlew test

# Run performance benchmarks
./gradlew performanceTest

# Run all tests
./gradlew testAll
```

### Running a Single Test

You can run a specific test class or test method using the `--tests` flag:

```bash
# Run a specific test class
./gradlew performanceTest --tests "ChunkOperationsPerformanceTest"

# Run a specific test method
./gradlew performanceTest --tests "ChunkOperationsPerformanceTest.testBlockPosCreation1Chunk"

# Run all tests in a package
./gradlew test --tests "net.minecraft.util.*"

# Run with full class path
./gradlew performanceTest --tests "net.minecraft.world.level.chunk.ChunkOperationsPerformanceTest"
```

**Example: Running the chunk performance test**

To run just the chunk operations performance tests:
```bash
./gradlew performanceTest --tests "ChunkOperationsPerformanceTest"
```

To run a specific chunk test (e.g., the 128 chunks BlockPos creation test):
```bash
./gradlew performanceTest --tests "ChunkOperationsPerformanceTest.testBlockPosCreation128Chunks"
```

Output will show:
```
> Task :performanceTest

Chunk Operations Performance Tests > should measure BlockPos creation for 128 chunks worth of blocks STANDARD_OUT
    BlockPos creation (128 chunks): 72.15 ms total (0.56 ms/chunk, 1,774 chunks/sec, 29,067,800 items/sec)

Chunk Operations Performance Tests > should measure BlockPos creation for 128 chunks worth of blocks PASSED
```

## Documentation

For complete documentation on the testing infrastructure, see:
**[docs/HOWTO-TESTING.md](../docs/HOWTO-TESTING.md)**

This includes:
- How to write tests
- Testing frameworks and libraries used
- Best practices
- IDE integration
- Troubleshooting

## Example Tests

- **Unit Test Example**: `misc/net/minecraft/util/MthTest.java`
- **Performance Test Example**: `performance/net/minecraft/world/BlockPosBenchmark.java`

## Test Isolation

Tests are completely isolated from production builds:
- Test source files are excluded from main source set
- Test dependencies are scoped to `testImplementation`
- Production JARs and distributions never include test code or dependencies
