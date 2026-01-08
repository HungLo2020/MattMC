# Profiler Technical Deep Dive

This document provides a technical explanation of how the MattMC profiler works internally.

## Architecture Overview

The profiler consists of several key components working together:

```
┌─────────────────────────────────────────────────────────────┐
│                    User Types Command                        │
│                    /profile start|stop                       │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    ProfileCommand.java                       │
│  • Handles command registration                             │
│  • Validates command execution                               │
│  • Calls ProfilerManager                                     │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                   ProfilerManager.java                       │
│  • Central coordinator                                       │
│  • Manages profiling session lifecycle                       │
│  • Coordinates all tracking components                       │
└────────────────────────────┬────────────────────────────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │Thread        │ │Main Thread   │ │Render Thread │
    │Tracker       │ │Profiler      │ │Profiler      │
    │              │ │              │ │              │
    │Monitors all  │ │Tracks tick   │ │Tracks frame  │
    │Java threads  │ │operations    │ │operations    │
    └──────────────┘ └──────────────┘ └──────────────┘
            │                │                │
            └────────────────┼────────────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ProfilingSession  │
                    │Data Container    │
                    └────────┬─────────┘
                             │
                             ▼
                  ┌────────────────────────┐
                  │ProfilerReportGenerator │
                  │Formats & saves report  │
                  └────────────────────────┘
```

## Component Details

### 1. ProfileCommand (Command Handler)

**Location:** `src/main/java/net/minecraft/server/commands/ProfileCommand.java`

**Purpose:** Provides the `/profile` command interface

**Key Methods:**
- `register(CommandDispatcher)` - Registers commands with Brigadier
- `startProfiling(CommandSourceStack)` - Handles `/profile start`
- `stopProfiling(CommandSourceStack)` - Handles `/profile stop`

**Example Code:**
```java
private static int startProfiling(CommandSourceStack source) throws CommandSyntaxException {
    if (ProfilerManager.isRunning()) {
        throw ERROR_ALREADY_RUNNING.create();
    }
    
    if (!ProfilerManager.start(source)) {
        throw START_FAILED.create();
    }
    
    source.sendSuccess(
        () -> Component.literal("Profiling started...").withStyle(ChatFormatting.GREEN),
        true
    );
    return 1;
}
```

**Features:**
- Permission check: Requires operator level 2
- Error handling for invalid states
- User feedback with clickable report paths
- Command auto-completion support

---

### 2. ProfilerManager (Central Coordinator)

**Location:** `src/main/java/net/minecraft/util/profiling/custom/ProfilerManager.java`

**Purpose:** Coordinates all profiling activities and manages session lifecycle

**Key Methods:**
- `start(CommandSourceStack)` - Initializes profiling session
- `stop()` - Ends session and generates report
- `isRunning()` - Checks if profiling is active
- `recordMainThreadOperation(String, long)` - Records main thread timing
- `recordRenderThreadOperation(String, long)` - Records render thread timing

**State Management:**
```java
private static volatile ProfilingSession currentSession = null;
private static final Object sessionLock = new Object();
```

**Thread Safety:**
- Uses `synchronized` blocks for session lifecycle
- Volatile session reference for visibility
- Thread-safe data structures in sub-components

**Start Sequence:**
```java
public static boolean start(CommandSourceStack initiator) {
    synchronized (sessionLock) {
        if (currentSession != null) {
            return false;  // Already running
        }
        
        // Create new session
        currentSession = new ProfilingSession(
            UUID.randomUUID(),
            System.nanoTime(),
            initiator
        );
        
        // Initialize components
        threadTracker = new ThreadTracker();
        threadTracker.start();
        
        mainThreadProfiler = new MainThreadProfiler();
        renderThreadProfiler = new RenderThreadProfiler();
        
        return true;
    }
}
```

**Stop Sequence:**
```java
public static Path stop() throws Exception {
    synchronized (sessionLock) {
        currentSession.setEndTime(System.nanoTime());
        
        // Collect data from all components
        threadTracker.stop();
        currentSession.setThreadRecords(threadTracker.getRecords());
        currentSession.setMainThreadOperations(mainThreadProfiler.getOperations());
        currentSession.setRenderThreadOperations(renderThreadProfiler.getOperations());
        
        // Generate and save report
        ProfilerReportGenerator generator = new ProfilerReportGenerator();
        Path reportPath = generator.generate(currentSession);
        
        return reportPath;
    } finally {
        // Clean up
        currentSession = null;
        threadTracker = null;
        mainThreadProfiler = null;
        renderThreadProfiler = null;
    }
}
```

---

### 3. ThreadTracker (Thread Monitoring)

