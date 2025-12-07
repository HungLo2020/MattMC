# MattMC Testing Infrastructure Buildout

## Overview

This document outlines the testing infrastructure requirements and implementation plan for MattMC. The goal is to establish a robust, maintainable testing framework that supports both pass/fail tests and performance benchmarks while keeping tests isolated from production builds.

## Directory Structure

```
src/test/
├── performance/    # Performance benchmarks and load tests
└── misc/          # General pass/fail unit and integration tests
```

### Directory Purposes

- **`src/test/performance/`**: Contains performance tests, benchmarks, and load tests that measure execution time, memory usage, throughput, and other performance metrics. These tests typically don't have pass/fail criteria but generate reports for analysis.

- **`src/test/misc/`**: Contains traditional unit tests, integration tests, and other pass/fail tests that verify correctness of functionality.

## Core Requirements

### 1. Test Isolation from Production Builds

**Requirement**: All tests must be excluded from final builds and should not be included when running client or server, or when exporting builds via DevExport (RunExport.sh).

**Implementation**:

Tests will be configured in Gradle to use a separate `test` source set that is explicitly excluded from:
- Standard `jar` and `fatJar` tasks
- `clientFatJar` task
- `clientDist` and `clientDistZip` distribution tasks
- Runtime classpath for `runClient` and `runServer` tasks

### 2. Development-Only Scope

**Requirement**: Tests should only exist in the development environment to reduce bloat in release builds.

**Implementation**:
- Tests use `testImplementation` and `testRuntimeOnly` Gradle configurations
- Test dependencies are not included in any runtime or distribution configurations
- `.gitignore` is configured to exclude test build artifacts and reports

### 3. Performance Test Isolation

**Requirement**: Performance tests should be isolated from other tests that are pass/fail.

**Implementation**:
- Separate directory structure (`performance/` vs `misc/`)
- Separate Gradle test tasks allow running performance tests independently
- Performance tests can be excluded from CI/CD pipelines where appropriate

### 4. Clear and Concise Test Output

**Requirement**: Test output should be clear, concise, and useful for developers.

**Implementation**:
- Use JUnit 5 with detailed assertion messages
- Configure test reporting with summary statistics
- Performance tests output formatted results with units and comparisons
- Colored console output for better readability (via Gradle test logging)

## Recommended Testing Frameworks

### Core Testing Framework: JUnit 5 (Jupiter)

**Why JUnit 5?**
- Industry standard for Java testing
- Modern API with improved annotations (@Test, @BeforeEach, @AfterEach, etc.)
- Excellent IDE integration (IntelliJ IDEA, Eclipse)
- Strong assertion library
- Supports parameterized tests and test suites
- Compatible with Java 21

**Dependencies**:
```gradle
testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.1'
testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.1'
testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.1'
```

### Assertions: AssertJ

**Why AssertJ?**
- Fluent API for more readable assertions
- Better error messages than standard JUnit assertions
- Rich assertion methods for collections, strings, and objects
- Minimal learning curve for developers familiar with JUnit

**Dependencies**:
```gradle
testImplementation 'org.assertj:assertj-core:3.25.1'
```

### Performance Testing: JMH (Java Microbenchmark Harness)

**Why JMH?**
- Developed by Oracle/OpenJDK team specifically for microbenchmarking
- Prevents JVM optimization pitfalls (dead code elimination, constant folding)
- Accurate warmup and measurement phases
- Statistical analysis of results
- Standard format for performance benchmarks

**Dependencies**:
```gradle
testImplementation 'org.openjdk.jmh:jmh-core:1.37'
testAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
```

### Mocking: Mockito

**Why Mockito?**
- Most popular mocking framework for Java
- Simple API for creating mocks and stubs
- Useful for isolating code under test
- Good integration with JUnit 5

**Dependencies**:
```gradle
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'org.mockito:mockito-junit-jupiter:5.8.0'
```

### Additional Utilities

#### Awaitility (for asynchronous testing)
Useful for testing Minecraft's networked and threaded components:
```gradle
testImplementation 'org.awaitility:awaitility:4.2.0'
```

#### TestContainers (optional, for integration tests)
If you need to test against external services:
```gradle
testImplementation 'org.testcontainers:testcontainers:1.19.3'
testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
```

