# MattMC Performance Testing Guide

This document provides comprehensive documentation for the MattMC performance testing suite. It explains how to run each test, what aspects they measure, and how to interpret the results.

## Table of Contents

1. [Overview](#overview)
2. [Running Performance Tests](#running-performance-tests)
3. [Viewing Performance Reports](#viewing-performance-reports)
4. [Test Categories](#test-categories)
5. [Test Files and Descriptions](#test-files-and-descriptions)
6. [Performance Metrics](#performance-metrics)
7. [Interpreting Results](#interpreting-results)
8. [Performance Baselines](#performance-baselines)
9. [Troubleshooting](#troubleshooting)

---

## Overview

The MattMC performance testing suite is designed to measure and validate the performance characteristics of the game engine across multiple dimensions:

- **Frame Time / FPS**: How long each frame takes to process and render
- **Memory Usage**: Heap allocation patterns and memory efficiency
- **CPU / Thread Utilization**: How well the engine uses available CPU cores
- **Terrain Generation**: Speed of procedural world generation
- **Lighting Calculations**: Performance of light propagation algorithms
- **World Save/Load**: Time to persist and restore world state
- **Chunk I/O**: Disk read/write performance for chunk data

All tests are implemented using JUnit 5 and can be run alongside regular unit tests.

---

## Running Performance Tests

### Running All Performance Tests

```bash
# Run all tests including performance tests
./gradlew test

# Run only performance tests
./gradlew test --tests "mattmc.performance.*"
```

### Running Specific Test Categories

```bash
# Terrain generation tests
./gradlew test --tests "mattmc.performance.TerrainGenerationPerformanceTest"

# Lighting tests
./gradlew test --tests "mattmc.performance.LightingCalculationPerformanceTest"

# Memory tests
./gradlew test --tests "mattmc.performance.MemoryUsagePerformanceTest"

# CPU/Thread tests
./gradlew test --tests "mattmc.performance.CPUThreadUsagePerformanceTest"

# Frame time tests
./gradlew test --tests "mattmc.performance.FrameTimePerformanceTest"

# World save/load tests
./gradlew test --tests "mattmc.performance.WorldSaveLoadPerformanceTest"

# World launch tests
./gradlew test --tests "mattmc.performance.WorldLaunchTimePerformanceTest"

# Chunk I/O tests
./gradlew test --tests "mattmc.performance.ChunkIOPerformanceTestSuite"
```

### Running Individual Tests

```bash
# Example: Run a specific test method
./gradlew test --tests "mattmc.performance.TerrainGenerationPerformanceTest.testSingleChunkGenerationTime"
```

---

## Viewing Performance Reports

After running performance tests, detailed metrics are available in multiple formats:

### Dedicated Performance Report (Recommended)

The most detailed performance data is available in the dedicated performance report:

```
build/reports/performance/
├── performance-report.html  # Interactive HTML report with all metrics
├── performance-report.txt   # Plain text report
└── performance-report.csv   # CSV for spreadsheet analysis
```

**Open the HTML report in your browser for the best experience:**
```bash
# On Linux
xdg-open build/reports/performance/performance-report.html

# On macOS
open build/reports/performance/performance-report.html

# On Windows
start build/reports/performance/performance-report.html
```

The HTML report includes:
- 📊 All measured metrics (execution time, memory, CPU time, etc.)
- 📈 Per-iteration timing data
- 🎯 Frame time percentiles (95th, 99th)
- 💾 Memory usage deltas
- 🧵 Thread count information

### JUnit Test Report

The standard JUnit HTML report is also available at:
```
build/reports/tests/test/index.html
```

This report now includes stdout/stderr output with detailed metrics in the "Standard output" section of each test.

### Console Output

For immediate feedback during development:
```bash
./gradlew test --tests "mattmc.performance.*" --info
```

---

## Test Categories

### 1. Frame Time Performance (`FrameTimePerformanceTest`)

Tests that simulate frame operations to measure frame time consistency and FPS.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testChunkIterationFrameTimes` | Frame time stability during chunk iteration | 99th percentile < 2x average |
| `testBlockUpdateFrameTimes` | Frame times during block modifications | Average < 16ms (60 FPS) |
| `testLightPropagationFrameTimes` | Frame times during light changes | 95th percentile < 16ms |
| `testLongTermFrameTimeStability` | Performance degradation over time | Degradation ratio < 1.5 |
| `testTargetFPS` | FPS achievement during typical operations | FPS >= 60 |
| `testFrameTimeJitter` | Frame-to-frame time variance | Average jitter < 5ms |
| `testHeavyLoadFrameDrops` | Performance under stress | Max frame time < 100ms |

### 2. Memory Usage Performance (`MemoryUsagePerformanceTest`)

Tests that measure memory allocation patterns and efficiency.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testChunkMemoryUsage` | Memory per chunk allocation | < 10MB per chunk |
| `testBlockOperationAllocations` | Allocation rate during block ops | < 100 bytes/operation |
| `testLightStorageMemoryEfficiency` | Light data memory overhead | < 100x theoretical minimum |
| `testMemoryReleaseAfterChunkUnload` | Memory reclamation | Delta < 50MB after GC |
| `testGCPausesDuringHighAllocation` | GC impact on performance | GC < 20% of execution time |
| `testMemoryPoolUsage` | Memory pool exhaustion | No pool > 90% used |
| `testStringAllocationMinimization` | String allocation in hot paths | Delta < 1MB |

### 3. CPU/Thread Usage Performance (`CPUThreadUsagePerformanceTest`)

Tests that measure thread utilization and CPU efficiency.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testThreadCountDuringChunkLoading` | Thread pool size limits | < processors × 4 + 20 |
| `testCPUTimeDistribution` | CPU time across threads | Utilization > 50% |
| `testThreadContention` | Lock contention overhead | Blockage < 10% |
| `testAsyncChunkLoaderParallelism` | Parallel processing effectiveness | Proportional speedup |
| `testThreadNaming` | Thread identification | Identifiable worker threads |
| `testMainThreadNotBlocked` | Main thread responsiveness | > 60 updates during heavy work |
| `testThreadPoolScaling` | Adaptive thread pool sizing | Scales with workload |

### 4. Terrain Generation Performance (`TerrainGenerationPerformanceTest`)

Tests that measure world generation speed.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testSingleChunkGenerationTime` | Single chunk generation | < 50ms per chunk |
| `testBatchChunkGenerationTime` | Batch generation efficiency | < 1000ms for 25 chunks |
| `testTerrainHeightCalculationSpeed` | Noise sampling speed | < 100ms for 10000 samples |
| `testChunkGenerationMemoryUsage` | Memory during generation | < 10MB per chunk |
| `testGenerationTimeConsistencyAcrossSeeds` | Seed-independent performance | Variance CV < 20% |
| `testFarCoordinateGenerationPerformance` | Far coordinate overhead | < 2x slower than origin |

### 5. Lighting Calculation Performance (`LightingCalculationPerformanceTest`)

Tests that measure light propagation algorithm efficiency.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testSkylightInitializationSpeed` | Skylight init per chunk | < 100ms per chunk |
| `testBlockLightPropagationSpeed` | Single source propagation | < 10ms |
| `testMultipleLightSourceScaling` | Multi-source scaling | < 100x for 50 sources |
| `testLightRemovalSpeed` | Light removal efficiency | < 3x addition time |
| `testSkylightBlockChangeUpdate` | Light update on block change | < 5ms |
| `testRGBLightPerformance` | RGB vs white light overhead | 0.5-2.0x ratio |
| `testLightPropagationMemoryUsage` | Memory during light ops | Delta < 50MB |

### 6. World Save/Load Performance (`WorldSaveLoadPerformanceTest`)

Tests that measure world persistence operations.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testSmallWorldSaveTime` | 25 chunk save time | < 1000ms |
| `testMediumWorldSaveTime` | 100 chunk save time | < 3000ms |
| `testWorldLoadTime` | 50 chunk load time | < 3000ms |
| `testIncrementalSaveTime` | Dirty chunk save efficiency | > 2x speedup |
| `testSaveMemoryUsage` | Memory during save | Delta < 100MB |
| `testSavePerformanceConsistency` | Save time variance | CV < 30% |

### 7. World Launch Time Performance (`WorldLaunchTimePerformanceTest`)

Tests that measure world startup performance.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testNewWorldCreationTime` | Empty world creation | < 50ms |
| `testWorldWithSpawnChunksTime` | Spawn area (9 chunks) | < 500ms |
| `testWorldInitWithRenderDistance8` | Full RD 8 (289 chunks) | < 15000ms |
| `testExistingWorldVsNewWorld` | Load vs generate comparison | Load < 3x generate |
| `testWorldGeneratorInitTime` | Generator initialization | < 5ms |
| `testWorldLightManagerInitTime` | Light manager init | < 5ms |
| `testLevelInitMemoryFootprint` | Level init memory | < 50MB |
| `testMultipleWorldInstancesMemory` | Memory leak detection | Delta < 100MB |

### 8. Chunk I/O Performance (`ChunkIOPerformanceTestSuite`)

Tests that measure chunk serialization and disk I/O.

| Test Name | What It Measures | Target |
|-----------|------------------|--------|
| `testChunkSerializationSpeed` | toNBT speed | < 20ms per chunk |
| `testChunkDeserializationSpeed` | fromNBT speed | < 20ms per chunk |
| `testRegionFileWriteSpeed` | Region file write | < 50ms per chunk |
| `testRegionFileReadSpeed` | Region file read | < 30ms per chunk |
| `testBatchChunkSaveScaling` | 100 chunk batch save | < 50ms per chunk |
| `testBatchChunkLoadScaling` | 100 chunk batch load | < 30ms per chunk |
| `testRegionFileCacheEffectiveness` | Cache speedup | > 1.5x speedup |
| `testChunkWithLightDataSerializationSpeed` | Light data overhead | < 3x overhead |

---

## Performance Metrics

The performance test suite measures and reports the following metrics:

### Time Metrics
- **Execution Time**: Total wall-clock time for the operation
- **Average Time per Iteration**: Time divided by iteration count
- **Min/Max Time**: Range of times across iterations
- **Percentiles (95th, 99th)**: For frame time analysis

### Memory Metrics
- **Heap Memory Used**: Current heap allocation
- **Heap Memory Delta**: Change in heap size during operation
- **Per-object Memory**: Memory allocated per chunk/operation
- **GC Time**: Time spent in garbage collection

### Thread Metrics
- **Thread Count**: Number of active threads
- **CPU Time**: Total CPU time consumed
- **Blocked Time**: Time threads spent waiting for locks
- **Thread Utilization**: CPU time / wall time ratio

### Frame Metrics
- **FPS**: Frames per second (1000 / avgFrameTimeMs)
- **Frame Time**: Time to process one frame
- **Jitter**: Frame-to-frame time variance

---

## Interpreting Results

### Good Performance Indicators

✅ Consistent frame times with low variance
✅ Memory delta near zero after operations complete
✅ Thread utilization scales with workload
✅ GC pauses below 20% of execution time
✅ Operations complete within target times

### Warning Signs

⚠️ High variance in frame times (CV > 30%)
⚠️ Memory continuously growing (potential leak)
⚠️ Thread count exceeding available cores × 4
⚠️ Blocked time > 10% of execution time
⚠️ Performance degradation over time

### Critical Issues

❌ Average frame time > 16ms (< 60 FPS)
❌ Any frame > 100ms (major stutter)
❌ Memory pool > 90% usage
❌ Thread deadlocks or livelocks
❌ Save/load exceeding timeout

---

## Performance Baselines

These are the expected performance targets for a typical development machine:

| Operation | Target Time | Maximum Acceptable |
|-----------|-------------|-------------------|
| Chunk Generation | 20-30ms | 50ms |
| Skylight Init | 30-50ms | 100ms |
| Light Propagation | 2-5ms | 10ms |
| Chunk Serialize | 5-10ms | 20ms |
| Chunk Deserialize | 5-10ms | 20ms |
| Region File Write | 10-30ms | 50ms |
| Region File Read | 5-15ms | 30ms |
| World Launch (RD 8) | 5-10s | 15s |

### Hardware Assumptions

These baselines assume:
- Modern multi-core CPU (4+ cores)
- 8GB+ RAM
- SSD storage
- Java 21 or later

Performance may vary based on:
- CPU single-threaded speed
- Available memory
- Disk I/O speed
- Background processes
- JIT compilation state

---

## Troubleshooting

### Tests Failing Due to Slow Performance

1. **Ensure JIT warmup**: Tests include warmup iterations, but cold JVM may affect results
2. **Check background processes**: Close resource-intensive applications
3. **Verify heap settings**: Ensure sufficient heap space with `-Xmx` flag
4. **Run in isolation**: Some tests may interfere with each other

### Inconsistent Results

1. **Run multiple times**: Some variance is expected
2. **Check GC activity**: GC pauses can cause spikes
3. **Monitor system load**: Other processes may compete for resources
4. **Consider disk state**: First run after reboot may have cold disk cache

### Memory-Related Failures

1. **Increase heap**: Add `-Xmx4g` or higher to JVM args
2. **Check for leaks**: Look for objects not being released
3. **Monitor GC logs**: Enable `-verbose:gc` for debugging

### Thread-Related Issues

1. **Check deadlocks**: Use `jstack` to analyze thread state
2. **Monitor contention**: Enable `-XX:+PrintLockStatistics`
3. **Verify thread pool sizing**: Ensure pool matches available cores

---

## Adding New Performance Tests

When adding new performance tests:

1. Extend `PerformanceTestBase` for consistent measurement utilities
2. Include warmup iterations before measurement
3. Use `forceGC()` before memory measurements
4. Document expected performance targets
5. Use assertions to catch regressions
6. Add test to this documentation

Example test structure:

```java
@Test
@DisplayName("Description of what is being tested")
void testYourOperation() {
    PerformanceResult result = measureOperation(
        "Operation Name",
        iterations,      // number of measured iterations
        warmupIterations, // warmup iterations
        () -> {
            // Your operation here
        }
    );
    
    System.out.println(result);
    
    // Assert performance target
    assertTrue(result.getAvgTimePerIterationMs() < TARGET_MS,
        "Operation exceeded target time");
}
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2024 | Initial performance test suite |

---

## Contributing

When contributing to performance tests:

1. Ensure tests are deterministic and reliable
2. Document any hardware-specific assumptions
3. Include both success criteria and failure conditions
4. Add appropriate logging for debugging
5. Update this documentation with new tests

For questions or issues, please open a GitHub issue with the `performance` label.