**Location:** `src/main/java/net/minecraft/util/profiling/custom/ThreadTracker.java`

**Purpose:** Monitors all JVM threads during profiling

**How It Works:**

1. **Thread Discovery:**
```java
ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
long[] threadIds = threadBean.getAllThreadIds();
ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds, 0);
```

2. **Periodic Scanning:**
- Scans every 100ms for new threads
- Updates statistics for existing threads
- Marks terminated threads

3. **Data Collected Per Thread:**
- Thread ID and name
- CPU time (time spent executing)
- User time (CPU time in user mode)
- Wait time (time spent waiting)
- Block time (time spent blocked)
- Thread state history

**Scheduling:**
```java
scanner.scheduleAtFixedRate(
    this::scanThreads,
    100,      // Initial delay (ms)
    100,      // Period (ms)
    TimeUnit.MILLISECONDS
);
```

---

### 4. MainThreadProfiler (Server Thread Profiling)

**Location:** `src/main/java/net/minecraft/util/profiling/custom/MainThreadProfiler.java`

**Purpose:** Tracks operations on the main server thread

**Data Structures:**
```java
private final Map<String, OperationRecord> operations;  // Operation name -> timing data
private int tickCount;                                  // Total ticks executed
private long totalTickTime;                             // Total time spent in ticks
```

**Recording Operations:**
```java
public void recordOperation(String operation, long durationNanos) {
    operations.computeIfAbsent(operation, k -> new OperationRecord(k))
        .addSample(durationNanos);
}
```

**What Gets Tracked:**
- `tick.level` - World ticking (entities, blocks, chunks)
- `tick.connection` - Network packet processing
- `tick.sendChunks` - Chunk data transmission
- `tick.commandFunctions` - Command function execution
- `tick.players` - Player management

---

### 5. RenderThreadProfiler (Client Thread Profiling)

**Location:** `src/main/java/net/minecraft/util/profiling/custom/RenderThreadProfiler.java`

**Purpose:** Tracks operations on the render thread (client-side only)

**Similar to MainThreadProfiler but tracks:**
- `frame.gameRenderer` - Main rendering operations
- `frame.packetProcessing` - Client-side packet handling
- Frame count and timing

---

### 6. OperationRecord (Timing Data)

**Location:** `src/main/java/net/minecraft/util/profiling/custom/OperationRecord.java`

**Purpose:** Stores timing statistics for a single operation type

**Data Tracked:**
```java
private final String operation;      // Operation name
private long totalTime;              // Sum of all sample durations
private long callCount;              // Number of times operation was called
private long minTime;                // Fastest execution
private long maxTime;                // Slowest execution
private final List<Long> samples;    // All individual timings
```

**Statistical Methods:**
```java
public double getAvgTime() {
    return callCount > 0 ? (double) totalTime / callCount : 0.0;
}

public long getPercentile(double percentile) {
    List<Long> sorted = new ArrayList<>(samples);
    Collections.sort(sorted);
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    return sorted.get(index);
}
```

**Thread Safety:**
```java
public synchronized void addSample(long durationNanos) {
    totalTime += durationNanos;
    callCount++;
    minTime = Math.min(minTime, durationNanos);
    maxTime = Math.max(maxTime, durationNanos);
    samples.add(durationNanos);
}
```

---

### 7. ProfilingSession (Data Container)

**Location:** `src/main/java/net/minecraft/util/profiling/custom/ProfilingSession.java`

**Purpose:** Contains all data for a single profiling session

**Key Fields:**
```java
private final UUID sessionId;                              // Unique session ID
private final long startTime;                              // Nanos when started
private long endTime;                                      // Nanos when stopped
private final CommandSourceStack initiator;                // Who started it

private Map<Long, ThreadRecord> threads;                   // All threads
private Map<String, OperationRecord> mainThreadOperations; // Main thread ops
private Map<String, OperationRecord> renderThreadOperations; // Render thread ops

private int totalTicks;                                    // Total ticks executed
private int totalFrames;                                   // Total frames rendered
private double avgTickTime;                                // Average tick duration
private double avgFrameTime;                               // Average frame duration
```

**Convenience Methods:**
```java
public long getDuration() {
    return endTime - startTime;
}

public double getDurationSeconds() {
    return getDuration() / 1_000_000_000.0;
}
```

---

### 8. ProfilerReportGenerator (Report Formatting)

**Location:** `src/main/java/net/minecraft/util/profiling/custom/ProfilerReportGenerator.java`

**Purpose:** Generates formatted text reports from session data

**Report Sections:**
1. Header (session info, system info)
2. Thread Summary (thread counts and categories)
3. Primary Thread Analysis (main/render thread breakdowns)
4. All Threads Detail (complete thread list)
5. Performance Notes (automated analysis)
6. Footer (timestamp, file path)