## Gradle Configuration

### Source Set Configuration

Add to `build.gradle`:

```gradle
sourceSets {
    main {
        java {
            srcDirs = ['.']
            exclude 'gradle/**'
            exclude 'build/**'
            exclude '.git/**'
            exclude '.idea/**'
            exclude 'com/mojang/authlib/**'
        }
        resources {
            srcDirs = ['src/main/resources']
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

### Test Dependencies Configuration

Add to the `dependencies` block in `build.gradle`:

```gradle
// Testing dependencies - only available in test scope
testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.1'
testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.1'
testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.1'

// Fluent assertions
testImplementation 'org.assertj:assertj-core:3.25.1'

// Mocking framework
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'org.mockito:mockito-junit-jupiter:5.8.0'

// Performance benchmarking
testImplementation 'org.openjdk.jmh:jmh-core:1.37'
testAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'

// Async testing utilities
testImplementation 'org.awaitility:awaitility:4.2.0'
```

### Test Task Configuration

Add test tasks to `build.gradle`:

```gradle
// Configure JUnit 5 test platform
test {
    useJUnitPlatform()
    
    // Configure test output
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "full"
        showStandardStreams = false
        showCauses = true
        showStackTraces = true
        
        // Show summary after test execution
        afterSuite { desc, result ->
            if (!desc.parent) {
                println "\nTest Results: ${result.resultType}"
                println "Tests run: ${result.testCount}, " +
                        "Passed: ${result.successfulTestCount}, " +
                        "Failed: ${result.failedTestCount}, " +
                        "Skipped: ${result.skippedTestCount}"
            }
        }
    }
    
    // Set max heap for tests
    maxHeapSize = "2g"
    
    // Exclude performance tests from regular test runs
    exclude '**/performance/**'
}

// Separate task for performance tests
tasks.register('performanceTest', Test) {
    group = 'verification'
    description = 'Runs performance tests and benchmarks'
    
    useJUnitPlatform {
        includeTags 'performance'
    }
    
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "full"
        showStandardStreams = true  // Show stdout for performance metrics
    }
    
    // Include only performance tests
    include '**/performance/**'
    
    // More memory for performance tests
    maxHeapSize = "4g"
}

// Task to run all tests (both misc and performance)
tasks.register('testAll', Test) {
    group = 'verification'
    description = 'Runs all tests including performance tests'
    
    useJUnitPlatform()
    
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "full"
        showStandardStreams = true
    }
    
    maxHeapSize = "4g"
}
```

### Exclude Tests from Distribution Tasks

Ensure the existing distribution tasks don't include test code. The current configuration already excludes tests by only including the `main` source set, but this should be explicitly verified:

```gradle
// Verify these tasks only use main source set
fatJar {
    from sourceSets.main.output  // Only main, not test
}

clientFatJar {
    from sourceSets.main.output  // Only main, not test
}

clientDist {
    // Verify that only main runtime classpath is copied
    from configurations.runtimeClasspath  // Not testRuntimeClasspath
}
```

## Test Organization Best Practices

### Package Structure

Tests should mirror the package structure of the code being tested:

```
src/test/misc/
├── net/
│   └── minecraft/
│       ├── world/
│       │   └── level/
│       │       └── block/
│       │           └── BlockTest.java
│       ├── util/
│       │   └── MathUtilsTest.java
│       └── network/
│           └── protocol/
│               └── PacketTest.java

src/test/performance/
├── net/
│   └── minecraft/
│       ├── world/
│       │   └── chunk/
│       │       └── ChunkGenerationBenchmark.java
│       └── network/
│           └── NetworkThroughputBenchmark.java
```

### Naming Conventions

- **Unit Tests**: `[ClassName]Test.java` (e.g., `BlockTest.java`)
- **Integration Tests**: `[Feature]IntegrationTest.java` (e.g., `WorldLoadingIntegrationTest.java`)
- **Performance Tests**: `[Feature]Benchmark.java` (e.g., `ChunkGenerationBenchmark.java`)

### Test Class Structure

```java
// Example: src/test/misc/net/minecraft/util/MathHelperTest.java
package net.minecraft.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

@DisplayName("MathHelper Tests")
class MathHelperTest {
    
