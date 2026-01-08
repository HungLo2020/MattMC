# Profiler Implementation Plan

## Executive Summary

This document provides a comprehensive plan for implementing a command-line profiler for MattMC that can be controlled via `/profile start` and `/profile stop` commands. The profiler will track all threads used by the game, with special emphasis on the main (server tick) thread and render thread, providing detailed breakdowns of time spent in different operations.

## Table of Contents

1. [Requirements](#requirements)
2. [Architecture Overview](#architecture-overview)
3. [Technical Design](#technical-design)
4. [Implementation Details](#implementation-details)
5. [Report Format](#report-format)
6. [Integration Points](#integration-points)
7. [Implementation Steps](#implementation-steps)
8. [Testing Strategy](#testing-strategy)
9. [Future Enhancements](#future-enhancements)

---

## Requirements

### Functional Requirements

1. **Command Interface**
   - `/profile start` - Begins profiling session
   - `/profile stop` - Ends profiling session and generates report
   - Commands must work in-game during both RunDev and RunExport modes
   - Appropriate permission level (operator level 2 or higher)

2. **Thread Tracking**
   - Track ALL threads created during profiling session
   - Record thread lifecycle (creation time, death time, total lifetime)
   - Capture thread names and purposes
   - Track thread activity vs idle time

3. **Primary Thread Analysis**
   - **Main Thread (Server Tick Thread)**: Detailed breakdown of tick operations
   - **Render Thread (Client-side)**: Detailed breakdown of rendering operations
   - Time-based percentage breakdowns (e.g., "37% building meshes")

4. **Report Output**
   - Generate reports in crash-reports directory or similar accessible location
   - Reports must be accessible in both development and exported builds
   - Human-readable format with structured data
   - Include timestamps, session duration, and system information

### Non-Functional Requirements

1. **Performance**
   - Minimal overhead when profiler is not running
   - Acceptable overhead during profiling (< 10% performance impact)
   - Efficient data collection and storage

2. **Reliability**
   - Must not crash the game
   - Handle edge cases (stop without start, multiple starts, etc.)
   - Graceful shutdown if game crashes during profiling

3. **Usability**
   - Clear feedback when starting/stopping
   - Report file location clearly communicated to user
   - Structured, easy-to-read reports

---

## Architecture Overview

### High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    /profile Command                        │
│                  (ProfileCommand.java)                     │
└─────────────────────────┬──────────────────────────────────┘
                          │
                          │ Controls
                          ▼
┌────────────────────────────────────────────────────────────┐
│               Profiler Manager                             │
│           (ProfilerManager.java)                           │
│  - Session management                                      │
│  - Thread registry coordination                            │
│  - Report generation orchestration                         │
└─────────────────────────┬──────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────┐ ┌────────────────┐
│ Thread Tracker  │ │Main Thread  │ │Render Thread   │
│                 │ │Profiler     │ │Profiler        │
│ - Intercept     │ │             │ │                │
│   thread        │ │- Tick phase │ │- Frame phase   │
│   creation      │ │  tracking   │ │  tracking      │
│ - Track all     │ │- Operation  │ │- Operation     │
│   threads       │ │  timing     │ │  timing        │
│ - Record        │ │- Method     │ │- Method        │
│   lifecycle     │ │  profiling  │ │  profiling     │
└─────────────────┘ └─────────────┘ └────────────────┘
          │               │               │
          └───────────────┼───────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────────┐
│               Report Generator                             │
│           (ProfilerReportGenerator.java)                   │
│  - Format data into readable report                        │
│  - Calculate percentages and statistics                    │
│  - Write to file in crash-reports directory                │
└────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

1. **ProfileCommand**: Command handler for `/profile start` and `/profile stop`
2. **ProfilerManager**: Central coordinator for profiling sessions
3. **ThreadTracker**: Monitors all threads in the JVM
4. **MainThreadProfiler**: Detailed profiling of server/main thread operations
5. **RenderThreadProfiler**: Detailed profiling of client render thread operations
6. **ProfilerReportGenerator**: Generates formatted reports from collected data

---

## Technical Design

### 1. Thread Discovery and Tracking

#### Existing Thread Infrastructure

MattMC uses several thread pools and execution contexts:

**Core Thread Pools (from `Util.java`):**
- `BACKGROUND_EXECUTOR` - Main background worker pool (ForkJoinPool)
  - Thread naming pattern: "Worker-Main-N" (verified in Util.makeExecutor())
  - Size: CPU cores - 1, max 255
- `IO_POOL` - I/O worker pool (CachedThreadPool)
  - Thread naming pattern: "IO-Worker-N" (verified in Util.makeIoExecutor())
- `DOWNLOAD_POOL` - Download worker pool (CachedThreadPool)
  - Thread naming pattern: "Download-N" (verified in Util.makeIoExecutor())

**Game Threads:**
- Main Server Thread: "Server thread" (from MinecraftServer)
- Render Thread: "Render thread" (from Minecraft client)
- Network threads: "Netty Server IO #N", "Netty Client IO #N"
- Chunk processing threads
- World generation threads
- File I/O threads

#### Thread Tracking Strategy

Use Java's `ThreadMXBean` to:
1. Enumerate all threads at profiler start
2. Periodically poll for new threads during profiling
3. Collect thread metadata:
   - Thread ID and name
   - Creation timestamp
   - CPU time
   - User time
   - Blocked time
   - Waited time
   - State history

```java
ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
long[] threadIds = threadBean.getAllThreadIds();
ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds, Integer.MAX_VALUE);
```

#### Custom Thread Instrumentation

Wrap thread creation points to auto-register with profiler:

```java
// In ProfilerManager
public static Thread trackThread(Thread thread, String purpose) {
    if (isActive()) {
        threadRegistry.register(thread, purpose, System.nanoTime());
    }
    return thread;
}
```

### 2. Main Thread Profiling

#### Tick Cycle Breakdown

The main thread (server thread) operates on a tick-based loop at 20 TPS (50ms per tick).

**Key Operations to Track:**

From `MinecraftServer.tickServer()`:
1. **Tick Functions** - Function execution
2. **Network Processing** - Packet handling
3. **World Ticking** - Level.tick()
   - Entity updates
   - Block updates
   - Tile entity updates
   - Chunk ticking
4. **Scheduled Tasks** - Task execution from queue
5. **Player Updates** - Player list management
6. **Command Processing** - Command execution
7. **Save Operations** - Auto-save (if triggered)

**Instrumentation Approach:**

Use existing profiler infrastructure (`ProfilerFiller`) with custom collector:

```java
// During profiling session, wrap profiler
ProfilerFiller wrappedProfiler = new ProfilerCollectorWrapper(
    server.getProfiler(), 
    mainThreadCollector
);
server.setProfiler(wrappedProfiler);
```

The `ProfilerCollectorWrapper` will:
- Forward all calls to underlying profiler
- Collect timing data into custom data structure
- Track call stack depth
- Calculate time percentages

### 3. Render Thread Profiling

#### Frame Cycle Breakdown

The render thread operates at variable FPS (typically 60+ FPS, 16.67ms per frame ideal).

**Key Operations to Track:**

From `GameRenderer.render()` and `LevelRenderer`:
1. **Frame Setup** - Camera and matrix setup
2. **World Rendering**
   - Terrain/chunk rendering (solid pass)
   - Entity rendering
   - Particle rendering
   - Translucent rendering (sorted)
   - Weather effects
3. **Post-Processing** - Shader effects
4. **GUI Rendering** - UI, HUD, screens
5. **Buffer Swap** - Present frame

**Render-Specific Metrics:**
- Draw calls per frame
- Rendered chunks
- Rendered entities
- Triangle count
- Texture bindings
- Shader program switches
- GPU wait time (via fences)

**Instrumentation Approach:**

Inject profiling at render system level:

```java
// In RenderSystem
public static void profiledCall(String operation, Runnable task) {
    if (ProfilerManager.isActive() && RenderSystem.isOnRenderThread()) {
        long start = System.nanoTime();
        task.run();
        long duration = System.nanoTime() - start;
        ProfilerManager.recordRenderOperation(operation, duration);
    } else {
        task.run();
    }
}
```

### 4. Data Structures

#### Thread Record

```java
public class ThreadRecord {
    long threadId;
    String name;
    String purpose;
    long createdAt;      // nanoseconds
    long terminatedAt;   // nanoseconds, -1 if still running
    long totalCpuTime;   // nanoseconds
    long totalUserTime;  // nanoseconds
    long totalWaitTime;  // milliseconds
    long totalBlockTime; // milliseconds
    Map<Thread.State, Long> stateTimeMap; // time in each state
    List<ThreadSnapshot> snapshots;
}
```

#### Operation Record

```java
public class OperationRecord {
    String operation;      // e.g., "tick.entity", "render.chunks"
    long totalTime;        // nanoseconds
    long callCount;
    long minTime;
    long maxTime;
    double avgTime;
    List<Long> samples;    // for percentile calculations
}
```

#### Profiling Session

```java
public class ProfilingSession {
    long startTime;
    long endTime;
    UUID sessionId;
    CommandSourceStack initiator;
    
    // Thread data
    Map<Long, ThreadRecord> threads;
    
    // Operation data
    Map<String, OperationRecord> mainThreadOperations;
    Map<String, OperationRecord> renderThreadOperations;
    Map<String, OperationRecord> otherOperations;
    
    // Aggregate statistics
    int totalTicks;
    int totalFrames;
    double avgTickTime;
    double avgFrameTime;
    long totalSamples;
}
```

### 5. Report Generation

#### Report Directory

**Target Directory:** `debug/profiling/` (similar to existing perf command)

- Aligns with existing profiling infrastructure (`MetricsPersister.PROFILING_RESULTS_DIR` - verified to be `Paths.get("debug/profiling")`)
- Accessible in both dev and production
- Automatically created if missing (see MetricsPersister.saveReports() which calls `Files.createDirectories(PROFILING_RESULTS_DIR)`)
- Gitignored by default

**Alternative (as mentioned in requirements):** Could use `crash-reports/` if preferred, but `debug/profiling/` is more semantically appropriate and consistent with existing profiling tools.

#### Report Format

**Filename:** `profile-YYYY-MM-DD_HH.mm.ss.txt`

**Report Structure:**

```
================================================================================
                        MattMC Performance Profile Report
================================================================================

Session Information:
  Session ID:     550e8400-e29b-41d4-a716-446655440000
  Started by:     Player (Operator level 2)
  Start Time:     2026-01-08 03:00:15
  End Time:       2026-01-08 03:05:27
  Duration:       5 minutes, 12 seconds (312.45 seconds)
  
System Information:
  Minecraft:      1.21.10 (MattMC)
  Java Version:   21.0.1
  OS:             Linux 5.15.0
  CPU Cores:      16
  Max Memory:     2048 MB
  Used Memory:    1456 MB

================================================================================
                            THREAD SUMMARY
================================================================================

Total Threads Tracked: 47
  - Active at end:     42
  - Terminated:        5

Thread Categories:
  Main Threads:        2  (Server thread, Render thread)
  Network I/O:         8  (Netty Server IO #1-4, Netty Client IO #1-4)
  Worker Pools:        24 (Worker-Main-1 through Worker-Main-24)
  File I/O:            4  (IO-Worker-1 through IO-Worker-4)
  Other:              9

================================================================================
                        PRIMARY THREAD ANALYSIS
================================================================================

────────────────────────────────────────────────────────────────────────────────
Server Thread (Main Thread)
────────────────────────────────────────────────────────────────────────────────

Total Active Time:    310.12 seconds (99.25% of session)
Total Ticks:          6,202
Average Tick Time:    50.0 ms
Tick Time 95th %ile:  52.3 ms
Tick Time 99th %ile:  67.8 ms
Max Tick Time:        125.4 ms (during world save)

Time Distribution by Operation:

1. World Ticking                               186.45s    60.13%
   ├─ Entity Updates                           89.23s     28.77%
   │  ├─ Entity AI                             45.12s     14.55%
   │  ├─ Entity Movement                       28.34s      9.14%
   │  └─ Entity Collision                      15.77s      5.08%
   ├─ Block Ticking                            52.34s     16.88%
   ├─ Tile Entity Updates                      28.67s      9.24%
   └─ Chunk Ticking                            16.21s      5.23%

2. Network Processing                          45.67s     14.73%
   ├─ Packet Decoding                          18.23s      5.88%
   ├─ Packet Handling                          22.45s      7.24%
   └─ Packet Encoding                           4.99s      1.61%

3. Scheduled Tasks                             34.56s     11.15%

4. Player Management                           18.23s      5.88%

5. World Loading/Generation                    12.45s      4.02%

6. Command Processing                           6.78s      2.19%

7. Auto-Save Operations                         3.45s      1.11%
   (3 saves during session)

8. Other                                        2.53s      0.82%

Top 10 Most Time-Consuming Methods:

1. net.minecraft.world.entity.Entity.tick()                      45.12s   14.55%
2. net.minecraft.world.level.block.Block.tick()                  38.45s   12.39%
3. net.minecraft.world.level.Level.tickBlockEntities()           28.67s    9.24%
4. net.minecraft.server.network.ServerGamePacketListenerImpl.*   22.45s    7.24%
5. net.minecraft.world.entity.ai.Brain.tick()                    19.34s    6.24%
6. net.minecraft.world.level.chunk.LevelChunk.tick()             16.21s    5.23%
7. net.minecraft.world.entity.LivingEntity.aiStep()              14.56s    4.70%
8. net.minecraft.server.level.ServerLevel.tick()                 12.34s    3.98%
9. net.minecraft.world.entity.Mob.serverAiStep()                 10.23s    3.30%
10. net.minecraft.server.players.PlayerList.tick()                9.12s    2.94%

────────────────────────────────────────────────────────────────────────────────
Render Thread (Client-side only)
────────────────────────────────────────────────────────────────────────────────

Total Active Time:    309.87 seconds (99.17% of session)
Total Frames:         18,592
Average FPS:          59.98
Frame Time Average:   16.67 ms
Frame Time 95th %ile: 18.23 ms
Frame Time 99th %ile: 23.45 ms
Max Frame Time:       45.67 ms

Time Distribution by Operation:

1. Terrain Rendering                           124.56s    40.20%
   ├─ Chunk Mesh Building                      67.23s     21.70%
   ├─ Solid Pass Rendering                     32.45s     10.47%
   ├─ Cutout Pass Rendering                    14.23s      4.59%
   └─ Translucent Pass Rendering               10.65s      3.44%

2. Entity Rendering                            58.34s     18.83%
   ├─ Entity Model Rendering                   34.56s     11.15%
   ├─ Entity Animation                         12.34s      3.98%
   └─ Entity Culling/Sorting                   11.44s      3.69%

3. Particle Rendering                          28.45s      9.18%

4. GUI Rendering                               42.67s     13.77%
   ├─ HUD Rendering                            18.23s      5.88%
   ├─ Screen Rendering                         15.34s      4.95%
   └─ Text Rendering                            9.10s      2.94%

5. Post-Processing                             12.34s      3.98%

6. Buffer Management                           18.67s      6.02%

7. Camera/Matrix Setup                          8.45s      2.73%

8. Sky/Weather Rendering                        6.78s      2.19%

9. Other                                        9.61s      3.10%

Top 10 Most Time-Consuming Methods:

1. SectionRenderDispatcher.rebuildSectionSync()                  67.23s   21.70%
2. LevelRenderer.renderChunkLayer()                              32.45s   10.47%
3. EntityRenderDispatcher.render()                               34.56s   11.15%
4. GuiGraphics.drawString()                                      18.23s    5.88%
5. ParticleEngine.render()                                       28.45s    9.18%
6. Screen.render()                                               15.34s    4.95%
7. LevelRenderer.renderSky()                                      6.78s    2.19%
8. EntityRenderer.render()                                       12.34s    3.98%
9. BufferBuilder.build()                                         11.23s    3.62%
10. GameRenderer.renderLevel()                                    8.67s    2.80%

Rendering Statistics:
  Total Draw Calls:        2,456,789
  Avg Draw Calls/Frame:    132.1
  Total Chunks Rendered:   185,920,000
  Avg Chunks/Frame:        10,000
  Total Entities Rendered: 12,345,600
  Avg Entities/Frame:      664

================================================================================
                        ALL THREADS DETAIL
================================================================================

[Main Thread]
  ID:           1
  Name:         Server thread
  Purpose:      Main server tick loop
  Created:      2026-01-08 03:00:15.123
  Terminated:   [Still Running]
  Lifetime:     312.45 seconds
  CPU Time:     310.12 seconds (99.25% active)
  User Time:    308.45 seconds
  Wait Time:    0.89 seconds
  Block Time:   0.12 seconds
  State Breakdown:
    RUNNABLE:   99.25%
    WAITING:    0.56%
    BLOCKED:    0.19%

[Render Thread]
  ID:           2
  Name:         Render thread
  Purpose:      Client rendering loop
  Created:      2026-01-08 03:00:15.234
  Terminated:   [Still Running]
  Lifetime:     312.31 seconds
  CPU Time:     309.87 seconds (99.22% active)
  User Time:    307.23 seconds
  Wait Time:    1.12 seconds
  Block Time:   0.23 seconds
  State Breakdown:
    RUNNABLE:   99.22%
    WAITING:    0.67%
    BLOCKED:    0.11%

[Network Thread Group: 8 threads]
  Pattern:      Netty Server IO #N, Netty Client IO #N
  Purpose:      Network packet handling (Netty event loops)
  Total Time:   89.45 seconds (combined CPU time)
  Average Load: 11.18 seconds per thread
  Details:
    - Netty Server IO #1:  ID 23, 12.34s CPU
    - Netty Server IO #2:  ID 24, 11.23s CPU
    - Netty Client IO #1:  ID 25, 13.45s CPU
    ...

[Worker Thread Group: 24 threads]
  Pattern:      Worker-Main-N
  Purpose:      Background task processing (ForkJoinPool)
  Total Time:   567.89 seconds (combined CPU time)
  Average Load: 23.66 seconds per thread
  Peak Thread:  Worker-Main-5 with 45.67s CPU time
  Details:
    - Worker-Main-1:   ID 10, 23.45s CPU
    - Worker-Main-2:   ID 11, 24.56s CPU
    ...

[I/O Thread Group: 4 threads]
  Pattern:      IO-Worker-N
  Purpose:      File I/O operations
  Total Time:   45.67 seconds (combined CPU time)
  Average Load: 11.42 seconds per thread
  Details:
    - IO-Worker-1:     ID 30, 12.34s CPU
    - IO-Worker-2:     ID 31, 11.23s CPU
    ...

[Short-Lived Threads: 5 terminated]
  1. World-Upgrade-1   ID 45, Lived 2.34s, Purpose: Data migration
  2. Asset-Loader-1    ID 46, Lived 1.23s, Purpose: Resource loading
  3. Config-Reload-1   ID 47, Lived 0.67s, Purpose: Configuration reload
  4. Chunk-Save-1      ID 48, Lived 3.45s, Purpose: Chunk serialization
  5. Backup-Thread-1   ID 49, Lived 5.67s, Purpose: World backup

================================================================================
                            PERFORMANCE NOTES
================================================================================

High CPU Usage Areas:
  ⚠ Entity AI consuming 14.55% of main thread time
    → Consider reducing entity count or AI complexity
  
  ⚠ Chunk mesh building consuming 21.70% of render thread time
    → Large number of chunk updates, possibly due to block changes
  
  ℹ Three auto-saves during session added 3.45s total pause time
    → Average 1.15s per save (within acceptable range)

Thread Pool Utilization:
  ✓ Worker pool well-balanced (23.66s avg, 45.67s max)
  ✓ I/O pool low contention (11.42s avg)
  ✓ Network threads evenly distributed

Recommendations:
  1. Entity AI optimization could improve overall TPS
  2. Consider reducing view distance if chunk rendering is bottleneck
  3. Thread pool sizes are appropriate for current load

================================================================================
                            END OF REPORT
================================================================================
Generated: 2026-01-08 03:05:27
Report File: debug/profiling/profile-2026-01-08_03.05.27.txt
```

---

## Implementation Details

### 1. Package Structure

```
src/main/java/net/minecraft/server/commands/
  └── ProfileCommand.java               # Command registration and handling

src/main/java/net/minecraft/util/profiling/custom/
  ├── ProfilerManager.java              # Central profiler coordinator
  ├── ProfilingSession.java             # Session data container
  ├── ThreadTracker.java                # Thread monitoring and tracking
  ├── ThreadRecord.java                 # Thread data record
  ├── MainThreadProfiler.java           # Main thread operation tracking
  ├── RenderThreadProfiler.java         # Render thread operation tracking
  ├── OperationRecord.java              # Operation timing record
  ├── ProfilerCollectorWrapper.java     # Wraps existing ProfilerFiller
  └── ProfilerReportGenerator.java      # Report formatting and output
```

### 2. ProfileCommand Implementation

```java
package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.profiling.custom.ProfilerManager;
import java.nio.file.Path;

public class ProfileCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = 
        new SimpleCommandExceptionType(Component.translatable("commands.profile.alreadyRunning"));
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = 
        new SimpleCommandExceptionType(Component.translatable("commands.profile.notRunning"));
    private static final SimpleCommandExceptionType START_FAILED = 
        new SimpleCommandExceptionType(Component.translatable("commands.profile.start.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("profile")
                .requires(source -> source.hasPermission(2))  // Operator level 2
                .then(Commands.literal("start")
                    .executes(context -> startProfiling(context.getSource())))
                .then(Commands.literal("stop")
                    .executes(context -> stopProfiling(context.getSource())))
        );
    }

    private static int startProfiling(CommandSourceStack source) throws CommandSyntaxException {
        if (ProfilerManager.isRunning()) {
            throw ERROR_ALREADY_RUNNING.create();
        }

        if (!ProfilerManager.start(source)) {
            throw START_FAILED.create();
        }

        source.sendSuccess(
            () -> Component.translatable("commands.profile.started")
                .withStyle(ChatFormatting.GREEN),
            true
        );
        return 1;
    }

    private static int stopProfiling(CommandSourceStack source) throws CommandSyntaxException {
        if (!ProfilerManager.isRunning()) {
            throw ERROR_NOT_RUNNING.create();
        }

        try {
            Path reportPath = ProfilerManager.stop();
            
            Component pathComponent = Component.literal(reportPath.toString())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(ChatFormatting.AQUA)
                .withStyle(style -> 
                    style.withClickEvent(new ClickEvent(
                        ClickEvent.Action.COPY_TO_CLIPBOARD, 
                        reportPath.toAbsolutePath().toString()
                    ))
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("chat.copy.click")
                    ))
                );

            source.sendSuccess(
                () -> Component.translatable("commands.profile.stopped", pathComponent)
                    .withStyle(ChatFormatting.GREEN),
                true
            );
            return 1;
        } catch (Exception e) {
            source.sendFailure(
                Component.translatable("commands.profile.failed", e.getMessage())
            );
            return 0;
        }
    }
}
```

### 3. ProfilerManager Implementation

**Core responsibilities:**
- Manage profiling session lifecycle
- Coordinate thread tracking
- Coordinate main/render thread profilers
- Generate reports on stop

```java
package net.minecraft.util.profiling.custom;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;
import java.util.UUID;

public class ProfilerManager {
    private static volatile ProfilingSession currentSession = null;
    private static final Object sessionLock = new Object();
    
    private static ThreadTracker threadTracker;
    private static MainThreadProfiler mainThreadProfiler;
    private static RenderThreadProfiler renderThreadProfiler;

    public static boolean start(CommandSourceStack initiator) {
        synchronized (sessionLock) {
            if (currentSession != null) {
                return false;
            }

            try {
                currentSession = new ProfilingSession(
                    UUID.randomUUID(),
                    System.nanoTime(),
                    initiator
                );

                // Initialize trackers
                threadTracker = new ThreadTracker();
                threadTracker.start();

                // Hook into main thread
                MinecraftServer server = initiator.getServer();
                if (server != null) {
                    mainThreadProfiler = new MainThreadProfiler();
                    mainThreadProfiler.attach(server);
                }

                // Hook into render thread (client-side only)
                if (isClientSide()) {
                    renderThreadProfiler = new RenderThreadProfiler();
                    renderThreadProfiler.attach(Minecraft.getInstance());
                }

                return true;
            } catch (Exception e) {
                currentSession = null;
                return false;
            }
        }
    }

    public static Path stop() throws Exception {
        synchronized (sessionLock) {
            if (currentSession == null) {
                throw new IllegalStateException("No profiling session active");
            }

            try {
                currentSession.setEndTime(System.nanoTime());

                // Collect final data
                threadTracker.stop();
                currentSession.setThreadRecords(threadTracker.getRecords());

                if (mainThreadProfiler != null) {
                    mainThreadProfiler.detach();
                    currentSession.setMainThreadOperations(
                        mainThreadProfiler.getOperations()
                    );
                }

                if (renderThreadProfiler != null) {
                    renderThreadProfiler.detach();
                    currentSession.setRenderThreadOperations(
                        renderThreadProfiler.getOperations()
                    );
                }

                // Generate report
                ProfilerReportGenerator generator = new ProfilerReportGenerator();
                Path reportPath = generator.generate(currentSession);

                return reportPath;
            } finally {
                // Cleanup
                currentSession = null;
                threadTracker = null;
                mainThreadProfiler = null;
                renderThreadProfiler = null;
            }
        }
    }

    public static boolean isRunning() {
        return currentSession != null;
    }

    private static boolean isClientSide() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Called by instrumented code to record operations
    public static void recordMainThreadOperation(String operation, long durationNanos) {
        if (mainThreadProfiler != null) {
            mainThreadProfiler.recordOperation(operation, durationNanos);
        }
    }

    public static void recordRenderThreadOperation(String operation, long durationNanos) {
        if (renderThreadProfiler != null) {
            renderThreadProfiler.recordOperation(operation, durationNanos);
        }
    }
}
```

### 4. ThreadTracker Implementation

```java
package net.minecraft.util.profiling.custom;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.concurrent.*;

public class ThreadTracker {
    private final ThreadMXBean threadBean;
    private final Map<Long, ThreadRecord> threads;
    private final ScheduledExecutorService scanner;
    private volatile boolean running;

    public ThreadTracker() {
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.threads = new ConcurrentHashMap<>();
        this.scanner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProfilerThreadScanner");
            t.setDaemon(true);
            return t;
        });
        this.running = false;

        // Enable CPU time measurement if supported
        if (threadBean.isThreadCpuTimeSupported()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }
    }

    public void start() {
        running = true;
        
        // Initial scan
        scanThreads();
        
        // Schedule periodic scans every 100ms
        scanner.scheduleAtFixedRate(
            this::scanThreads,
            100,
            100,
            TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        running = false;
        scanner.shutdown();
        try {
            scanner.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Final scan and mark terminated threads
        scanThreads();
        markTerminatedThreads();
    }

    private void scanThreads() {
        long[] threadIds = threadBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds, 0);

        for (int i = 0; i < threadIds.length; i++) {
            long id = threadIds[i];
            ThreadInfo info = threadInfos[i];

            if (info == null) continue;

            ThreadRecord record = threads.get(id);
            if (record == null) {
                // New thread discovered
                record = new ThreadRecord(id, info.getThreadName());
                threads.put(id, record);
            }

            // Update thread statistics
            if (threadBean.isThreadCpuTimeSupported()) {
                record.updateCpuTime(threadBean.getThreadCpuTime(id));
                record.updateUserTime(threadBean.getThreadUserTime(id));
            }
            
            if (threadBean.isThreadContentionMonitoringSupported()) {
                record.updateWaitTime(info.getWaitedTime());
                record.updateBlockTime(info.getBlockedTime());
            }

            record.recordState(info.getThreadState());
        }
    }

    private void markTerminatedThreads() {
        long now = System.nanoTime();
        Set<Long> currentThreadIds = new HashSet<>();
        for (long id : threadBean.getAllThreadIds()) {
            currentThreadIds.add(id);
        }

        for (Map.Entry<Long, ThreadRecord> entry : threads.entrySet()) {
            if (!currentThreadIds.contains(entry.getKey())) {
                entry.getValue().markTerminated(now);
            }
        }
    }

    public Map<Long, ThreadRecord> getRecords() {
        return new HashMap<>(threads);
    }
}
```

### 5. MainThreadProfiler Implementation

```java
package net.minecraft.util.profiling.custom;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainThreadProfiler {
    private final Map<String, OperationRecord> operations;
    private MinecraftServer server;
    private ProfilerFiller originalProfiler;
    private ProfilerCollectorWrapper wrappedProfiler;

    public MainThreadProfiler() {
        this.operations = new ConcurrentHashMap<>();
    }

    public void attach(MinecraftServer server) {
        this.server = server;
        this.originalProfiler = server.getProfiler();
        this.wrappedProfiler = new ProfilerCollectorWrapper(
            originalProfiler,
            this::recordOperation
        );
        
        // NOTE: MinecraftServer does not expose a setProfiler() method
        // Alternative approaches:
        // 1. Add setProfiler() method to MinecraftServer (requires minimal modification)
        // 2. Use instrumentation hooks at key tick operations instead
        // 3. Use byte code instrumentation (more complex, not recommended)
        // Recommended: Add simple setProfiler() method to MinecraftServer
    }

    public void detach() {
        // Restore original profiler if we added setProfiler() capability
        // Otherwise, just clean up local references
        if (server != null && originalProfiler != null) {
            // server.setProfiler(originalProfiler);  // if method is added
        }
    }

    public void recordOperation(String operation, long durationNanos) {
        operations.computeIfAbsent(operation, k -> new OperationRecord(k))
            .addSample(durationNanos);
    }

    public Map<String, OperationRecord> getOperations() {
        return new HashMap<>(operations);
    }
}
```

### 6. Integration Points

#### A. Command Registration

Add to `Commands.java` in the `register()` method:

```java
ProfileCommand.register(dispatcher);
```

Location: Around line 150, with other command registrations.

#### B. Profiler Wrapping (Main Thread)

In `MinecraftServer.java`, expose profiler setter or add profiling hooks:

```java
// In MinecraftServer class
public void setProfiler(ProfilerFiller profiler) {
    this.profiler = profiler;
}

// Or add hooks at key points:
public void tickServer(BooleanSupplier hasTimeLeft) {
    long tickStart = Util.getNanos();
    try {
        // ... existing tick code ...
    } finally {
        long tickDuration = Util.getNanos() - tickStart;
        ProfilerManager.recordMainThreadOperation("tick", tickDuration);
    }
}
```

#### C. Profiler Wrapping (Render Thread)

In `Minecraft.java`, add hooks to `runTick()`:

```java
public void runTick(boolean renderLevel) {
    long frameStart = Util.getNanos();
    try {
        // ... existing render code ...
    } finally {
        long frameDuration = Util.getNanos() - frameStart;
        ProfilerManager.recordRenderThreadOperation("frame", frameDuration);
    }
}
```

In `GameRenderer.java`, add granular hooks:

```java
public void render(DeltaTracker deltaTracker, boolean renderLevel) {
    long start;
    
    start = Util.getNanos();
    setupCamera(deltaTracker.getGameTimeDeltaPartialTick(!renderLevel));
    ProfilerManager.recordRenderThreadOperation("render.camera", Util.getNanos() - start);
    
    start = Util.getNanos();
    if (renderLevel) {
        renderLevel(deltaTracker);
    }
    ProfilerManager.recordRenderThreadOperation("render.level", Util.getNanos() - start);
    
    // ... and so on for each major operation
}
```

#### D. Thread Creation Instrumentation

Wrap thread creation in `Util.java`:

```java
private static TracingExecutor makeExecutor(String string) {
    // ... existing code ...
    ForkJoinWorkerThread forkJoinWorkerThread = new ForkJoinWorkerThread(forkJoinPool) {
        protected void onStart() {
            TracyClient.setThreadName(string2, string.hashCode());
            
            // Add profiler tracking
            if (ProfilerManager.isRunning()) {
                ProfilerManager.trackThread(this, "Worker thread: " + string);
            }
            
            super.onStart();
        }
        // ... rest of code ...
    };
}
```

---

## Report Format

See detailed example in [Report Generation](#5-report-generation) section above.

Key sections:
1. **Session Information**: Metadata about the profiling session
2. **System Information**: Java, OS, memory stats
3. **Thread Summary**: High-level thread count and categories
4. **Primary Thread Analysis**: Deep dive into main and render threads
5. **All Threads Detail**: Complete list of all tracked threads
6. **Performance Notes**: Automated insights and recommendations

---

## Integration Points

### Files to Create

1. `src/main/java/net/minecraft/server/commands/ProfileCommand.java` - ~100 lines
2. `src/main/java/net/minecraft/util/profiling/custom/ProfilerManager.java` - ~200 lines
3. `src/main/java/net/minecraft/util/profiling/custom/ProfilingSession.java` - ~100 lines
4. `src/main/java/net/minecraft/util/profiling/custom/ThreadTracker.java` - ~150 lines
5. `src/main/java/net/minecraft/util/profiling/custom/ThreadRecord.java` - ~100 lines
6. `src/main/java/net/minecraft/util/profiling/custom/OperationRecord.java` - ~80 lines
7. `src/main/java/net/minecraft/util/profiling/custom/MainThreadProfiler.java` - ~120 lines
8. `src/main/java/net/minecraft/util/profiling/custom/RenderThreadProfiler.java` - ~120 lines
9. `src/main/java/net/minecraft/util/profiling/custom/ProfilerCollectorWrapper.java` - ~150 lines
10. `src/main/java/net/minecraft/util/profiling/custom/ProfilerReportGenerator.java` - ~500 lines

**Total: ~1,620 lines of new code**

### Files to Modify

1. `src/main/java/net/minecraft/commands/Commands.java`
   - Add `ProfileCommand.register(dispatcher);`
   - Location: In the Commands() constructor, add with other command registrations (currently lines 183-290)
   - Suggested placement: After JfrCommand.register() (around line 258) since it's related profiling functionality
   - Change: 1 line added

2. `src/main/java/net/minecraft/server/MinecraftServer.java`
   - Add profiling hooks to `tickServer()`
   - Add RECOMMENDED: `setProfiler(ProfilerFiller profiler)` method to enable profiler swapping
     ```java
     public void setProfiler(ProfilerFiller profiler) {
         this.profiler = profiler;
     }
     ```
   - Changes: ~10-20 lines

3. `src/main/java/net/minecraft/client/Minecraft.java`
   - Add profiling hooks to `runTick()`
   - Changes: ~10-20 lines

4. `src/main/java/net/minecraft/client/renderer/GameRenderer.java`
   - Add profiling hooks to major rendering operations
   - Changes: ~30-50 lines

5. `src/main/java/net/minecraft/Util.java` (Optional but recommended)
   - Add thread tracking to `makeExecutor()` and `makeIoExecutor()`
   - Changes: ~5-10 lines

6. Translation files (for command messages)
   - `src/main/resources/assets/minecraft/lang/en_us.json`
   - Add translation keys for profile command messages
   - Changes: ~10 lines

**Total modifications: ~60-110 lines across existing files**

---

## Implementation Steps

### Phase 1: Foundation (Core Infrastructure)

**Goal:** Set up basic profiling infrastructure without game integration

**Tasks:**
1. Create package `net.minecraft.util.profiling.custom`
2. Implement `ProfilingSession.java` - data container
3. Implement `ThreadRecord.java` - thread data structure
4. Implement `OperationRecord.java` - operation timing structure
5. Implement `ThreadTracker.java` - thread monitoring
6. Write unit tests for data structures

**Deliverables:**
- Data structures and thread tracking working independently
- Unit tests passing

**Time Estimate:** 4-6 hours

### Phase 2: Profiler Manager and Command

**Goal:** Create command interface and session management

**Tasks:**
1. Implement `ProfilerManager.java` - central coordinator
2. Implement `ProfileCommand.java` - command registration
3. Integrate command into `Commands.java`
4. Add translation strings
5. Test command invocation (start/stop without full functionality)

**Deliverables:**
- `/profile start` and `/profile stop` commands work
- Basic session lifecycle management
- Thread tracking operational

**Time Estimate:** 3-4 hours

### Phase 3: Main Thread Profiling

**Goal:** Instrument main/server thread for detailed profiling

**Tasks:**
1. Implement `MainThreadProfiler.java`
2. Implement `ProfilerCollectorWrapper.java`
3. Add hooks to `MinecraftServer.tickServer()`
4. Identify and instrument key server operations
5. Test main thread profiling in dedicated server

**Deliverables:**
- Main thread operations captured during profiling
- Timing data collected and categorized

**Time Estimate:** 6-8 hours

### Phase 4: Render Thread Profiling

**Goal:** Instrument render thread for client-side profiling

**Tasks:**
1. Implement `RenderThreadProfiler.java`
2. Add hooks to `Minecraft.runTick()`
3. Add hooks to `GameRenderer.render()`
4. Instrument major rendering operations
5. Test render thread profiling in client

**Deliverables:**
- Render thread operations captured
- Frame timing data collected

**Time Estimate:** 6-8 hours

### Phase 5: Report Generation

**Goal:** Create comprehensive report output

**Tasks:**
1. Implement `ProfilerReportGenerator.java`
2. Format session information section
3. Format thread summary section
4. Format primary thread analysis section
5. Format all threads detail section
6. Add performance notes and recommendations
7. Test report generation with sample data

**Deliverables:**
- Complete, formatted reports generated
- Reports saved to `debug/profiling/` directory

**Time Estimate:** 8-10 hours

### Phase 6: Testing and Refinement

**Goal:** End-to-end testing and polish

**Tasks:**
1. Test complete flow: start → run → stop → report
2. Test in dedicated server mode
3. Test in client mode
4. Test in integrated server mode
5. Test edge cases (stop without start, crash during profiling, etc.)
6. Performance impact testing
7. Fix bugs and refine output
8. Documentation updates

**Deliverables:**
- Fully functional profiler
- All test cases passing
- Documentation complete

**Time Estimate:** 6-8 hours

### Total Implementation Time: 33-44 hours

---

## Testing Strategy

### Unit Tests

1. **Data Structure Tests**
   - `ThreadRecord` - verify lifecycle tracking, state updates
   - `OperationRecord` - verify timing aggregation, statistics
   - `ProfilingSession` - verify session management

2. **Component Tests**
   - `ThreadTracker` - verify thread discovery and tracking
   - `ProfilerManager` - verify session lifecycle
   - `ProfilerReportGenerator` - verify report formatting

### Integration Tests

1. **Command Tests**
   - Test `/profile start` command
   - Test `/profile stop` command
   - Test error cases (double start, stop without start)
   - Test permission requirements

2. **Profiling Tests**
   - Start profiler, run server for 30 seconds, stop
   - Verify thread list includes expected threads
   - Verify main thread operations captured
   - Verify report generated

3. **Client Tests**
   - Start profiler in client, play for 1 minute, stop
   - Verify render thread operations captured
   - Verify report includes both main and render thread data

### Performance Tests

1. **Overhead Measurement**
   - Measure TPS with profiler off
   - Measure TPS with profiler on
   - Verify < 10% impact

2. **Memory Tests**
   - Monitor memory usage during long profiling session (30+ minutes)
   - Verify no memory leaks

### Edge Case Tests

1. Server crash during profiling
2. Multiple profiling sessions in sequence
3. Very short profiling session (< 1 second)
4. Very long profiling session (> 1 hour)
5. High load scenarios (many entities, chunks, players)

---

## Future Enhancements

### Phase 2 Features (Post-MVP)

1. **Advanced Filtering**
   - `/profile start --threads=main,render` - profile specific threads only
   - `/profile start --duration=300` - auto-stop after duration
   - `/profile start --sample-rate=50` - adjust sampling frequency

2. **Live Monitoring**
   - `/profile status` - show current profiling stats
   - Real-time display of top operations in-game

3. **Export Formats**
   - JSON export for programmatic analysis
   - CSV export for spreadsheet analysis
   - Flamegraph generation for visualization

4. **Comparative Analysis**
   - Save multiple profiles
   - Compare profiles to identify regressions
   - Trend analysis over time

5. **Alert System**
   - Automatic alerts when operations exceed thresholds
   - Integration with monitoring systems

6. **Remote Profiling**
   - Profile dedicated server from remote client
   - Web dashboard for viewing reports

7. **Integration with Existing Tools**
   - Export to JFR format for Java Mission Control
   - Integration with Tracy profiler
   - VisualVM connection

8. **Advanced Thread Analysis**
   - Thread contention detection
   - Deadlock detection
   - Thread pool efficiency analysis

---

## Appendix: Key Minecraft Threads

Based on code analysis, here are the primary threads to track:

### Always Present

1. **Main Thread** (`Server thread` or `Client thread`)
   - Server tick loop or client main loop
   - Highest priority for profiling

2. **Render Thread** (`Render thread`) - Client only
   - OpenGL rendering operations
   - Second highest priority for profiling

### Network Threads

3. **Netty Server I/O** (`Netty Server IO #N`)
   - Server-side network packet processing
   - Multiple threads (Netty event loop)

4. **Netty Client I/O** (`Netty Client IO #N`)
   - Client-side network packet processing
   - Multiple threads (Netty event loop)

### Worker Pools

5. **Background Workers** (`Worker-Main-N`)
   - General background task processing
   - ForkJoinPool, count = CPU cores - 1

6. **I/O Workers** (`IO-Worker-N`)
   - File I/O operations
   - Cached thread pool

7. **Download Workers** (`Download-N`)
   - Resource downloads
   - Cached thread pool, daemon threads

### Specialized Threads

8. **Chunk Workers**
   - Chunk loading, saving, generation
   - Multiple pools depending on configuration

9. **World Generator Threads**
   - Terrain generation
   - Feature placement

10. **Sound Engine** (`Sound Engine`)
    - Audio processing
    - Client-side only

11. **Garbage Collector Threads**
    - JVM GC threads
    - Track for overhead analysis

### Short-Lived Threads

12. **Asset Loading**
13. **Config Reload**
14. **World Upgrade**
15. **Backup Threads**

---

## Conclusion

This profiler implementation will provide MattMC with professional-grade performance analysis capabilities accessible directly from in-game commands. By tracking all threads with special focus on the main and render threads, developers and server administrators will have detailed insights into where the game spends its time, enabling targeted optimizations and performance tuning.

The modular design allows for incremental implementation and future enhancements while maintaining minimal impact on game performance when not actively profiling. The comprehensive report format provides both high-level overviews and detailed breakdowns suitable for both casual users and advanced developers.

**Key Benefits:**
- ✅ In-game command interface (no external tools needed)
- ✅ Comprehensive thread tracking (all threads, not just main/render)
- ✅ Detailed operation breakdowns with percentages
- ✅ Accessible reports in standard directory
- ✅ Works in both development and production builds
- ✅ Minimal performance overhead
- ✅ Extensible architecture for future features
