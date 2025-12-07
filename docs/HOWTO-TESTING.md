# MattMC Testing Infrastructure Guide

## Table of Contents
- [Overview](#overview)
- [Testing Infrastructure Architecture](#testing-infrastructure-architecture)
- [Directory Structure](#directory-structure)
- [Testing Frameworks and Libraries](#testing-frameworks-and-libraries)
- [Writing Tests](#writing-tests)
- [Running Tests](#running-tests)
- [Test Isolation from Production Builds](#test-isolation-from-production-builds)
- [IDE Integration](#ide-integration)
- [Best Practices](#best-practices)
- [Advanced Topics](#advanced-topics)
- [Troubleshooting](#troubleshooting)

## Overview

MattMC uses a comprehensive testing infrastructure built on industry-standard Java testing frameworks. The testing system is designed to:

- **Separate concerns**: Performance tests are isolated from pass/fail tests
- **Exclude from production**: Tests are never included in production builds or distributions
- **Support multiple test types**: Unit tests, integration tests, and performance benchmarks
- **Provide clear output**: Formatted, actionable test results
- **Integrate with IDEs**: Works seamlessly with IntelliJ IDEA and Eclipse

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

### 2. **Test Dependencies**

All testing dependencies use the `testImplementation` or `testRuntimeOnly` configurations, which means they are only available during testing and never included in production builds:

```gradle
// JUnit 5 (Jupiter) - Core testing framework
testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.1'
testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.1'
testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.1'

// AssertJ - Fluent assertions library
testImplementation 'org.assertj:assertj-core:3.25.1'

// Mockito - Mocking framework
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'org.mockito:mockito-junit-jupiter:5.8.0'

// JMH - Java Microbenchmark Harness for performance testing
testImplementation 'org.openjdk.jmh:jmh-core:1.37'
testAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'

// Awaitility - Async testing utilities
testImplementation 'org.awaitility:awaitility:4.2.0'
```

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

## Testing Frameworks and Libraries

### JUnit 5 (Jupiter)

**Purpose**: Core testing framework for writing and running tests.

**Why JUnit 5?**
- Industry standard for Java testing
- Modern API with improved annotations
- Excellent IDE integration
- Strong assertion library
- Supports parameterized tests and test suites
- Compatible with Java 21

**Key Annotations**:
- `@Test`: Marks a method as a test
- `@DisplayName`: Provides human-readable test names
- `@BeforeEach`/`@AfterEach`: Setup and teardown before/after each test
- `@BeforeAll`/`@AfterAll`: Setup and teardown before/after all tests
- `@ParameterizedTest`: Run test with multiple parameter sets
- `@Tag`: Categorize tests (e.g., "fast", "slow", "integration")

### AssertJ

**Purpose**: Fluent assertion library for more readable test assertions.

**Why AssertJ?**
- Fluent, chainable API
- Better error messages than standard JUnit assertions
- Rich assertion methods for collections, strings, and objects
- Minimal learning curve

**Example Usage**:
```java
assertThat(result).isNotNull();
assertThat(value).isEqualTo(expected);
assertThat(list).hasSize(3).contains("item1", "item2");
assertThat(number).isCloseTo(5.0, within(0.001));
```

### Mockito

**Purpose**: Mocking framework for creating test doubles.

**Why Mockito?**
- Most popular mocking framework for Java
- Simple API for creating mocks and stubs
- Useful for isolating code under test
- Good integration with JUnit 5

**Example Usage**:
```java
// Create a mock
MyService mockService = mock(MyService.class);

// Define behavior
when(mockService.getData()).thenReturn("test data");

// Verify interactions
verify(mockService).getData();
```

### JMH (Java Microbenchmark Harness)

**Purpose**: Performance benchmarking framework.

**Why JMH?**
- Developed by Oracle/OpenJDK team specifically for microbenchmarking
- Prevents JVM optimization pitfalls
- Accurate warmup and measurement phases
- Statistical analysis of results
- Standard format for performance benchmarks

**Key Annotations**:
- `@Benchmark`: Marks a method as a benchmark
- `@State`: Defines benchmark state
- `@Setup`/`@TearDown`: Setup and teardown for benchmarks
- `@Warmup`: Configure warmup iterations
- `@Measurement`: Configure measurement iterations
- `@BenchmarkMode`: Define what to measure (throughput, average time, etc.)

### Awaitility

**Purpose**: Testing asynchronous operations.

**Why Awaitility?**
- Simplifies testing of asynchronous code
- Useful for Minecraft's networked and threaded components
- Avoids Thread.sleep() in tests
- Provides clear, readable syntax

**Example Usage**:
```java
await()
    .atMost(Duration.ofSeconds(5))
    .until(() -> asyncOperation.isComplete());
```

## Writing Tests

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

## Test Isolation from Production Builds

### How Tests Are Excluded

Tests are completely isolated from production builds through multiple mechanisms:

1. **Source Set Separation**: The main source set explicitly excludes `src/test/**`:
   ```gradle
   main {
       java {
           srcDirs = ['.']
           exclude 'src/test/**'
       }
   }
   ```

2. **Dependency Scoping**: All test dependencies use `testImplementation` or `testRuntimeOnly`, which are not included in runtime classpaths.

3. **Explicit Build Configuration**: Production builds (jar, fatJar, clientDist) only use `sourceSets.main.output`:
   ```gradle
   fatJar {
       from sourceSets.main.output  // Only main, not test
   }
   ```

### Verification

You can verify that tests are excluded from builds:

```bash
# Build the JAR
./gradlew jar

# Check that test classes are not included
jar tf build/libs/MattMC-1.21.10.jar | grep -i "MthTest"
# (Should return nothing)

# Check that test dependencies are not included
jar tf build/libs/MattMC-1.21.10-all.jar | grep -i "junit"
# (Should return nothing)
```

### What Gets Excluded

The following are **never** included in production builds:
- Test source files from `src/test/`
- Test class files
- Test dependencies (JUnit, AssertJ, Mockito, JMH, Awaitility)
- Test resources from `src/test/resources/`

The following **are** included in production builds:
- All source files from the main source set (excluding `src/test/`)
- Production dependencies
- Resources from `src/main/resources/`

## IDE Integration

### IntelliJ IDEA

#### Setting Up

1. Open the project in IntelliJ IDEA
2. IntelliJ will automatically detect the Gradle configuration
3. Test source roots will be marked in blue/green

#### Running Tests

- **Run single test**: Right-click on a test method → "Run 'testName()'"
- **Run test class**: Right-click on a test class → "Run 'ClassName'"
- **Run all tests in package**: Right-click on a package → "Run Tests in 'package'"
- **Debug tests**: Right-click → "Debug" instead of "Run"
- **Run with coverage**: Right-click → "Run with Coverage"

#### Gradle Tasks

Use the Gradle tool window (View → Tool Windows → Gradle) to run test tasks:
- Navigate to MattMC → Tasks → verification
- Double-click on `test`, `performanceTest`, or `testAll`

#### Keyboard Shortcuts

- `Ctrl+Shift+F10` (Windows/Linux) or `Cmd+Shift+R` (Mac): Run test at cursor
- `Shift+F10` (Windows/Linux) or `Ctrl+R` (Mac): Re-run last test

### Eclipse

#### Setting Up

1. Import the project as a Gradle project (File → Import → Gradle → Existing Gradle Project)
2. Eclipse will configure the test source folders automatically

#### Running Tests

- **Run single test**: Right-click on test class → "Run As → JUnit Test"
- **Run all tests**: Right-click on `src/test` → "Run As → JUnit Test"

#### Gradle Tasks

Use the Gradle Tasks view (Window → Show View → Gradle Tasks):
- Expand MattMC → verification
- Double-click on test tasks to run them

## Best Practices

### Test Organization

1. **Mirror package structure**: Test packages should match the code they test
   ```
   src/main/java/net/minecraft/util/Mth.java
   src/test/misc/net/minecraft/util/MthTest.java
   ```

2. **Use descriptive names**: Test class and method names should clearly describe what they test
   ```java
   @Test
   @DisplayName("should clamp negative values to minimum bound")
   void testClampNegativeValue() { ... }
   ```

3. **Follow naming conventions**:
   - Unit tests: `[ClassName]Test.java`
   - Integration tests: `[Feature]IntegrationTest.java`
   - Benchmarks: `[Feature]Benchmark.java`

### Test Structure

Use the **Arrange-Act-Assert** (AAA) pattern:

```java
@Test
void testBlockPosCreation() {
    // Arrange: Set up test data
    int x = 100, y = 64, z = 200;
    
    // Act: Execute the code under test
    BlockPos pos = new BlockPos(x, y, z);
    
    // Assert: Verify the results
    assertThat(pos.getX()).isEqualTo(x);
    assertThat(pos.getY()).isEqualTo(y);
    assertThat(pos.getZ()).isEqualTo(z);
}
```

### Test Independence

- Each test should be independent and not rely on other tests
- Use `@BeforeEach` for setup instead of relying on test execution order
- Clean up resources in `@AfterEach` if needed

### Test Coverage

Focus on testing:
1. **Public APIs**: All public methods should have tests
2. **Edge cases**: Boundary conditions, null values, empty collections
3. **Error conditions**: Exception handling, invalid input
4. **Complex logic**: Algorithms, calculations, state machines
5. **Integration points**: Network protocols, data serialization

Don't test:
- Private methods (test through public API)
- Getters/setters without logic
- Framework code
- Simple delegations

### Assertions

Use AssertJ for clearer, more readable assertions:

```java
// Less clear (JUnit)
assertEquals(5, result);
assertTrue(list.contains("item"));

// More clear (AssertJ)
assertThat(result).isEqualTo(5);
assertThat(list).contains("item");
```

### Performance Testing

When writing performance benchmarks:

1. **Include warmup**: Allow JVM to warm up and JIT compile
   ```java
   @Warmup(iterations = 3, time = 1)
   ```

2. **Multiple measurements**: Run multiple iterations for statistical significance
   ```java
   @Measurement(iterations = 5, time = 1)
   ```

3. **Use realistic workloads**: Test actual use cases, not artificial scenarios

4. **Avoid dead code elimination**: Use `Blackhole` to consume results
   ```java
   @Benchmark
   public void benchmark(Blackhole bh) {
       bh.consume(expensiveCalculation());
   }
   ```

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

## Troubleshooting

### Tests Not Running

**Problem**: `./gradlew test` reports "NO-SOURCE" or doesn't find tests.

**Solution**:
- Verify test classes are in `src/test/misc/` or `src/test/performance/`
- Ensure test classes have `Test` suffix
- Check that test methods have `@Test` annotation
- Verify no compilation errors in test code

### Dependencies Not Found

**Problem**: Test dependencies not available during compilation.

**Solution**:
- Verify dependencies are in `testImplementation` configuration
- Run `./gradlew clean build` to refresh dependencies
- Check that `useJUnitPlatform()` is configured in test task

### Performance Tests Not Running

**Problem**: `./gradlew performanceTest` reports NO-SOURCE.

**Solution**:
- Performance tests need to be in `src/test/performance/` directory
- If using JUnit tags, tests need `@Tag("performance")` annotation
- JMH benchmarks don't work with `performanceTest` task by default - run them directly with their `main()` method

### Test Classes in Production JAR

**Problem**: Test classes appear in the production JAR file.

**Solution**:
- Verify `exclude 'src/test/**'` is in main source set
- Ensure production tasks use `sourceSets.main.output`
- Run `./gradlew clean` and rebuild

### OutOfMemoryError During Tests

**Problem**: Tests fail with heap space errors.

**Solution**:
- Increase heap size in test task:
  ```gradle
  test {
      maxHeapSize = "4g"
  }
  ```
- Split tests into smaller suites
- Check for memory leaks in test setup

### Slow Tests

**Problem**: Tests take too long to run.

**Solutions**:
- Run only changed tests during development
- Use tags to separate fast and slow tests
- Optimize test setup and teardown
- Use mocks instead of real dependencies
- Run tests in parallel (advanced configuration)

## Summary

The MattMC testing infrastructure provides:

✅ **Complete isolation** from production builds  
✅ **Modern tooling** with JUnit 5, AssertJ, Mockito, and JMH  
✅ **Clear organization** with separate directories for different test types  
✅ **Multiple execution modes** for different testing scenarios  
✅ **IDE integration** for efficient development workflow  
✅ **Comprehensive documentation** to get started quickly

## Additional Resources

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Gradle Testing Guide](https://docs.gradle.org/current/userguide/java_testing.html)
- [Awaitility Documentation](https://github.com/awaitility/awaitility/wiki/Usage)

---

**Document Version**: 1.0  
**Last Updated**: December 2024  
**Author**: MattMC Development Team
