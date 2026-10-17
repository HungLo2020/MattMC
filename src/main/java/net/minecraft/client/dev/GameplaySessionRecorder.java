package net.minecraft.client.dev;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Records a hand-played session for offline performance diagnosis
 * ({@code python3 DevUtils/RunDev.py --record}). Enabled only when the
 * {@code mattmc.dev.recordDir} system property names an output directory.
 *
 * <p>The render thread only appends primitives to bounded buffers; a daemon
 * thread formats them into CSV files:
 * <ul>
 *   <li>{@code ticks.csv}: each client loop iteration's start, start-to-start
 *       interval, duration and exclusive time per instrumented phase;</li>
 *   <li>{@code frames.csv}: each presented Rust whole frame's Java timestamps
 *       plus every field of the native submit result and frame profile;</li>
 *   <li>{@code seconds.csv}: once a second, JVM GC/heap/CPU/JIT counters and
 *       the effective video settings and window state;</li>
 *   <li>{@code phases.csv}: phase id to name.</li>
 * </ul>
 * Timestamps are {@link System#nanoTime()} relative to recording start.
 */
public final class GameplaySessionRecorder {
	public static final String DIR_PROPERTY = "mattmc.dev.recordDir";
	@Nullable private static final String DIR = System.getProperty(DIR_PROPERTY);
	public static final boolean ENABLED = DIR != null && !DIR.isBlank();

	/** Bound on buffered, unwritten longs per stream (~64 MiB) before rows are dropped. */
	private static final int MAX_BUFFERED_LONGS = 8 << 20;
	private static final int MAX_PHASE_DEPTH = 64;

	private static final long START_NANOS = System.nanoTime();
	/** Guards the row buffers shared with the render thread. */
	private static final Object LOCK = new Object();
	/** Serializes the writer thread and the shutdown flush. */
	private static final Object WRITE_LOCK = new Object();
	private static final Map<String, Integer> PHASE_IDS = new HashMap<>();
	private static final List<String> NEW_PHASE_NAMES = new ArrayList<>();

	@Nullable private static Thread renderThread;
	private static long tickSeq;
	private static long tickStart;
	private static long lastTickStart;
	private static boolean tickOpen;
	private static final int[] stackIds = new int[MAX_PHASE_DEPTH];
	private static final long[] stackStarts = new long[MAX_PHASE_DEPTH];
	private static final long[] stackChildNanos = new long[MAX_PHASE_DEPTH];
	private static int depth;
	// Vanilla profiler sections ("mc:<name>") use their own stack: they are not
	// guaranteed to nest with GraphicsFrameBenchmark phases. Their exclusive
	// times overlap benchmark-phase times and are read as a separate breakdown.
	private static final int[] profilerIds = new int[MAX_PHASE_DEPTH];
	private static final long[] profilerStarts = new long[MAX_PHASE_DEPTH];
	private static final long[] profilerChildNanos = new long[MAX_PHASE_DEPTH];
	private static int profilerDepth;
	private static final Map<String, String> PROFILER_NAMES = new HashMap<>();
	private static long[] phaseNanos = new long[256];
	private static int[] touched = new int[256];
	private static int touchedCount;

	private static LongRows ticks = new LongRows();
	private static LongRows frames = new LongRows();
	private static long droppedTicks;
	private static long droppedFrames;

	@Nullable private static FrameSchema frameSchema;
	private static long frameSeq;

	private GameplaySessionRecorder() {}

	static {
		if (ENABLED) {
			Thread writer = new Thread(GameplaySessionRecorder::writerLoop, "MattMC session recorder");
			writer.setDaemon(true);
			writer.start();
			Runtime.getRuntime().addShutdownHook(new Thread(GameplaySessionRecorder::flushQuietly, "MattMC session recorder flush"));
		}
	}

	// ----- render thread hooks -----

	static void beginTick() {
		if (!ENABLED) return;
		Thread thread = Thread.currentThread();
		if (renderThread == null) renderThread = thread;
		if (thread != renderThread) return;
		long now = System.nanoTime();
		lastTickStart = tickStart;
		tickStart = now;
		tickOpen = true;
		depth = 0;
		profilerDepth = 0;
	}

	static void endTick(long tickNanos) {
		if (!ENABLED || Thread.currentThread() != renderThread || !tickOpen) return;
		tickOpen = false;
		int fields = 5 + 2 * touchedCount;
		synchronized (LOCK) {
			if (ticks.size + fields > MAX_BUFFERED_LONGS) {
				droppedTicks++;
			} else {
				ticks.add(tickSeq);
				ticks.add(tickStart - START_NANOS);
				ticks.add(lastTickStart == 0L ? -1L : tickStart - lastTickStart);
				ticks.add(tickNanos);
				ticks.add(touchedCount);
				for (int i = 0; i < touchedCount; i++) {
					ticks.add(touched[i]);
					ticks.add(phaseNanos[touched[i]]);
				}
			}
		}
		for (int i = 0; i < touchedCount; i++) phaseNanos[touched[i]] = 0L;
		touchedCount = 0;
		tickSeq++;
	}

	static void beginPhase(String name) {
		if (Thread.currentThread() != renderThread || !tickOpen) return;
		if (depth >= MAX_PHASE_DEPTH) {
			depth++;
			return;
		}
		stackIds[depth] = phaseId(name);
		stackStarts[depth] = System.nanoTime();
		stackChildNanos[depth] = 0L;
		depth++;
	}

	static void endPhase() {
		if (Thread.currentThread() != renderThread || !tickOpen || depth == 0) return;
		depth--;
		if (depth >= MAX_PHASE_DEPTH) return;
		long inclusive = Math.max(0L, System.nanoTime() - stackStarts[depth]);
		int id = stackIds[depth];
		if (phaseNanos[id] == 0L) {
			if (touchedCount == touched.length) touched = Arrays.copyOf(touched, touched.length * 2);
			touched[touchedCount++] = id;
		}
		phaseNanos[id] += Math.max(0L, inclusive - stackChildNanos[depth]);
		if (depth > 0) stackChildNanos[depth - 1] += inclusive;
	}

	static void profilerPush(String name) {
		if (Thread.currentThread() != renderThread || !tickOpen) return;
		if (profilerDepth >= MAX_PHASE_DEPTH) {
			profilerDepth++;
			return;
		}
		profilerIds[profilerDepth] = phaseId(PROFILER_NAMES.computeIfAbsent(name, key -> "mc:" + key));
		profilerStarts[profilerDepth] = System.nanoTime();
		profilerChildNanos[profilerDepth] = 0L;
		profilerDepth++;
	}

	static void profilerPop() {
		if (Thread.currentThread() != renderThread || !tickOpen || profilerDepth == 0) return;
		profilerDepth--;
		if (profilerDepth >= MAX_PHASE_DEPTH) return;
		long inclusive = Math.max(0L, System.nanoTime() - profilerStarts[profilerDepth]);
		int id = profilerIds[profilerDepth];
		if (phaseNanos[id] == 0L) {
			if (touchedCount == touched.length) touched = Arrays.copyOf(touched, touched.length * 2);
			touched[touchedCount++] = id;
		}
		phaseNanos[id] += Math.max(0L, inclusive - profilerChildNanos[profilerDepth]);
		if (profilerDepth > 0) profilerChildNanos[profilerDepth - 1] += inclusive;
	}

	/** Forwards the client's vanilla profiler sections into the recorder while recording. */
	public static final net.minecraft.util.profiling.ProfilerFiller PROFILER = new net.minecraft.util.profiling.ProfilerFiller() {
		@Override public void startTick() {}
		@Override public void endTick() {}
		@Override public void push(String string) { profilerPush(string); }
		@Override public void push(java.util.function.Supplier<String> supplier) { profilerPush(supplier.get()); }
		@Override public void pop() { profilerPop(); }
		@Override public void popPush(String string) { profilerPop(); profilerPush(string); }
		@Override public void popPush(java.util.function.Supplier<String> supplier) { profilerPop(); profilerPush(supplier.get()); }
		@Override public void markForCharting(net.minecraft.util.profiling.metrics.MetricCategory metricCategory) {}
		@Override public void incrementCounter(String string, int i) {}
		@Override public void incrementCounter(java.util.function.Supplier<String> supplier, int i) {}
	};

	private static int phaseId(String name) {
		Integer id = PHASE_IDS.get(name);
		if (id != null) return id;
		int next = PHASE_IDS.size();
		PHASE_IDS.put(name, next);
		if (next >= phaseNanos.length) phaseNanos = Arrays.copyOf(phaseNanos, phaseNanos.length * 2);
		synchronized (LOCK) {
			NEW_PHASE_NAMES.add(name);
		}
		return next;
	}

	/**
	 * Records one completed Rust whole frame. {@code result} is the bridge's
	 * whole-frame submit result record; every long field (including nested
	 * records) becomes a column, so the recording follows the ABI as it grows.
	 */
	public static void recordFrame(
		long executeStart, long acquireStart, long acquireEnd, long submitStart, long submitEnd,
		long presentStart, long presentEnd, long frameId, long submissionId, Record result
	) {
		if (!ENABLED) return;
		FrameSchema schema = frameSchema;
		if (schema == null) {
			schema = FrameSchema.of(result.getClass());
			frameSchema = schema;
			synchronized (LOCK) {
				pendingFrameHeader = schema.header();
			}
		}
		int fields = 10 + schema.width();
		synchronized (LOCK) {
			if (frames.size + fields > MAX_BUFFERED_LONGS) {
				droppedFrames++;
				return;
			}
			frames.add(frameSeq++);
			frames.add(frameId);
			frames.add(submissionId);
			frames.add(executeStart - START_NANOS);
			frames.add(acquireStart - START_NANOS);
			frames.add(acquireEnd - START_NANOS);
			frames.add(submitStart - START_NANOS);
			frames.add(submitEnd - START_NANOS);
			frames.add(presentStart - START_NANOS);
			frames.add(presentEnd - START_NANOS);
			schema.append(result, frames);
		}
	}

	// ----- writer thread -----

	@Nullable private static String pendingFrameHeader;

	private static void writerLoop() {
		Path dir = Path.of(DIR);
		try {
			Files.createDirectories(dir);
			try (BufferedWriter tickOut = Files.newBufferedWriter(dir.resolve("ticks.csv"), StandardCharsets.UTF_8);
				 BufferedWriter frameOut = Files.newBufferedWriter(dir.resolve("frames.csv"), StandardCharsets.UTF_8);
				 BufferedWriter phaseOut = Files.newBufferedWriter(dir.resolve("phases.csv"), StandardCharsets.UTF_8);
				 BufferedWriter secondOut = Files.newBufferedWriter(dir.resolve("seconds.csv"), StandardCharsets.UTF_8)) {
				tickOut.write("tick,start_ns,interval_ns,duration_ns,phases\n");
				phaseOut.write("id,name\n");
				secondOut.write(SecondSampler.HEADER);
				SecondSampler seconds = new SecondSampler();
				Writers writers = new Writers(tickOut, frameOut, phaseOut, secondOut);
				long nextSecond = System.nanoTime();
				while (true) {
					synchronized (WRITE_LOCK) {
						drain(writers);
						long now = System.nanoTime();
						if (now >= nextSecond) {
							secondOut.write(seconds.sample(now - START_NANOS, droppedTicks, droppedFrames));
							secondOut.flush();
							nextSecond = now + 1_000_000_000L;
						}
					}
					Thread.sleep(250L);
				}
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		} catch (IOException error) {
			System.err.println("MattMC session recorder failed: " + error);
		}
	}

	private record Writers(BufferedWriter ticks, BufferedWriter frames, BufferedWriter phases, BufferedWriter seconds) {}

	@Nullable private static volatile Writers activeWriters;

	private static void drain(Writers writers) throws IOException {
		activeWriters = writers;
		LongRows tickRows;
		LongRows frameRows;
		String header;
		List<String> names;
		synchronized (LOCK) {
			tickRows = ticks;
			frameRows = frames;
			ticks = new LongRows();
			frames = new LongRows();
			header = pendingFrameHeader;
			pendingFrameHeader = null;
			names = new ArrayList<>(NEW_PHASE_NAMES);
			NEW_PHASE_NAMES.clear();
		}
		for (String name : names) {
			writers.phases.write(PHASE_IDS_SNAPSHOT.size() + "," + csv(name) + "\n");
			PHASE_IDS_SNAPSHOT.add(name);
		}
		writeTicks(tickRows, writers.ticks);
		if (header != null) writers.frames.write(header);
		FrameSchema schema = frameSchema;
		if (schema != null) writeFrames(frameRows, 10 + schema.width(), writers.frames);
		writers.phases.flush();
		writers.ticks.flush();
		writers.frames.flush();
	}

	/** Phase names in id order as written by the writer thread. */
	private static final List<String> PHASE_IDS_SNAPSHOT = new ArrayList<>();

	private static void writeTicks(LongRows rows, BufferedWriter out) throws IOException {
		StringBuilder line = new StringBuilder(256);
		int i = 0;
		while (i < rows.size) {
			line.setLength(0);
			line.append(rows.data[i]).append(',').append(rows.data[i + 1]).append(',')
				.append(rows.data[i + 2]).append(',').append(rows.data[i + 3]).append(',');
			int count = (int)rows.data[i + 4];
			i += 5;
			for (int p = 0; p < count; p++, i += 2) {
				if (p > 0) line.append(';');
				line.append(rows.data[i]).append(':').append(rows.data[i + 1]);
			}
			out.write(line.append('\n').toString());
		}
	}

	private static void writeFrames(LongRows rows, int width, BufferedWriter out) throws IOException {
		StringBuilder line = new StringBuilder(width * 6);
		for (int row = 0; row + width <= rows.size; row += width) {
			line.setLength(0);
			for (int c = 0; c < width; c++) {
				if (c > 0) line.append(',');
				line.append(rows.data[row + c]);
			}
			out.write(line.append('\n').toString());
		}
	}

	private static void flushQuietly() {
		Writers writers = activeWriters;
		if (writers == null) return;
		try {
			synchronized (WRITE_LOCK) {
				drain(writers);
			}
		} catch (IOException | RuntimeException ignored) {
			// Best effort at shutdown; the writer thread flushes every 250 ms.
		}
	}

	private static String csv(String value) {
		return value.indexOf(',') < 0 && value.indexOf('"') < 0 ? value : '"' + value.replace("\"", "\"\"") + '"';
	}

	/** A growable primitive buffer of row-major longs. */
	private static final class LongRows {
		long[] data = new long[4096];
		int size;

		void add(long value) {
			if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
			data[size++] = value;
		}
	}

	/** Flattens every long component of a record type (recursively) without boxing. */
	private static final class FrameSchema {
		private final List<String> names = new ArrayList<>();
		private final List<Node> roots = new ArrayList<>();

		/** A record component: a long leaf, or a nested record with children. */
		private record Node(MethodHandle getter, boolean leaf, List<Node> children, int leaves) {}

		static FrameSchema of(Class<?> type) {
			FrameSchema schema = new FrameSchema();
			schema.roots.addAll(schema.nodes(type, ""));
			return schema;
		}

		private List<Node> nodes(Class<?> type, String prefix) {
			List<Node> nodes = new ArrayList<>();
			RecordComponent[] components = type.getRecordComponents();
			if (components == null) return nodes;
			for (RecordComponent component : components) {
				Class<?> componentType = component.getType();
				String name = prefix + component.getName();
				try {
					Method accessor = component.getAccessor();
					accessor.setAccessible(true);
					MethodHandle getter = MethodHandles.lookup().unreflect(accessor);
					if (componentType == long.class) {
						names.add(name);
						nodes.add(new Node(getter.asType(MethodType.methodType(long.class, Object.class)), true, List.of(), 1));
					} else if (componentType.isRecord()) {
						List<Node> children = nodes(componentType, name + ".");
						int leaves = children.stream().mapToInt(Node::leaves).sum();
						nodes.add(new Node(getter.asType(MethodType.methodType(Object.class, Object.class)), false, children, leaves));
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Skip a component that cannot be read; the header omits it too.
				}
			}
			return nodes;
		}

		int width() {
			return names.size();
		}

		String header() {
			StringBuilder header = new StringBuilder(
				"frame,frame_id,submission_id,execute_start_ns,acquire_start_ns,acquire_end_ns,"
					+ "submit_start_ns,submit_end_ns,present_start_ns,present_end_ns");
			for (String name : names) header.append(',').append(name);
			return header.append('\n').toString();
		}

		void append(Object record, LongRows out) {
			appendNodes(roots, record, out);
		}

		private static void appendNodes(List<Node> nodes, @Nullable Object record, LongRows out) {
			for (Node node : nodes) {
				if (record == null) {
					for (int i = 0; i < node.leaves; i++) out.add(-1L);
					continue;
				}
				try {
					if (node.leaf) {
						out.add((long)node.getter.invokeExact(record));
					} else {
						appendNodes(node.children, (Object)node.getter.invokeExact(record), out);
					}
				} catch (Throwable error) {
					for (int i = 0; i < node.leaves; i++) out.add(-1L);
				}
			}
		}
	}

	/** Once-a-second JVM counters and effective client settings (read racily, display only). */
	private static final class SecondSampler {
		static final String HEADER = "t_ns,gc_count,gc_ms,heap_used_mb,heap_committed_mb,process_cpu_pct,"
			+ "system_cpu_pct,jit_ms,threads,dropped_ticks,dropped_frames,fps_counter,vsync,max_fps,"
			+ "render_distance,graphics_mode,window_w,window_h,fullscreen,screen,in_world\n";

		String sample(long t, long droppedTicks, long droppedFrames) {
			long gcCount = 0L;
			long gcMs = 0L;
			for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
				gcCount += Math.max(0L, gc.getCollectionCount());
				gcMs += Math.max(0L, gc.getCollectionTime());
			}
			var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
			double processCpu = -1.0;
			double systemCpu = -1.0;
			if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
				processCpu = os.getProcessCpuLoad() * 100.0;
				systemCpu = os.getCpuLoad() * 100.0;
			}
			var compilation = ManagementFactory.getCompilationMXBean();
			long jitMs = compilation != null && compilation.isCompilationTimeMonitoringSupported()
				? compilation.getTotalCompilationTime() : -1L;
			StringBuilder line = new StringBuilder(256).append(t).append(',').append(gcCount).append(',').append(gcMs)
				.append(',').append(memory.getUsed() >> 20).append(',').append(memory.getCommitted() >> 20)
				.append(',').append(String.format(java.util.Locale.ROOT, "%.1f,%.1f", processCpu, systemCpu))
				.append(',').append(jitMs).append(',').append(Thread.activeCount())
				.append(',').append(droppedTicks).append(',').append(droppedFrames);
			appendClientState(line);
			return line.append('\n').toString();
		}

		private static void appendClientState(StringBuilder line) {
			try {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft == null || minecraft.options == null || minecraft.getWindow() == null) {
					line.append(",,,,,,,,,,");
					return;
				}
				var options = minecraft.options;
				var window = minecraft.getWindow();
				line.append(',').append(minecraft.getFps())
					.append(',').append(options.enableVsync().get())
					.append(',').append(options.framerateLimit().get())
					.append(',').append(options.renderDistance().get())
					.append(',').append(options.graphicsMode().get())
					.append(',').append(window.getWidth())
					.append(',').append(window.getHeight())
					.append(',').append(window.isFullscreen())
					.append(',').append(minecraft.screen == null ? "none" : csv(minecraft.screen.getClass().getSimpleName()))
					.append(',').append(minecraft.level != null);
			} catch (RuntimeException error) {
				line.append(",,,,,,,,,,");
			}
		}
	}
}
