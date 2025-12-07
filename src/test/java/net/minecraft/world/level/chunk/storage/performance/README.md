# Chunk Performance Testing Infrastructure

This directory contains performance benchmarking tests for MattMC's chunk save and load operations.

## Overview

The `ChunkPerformanceTest` provides comprehensive benchmarks to measure the performance of chunk serialization, I/O operations, and deserialization. It establishes baseline metrics and can be used to validate optimization efforts.

## Running the Tests

### Option 1: Using the convenience script (Recommended)
```bash
./RunChunkPerformanceTest.sh
```

### Option 2: Using Gradle directly
```bash
./gradlew runChunkPerformanceTest
```

**Note:** If you want to compile and run in one command, use `&&` not `$$`:
```bash
./gradlew build && ./gradlew runChunkPerformanceTest
```

### Option 3: Without daemon (for CI/automation)
```bash
./gradlew runChunkPerformanceTest --no-daemon
```

## Requirements

- Java 21 or higher
- At least 4GB of RAM available (test uses 2-4GB heap)
- Sufficient disk space for temporary test data

## Test Scenarios

The benchmark suite includes 8 comprehensive test scenarios:

### Single Chunk Tests
1. **Empty Chunk** - Minimal data, tests overhead
2. **Simple Chunk** - 4 sections with uniform blocks
3. **Complex Chunk** - 8 sections with varied block types
4. **Varied Chunk** - 12 sections with layered patterns

### Bulk Operation Tests
5. **10 Simple Chunks** - Small-scale batch operation
6. **50 Simple Chunks** - Medium-scale batch operation
7. **100 Simple Chunks** - Large-scale batch operation
8. **100 Mixed Chunks** - Mixed complexity batch operation

## Metrics Measured

Each test measures the following phases separately:

- **Serialization Time** - Converting chunk data to serializable format
- **NBT Encoding Time** - Encoding data to NBT format
- **Save to Disk Time** - Writing data to region files (includes compression)
- **Load from Disk Time** - Reading data from region files (includes decompression)
- **Deserialization Time** - Parsing loaded data back into usable format
- **Total Time** - Sum of all phases
- **Data Size** - Size of serialized chunk data

## Baseline Performance

**Last measured on:** Initial implementation (commit 4145ac87)

### Single Chunk Operations
| Type | Total Time | Save Time | Load Time | Size |
|------|-----------|-----------|-----------|------|
| Empty | 0.67ms | 0.46ms | 0.19ms | 0.4 KB |
| Simple | 0.91ms | 0.56ms | 0.31ms | 6.0 KB |
| Complex | 2.16ms | 1.43ms | 0.66ms | 44.0 KB |
| Varied | 4.09ms | 3.19ms | 0.78ms | 130.8 KB |

### Bulk Operations
| Test | Total Time | Save Time | Load Time | Size |
|------|-----------|-----------|-----------|------|
| 10 chunks | 4.09ms | 2.89ms | 1.10ms | 59.5 KB |
| 50 chunks | 14.42ms | 10.28ms | 3.87ms | 297.6 KB |
| 100 chunks | 26.75ms | 19.50ms | 6.78ms | 595.2 KB |
| 100 mixed | 89.82ms | 72.24ms | 16.80ms | 4529.2 KB |

### Key Findings
- **Disk I/O dominates:** 60-80% of total time is spent on save/load operations
- **Linear scaling:** Performance scales linearly with chunk count
- **Complexity impact:** Mixed-complexity chunks take 3-4x longer than uniform chunks
- **Serialization is fast:** <1% overhead in most cases

## Test Configuration

The tests use the following configuration:

- **Warmup Iterations:** 5 (to stabilize JVM performance)
- **Test Iterations:** 10 (for statistical averaging)
- **JVM Settings:**
  - Heap: 2GB initial, 4GB maximum
  - GC: G1GC with tuned parameters
  - Experimental VM options enabled for optimal performance

## Interpreting Results

### Good Performance Indicators
- Save times under 1ms for simple chunks
- Load times under 0.5ms for simple chunks
- Linear scaling with chunk count
- Consistent times across iterations (low variance)

### Performance Issues to Watch For
- Save times increasing non-linearly with chunk count
- High variance between test iterations
- Disproportionate time in serialization/deserialization vs I/O
- Unexpectedly large data sizes

## Troubleshooting

### Build Errors

If you see errors like "cannot find symbol: class SharedConstants":

1. Ensure you're using Java 21:
   ```bash
   java -version
   ```

2. Clean and rebuild:
   ```bash
   ./gradlew clean compileTestJava
   ```

3. Check that main sources compiled first:
   ```bash
   ./gradlew classes compileTestJava
   ```

### Command Syntax Error

If you see "Task 'XXXXX' not found" where XXXXX is a number:

- You likely used `$$` instead of `&&` in your shell command
- Correct: `./gradlew build && ./gradlew runChunkPerformanceTest`
- Incorrect: `./gradlew build $$ ./gradlew runChunkPerformanceTest`

### Out of Memory Errors

If tests fail with OutOfMemoryError:

1. Increase system available memory
2. Reduce test heap settings in `build.gradle` (lines 674-685)
3. Run fewer chunks at once (modify test scenarios in ChunkPerformanceTest.java)

## Modifying Tests

To add new test scenarios or modify existing ones:

1. Edit `ChunkPerformanceTest.java`
2. Add new test methods following the pattern of existing tests
3. Update the `runAllTests()` method to include your new tests
4. Recompile and run: `./gradlew compileTestJava runChunkPerformanceTest`

## Integration with Optimization Work

This infrastructure is designed to validate the 15 optimization opportunities identified in the investigation:

1. Run baseline tests (already done - see above)
2. Implement an optimization
3. Re-run tests: `./RunChunkPerformanceTest.sh`
4. Compare results to baseline
5. Document improvement percentage

Expected improvements from key optimizations:
- **LZ4 compression:** 40-60% faster save/load
- **Parallel serialization:** 30-40% faster saves
- **Region cache expansion:** 15-25% improvement on exploration workloads
- **Adaptive throttling:** 20-30% better throughput under load

## Files

- `ChunkPerformanceTest.java` - Main test implementation
- `README.md` - This file
- `/RunChunkPerformanceTest.sh` - Convenience script (in project root)

## Support

For issues or questions about the performance tests:
1. Check this README first
2. Review the test output for error messages
3. Check the GitHub issue/PR where these tests were introduced
4. Examine the test code itself - it's well-commented

---

**Note:** These are performance benchmarks, not unit tests. They measure timing and throughput, not correctness. The actual chunk save/load correctness is validated elsewhere in the codebase.
