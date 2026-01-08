# Profiler Metrics Explained

This document explains what the various metrics in the profiler reports mean and how to interpret them.

## Understanding "Active Time" and Thread Utilization

### What is "Active Time"?

**Active Time** is the total amount of CPU time that a thread consumed during the profiling session. This is measured in seconds and represents actual computation time, not wall-clock time.

### Why Can Utilization Exceed 100%?

In previous versions, you may have seen percentages like **153.10% of session**, which seems impossible. Here's what was happening:

**The Math:**
- Session Duration (wall-clock): 12.69 seconds
- Thread Active Time (CPU time): 19.43 seconds  
- Old Calculation: (19.43 / 12.69) × 100 = **153.10%**

**Why This Happens:**

This is **mathematically correct** for multi-threaded applications but confusing to interpret. A thread can accumulate more CPU time than wall-clock time has passed due to several factors:

1. **Concurrent Execution**: While the session runs for 12.69 wall-clock seconds, the render thread is working concurrently with other threads. It can consume CPU cycles continuously during that time.

2. **High Priority Scheduling**: The render thread may get scheduled very aggressively by the OS, receiving more than its "fair share" of CPU time if other threads are idle or waiting.

3. **Time Measurement Precision**: The thread's CPU time is measured at nanosecond precision and includes all context switches and kernel time attributed to that thread.

4. **Multiple Cores**: On multi-core systems, a thread can potentially use 100% of one core while the system as a whole is tracking wall-clock time. If you have 4 cores and 4 threads all running at 100%, you could theoretically see 400% total utilization across all threads.

### The Fix: Capped Utilization Display

**Current Behavior:**

To avoid confusion, the HTML report now **caps the displayed utilization at 100%**:

```
Active Time: 19.43 seconds (100.00% utilized)
```

This means:
- **100% utilized** = The thread was maximally busy during the entire session
- **50% utilized** = The thread was actively working about half the time
- **< 100%** = The thread had idle time or was waiting

**What "100% utilized" really means:**

When you see 100% utilization, it indicates that the thread was **constantly busy** throughout the profiling session. This is actually expected for:

- **Render Thread**: Should be near 100% when rendering frames continuously
- **Server Thread**: Should be high (60-100%) when the server is actively processing game ticks

### Interpreting the Metrics

#### Server Thread (Main Thread)

```
Active Time: 6.66 seconds (35.98% utilized)
Total Ticks: 370
Average Tick Time: 9.9 ms
```

**What this means:**
- The server thread spent 6.66 seconds of CPU time processing game logic
- Out of the session duration, it was actively working 35.98% of the time
- The remaining ~64% of the time it was sleeping/waiting (which is normal for 20 TPS pacing)
- It processed 370 game ticks during the session
- Each tick took an average of 9.9ms to process

**Ideal values:**
- **Average Tick Time**: Should be well under 50ms (the tick budget). 9.9ms indicates excellent performance with 5x headroom.
- **Utilization**: 30-40% is normal for a server running at 20 TPS, as the thread sleeps between ticks.

#### Render Thread (Client)

```
Active Time: 19.43 seconds (100.00% utilized)
Total Frames: 1,100
Average FPS: 59.46
Average Frame Time: 4.75 ms
```

**What this means:**
- The render thread spent 19.43 seconds of CPU time rendering frames
- It was maximally busy (100% utilized) throughout the session
- It rendered 1,100 frames during the session
- Maintained ~60 FPS average
- Each frame took an average of 4.75ms to render

**Ideal values:**
- **Average FPS**: 60+ is ideal for smooth gameplay
- **Frame Time**: Should be under 16.67ms (60 FPS budget). 4.75ms indicates excellent performance.
- **Utilization**: High utilization (80-100%) is normal when rendering continuously

### When to Be Concerned

**Red Flags:**

1. **Server Thread**
   - Average Tick Time > 50ms (indicates lag)
   - Utilization > 80% continuously (may indicate insufficient sleep time, potential tick loop issues)

2. **Render Thread**  
   - Average Frame Time > 16.67ms (indicates FPS drops below 60)
   - Average FPS < 30 (poor performance)

**Green Lights:**

1. **Server Thread**
   - Average Tick Time < 30ms
   - Utilization 30-50% (healthy sleep pattern)

2. **Render Thread**
   - Average Frame Time < 16ms
   - Average FPS > 60
   - Utilization can be high (80-100%) when actively rendering

## Hierarchical Breakdown

The hierarchical breakdown shows **where time is spent within each operation**. The percentages in the breakdown are **relative to total profiled operation time**, not wall-clock time.

### Example:

```
root.tick.levels                      3.56s   54.77%
  ├─ entities                          1.85s   28.46%
  │  ├─ regular                        1.20s   18.46%
  │  ├─ passengers                     0.35s    5.38%
  │  └─ blockCollision                 0.30s    4.62%
  ├─ blockEntities                     0.95s   14.62%
  └─ blocks                            0.45s    6.92%
```

**Interpretation:**
- Out of all profiled operations, 54.77% of the time was spent in `tick.levels`
- Within `tick.levels`, 28.46% was spent on entities (52% of tick.levels time)
- Regular entity processing took 18.46% of total time (33% of tick.levels time)

### Using the Interactive HTML Report

The HTML report allows you to **drill down** through the hierarchy:

1. Start with collapsed view showing only top-level operations
2. Click ▶ to expand an operation and see what's happening inside it
3. Keep drilling down to find bottlenecks
4. Time and percentages help identify the heaviest operations

**Workflow:**

1. Look for operations with high percentages (> 20%)
2. Expand them to see sub-operations
3. Identify specific bottlenecks (e.g., "blockEntities" taking 14.62%)
4. Optimize the heaviest leaf nodes

## Summary

- **Active Time**: Actual CPU time used by the thread
- **Utilization**: How busy the thread was (now capped at 100% for clarity)
- **100% utilized**: Thread was maximally busy, not an error
- **Hierarchical Breakdown**: Shows where time is spent within operations
- **Use HTML report**: Interactive drill-down to find specific bottlenecks

The profiler gives you deep insight into performance. Focus on:
1. **High-level metrics**: Are tick times and frame times acceptable?
2. **Hierarchical drill-down**: Where specifically is time being spent?
3. **Optimization targets**: Focus on the heaviest operations first
