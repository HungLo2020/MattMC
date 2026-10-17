package fallen;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.util.NativeLibraryLoader;

/** Opt-in counterpart of the separate native diagnostic overlay. Its only
 * retained values are method handles, primitive metadata and immutable strings. */
public final class RegionNativeTrace {
    private static final MethodHandle BINDING = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_region_trace_binding",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle MARKER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_region_trace_marker",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle SNAPSHOT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_region_trace_snapshot",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle DUMP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_region_trace_dump",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private record Context(long level, String mode, long frame, long cursor) {}
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static long id(Object value) { return RegionSaveAgent.identity(value); }
    private static Object field(Object value, String name) throws Exception {
        Class<?> type = value.getClass();
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(value); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void error(Throwable failure) { RegionSaveAgent.observe(new TreeMap<>(Map.of("event", "observer-error", "failure", failure.toString()))); }
    public static long marker(long sequence) {
        try { return (long)MARKER.invokeExact(sequence); }
        catch (Throwable failure) { throw new IllegalStateException("diagnostic marker failed", failure); }
    }
    public static void binding(Object pointer, Object coordinator) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(24, 8);
            Object storage = field(coordinator, "storage");
            int enabled = RegionLightTrace.isEndStorage(storage) ? 1 : 0;
            int status = (int)BINDING.invokeExact((MemorySegment)pointer, enabled, out);
            if (status != 0) throw new IllegalStateException("diagnostic binding " + status);
            if (enabled == 0) return;
            var row = new TreeMap<String,Object>(); row.put("event", "native-binding");
            row.put("coordinatorIdentity", id(coordinator)); row.put("storageIdentity", id(storage));
            row.put("ownerIncarnation", out.getAtIndex(ValueLayout.JAVA_LONG, 0)); row.put("nativeEngine", out.getAtIndex(ValueLayout.JAVA_LONG, 1));
            row.put("sky", out.getAtIndex(ValueLayout.JAVA_LONG, 2)); RegionSaveAgent.observe(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void updateStatus(Object level, long key, boolean empty) {
        try {
            if (!RegionLightTrace.selectedColumn(key) || !RegionLightTrace.isEndEngine(field(level,"block")) && !RegionLightTrace.isEndEngine(field(level,"sky"))) return;
            var row = new TreeMap<String,Object>(); row.put("event", "native-update-status"); row.put("levelIdentity", id(level));
            row.put("sectionKey", key); row.put("empty", empty); RegionSaveAgent.observe(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void command(Object level, int kind, long key, boolean value) {
        try {
            if (!RegionLightTrace.isEndEngine(field(level,"block")) && !RegionLightTrace.isEndEngine(field(level,"sky"))) return;
            if (kind != 3 && !RegionLightTrace.selectedColumn(key)) return;
            var row = new TreeMap<String,Object>(); row.put("event", "native-command"); row.put("levelIdentity", id(level));
            row.put("kind", kind); row.put("sectionKey", key); row.put("value", value); RegionSaveAgent.observe(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void resume(Object level, long frame, Object tasks, long cursor) {
        try {
            if (!RegionLightTrace.isEndEngine(field(level,"block")) && !RegionLightTrace.isEndEngine(field(level,"sky"))) return;
            CONTEXT.set(new Context(id(level), frame == 0 ? "task" : "direct", frame, cursor));
            var row = new TreeMap<String,Object>(); row.put("event", "native-resume"); row.put("levelIdentity", id(level));
            row.put("mode", frame == 0 ? "task" : "direct"); row.put("blockEngineIdentity", id(field(level,"block")));
            row.put("skyEngineIdentity", id(field(level,"sky"))); RegionSaveAgent.observe(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void resumeEnd() { CONTEXT.remove(); }
    public static void adopted(Object adopted, Object source, Object pointer) {
        if (CONTEXT.get() == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(24, 8);
            int status = (int)SNAPSHOT.invokeExact((MemorySegment)pointer, out);
            if (status != 0) throw new IllegalStateException("diagnostic publication identity " + status);
            Context context = CONTEXT.get();
            var row = new TreeMap<String,Object>(); row.put("event", "native-adopted");
            row.put("publication", out.getAtIndex(ValueLayout.JAVA_LONG, 0)); row.put("ownerIncarnation", out.getAtIndex(ValueLayout.JAVA_LONG, 1));
            row.put("nativeEngine", out.getAtIndex(ValueLayout.JAVA_LONG, 2)); row.put("nativeMapIdentity", id(adopted)); row.put("sourceNativeMapIdentity", id(source));
            row.put("levelIdentity", context == null ? 0 : context.level); row.put("mode", context == null ? "missing-context" : context.mode);
            RegionSaveAgent.observe(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void flush() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment meta = arena.allocate(32, 8);
            long count = (long)DUMP.invokeExact(MemorySegment.NULL, 0L, meta);
            if (count < 0) throw new IllegalStateException("diagnostic dump " + count);
            MemorySegment rows = arena.allocate(count * 16 * 8, 8);
            long copied = (long)DUMP.invokeExact(rows, count, meta);
            if (copied != count) throw new IllegalStateException("diagnostic capture still changing " + count + "/" + copied);
            Path output = Path.of(System.getProperty("audit.output"), "native-trace.jsonl");
            try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) {
                for (long i = 0; i < count; i++) {
                    long[] row = new long[16]; for (int j = 0; j < 16; j++) row[j] = rows.getAtIndex(ValueLayout.JAVA_LONG, i * 16 + j);
                    writer.write(new com.google.gson.Gson().toJson(row)); writer.newLine();
                }
            }
            var summary = new TreeMap<String,Object>(); summary.put("capacity", meta.getAtIndex(ValueLayout.JAVA_LONG,0));
            summary.put("stored", count); summary.put("dropped", meta.getAtIndex(ValueLayout.JAVA_LONG,2));
            summary.put("sequence", meta.getAtIndex(ValueLayout.JAVA_LONG,3)); summary.put("width",16);
            Files.writeString(output.resolveSibling("native-buffer-summary.json"), new com.google.gson.Gson().toJson(summary), StandardOpenOption.CREATE_NEW);
        }
    }
}
