# JSON Resource Loading Performance Testing - Example Session

This document shows a real example session of using the performance testing infrastructure.

## Running the Tests

```bash
$ cd /home/runner/work/MattMC/MattMC
$ ./gradlew performanceTest --tests "JsonResourceLoadingPerformanceTest"
```

## Example Output

```
> Task :performanceTest

JSON Resource Loading Performance Tests > should measure complete end-to-end resource loading STANDARD_OUT

    === Complete End-to-End Resource Loading ===

    Performance Breakdown:
      Stage 1 - File I/O Performance Summary:
      Iterations: 100
      Average:    85.17 μs
      Median:     81.87 μs
      Min:        61.43 μs
      Max:        127.19 μs
      P95:        107.30 μs
      P99:        121.70 μs
      
      Stage 2 - Parsing Performance Summary:
      Iterations: 100
      Average:    309.10 μs
      Median:     273.99 μs
      Min:        91.19 μs
      Max:        644.08 μs
      P95:        479.58 μs
      P99:        522.02 μs
      
      Total (All Stages) Performance Summary:
      Iterations: 100
      Average:    396.81 μs
      Median:     365.43 μs
      Min:        158.07 μs
      Max:        763.31 μs
      P95:        558.75 μs
      P99:        625.24 μs

    Percentage Breakdown:
      File I/O: 21.5%
      Parsing:  77.9%

JSON Resource Loading Performance Tests > should measure complete end-to-end resource loading PASSED

JSON Resource Loading Performance Tests > should measure file I/O separately from parsing STANDARD_OUT

    === File I/O vs Parsing Performance Breakdown ===
    File I/O Performance Summary:
      Iterations: 100
      Average:    61.27 μs
      Median:     54.33 μs
      Min:        48.47 μs
      Max:        327.78 μs
      P95:        82.20 μs
      P99:        181.37 μs
      
    Parsing Performance Summary:
      Iterations: 100
      Average:    107.66 μs
      Median:     97.64 μs
      Min:        88.92 μs
      Max:        335.13 μs
      P95:        133.94 μs
      P99:        330.50 μs

    Total Pipeline (I/O + Parsing): 168.92 μs average

JSON Resource Loading Performance Tests > should measure file I/O separately from parsing PASSED

JSON Resource Loading Performance Tests > should load large JSON files quickly with Gson STANDARD_OUT

    === Large JSON Loading Performance (Gson) ===
    Large JSON (Gson) Performance Summary:
      Iterations: 100
      Average:    173.98 μs
      Median:     160.09 μs
      Min:        137.33 μs
      Max:        446.26 μs
      P95:        253.06 μs
      P99:        386.50 μs

JSON Resource Loading Performance Tests > should load large JSON files quickly with Gson PASSED

JSON Resource Loading Performance Tests > should load large JSON files quickly with NightConfig STANDARD_OUT

    === Large JSON Loading Performance (NightConfig) ===
    Large JSON (NightConfig) Performance Summary:
      Iterations: 100
      Average:    480.99 μs
      Median:     423.69 μs
      Min:        253.12 μs
      Max:        911.15 μs
      P95:        691.18 μs
      P99:        892.08 μs

JSON Resource Loading Performance Tests > should load large JSON files quickly with NightConfig PASSED

Performance Test Results: SUCCESS
Tests run: 8, Passed: 8, Failed: 0, Skipped: 0

BUILD SUCCESSFUL in 56s
```

## What This Tells Us

### 1. Parsing is the Bottleneck
From the "Complete End-to-End" test:
- File I/O: 21.5% (85.17 μs average)
- Parsing: 77.9% (309.10 μs average)

**Insight:** If you want to optimize JSON loading, focus on parsing, not file I/O.

### 2. Gson is Faster for Larger Files
Comparing the large JSON tests:
- Gson: 173.98 μs average
- NightConfig: 480.99 μs average

**Insight:** Gson is ~2.8x faster than NightConfig for the 8KB test file.

### 3. P95/P99 Shows Worst-Case Performance
For the large JSON with Gson:
- Average: 173.98 μs
- P95: 253.06 μs (45% slower than average)
- P99: 386.50 μs (122% slower than average)

**Insight:** 99% of loads complete in under 387 μs, but occasional outliers take longer.

### 4. Small Files Have Similar Performance
Small JSON (both parsers):
- Gson: ~81 μs average
- NightConfig: ~59 μs average

**Insight:** For small files, the choice of parser matters less.

## Creating Your Own Performance Test