    @BeforeEach
    void setUp() {
        // Setup code if needed
    }
    
    @Test
    @DisplayName("should clamp values within range")
    void testClamp() {
        assertThat(Mth.clamp(5, 0, 10)).isEqualTo(5);
        assertThat(Mth.clamp(-5, 0, 10)).isEqualTo(0);
        assertThat(Mth.clamp(15, 0, 10)).isEqualTo(10);
    }
    
    @Test
    @DisplayName("should calculate correct distance")
    void testDistance() {
        double distance = Mth.sqrt(
            Mth.square(3.0) + Mth.square(4.0)
        );
        assertThat(distance).isCloseTo(5.0, within(0.001));
    }
}
```

### Performance Test Structure

```java
// Example: src/test/performance/net/minecraft/world/ChunkGenerationBenchmark.java
package net.minecraft.world;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ChunkGenerationBenchmark {
    
    private ChunkGenerator generator;
    
    @Setup
    public void setup() {
        // Initialize chunk generator
        generator = new ChunkGenerator(/* params */);
    }
    
    @Benchmark
    public void benchmarkChunkGeneration() {
        // Code to benchmark
        generator.generateChunk(0, 0);
    }
    
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(ChunkGenerationBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
```

## Running Tests

### Run All Unit/Integration Tests
```bash
./gradlew test
```

### Run Only Performance Tests
```bash
./gradlew performanceTest
```

### Run All Tests (Unit + Performance)
```bash
./gradlew testAll
```

### Run Tests for Specific Package
```bash
./gradlew test --tests "net.minecraft.world.*"
```

### Run Single Test Class
```bash
./gradlew test --tests "net.minecraft.util.MathHelperTest"
```

### Run Specific Test Method
```bash
./gradlew test --tests "net.minecraft.util.MathHelperTest.testClamp"
```

### Generate Test Reports
Test reports are automatically generated at:
```
build/reports/tests/test/index.html
build/reports/tests/performanceTest/index.html
```

## IDE Integration

### IntelliJ IDEA

1. **Running Tests**: Right-click on a test class/method and select "Run"
2. **Debugging Tests**: Right-click and select "Debug"
3. **Test Coverage**: Right-click and select "Run with Coverage"
4. **Gradle Tasks**: Use the Gradle tool window to run test tasks

### Eclipse

1. **Import Project**: Import as Gradle project
2. **Running Tests**: Right-click on test class and select "Run As > JUnit Test"
3. **Gradle Tasks**: Use Gradle Tasks view to run test tasks

## Continuous Integration

### GitHub Actions Example

If you want to add CI testing, create `.github/workflows/test.yml`:

```yaml
name: Tests

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Run tests
      run: ./gradlew test
    
    - name: Publish test results
      uses: EnricoMi/publish-unit-test-result-action@v2
      if: always()
      with:
        files: build/test-results/test/**/*.xml
```

## .gitignore Updates

Add test-related entries to `.gitignore` (if not already present):

```gitignore
# Test outputs
build/test-results/
build/reports/tests/
.test-data/

# JMH generated files
jmh-result.json
jmh-result.txt

# Test coverage
.jacoco/
```

## Test Data Management

### Test Resources

Create `src/test/resources/` for test-specific resources:

```
src/test/resources/
├── test-worlds/         # Test world data
├── test-configs/        # Test configuration files
└── fixtures/            # Test data fixtures
```

### Test Data in Code

For inline test data, consider using:
- JUnit 5 `@ParameterizedTest` for multiple inputs
- Test fixtures as static methods
- Builder pattern for complex test objects

## Test Categories with JUnit 5 Tags

Use tags to categorize tests:

```java
@Test
@Tag("fast")
void quickUnitTest() { }

@Test
@Tag("slow")
@Tag("integration")
void slowIntegrationTest() { }

@Test
@Tag("performance")
void performanceBenchmark() { }
```

Run specific categories:
```bash
./gradlew test --tests "*" -Dgroups="fast"
./gradlew test --tests "*" -Dgroups="integration"
```

## Example Test Scenarios for MattMC

### Suggested Test Coverage Areas

1. **World Generation Tests** (`src/test/misc/`)
   - Biome distribution correctness
   - Chunk boundary consistency
   - Structure generation validation

2. **Network Protocol Tests** (`src/test/misc/`)
   - Packet serialization/deserialization
   - Protocol version compatibility
   - Connection handling

3. **Entity Behavior Tests** (`src/test/misc/`)
   - Movement calculations
   - AI pathfinding
   - Entity interactions

4. **Performance Benchmarks** (`src/test/performance/`)
   - Chunk generation speed
   - Network throughput
   - Entity tick performance
   - Rendering frame time

5. **Utility Function Tests** (`src/test/misc/`)
   - Math helpers
   - Data structure operations
   - Codec operations

## Security Considerations

### Test Dependencies

All test dependencies are:
- Scoped to `testImplementation` or `testRuntimeOnly`
- Not included in production builds
- Excluded from distribution JARs
- Never exposed to end users

### Test Data

- Never commit sensitive data to test resources
- Use mock credentials for authentication tests
- Sanitize any production data used in tests

## Performance Testing Best Practices

### Warmup Phase

Always include warmup iterations to allow JIT compilation:
```java
@Warmup(iterations = 3, time = 1)
```

### Measurement Phase

Use sufficient iterations for statistical significance:
```java
@Measurement(iterations = 5, time = 1)
```

### Result Interpretation

- Focus on average time and standard deviation
- Look for outliers that might indicate issues
- Compare results across changes to detect regressions
- Document baseline performance metrics

### Avoiding Pitfalls

- Don't test trivial operations (JVM optimizes them away)
- Ensure tests actually use computed results
- Test realistic workloads, not artificial scenarios
- Run on consistent hardware for comparison

## Documentation and Reporting

### Test Documentation

Each test should be self-documenting:
- Use descriptive test method names
- Add `@DisplayName` annotations for clarity
- Include comments for complex test setup
- Document expected behavior in assertions

### Performance Reports

Performance test results should include:
- Average execution time
- Standard deviation
- Min/max values
- Throughput metrics (operations per second)
- Memory usage statistics

## Maintenance and Evolution

### Adding New Tests

When adding functionality:
1. Write tests first (TDD approach) or alongside code
2. Ensure tests are in correct directory (`misc/` or `performance/`)
3. Follow naming conventions
4. Run tests before committing

### Updating Tests

When modifying code:
1. Update relevant tests
2. Add new test cases for new scenarios
3. Remove obsolete tests
4. Verify all tests still pass

### Test Refactoring

Regularly refactor tests to:
- Remove duplication
- Improve readability
- Update to new patterns
- Optimize slow tests

## Troubleshooting

### Tests Not Running

Check:
- `useJUnitPlatform()` is configured in test task
- Test dependencies are in `testImplementation`
- Test classes are in correct source set (`src/test/`)

### Gradle Not Finding Tests

Ensure:
- Test classes have `Test` suffix
- Test methods have `@Test` annotation
- No compilation errors in test code

### Performance Test Issues

Common problems:
- Insufficient warmup leading to inconsistent results
- Dead code elimination by JIT (use Blackhole consumer)
- Background processes affecting measurements
- Comparing results from different machines

## Summary

This testing infrastructure provides:

✅ **Isolation**: Tests completely separated from production code  
✅ **Organization**: Clear structure with `performance/` and `misc/` directories  
✅ **Modern Tools**: JUnit 5, AssertJ, JMH, Mockito  
✅ **Flexibility**: Run all tests, specific categories, or individual tests  
✅ **Clear Output**: Detailed, formatted test results  
✅ **IDE Integration**: Works seamlessly with IntelliJ IDEA and Eclipse  
✅ **Build Exclusion**: Tests never included in distributions  
✅ **Best Practices**: Follows Java testing standards

## Next Steps

1. **Add test dependencies** to `build.gradle` as outlined above
2. **Configure test tasks** in `build.gradle`
3. **Update `.gitignore`** with test artifacts
4. **Write first test** to validate the setup
5. **Document test results** and iterate

## Additional Resources

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [JMH Tutorial](https://github.com/openjdk/jmh)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Gradle Testing Guide](https://docs.gradle.org/current/userguide/java_testing.html)

---

**Document Version**: 1.0  
**Last Updated**: December 2024  
**Author**: MattMC Development Team
