# Performance Testing Quick Reference

## Run Tests
```bash
# All performance tests
./gradlew performanceTest

# JSON loading tests only
./gradlew performanceTest --tests "JsonResourceLoadingPerformanceTest"

# Specific test
./gradlew performanceTest --tests "JsonResourceLoadingPerformanceTest.testLargeJsonWithGson"
```

## Quick Test Template
```java
import net.minecraft.resources.PerformanceTimer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

@DisplayName("My Performance Tests")
class MyPerformanceTest {
    
    private static final int WARMUP_ITERATIONS = 10;
    private static final int TEST_ITERATIONS = 100;
    
    @Test
    @DisplayName("should do something quickly")
    void testSomething() {
        PerformanceTimer timer = new PerformanceTimer();
        
        // Warmup
        PerformanceTimer.warmup(WARMUP_ITERATIONS, () -> {
            // Your operation
        });
        
        // Measure
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            timer.time(() -> {
                // Your operation
            });
        }
        
        // Print results
        timer.printSummary("Operation Name");
    }
}
```

## PerformanceTimer API
```java
PerformanceTimer timer = new PerformanceTimer();

// Start/Stop manually
timer.start();
// ... operation ...
long nanos = timer.stop();

// Time automatically
timer.time(() -> { /* operation */ });

// Statistics
timer.getAverage();    // Average duration
timer.getMedian();     // Median duration
timer.getMin();        // Minimum duration
timer.getMax();        // Maximum duration
timer.getP95();        // 95th percentile
timer.getP99();        // 99th percentile
timer.getCount();      // Number of measurements

// Output
timer.printSummary("Label");
PerformanceTimer.formatDuration(nanos);  // "123.45 μs"
PerformanceTimer.warmup(10, operation);  // Warmup helper
```

## Naming Convention
- Test class: `*PerformanceTest.java` or `*Benchmark.java`
- Location: `src/test/performance/`
- Package: Matches the code being tested

## Performance Targets
| File Size | Target Time |
|-----------|-------------|
| < 1KB     | < 100μs     |
| 1-10KB    | < 200μs     |
| 10-100KB  | < 500μs     |
| > 100KB   | < 2ms       |

## Understanding Output
```
Performance Summary:
  Iterations: 100          ← Number of measurements
  Average:    175.85 μs    ← Mean time
  Median:     160.34 μs    ← Middle value (ignore outliers)
  Min:        140.78 μs    ← Best case
  Max:        460.87 μs    ← Worst case
  P95:        256.93 μs    ← 95% were faster
  P99:        456.32 μs    ← 99% were faster
```

**Use Median for typical performance, P95/P99 for worst-case analysis.**