Here's a minimal example:

```java
package net.minecraft.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

@DisplayName("My Resource Loading Tests")
class MyResourceLoadingPerformanceTest {
    
    private static final int WARMUP_ITERATIONS = 10;
    private static final int TEST_ITERATIONS = 100;
    
    @Test
    @DisplayName("should load my custom resource quickly")
    void testMyCustomResource() {
        System.out.println("\n=== My Custom Resource Loading ===");
        
        PerformanceTimer timer = new PerformanceTimer();
        
        // Warmup (important!)
        PerformanceTimer.warmup(WARMUP_ITERATIONS, () -> {
            loadMyResource();
        });
        
        // Measure
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            timer.time(() -> {
                loadMyResource();
            });
        }
        
        // Print results
        timer.printSummary("My Custom Resource");
    }
    
    private void loadMyResource() {
        // Your resource loading code here
    }
}
```

Save as `src/test/performance/net/minecraft/resources/MyResourceLoadingPerformanceTest.java`

Run with:
```bash
./gradlew performanceTest --tests "MyResourceLoadingPerformanceTest"
```

## Tips for Accurate Measurements

### 1. Always Warm Up
JIT compilation affects the first few iterations:
```java
PerformanceTimer.warmup(10, operation);  // Do this!
```

### 2. Run Enough Iterations
At least 100 iterations for reliable statistics:
```java
for (int i = 0; i < 100; i++) {  // Not 10!
    timer.time(operation);
}
```

### 3. Focus on P95/P99
For user-facing operations:
- **Average** can be misleading (affected by outliers)
- **P95** = 95% of users will see this or better
- **P99** = worst-case for 99% of users

### 4. Test Realistic Data
Use real JSON files from your application:
```java
String resourcePath = "/assets/minecraft/sounds.json";
// Not: String resourcePath = "/tiny-test-file.json";
```

## Common Patterns

### Pattern 1: Measure Stages Separately
```java
PerformanceTimer ioTimer = new PerformanceTimer();
PerformanceTimer parseTimer = new PerformanceTimer();

for (int i = 0; i < 100; i++) {
    ioTimer.time(() -> { /* read file */ });
    parseTimer.time(() -> { /* parse JSON */ });
}

ioTimer.printSummary("File I/O");
parseTimer.printSummary("Parsing");
```

### Pattern 2: Compare Implementations
```java
void testGsonVsJackson() {
    PerformanceTimer gsonTimer = new PerformanceTimer();
    PerformanceTimer jacksonTimer = new PerformanceTimer();
    
    // Warmup both
    PerformanceTimer.warmup(10, () -> gsonLoad());
    PerformanceTimer.warmup(10, () -> jacksonLoad());
    
    // Measure both
    for (int i = 0; i < 100; i++) {
        gsonTimer.time(() -> gsonLoad());
        jacksonTimer.time(() -> jacksonLoad());
    }
    
    gsonTimer.printSummary("Gson");
    jacksonTimer.printSummary("Jackson");
}
```

### Pattern 3: Size Scaling Test
```java
void testScaling() {
    String[] files = {"small.json", "medium.json", "large.json"};
    
    for (String file : files) {
        PerformanceTimer timer = new PerformanceTimer();
        PerformanceTimer.warmup(10, () -> load(file));
        
        for (int i = 0; i < 100; i++) {
            timer.time(() -> load(file));
        }
        
        timer.printSummary(file);
    }
}
```

## Interpreting Results

### Good Performance
- Average < 200 μs for medium files (1-10KB)
- P95 within 2x of average
- P99 within 3x of average

### Needs Optimization
- Average > 1ms for medium files
- P95 > 3x average (high variance)
- Large gap between median and average

### Red Flags
- Very high P99 (> 10x average) = occasional stalls
- High max (> 100x average) = GC pauses or disk I/O issues
- Increasing times with file size (non-linear) = algorithmic issue

## Next Steps

1. **Measure your code**: Create tests for your resource loading
2. **Find bottlenecks**: Use stage-by-stage timing
3. **Compare alternatives**: Test different parsers/approaches
4. **Optimize**: Focus on the slowest stages
5. **Verify**: Re-run tests to confirm improvements

For more details, see:
- [JSON_PERFORMANCE_TESTING.md](JSON_PERFORMANCE_TESTING.md) - Complete guide
- [PERFORMANCE_TESTING_QUICKREF.md](PERFORMANCE_TESTING_QUICKREF.md) - Quick reference
