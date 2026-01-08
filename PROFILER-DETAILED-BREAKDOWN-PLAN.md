# Profiler Detailed Breakdown - Implementation Plan

## Problem Statement

The current profiler only shows top-level operations like:
- `tick.level` taking 97% of time
- `frame.gameRenderer` taking 98% of time

This doesn't provide enough detail to identify specific bottlenecks. We need to know:
- **Within tick.level**: How much time is spent on entities vs blocks vs tile entities vs chunks?
- **Within frame.gameRenderer**: How much time is spent on terrain vs entities vs particles vs GUI?

## Current Limitation

The profiler currently only records timing at a few manually-instrumented points:

```java
// MinecraftServer.java
long startTime = Util.getNanos();
serverLevel.tick(booleanSupplier);
ProfilerManager.recordMainThreadOperation("tick.level", Util.getNanos() - startTime);
```

This gives us the **total** time for `tick.level` but no breakdown of what happens inside `serverLevel.tick()`.

## Solution: Hook Into Minecraft's Existing Profiler System

Minecraft already has a comprehensive hierarchical profiling system via `ProfilerFiller`!

Throughout the codebase, there are hundreds of profiler calls like:
```java
profilerFiller.push("entities");
// ... do entity updates ...
profilerFiller.pop();

profilerFiller.push("blockEntities");
// ... do tile entity updates ...
profilerFiller.pop();
```

These create a **tree structure** of profiling data:
```
root
├── tick
│   ├── commandFunctions
│   ├── levels
│   │   ├── entities
│   │   ├── blockEntities
│   │   ├── chunks
│   │   └── ...
│   ├── connection
│   └── players
└── ...
```

**The key insight:** We can intercept these `push()` and `pop()` calls to build a complete timing tree!

## Implementation Strategy

### Approach 1: Wrap the ProfilerFiller (RECOMMENDED)

Create a `ProfilingCollectorWrapper` that:
1. Wraps the existing `ProfilerFiller` used by the server/client
2. Forwards all calls to the original profiler (so existing profiling tools still work)
3. Records timing data for each push/pop pair
4. Builds a hierarchical tree of timing data

**Advantages:**
- ✅ Leverages hundreds of existing profiler calls throughout the codebase
- ✅ No need to manually instrument every operation
- ✅ Automatically captures detailed breakdowns
- ✅ Compatible with existing profiler infrastructure
- ✅ Easy to enable/disable (just swap the profiler instance)

**Implementation:**

```java
public class ProfilingCollectorWrapper implements ProfilerFiller {
    private final ProfilerFiller delegate;
    private final Stack<Long> startTimes;
    private final Stack<String> pathStack;
    private final Map<String, OperationRecord> operations;
    
    @Override
    public void push(String name) {
        delegate.push(name);
        
        // Record start time
        startTimes.push(Util.getNanos());
        
        // Track current path
        String path = buildPath() + "." + name;
        pathStack.push(path);
    }
    
    @Override
    public void pop() {
        delegate.pop();
        
        // Calculate duration
        long duration = Util.getNanos() - startTimes.pop();
        String path = pathStack.pop();
        
        // Record timing
        operations.computeIfAbsent(path, k -> new OperationRecord(k))
            .addSample(duration);
    }
    
    // Forward all other methods to delegate...
}
```

### Approach 2: Custom Instrumentation Points

Manually add profiling calls at key points in the code.

**Disadvantages:**
- ❌ Requires instrumenting hundreds of locations
- ❌ Easy to miss important operations
- ❌ More invasive code changes
- ❌ Harder to maintain

**Verdict:** Approach 1 (wrapper) is clearly superior.

## Detailed Design

### 1. ProfilerCollectorWrapper Class

**Location:** `src/main/java/net/minecraft/util/profiling/custom/ProfilerCollectorWrapper.java`

**Responsibilities:**
- Wrap an existing ProfilerFiller instance
- Track timing for all push/pop pairs
- Build hierarchical path names (e.g., "root.tick.entities")
- Record timing data in OperationRecord instances
- Forward all calls to the underlying profiler

**Key Data Structures:**
```java
private final ProfilerFiller delegate;           // Original profiler
private final Deque<Long> startTimes;           // Stack of start timestamps
private final Deque<String> pathComponents;     // Stack of path components
private final Map<String, OperationRecord> operations;  // Timing data by path
```

**Path Construction:**
The wrapper builds hierarchical paths by tracking the stack:
- `push("tick")` → path = "root.tick"
- `push("entities")` → path = "root.tick.entities"
- `pop()` → path = "root.tick"
- `pop()` → path = "root"

### 2. Integration with ProfilerManager

**Modify ProfilerManager.start():**
```java
public static boolean start(CommandSourceStack initiator) {
    synchronized (sessionLock) {
        // ... existing code ...
        
        // Wrap the server profiler
        MinecraftServer server = initiator.getServer();
        if (server != null) {
            ProfilerFiller original = server.getProfiler();
            ProfilerCollectorWrapper wrapper = new ProfilerCollectorWrapper(original);
            server.setProfiler(wrapper);  // Need to add this method
            
            mainThreadProfiler = new MainThreadProfiler(wrapper);
        }
        
        // ... existing code ...
    }
}
```

