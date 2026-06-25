# MattMC Testing Infrastructure Guide

## Table of Contents
- [Testing Infrastructure Architecture](#testing-infrastructure-architecture)
- [Directory Structure](#directory-structure)
- [Writing Tests](#writing-tests)
- [Running Tests](#running-tests)
- [List of all Tests](#list-of-all-tests)
- [Advanced Topics](#advanced-topics)
- [Troubleshooting](#troubleshooting)

## Testing Infrastructure Architecture

The testing infrastructure is built on the following components:

### 1. **Gradle Source Sets**

Tests use a separate `test` source set that is completely isolated from the `main` source set:

```gradle
sourceSets {
    main {
        java {
            srcDirs = ['.']
            exclude 'src/test/**'  // Tests are excluded from main
            // ... other excludes
        }
    }
    
    test {
        java {
            srcDirs = ['src/test']
        }
        resources {
            srcDirs = ['src/test/resources']
        }
    }
}
```

This ensures that test code is never compiled into production JARs.

### 3. **Test Tasks**

Three Gradle tasks are available for running tests:

- **`test`**: Runs unit and integration tests (excludes performance tests)
- **`performanceTest`**: Runs only performance benchmarks
- **`testAll`**: Runs all tests including performance tests

## Directory Structure

```
src/test/
├── performance/              # Performance benchmarks and load tests
│   └── net/
│       └── minecraft/
│           └── world/
│               └── BlockPosBenchmark.java
├── misc/                     # General pass/fail unit and integration tests
│   └── net/
│       └── minecraft/
│           └── util/
│               └── MthTest.java
└── resources/                # Test-specific resources
    ├── test-worlds/          # Test world data
    ├── test-configs/         # Test configuration files
    └── fixtures/             # Test data fixtures
```

### Directory Purposes

- **`src/test/performance/`**: Contains performance tests and benchmarks that measure execution time, memory usage, throughput, and other performance metrics. These tests use JMH (Java Microbenchmark Harness) and typically don't have pass/fail criteria but generate reports for analysis.

- **`src/test/misc/`**: Contains traditional unit tests, integration tests, and other pass/fail tests that verify correctness of functionality using JUnit 5.

- **`src/test/resources/`**: Contains test-specific resources like test data, configuration files, and fixtures that are loaded during test execution.

## Writing Tests

### Naming Conventions

**IMPORTANT**: Test classes must follow specific naming conventions to be properly categorized:

- **Unit/Integration Tests**: Must end with `Test.java` (e.g., `MthTest.java`, `NetworkTest.java`)
  - Placed in `src/test/misc/` directory
  - Run with `./gradlew test` task
  - Excluded from `performanceTest` task

- **Performance Tests**: Must end with `PerformanceTest.java` or `Benchmark.java` (e.g., `MthPerformanceTest.java`, `BlockPosBenchmark.java`)
  - Placed in `src/test/performance/` directory
  - Run with `./gradlew performanceTest` task
  - Excluded from regular `test` task

The naming convention is used by Gradle to filter which tests run in each task:
- Regular `test` task excludes `*PerformanceTest.class` and `*Benchmark.class`
- `performanceTest` task includes only `*PerformanceTest.class` and `*Benchmark.class`
- `testAll` task runs everything

### Unit Test Example

Unit tests should mirror the package structure of the code being tested and use the naming convention `[ClassName]Test.java`:

```java
package net.minecraft.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the Mth utility class.
 */
@DisplayName("Mth Utility Tests")
class MthTest {
    
    @BeforeEach
    void setUp() {
        // Setup code if needed
    }
    
    @Test
    @DisplayName("should clamp values within range")
    void testClamp() {
        // Test value within range
        assertThat(Mth.clamp(5, 0, 10)).isEqualTo(5);
        
        // Test value below minimum
        assertThat(Mth.clamp(-5, 0, 10)).isEqualTo(0);
        
        // Test value above maximum
        assertThat(Mth.clamp(15, 0, 10)).isEqualTo(10);
    }
    
    @Test
    @DisplayName("should calculate square root correctly")
    void testSqrt() {
        assertThat(Mth.sqrt(0.0f)).isCloseTo(0.0f, within(0.001f));
        assertThat(Mth.sqrt(4.0f)).isCloseTo(2.0f, within(0.001f));
        assertThat(Mth.sqrt(9.0f)).isCloseTo(3.0f, within(0.001f));
    }
}
```

### Performance Test Example

Performance tests use JMH and follow the naming convention `[Feature]Benchmark.java`:

```java
package net.minecraft.world;

import net.minecraft.core.BlockPos;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Performance benchmark for BlockPos operations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BlockPosBenchmark {
    
    private BlockPos pos;
    
    @Setup
    public void setup() {
        pos = new BlockPos(100, 64, 200);
    }
    
    @Benchmark
    public BlockPos benchmarkBlockPosCreation() {
        return new BlockPos(100, 64, 200);
    }
    
    @Benchmark
    public BlockPos benchmarkBlockPosOffset() {
        return pos.offset(1, 0, 1);
    }
    
    @Benchmark
    public long benchmarkBlockPosAsLong() {
        return pos.asLong();
    }
    
    /**
     * Main method to run the benchmark standalone.
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(BlockPosBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
```

**Note**: JMH benchmarks like the one above are compiled but won't run with the `performanceTest` Gradle task. They're meant to be run directly via their `main()` method or with dedicated JMH runners. For performance tests that run with `./gradlew performanceTest`, use JUnit-based performance tests (see below).

### JUnit Performance Test Example

For simple performance measurements that integrate with the Gradle test tasks, use JUnit with timing:

```java
package net.minecraft.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * JUnit-based performance test.
 * Note: Must end with "PerformanceTest" to be included in performanceTest task.
 */
@DisplayName("Mth Performance Tests")
class MthPerformanceTest {
    
    private static final int ITERATIONS = 100_000;
    
    @Test
    @DisplayName("should perform clamp operations quickly")
    void testClampPerformance() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            Mth.clamp(i, 0, 1000);
        }
        
        long duration = System.nanoTime() - startTime;
        double avgNanos = duration / (double) ITERATIONS;
        
        System.out.printf("Clamp: %,d iterations in %.2f ms (avg: %.2f ns/op)%n",
            ITERATIONS, duration / 1_000_000.0, avgNanos);
    }
}
```

**When to use each approach:**
- **JMH** (`*Benchmark.java`): For accurate microbenchmarks, statistical analysis, and comparing implementations
- **JUnit Performance Tests** (`*PerformanceTest.java`): For simple timing measurements and regression detection in CI

### Parameterized Test Example

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ParameterizedTest
@CsvSource({
    "0, 0, 0",
    "1, 1, 1",
    "5, 10, 5",
    "15, 10, 10",
    "-5, 0, 0"
})
@DisplayName("should clamp various values")
void testClampParameterized(int value, int max, int expected) {
    assertThat(Mth.clamp(value, 0, max)).isEqualTo(expected);
}
```

### Mock Example

```java
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NetworkTest {
    
    @Mock
    private Connection mockConnection;
    
    @Test
    void testPacketSending() {
        when(mockConnection.isConnected()).thenReturn(true);
        
        PacketSender sender = new PacketSender(mockConnection);
        sender.sendPacket(new TestPacket());
        
        verify(mockConnection).send(any(TestPacket.class));
    }
}
```

## Running Tests

### Command Line

#### Run All Unit/Integration Tests (excludes performance tests)
```bash
./gradlew test
```

#### Run Only Performance Tests
```bash
./gradlew performanceTest
```

#### Run All Tests (Unit + Performance)
```bash
./gradlew testAll
```

#### Run Tests for Specific Package
```bash
./gradlew test --tests "net.minecraft.world.*"
```

#### Run Single Test Class
```bash
./gradlew test --tests "net.minecraft.util.MthTest"
```

#### Run Specific Test Method
```bash
./gradlew test --tests "net.minecraft.util.MthTest.testClamp"
```

#### Run with More Verbose Output
```bash
./gradlew test --info
```

### Test Reports

After running tests, HTML reports are automatically generated at:
```
build/reports/tests/test/index.html
build/reports/tests/performanceTest/index.html
```

Open these files in a web browser to see detailed test results, including:
- Test execution times
- Stack traces for failures
- Standard output/error for tests
- Summary statistics

### Test Output

The test tasks are configured to show clear, formatted output:

```
Test Results: SUCCESS
Tests run: 3, Passed: 3, Failed: 0, Skipped: 0
```

When tests fail, you'll see:
- The test name that failed
- The assertion that failed
- The expected vs actual values
- Full stack trace

## List of All Tests
- Chunk Performance Test: `./gradlew performancetest --tests net.minecraft.world.level.chunk.ChunkGenerationPerformanceTest`
- Chunk Performance Test: `./gradlew performancetest --tests net.minecraft.world.level.chunk.ChunkGenerationBatcgPerformanceTest`



## Advanced Topics

### Test Tags

Use tags to categorize tests:

```java
@Test
@Tag("fast")
void quickTest() { }

@Test
@Tag("slow")
@Tag("integration")
void slowIntegrationTest() { }
```

Run tests by tag (requires configuration in build.gradle):
```bash
./gradlew test -Dgroups="fast"
```

### Test Fixtures

Create test data in the `src/test/resources/fixtures/` directory:

```java
@Test
void testWorldLoading() {
    Path worldData = Paths.get("src/test/resources/fixtures/test-world.dat");
    World world = WorldLoader.load(worldData);
    assertThat(world).isNotNull();
}
```

### Custom Assertions

Create custom assertions for domain-specific validations:

```java
public class BlockPosAssert extends AbstractAssert<BlockPosAssert, BlockPos> {
    public BlockPosAssert(BlockPos actual) {
        super(actual, BlockPosAssert.class);
    }
    
    public static BlockPosAssert assertThat(BlockPos actual) {
        return new BlockPosAssert(actual);
    }
    
    public BlockPosAssert isAbove(BlockPos other) {
        isNotNull();
        if (actual.getY() <= other.getY()) {
            failWithMessage("Expected <%s> to be above <%s>", actual, other);
        }
        return this;
    }
}
```

### Testing Asynchronous Code

Use Awaitility for async operations:

```java
@Test
void testAsyncOperation() {
    AsyncOperation op = new AsyncOperation();
    op.start();
    
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> op.isComplete());
    
    assertThat(op.getResult()).isEqualTo("expected");
}
```

## Chunk Generation Performance Tests

MattMC includes specialized performance tests for chunk generation that use the actual Minecraft world generation pathways. These tests are designed to provide realistic performance metrics for chunk generation under real-world conditions.

### Overview

Unlike simplified chunk operation tests, the chunk generation performance tests:
- Create a full Minecraft server environment
- Use the real world generation pipeline (terrain, features, structures, lighting)
- Generate chunks with a fixed seed for reproducibility
- Provide comprehensive timing metrics

### Available Tests

#### 1. ChunkGenerationPerformanceTest

**Purpose**: Measures performance for generating 100 chunks in a single run.

**What it does**:
- Creates a Minecraft server with a test world (seed: 12345)
- Generates 100 chunks through the complete generation pipeline
- Tracks individual chunk generation times
- Reports: total time, average time, fastest chunk, slowest chunk

**Running the test**:
```bash
./gradlew performanceTest --tests "net.minecraft.world.level.chunk.ChunkGenerationPerformanceTest"
```

**Expected output**:
```
========================================
Chunk Generation Performance Test Results
========================================
World Seed: 12345
Chunks Generated: 100
Total Time: 15234.56 ms (15.23 seconds)
Average Time per Chunk: 152.35 ms
Fastest Chunk: 45.23 ms
Slowest Chunk: 456.78 ms
Chunks per Second: 6.56
========================================
```

#### 2. ChunkGenerationBatchPerformanceTest

**Purpose**: Provides statistically stable metrics by running the 100-chunk test 20 times and averaging results.

**What it does**:
- Runs 20 iterations of the 100-chunk generation test
- Creates a fresh world for each iteration
- Aggregates timing data across all runs
- Calculates averages and standard deviations
- Reports: average total time, average time per chunk, average fastest, average slowest

**Running the test**:
```bash
./gradlew performanceTest --tests "net.minecraft.world.level.chunk.ChunkGenerationBatchPerformanceTest"
```

**Expected output**:
```
========================================
Chunk Generation Batch Performance Test
========================================
Configuration: 20 runs × 100 chunks
World Seed: 12345
========================================
...
(Progress for each run)
...
========================================
Batch Test Aggregate Results
========================================
Runs Completed: 20
Total Chunks Generated: 2000
----------------------------------------
Average Total Time: 15100.45 ms (±345.67 ms)
Average Time per Chunk: 151.00 ms (±3.46 ms)
Average Fastest Chunk: 44.56 ms
Average Slowest Chunk: 458.90 ms
Average Chunks per Second: 6.62
========================================
```

### Test Configuration

Both tests use the following configuration:
- **Fixed Seed**: `12345` for reproducibility
- **Chunk Status**: `ChunkStatus.FULL` (complete generation including lighting)
- **World Type**: Normal Overworld generation
- **Game Mode**: Creative
- **Difficulty**: Normal

### Technical Details

#### Server Environment

These tests create a minimal test server environment that includes:
- Bootstrap initialization of Minecraft registries
- World data loading and configuration
- Chunk generation pipeline with all standard features
- Lighting calculations
- Structure generation
- Feature placement

#### Graphics Context

While these tests initialize the full Minecraft server, they run in a headless mode suitable for CI/CD environments. The "graphics context" mentioned in the requirements refers to the internal rendering pipeline used during chunk generation, which is automatically handled by the test setup.

#### Test Data

The tests create temporary world data in:
- `test-chunk-gen-world/` (single run test)
- `test-chunk-gen-batch-world/` (batch test)

These directories are automatically cleaned up after each test run.

### Interpreting Results

**Total Time**: The complete duration for generating all chunks, including server initialization overhead.

**Average Time per Chunk**: The mean generation time across all chunks. This is the most useful metric for comparing performance across different systems or code changes.

**Fastest/Slowest Chunk**: The minimum and maximum generation times. Variation is expected due to:
- Chunk complexity (terrain features, structures, caves)
- JVM warmup and JIT compilation
- Garbage collection pauses
- System load

**Chunks per Second**: Throughput metric useful for estimating world generation speed.

### Use Cases

**Single Run Test** (`ChunkGenerationPerformanceTest`):
- Quick performance checks during development
- Testing specific chunk generation scenarios
- Debugging performance issues
- Profiling with external tools

**Batch Test** (`ChunkGenerationBatchPerformanceTest`):
- Stable performance baselines for regression testing
- Comparing performance across code changes
- Benchmarking different hardware configurations
- Statistical analysis of generation performance

### Running Both Tests

To run all chunk generation performance tests:
```bash
./gradlew performanceTest --tests "net.minecraft.world.level.chunk.*ChunkGeneration*PerformanceTest"
```

### CI/CD Integration

These tests can run in automated environments:
```bash
# Run tests and save output
./gradlew performanceTest --tests "net.minecraft.world.level.chunk.ChunkGenerationPerformanceTest" > chunk_perf_results.txt

# Run batch tests for stable metrics
./gradlew performanceTest --tests "net.minecraft.world.level.chunk.ChunkGenerationBatchPerformanceTest" > chunk_batch_results.txt
```

## Troubleshooting

If tests fail locally but pass in CI, first compare Java versions, operating
system behavior, and generated files. If tests pass locally but fail only on one
runner, check for path separator, line ending, filesystem case-sensitivity, and
native library differences.

### Notes

- First run may be slower due to JVM warmup - the batch test accounts for this
- Results will vary based on hardware, JVM version, and system load
- For consistent results, run tests on a quiet system with minimal background processes
- The fixed seed ensures the same chunks are generated each time for reproducibility

## Summary

The MattMC testing infrastructure provides:

✅ **Complete isolation** from production builds  
✅ **Modern tooling** with JUnit 5, AssertJ, Mockito, and JMH  
✅ **Clear organization** with separate directories for different test types  
✅ **Multiple execution modes** for different testing scenarios  
✅ **IDE integration** for efficient development workflow  
✅ **Comprehensive documentation** to get started quickly  
✅ **Realistic chunk generation performance tests** using actual Minecraft pathways

## Additional Resources

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Gradle Testing Guide](https://docs.gradle.org/current/userguide/java_testing.html)
- [Awaitility Documentation](https://github.com/awaitility/awaitility/wiki/Usage)

---

**Document Version**: 1.1  
**Last Updated**: January 2025  
**Author**: MattMC Development Team