**Formatting Utilities:**
```java
private String centerText(String text, int width) {
    int padding = (width - text.length()) / 2;
    return " ".repeat(Math.max(0, padding)) + text;
}

private String truncate(String text, int maxLength) {
    if (text.length() <= maxLength) return text;
    return text.substring(0, maxLength - 3) + "...";
}
```

**Time Formatting:**
```java
private static final long NANOS_PER_SECOND = 1_000_000_000L;
private static final long NANOS_PER_MS = 1_000_000L;

double seconds = nanos / (double) NANOS_PER_SECOND;
double milliseconds = nanos / (double) NANOS_PER_MS;
```

---

## Instrumentation Points

The profiler records timing at key points in the game code:

### MinecraftServer.java (Main Thread)

**tickServer() method:**
```java
public void tickServer(BooleanSupplier hasTimeLeft) {
    long tickStart = Util.getNanos();
    
    // ... existing tick code ...
    
    long m = Util.getNanos() - tickStart;
    ProfilerManager.recordMainThreadTick(m);
}
```

**Individual Operations:**
```java
// Example: World ticking
long startTime = Util.getNanos();
serverLevel.tick(booleanSupplier);
ProfilerManager.recordMainThreadOperation("tick.level", Util.getNanos() - startTime);

// Example: Connection processing
startTime = Util.getNanos();
this.tickConnection();
ProfilerManager.recordMainThreadOperation("tick.connection", Util.getNanos() - startTime);
```

### Minecraft.java (Render Thread)

**runTick() method:**
```java
public void runTick(boolean renderLevel) {
    long frameStart = Util.getNanos();
    
    // Packet processing
    long startTime = Util.getNanos();
    this.connection.tick();
    ProfilerManager.recordRenderThreadOperation("frame.packetProcessing", Util.getNanos() - startTime);
    
    // Rendering
    startTime = Util.getNanos();
    this.gameRenderer.render(deltaTracker, renderLevel);
    ProfilerManager.recordRenderThreadOperation("frame.gameRenderer", Util.getNanos() - startTime);
    
    // Record total frame time
    ProfilerManager.recordRenderThreadFrame(Util.getNanos() - frameStart);
}
```

---

## Performance Considerations

### Overhead Analysis

**When Profiler is OFF:**
- Zero overhead - no code executes
- Check is just `if (currentSession != null)` which JIT optimizes away

**When Profiler is ON:**
- Minimal overhead per operation:
  - 2 calls to `System.nanoTime()` (~20-30ns each)
  - Simple arithmetic (subtraction)
  - HashMap lookup + insertion (~50ns)
  - Total: ~100-150ns per tracked operation

**For perspective:**
- 150ns = 0.00015ms = 0.000015% of a 1ms operation
- Even with 1000 operations per tick: 0.15ms overhead
- On a 9.9ms average tick: 1.5% overhead

### Memory Usage

**Typical Session (10 minutes):**
- 12,000 ticks (10 min × 60 sec × 20 TPS)
- ~5 operations per tick = 60,000 samples
- Each sample: ~32 bytes (operation name + timing data)
- Total: ~2MB for operation data
- Thread data: ~1MB for 50 threads
- **Grand total: ~3MB** (negligible)

---

## Data Flow Example

Let's trace a single tick:

1. **Server starts tick:**
```java
// MinecraftServer.java line ~1130
long tickStart = Util.getNanos();  // tickStart = 1000000000
```

2. **World update begins:**
```java
// MinecraftServer.java line ~1230
long startTime = Util.getNanos();  // startTime = 1000000100
serverLevel.tick(booleanSupplier);
// ... tick completes ...
```

3. **World update recorded:**
```java
// MinecraftServer.java line ~1240
ProfilerManager.recordMainThreadOperation(
    "tick.level", 
    Util.getNanos() - startTime  // e.g., 1000008000 - 1000000100 = 7900ns
);
```

4. **ProfilerManager processes:**
```java
// ProfilerManager.java line ~104
public static void recordMainThreadOperation(String operation, long durationNanos) {
    if (mainThreadProfiler != null) {
        mainThreadProfiler.recordOperation(operation, durationNanos);
    }
}
```

5. **MainThreadProfiler stores:**
```java
// MainThreadProfiler.java line ~21
public void recordOperation(String operation, long durationNanos) {
    operations.computeIfAbsent(operation, k -> new OperationRecord(k))
        .addSample(durationNanos);  // Adds 7900ns to "tick.level"
}
```