**Modify ProfilerManager.stop():**
```java
public static Path stop() throws Exception {
    synchronized (sessionLock) {
        // ... existing code ...
        
        // Restore original profiler
        if (originalServerProfiler != null) {
            initiator.getServer().setProfiler(originalServerProfiler);
        }
        
        // ... existing code ...
    }
}
```

### 3. Required Changes to MinecraftServer

Add a setter method to allow swapping the profiler:
```java
public class MinecraftServer {
    private ProfilerFiller profiler;
    
    // Add this method:
    public void setProfiler(ProfilerFiller profiler) {
        this.profiler = profiler;
    }
    
    // Existing getter:
    public ProfilerFiller getProfiler() {
        return this.profiler;
    }
}
```

This is a **minimal, safe change** that enables profiler swapping.

### 4. Enhanced Report Format

The report will now show hierarchical breakdowns:

```
────────────────────────────────────────────────────────────────────────────────
Server Thread (Main Thread)
────────────────────────────────────────────────────────────────────────────────

Total Active Time:    6.66 seconds (35.98% of session)
Total Ticks:          370
Average Tick Time:    9.9 ms

Time Distribution by Operation (Top Level):

1. root.tick                                    6.50s  100.00%
   └─ Total tick time

Detailed Breakdown - root.tick:

1. root.tick.levels                             3.56s   54.77%
   ├─ root.tick.levels.entities                 1.85s   28.46%
   │  ├─ root.tick.levels.entities.regular      1.20s   18.46%
   │  ├─ root.tick.levels.entities.passengers   0.35s    5.38%
   │  └─ root.tick.levels.entities.blockCollision 0.30s  4.62%
   │
   ├─ root.tick.levels.blockEntities            0.95s   14.62%
   │  ├─ root.tick.levels.blockEntities.ticking 0.75s   11.54%
   │  └─ root.tick.levels.blockEntities.pending 0.20s    3.08%
   │
   ├─ root.tick.levels.blocks                   0.45s    6.92%
   ├─ root.tick.levels.chunks                   0.21s    3.23%
   └─ root.tick.levels.other                    0.10s    1.54%

2. root.tick.connection                         0.07s    1.08%
   ├─ root.tick.connection.tick                 0.05s    0.77%
   └─ root.tick.connection.flushPackets         0.02s    0.31%

3. root.tick.players                            0.05s    0.77%

4. root.tick.commandFunctions                   0.02s    0.31%

5. root.tick.sendChunks                         0.03s    0.46%
```

**For Render Thread:**
```
────────────────────────────────────────────────────────────────────────────────
Render Thread
────────────────────────────────────────────────────────────────────────────────

Total Active Time:    5.23 seconds (28.27% of session)
Total Frames:         1,100
Average Frame Time:   4.75 ms

Detailed Breakdown - root.gameRenderer:

1. root.gameRenderer.level                      3.20s   61.19%
   ├─ root.gameRenderer.level.terrain           1.85s   35.38%
   │  ├─ root.gameRenderer.level.terrain.setup  0.45s    8.60%
   │  ├─ root.gameRenderer.level.terrain.solid  0.95s   18.17%
   │  └─ root.gameRenderer.level.terrain.translucent 0.45s 8.60%
   │
   ├─ root.gameRenderer.level.entities          0.85s   16.25%
   │  ├─ root.gameRenderer.level.entities.prepare 0.15s  2.87%
   │  ├─ root.gameRenderer.level.entities.render 0.65s  12.43%
   │  └─ root.gameRenderer.level.entities.outline 0.05s  0.96%
   │
   ├─ root.gameRenderer.level.particles         0.25s    4.78%
   ├─ root.gameRenderer.level.weather           0.15s    2.87%
   └─ root.gameRenderer.level.destroyProgress   0.10s    1.91%

2. root.gameRenderer.gui                        1.50s   28.68%
   ├─ root.gameRenderer.gui.hud                 0.85s   16.25%
   ├─ root.gameRenderer.gui.chat                0.35s    6.69%
   └─ root.gameRenderer.gui.overlays            0.30s    5.74%

3. root.gameRenderer.hand                       0.35s    6.69%

4. root.gameRenderer.other                      0.18s    3.44%
```

### 5. Report Configuration Options

Add command options to control detail level:

```
/profile start [--depth <n>]
/profile stop [--max-entries <n>]
```

**Options:**
- `--depth <n>`: Maximum depth of hierarchy to capture (default: unlimited)
  - `--depth 1`: Only root.tick, root.connection, etc.
  - `--depth 2`: root.tick.entities, root.tick.blocks, etc.
  - `--depth 3`: root.tick.entities.regular, root.tick.entities.passengers, etc.

- `--max-entries <n>`: Maximum operations to show in report (default: 100)
  - Prevents huge reports when there are thousands of operations
  - Shows top N by time spent

**Default behavior:** Capture everything, show top 100 operations in report.

### 6. Path Filtering and Grouping

The wrapper can implement intelligent filtering:

