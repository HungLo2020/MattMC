# JSON Resource Loading Performance Testing

This document describes how to use the performance testing infrastructure for measuring JSON resource loading times.

## Overview

The JSON resource loading performance testing infrastructure allows you to measure:
1. **File I/O time** - How long it takes to read JSON files from disk/resources
2. **Parsing time** - How long it takes to parse JSON text into objects
3. **End-to-end time** - Complete pipeline from file access to parsed objects
4. **Different parsers** - Compare Gson vs NightConfig performance

## Running Performance Tests

### Run all performance tests
```bash
./gradlew performanceTest
```

### Run only JSON resource loading tests
```bash
./gradlew performanceTest --tests "JsonResourceLoadingPerformanceTest"
```

### Run a specific test
```bash
./gradlew performanceTest --tests "JsonResourceLoadingPerformanceTest.testLargeJsonWithGson"
```

## Test Categories

The performance test suite includes:

### 1. Size-based Tests
- **Small JSON** (~100 bytes) - Simple configuration files
- **Medium JSON** (~4KB) - Language files, model definitions
- **Large JSON** (~8KB) - Sounds.json, complex data structures

### 2. Parser Comparison Tests
- **Gson** - Google's JSON library (used by Minecraft for most JSON)
- **NightConfig** - Used by Distant Horizons and some mods

### 3. Performance Breakdown Tests
- **File I/O vs Parsing** - Separates disk read time from parsing time
- **Complete End-to-End** - Measures all stages together with percentage breakdown

## Understanding the Output

When you run the tests, you'll see output like this:

```
=== Large JSON Loading Performance (Gson) ===
Large JSON (Gson) Performance Summary:
  Iterations: 100
  Average:    175.85 μs
  Median:     160.34 μs
  Min:        140.78 μs
  Max:        460.87 μs
  P95:        256.93 μs
  P99:        456.32 μs
```

### Metrics Explained:
- **Iterations** - Number of times the operation was measured (after warmup)
- **Average** - Mean time across all iterations
- **Median** - Middle value (less affected by outliers than average)
- **Min** - Fastest execution time
- **Max** - Slowest execution time
- **P95** - 95th percentile (95% of operations were faster than this)
- **P99** - 99th percentile (99% of operations were faster than this)

### Time Units:
- **ns** - Nanoseconds (1 billionth of a second)
- **μs** - Microseconds (1 millionth of a second)
- **ms** - Milliseconds (1 thousandth of a second)
- **s** - Seconds

## Example: Complete End-to-End Test Output

```
=== Complete End-to-End Resource Loading ===

Performance Breakdown:
  Stage 1 - File I/O Performance Summary:
  Iterations: 100
  Average:    85.24 μs
  Median:     82.22 μs
  Min:        44.97 μs
  Max:        124.77 μs
  P95:        119.09 μs
  P99:        122.31 μs
  
  Stage 2 - Parsing Performance Summary:
  Iterations: 100
  Average:    307.89 μs
  Median:     277.91 μs
  Min:        59.06 μs
  Max:        734.79 μs
  P95:        483.98 μs
  P99:        558.40 μs
  
  Total (All Stages) Performance Summary:
  Iterations: 100
  Average:    396.22 μs
  Median:     370.92 μs
  Min:        105.32 μs
  Max:        879.35 μs
  P95:        577.22 μs
  P99:        654.57 μs

Percentage Breakdown:
  File I/O: 21.5%
  Parsing:  77.7%
```

### Interpretation:
- **File I/O takes ~21.5%** of the total time (reading the JSON file)
- **Parsing takes ~77.7%** of the total time (converting text to objects)
- This means **parsing is the bottleneck**, not file I/O
- Total time to load and parse: **~396 microseconds average**

## Creating Your Own Performance Tests

### 1. Using the PerformanceTimer Utility

```java
import net.minecraft.resources.PerformanceTimer;

PerformanceTimer timer = new PerformanceTimer();

// Warmup (important for accurate JIT-compiled measurements)
PerformanceTimer.warmup(10, () -> {
    // Your operation here
    loadJsonFile();
});

// Actual measurements
for (int i = 0; i < 100; i++) {
    timer.time(() -> {
        loadJsonFile();
    });
}

// Print results
timer.printSummary("My JSON Loading Test");
```

### 2. Measuring Specific Stages

```java
PerformanceTimer ioTimer = new PerformanceTimer();
PerformanceTimer parseTimer = new PerformanceTimer();

for (int i = 0; i < 100; i++) {
    // Measure file I/O
    ioTimer.start();
    String content = readFile("myfile.json");
    ioTimer.stop();
    
    // Measure parsing
    parseTimer.start();
    JsonObject obj = gson.fromJson(content, JsonObject.class);
    parseTimer.stop();
}

ioTimer.printSummary("File I/O");
parseTimer.printSummary("Parsing");
```

### 3. Testing Your Own JSON Files

Add your JSON files to `src/test/resources/json_performance_tests/` and reference them:

```java
@Test
void testMyCustomJson() {
    String resourcePath = "/json_performance_tests/my_custom.json";
    PerformanceTimer timer = new PerformanceTimer();
    
    // Warmup
    PerformanceTimer.warmup(10, () -> {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        // ... load and parse
    });
    
    // Measure
    for (int i = 0; i < 100; i++) {
        timer.time(() -> {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            // ... load and parse
        });
    }
    
    timer.printSummary("My Custom JSON");
}
```

## Best Practices

### 1. Always Warm Up
JIT compilation affects initial runs. Always do 10+ warmup iterations:
```java
PerformanceTimer.warmup(10, operation);
```

### 2. Run Enough Iterations
Run at least 100 iterations to get reliable statistics:
```java
for (int i = 0; i < 100; i++) {
    timer.time(operation);
}
```

### 3. Focus on P95/P99
For user-facing operations, P95 and P99 are more important than average:
- **Average** can be misleading due to outliers
- **P95** tells you what 95% of users will experience
- **P99** helps identify worst-case performance

### 4. Test Realistic Data
Use JSON files similar to what you'll encounter in production:
- Real language files
- Actual model definitions
- Representative sound configurations

## Integration with Existing Tests

This infrastructure follows the same pattern as existing performance tests:
- Located in `src/test/performance/` directory
- Uses JUnit 5 `@Test` annotations
- Runs with `./gradlew performanceTest`
- Excluded from regular test runs

## Performance Targets

Based on the test results:
- **Small JSON (< 1KB)**: Target < 100μs
- **Medium JSON (1-10KB)**: Target < 200μs
- **Large JSON (10-100KB)**: Target < 500μs

If your tests exceed these targets, consider:
1. Using a faster JSON parser
2. Caching parsed results
3. Lazy loading when possible
4. Pre-processing JSON during build time

## Troubleshooting

### Tests are slow
- Check if warmup iterations are sufficient
- Increase heap size: `./gradlew performanceTest -Xmx4g`
- Run with `--no-daemon` to avoid Gradle daemon overhead

### Inconsistent results
- Ensure warmup is happening
- Increase iteration count
- Look at median and percentiles, not just average
- Check for background processes affecting performance

### Out of memory errors
- Reduce iteration count
- Increase heap size in `build.gradle` performanceTest task
- Close resources properly in tests

## Further Reading

- See `MthPerformanceTest.java` for simpler performance testing examples
- See `BlockPosBenchmark.java` for JMH-based benchmarking
- JUnit 5 documentation: https://junit.org/junit5/
- JMH documentation: https://openjdk.java.net/projects/code-tools/jmh/