6. **OperationRecord updates:**
```java
// OperationRecord.java line ~27
public synchronized void addSample(long durationNanos) {
    totalTime += durationNanos;      // totalTime += 7900
    callCount++;                      // callCount = 1
    minTime = Math.min(minTime, durationNanos);  // minTime = 7900
    maxTime = Math.max(maxTime, durationNanos);  // maxTime = 7900
    samples.add(durationNanos);       // samples = [7900]
}
```

7. **When profiling stops, report generates:**
```java
// ProfilerReportGenerator.java line ~232
private void generateOperationBreakdown(...) {
    OperationRecord op = operations.get("tick.level");
    double seconds = op.getTotalTime() / (double) NANOS_PER_SECOND;
    double percent = (op.getTotalTime() / (double) totalNanos) * 100;
    
    // Output: "1. tick.level    3.56s   97.40%"
}
```

---

## File Output

**Directory:** `debug/profiling/`
**Filename Pattern:** `profile-YYYY-MM-DD_HH.mm.ss.txt`
**Example:** `profile-2026-01-08_06.15.30.txt`

**File Creation:**
```java
// ProfilerReportGenerator.java line ~31
Path reportPath = MetricsPersister.PROFILING_RESULTS_DIR.resolve(filename);
Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8));
```

**Directory Creation:**
- Automatically created if it doesn't exist
- Uses `Files.createDirectories()` for recursive creation
- Typically gitignored to avoid committing debug data

---

## Thread Categories

The profiler automatically categorizes threads:

```java
private Map<String, List<ThreadRecord>> categorizeThreads(Map<Long, ThreadRecord> threads) {
    for (ThreadRecord thread : threads.values()) {
        String category;
        String name = thread.getName();
        
        if (name.contains("Server thread") || name.contains("Render thread")) {
            category = "Main Threads";
        } else if (name.contains("Netty")) {
            category = "Network I/O";
        } else if (name.contains("Worker-Main")) {
            category = "Worker Pools";
        } else if (name.contains("IO-Worker") || name.contains("Download")) {
            category = "File I/O";
        } else {
            category = "Other";
        }
        
        categories.computeIfAbsent(category, k -> new ArrayList<>()).add(thread);
    }
    return categories;
}
```

**Recognized Categories:**
- **Main Threads**: Server thread, Render thread
- **Network I/O**: Netty Server/Client IO threads
- **Worker Pools**: Worker-Main-N threads (ForkJoinPool)
- **File I/O**: IO-Worker-N, Download-N threads
- **Other**: Everything else (GC, JIT, custom plugin threads, etc.)

---

## Error Handling

**Scenarios Handled:**

1. **Double Start:**
```java
if (ProfilerManager.isRunning()) {
    throw ERROR_ALREADY_RUNNING.create();
}
```

2. **Stop Without Start:**
```java
if (!ProfilerManager.isRunning()) {
    throw ERROR_NOT_RUNNING.create();
}
```

3. **Report Generation Failure:**
```java
try {
    Path reportPath = ProfilerManager.stop();
    // ... success message ...
} catch (Exception e) {
    source.sendFailure(Component.literal("Failed: " + e.getMessage()));
    return 0;
}
```

4. **Thread Scanning Errors:**
- Gracefully handles null ThreadInfo (terminated threads)
- Catches exceptions during thread statistics collection
- Continues operation even if individual threads fail

---

## Future Enhancements

Potential improvements to the profiler:

1. **Live Statistics:**
   - `/profile status` command showing current stats
   - Real-time TPS and tick time display

2. **Filtering:**
   - Profile specific operations only
   - Exclude certain threads from tracking

3. **Export Formats:**
   - JSON output for programmatic analysis
   - CSV for spreadsheet import
   - Flamegraph generation

4. **Advanced Analysis:**
   - Automated bottleneck detection
   - Performance regression detection
   - Comparative analysis between sessions

5. **Integration:**
   - Export to Java Flight Recorder format
   - Integration with external monitoring tools
   - Web dashboard for viewing reports

---

## Summary

The profiler works by:

1. **Capturing timestamps** before and after operations
2. **Recording durations** in efficient data structures
3. **Monitoring threads** using Java's ThreadMXBean
4. **Generating reports** with formatted statistics
5. **Saving results** to human-readable text files

**Key strengths:**
- ✅ Minimal performance overhead (<2%)
- ✅ In-game command interface
- ✅ Comprehensive thread tracking
- ✅ Detailed operation breakdowns
- ✅ Thread-safe concurrent operation
- ✅ Easy-to-read output

**Technical highlights:**
- Uses `System.nanoTime()` for precise timing
- Thread-safe with synchronized blocks and concurrent collections
- Scheduled thread scanning at 100ms intervals
- Efficient percentile calculations
- Automatic thread categorization