**Skip trivial operations:**
- Operations taking < 0.1ms total time
- Operations called only once with negligible time

**Group similar operations:**
- `root.tick.entities[EntityType]` → Group by entity type
- `root.tick.blockEntities[BlockEntityType]` → Group by type

**Collapse infrequent paths:**
- Show top 20 sub-operations, group rest as "other"

## Implementation Plan

### Phase 1: Core Wrapper (2-3 hours)

1. Create `ProfilerCollectorWrapper.java`
   - Implement ProfilerFiller interface
   - Add timing tracking for push/pop
   - Build hierarchical paths
   - Store data in OperationRecord instances

2. Add unit tests
   - Test path construction
   - Test timing accuracy
   - Test nested push/pop sequences

### Phase 2: Integration (1-2 hours)

1. Add `setProfiler()` method to MinecraftServer
2. Modify ProfilerManager to use wrapper
3. Store original profiler reference for restoration
4. Test start/stop lifecycle

### Phase 3: Report Enhancement (2-3 hours)

1. Modify ProfilerReportGenerator
   - Add hierarchical output formatting
   - Implement tree rendering with indentation
   - Add percentage calculations relative to parent
   - Implement depth limiting

2. Test report generation with sample data

### Phase 4: Client-Side Support (1-2 hours)

1. Add `setProfiler()` method to Minecraft (client)
2. Wrap client profiler in ProfilerManager
3. Test render thread profiling

### Phase 5: Polish & Testing (2-3 hours)

1. Add configuration options
2. Test with real workloads
3. Verify performance impact
4. Document new features

**Total Estimated Time: 8-13 hours**

## Performance Considerations

### Overhead Analysis

**Without wrapper:**
- Current overhead: ~1% (only top-level timing)

**With wrapper:**
- Each push/pop pair: ~100ns overhead
- Typical tick: ~200 push/pop pairs = 20μs total = 0.02ms
- On 9.9ms tick: 0.2% overhead
- On 50ms tick: 0.04% overhead

**Conclusion:** Overhead is **negligible** even with detailed profiling.

### Memory Usage

**Per profiling session (10 minutes):**
- ~200 unique paths × 12,000 ticks = 2.4M samples
- Each sample: ~40 bytes (path + timing)
- Total: ~96 MB

**With filtering (skip < 0.1ms):**
- ~50 significant paths × 12,000 ticks = 600K samples
- Total: ~24 MB

**Conclusion:** Memory usage is **acceptable** for 10-minute sessions.

## Alternative Approaches Considered

### Option A: Sample-Based Profiling
Record stack traces at regular intervals (e.g., every 10ms).

**Pros:**
- Very low overhead
- Statistical representation

**Cons:**
- ❌ Imprecise for short operations
- ❌ Can miss important events
- ❌ Requires complex stack trace analysis

### Option B: Bytecode Instrumentation
Inject timing code at method boundaries via bytecode manipulation.

**Pros:**
- Can profile any method
- No code changes needed

**Cons:**
- ❌ Very complex to implement
- ❌ Potential compatibility issues
- ❌ Higher overhead
- ❌ Difficult to debug

### Option C: Manual Instrumentation Everywhere
Add timing code at hundreds of locations.

**Cons:**
- ❌ Massive code changes
- ❌ Hard to maintain
- ❌ Easy to miss operations

**Verdict:** ProfilerFiller wrapper (main proposal) is clearly the best approach.

## Expected Results

After implementation, users will get detailed reports like:

**Before (current):**
```
1. tick.level    3.56s   97.40%
```

**After (enhanced):**
```
1. root.tick.levels                      3.56s   54.77%
   ├─ entities                           1.85s   28.46%
   │  ├─ regular                         1.20s   18.46%
   │  ├─ passengers                      0.35s    5.38%
   │  └─ blockCollision                  0.30s    4.62%
   ├─ blockEntities                      0.95s   14.62%
   │  ├─ ticking                         0.75s   11.54%
   │  └─ pending                         0.20s    3.08%
   ├─ blocks                             0.45s    6.92%
   └─ chunks                             0.21s    3.23%
```

**This answers the user's question:** Now they can see exactly where time within `tick.level` is being spent!

## Documentation

After implementation, create a single comprehensive guide:

**PROFILER-DETAILED-GUIDE.md**

Contents:
1. How to use the profiler
2. Understanding hierarchical output
3. Interpreting the results
4. Common bottlenecks and solutions
5. Configuration options
6. Performance impact
7. Examples of typical profiles

## Summary

The solution is to **wrap Minecraft's existing ProfilerFiller** to capture hierarchical timing data from the hundreds of existing profiler calls throughout the codebase. This provides:

✅ Detailed breakdown of where time is spent  
✅ Hierarchical tree structure (root.tick.entities.regular)  
✅ Minimal code changes (just add setProfiler() and wrapper)  
✅ Negligible performance overhead (~0.2%)  
✅ Compatible with existing profiler infrastructure  
✅ Automatic capture of all profiled operations  

This directly solves the user's problem of "tick.level 97%" and "frame.gameRenderer 98%" by showing **exactly** where that time is spent within those operations.
