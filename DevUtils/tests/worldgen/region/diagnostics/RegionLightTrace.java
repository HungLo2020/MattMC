package fallen;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** Selected live reads and existing mutation boundaries. Only immutable
 * metadata/weak identities survive a hook. Stop after the first save-read loss. */
public final class RegionLightTrace {
    private static final Set<String> POSITIONS = Set.of("62,-46", "62,-47", "62,-48", "63,-48", "1,-1", "1,2", "2,2", "0,0");
    private record Store(String dimension, String layer) {}
    private record Engine(long storage, String dimension) {}
    private record Seen(String state, long layer, boolean actualSave) {}
    private static final Map<Long, Store> STORES = new ConcurrentHashMap<>();
    private static final Map<Long, Engine> ENGINES = new ConcurrentHashMap<>();
    private static final Map<Long, String> LEVELS = new ConcurrentHashMap<>();
    private static final Map<Long, Long> MAPS = new ConcurrentHashMap<>();
    private static final Map<Long, Long> NATIVE_MAPS = new ConcurrentHashMap<>();
    private static final Map<Long, Long> LISTENERS = new ConcurrentHashMap<>();
    private static final Map<String, Seen> READS = new ConcurrentHashMap<>();
    private static final AtomicBoolean STOP = new AtomicBoolean();

    private static long id(Object value) { return RegionSaveAgent.identity(value); }
    private static boolean end(String dimension) { return "minecraft:the_end".equals(dimension); }
    public static boolean isEndStorage(Object value) { Store meta = STORES.get(id(value)); return meta != null && end(meta.dimension); }
    public static boolean isEndEngine(Object value) { Engine meta = ENGINES.get(id(value)); return meta != null && end(meta.dimension); }
    public static boolean selectedColumn(long key) { return column(key); }
    private static boolean column(long key) { return POSITIONS.contains(SectionPos.x(key) + "," + SectionPos.z(key)); }
    private static boolean section(long key) { return column(key) && Set.of(2, 3, 4, 6).contains(SectionPos.y(key)); }
    private static String dimension(Object getter) {
        var world = ((LightChunkGetter)getter).getLevel();
        return world instanceof ServerLevel level ? level.dimension().location().toString() : "unsupported:" + world.getClass().getName();
    }
    private static Map<String, Object> base(String event) { return new TreeMap<>(Map.of("event", event)); }
    private static void log(Map<String, Object> row) { RegionSaveAgent.observe(row); }
    private static void error(Throwable failure) { log(new TreeMap<>(Map.of("event", "observer-error", "failure", failure.toString()))); }
    private static List<String> callers() {
        return StackWalker.getInstance().walk(frames -> frames.filter(f -> f.getClassName().startsWith("net.minecraft.") || f.getClassName().startsWith("fullgeneration."))
            .map(f -> f.getClassName() + "." + f.getMethodName()).limit(12).toList());
    }
    private static boolean copying(List<String> frames) { return frames.contains("net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf"); }
    private static boolean save(List<String> frames) { return frames.contains("net.minecraft.server.level.ChunkMap.save"); }
    private static Map<String, Object> location(long key) {
        var row = new TreeMap<String, Object>(); row.put("chunk", SectionPos.x(key) + "," + SectionPos.z(key)); row.put("section", SectionPos.y(key)); return row;
    }
    private static Map<String, Object> state(Object value) {
        var row = new TreeMap<String, Object>(); row.put("layerIdentity", id(value));
        if (value == null) { row.put("state", "null"); return row; }
        var layer = (DataLayer)value;
        if (layer.getClass() != DataLayer.class) { row.put("state", "unsupported-subclass"); return row; }
        if (layer.isDefinitelyHomogenous()) {
            int sample = layer.defaultValueForNativeLight(); row.put("state", sample == 0 ? "homogeneous-zero" : "homogeneous-nonzero"); row.put("defaultValue", sample); return row;
        }
        // A genuine public read can lazily acquire its normal cached read
        // projection. Never export an array, clone an owner or retain the layer.
        for (int i = 0; i < 4096; i++) {
            int sample = layer.get(i & 15, i >> 8, i >> 4 & 15);
            if (sample != 0) { row.put("state", "allocated-nonzero"); row.put("firstNonzeroIndex", i); row.put("sample", sample); return row; }
        }
        row.put("state", "allocated-zero"); return row;
    }
    public static void storageCreated(Object storage, Object getter, Object layer, Object updating, Object visible) {
        try {
            long identity = id(storage); String dimension = dimension(getter); STORES.put(identity, new Store(dimension, layer.toString()));
            MAPS.put(id(updating), identity); MAPS.put(id(visible), identity);
            if (!end(dimension) || STOP.get()) return;
            var row = base("live-storage-binding"); row.put("storageIdentity", identity); row.put("dimension", dimension); row.put("lightLayer", layer.toString());
            row.put("updatingMapIdentity", id(updating)); row.put("visibleMapIdentity", id(visible)); log(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void mapCreated(Object map, Object nativeMap) { if (!STOP.get()) NATIVE_MAPS.put(id(map), id(nativeMap)); }
    public static void engineCreated(Object engine, Object getter, Object storage) {
        try {
            long identity = id(engine); String dimension = dimension(getter); ENGINES.put(identity, new Engine(id(storage), dimension));
            if (!end(dimension) || STOP.get()) return;
            var row = base("live-engine-binding"); row.put("engineIdentity", identity); row.put("storageIdentity", id(storage)); row.put("dimension", dimension); log(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void levelCreated(Object level, Object getter) {
        try { LEVELS.put(id(level), dimension(getter)); } catch (Throwable failure) { error(failure); }
    }
    public static void listener(Object listener, Object level, Object layer) {
        try {
            if (STOP.get() || !end(LEVELS.get(id(level)))) return;
            long identity = id(listener); if (LISTENERS.put(identity, id(level)) != null) return;
            var row = base("live-listener-binding"); row.put("engineIdentity", identity); row.put("levelEngineIdentity", id(level)); row.put("lightLayer", layer.toString()); log(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void sourceRead(Object value, Object storage, Object map, long key, String stage) {
        try {
            if (STOP.get() || !section(key)) return;
            long identity = id(storage); Store meta = STORES.get(identity); if (meta == null || !end(meta.dimension)) return;
            var frames = callers(); if (!copying(frames)) return;
            var row = base("live-" + stage); row.putAll(location(key)); row.put("storageIdentity", identity); row.put("lightLayer", meta.layer);
            row.put("mapIdentity", id(map)); row.put("nativeMapIdentity", NATIVE_MAPS.getOrDefault(id(map), 0L)); row.put("actualSave", save(frames)); row.put("callers", frames); row.putAll(state(value)); log(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void engineRead(Object value, Object engine, Object position) {
        try {
            long key = ((SectionPos)position).asLong(); if (STOP.get() || !section(key)) return;
            long identity = id(engine); Engine meta = ENGINES.get(identity); if (meta == null || !end(meta.dimension)) return;
            var frames = callers(); if (!copying(frames)) return;
            var availability = state(value); String state = (String)availability.get("state"); boolean actualSave = save(frames);
            var row = base("live-engine-read"); row.putAll(location(key)); row.put("engineIdentity", identity); row.put("levelEngineIdentity", LISTENERS.getOrDefault(identity, 0L)); row.put("storageIdentity", meta.storage);
            row.put("lightLayer", STORES.get(meta.storage).layer); row.put("actualSave", actualSave); row.put("callers", frames); row.putAll(availability); log(row);
            String readKey = identity + ":" + key;
            Seen before = actualSave ? READS.get(readKey) : READS.put(readKey, new Seen(state, id(value), false));
            if (before != null && !before.actualSave && before.state.endsWith("nonzero") && actualSave && (state.equals("null") || state.equals("homogeneous-zero") || state.equals("allocated-zero")) && STOP.compareAndSet(false, true)) {
                var transition = base("first-live-loss"); transition.putAll(row); transition.put("event", "first-live-loss"); transition.put("previousState", before.state); transition.put("previousLayerIdentity", before.layer); transition.put("stop", "No further live-light observations after this first same-engine harness-to-save transition"); log(transition);
            }
        } catch (Throwable failure) { error(failure); }
    }
    public static void published(Object storage, Object nativeMap, Object visible) {
        try {
            if (STOP.get()) return; long identity = id(storage); Store meta = STORES.get(identity); if (meta == null || !end(meta.dimension)) return;
            MAPS.put(id(visible), identity); NATIVE_MAPS.put(id(visible), id(nativeMap));
            var row = base("live-publish"); row.put("storageIdentity", identity); row.put("lightLayer", meta.layer); row.put("mapIdentity", id(visible)); row.put("nativeMapIdentity", id(nativeMap)); log(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void boundary(Object source, Object storage, long key, int flag, Object value, String event) {
        try {
            if (STOP.get() || !(event.contains("retain") ? column(key) : section(key))) return;
            long identity = storage == null ? id(source) : id(storage); Store meta = STORES.get(identity);
            if (meta == null) { identity = MAPS.getOrDefault(id(source), 0L); meta = STORES.get(identity); }
            if (meta == null || !end(meta.dimension)) return;
            var row = base("live-" + event); row.putAll(location(key)); row.put("sourceIdentity", id(source)); row.put("storageIdentity", identity); row.put("lightLayer", meta.layer); row.put("flag", flag); row.put("valueNull", value == null); row.put("valueIdentity", id(value)); row.put("callers", callers()); log(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void updateChunk(Object engine, Object position, String phase) {
        try {
            var pos = (ChunkPos)position; long key = SectionPos.getZeroNode(pos.x, pos.z);
            if (STOP.get() && !Boolean.getBoolean("region.save.order") || !column(key) || !end(LEVELS.get(id(engine)))) return;
            var row = base("live-updateChunkStatus-" + phase); row.put("chunk", pos.x + "," + pos.z); row.put("levelEngineIdentity", id(engine)); row.put("kind", "enqueue boundary, not proof of task execution"); row.put("callers", callers()); log(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void coordinator(Object source, Object storage, int operation, long a, long b, String phase) {
        try {
            if (STOP.get() || !(operation == 1 || operation == 2 || operation == 0 && section(a))) return;
            Store meta = STORES.get(id(storage)); if (meta == null || !end(meta.dimension)) return;
            var row = base("live-coordinator-" + phase); row.put("coordinatorIdentity", id(source)); row.put("storageIdentity", id(storage)); row.put("lightLayer", meta.layer); row.put("operation", operation); row.put("a", a); row.put("b", b); if (operation == 0) row.putAll(location(a)); log(row);
        } catch (Throwable failure) { error(failure); }
    }
}
