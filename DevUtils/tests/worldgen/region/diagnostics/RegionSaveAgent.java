package fallen;

import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.*;

/** Opt-in selected-chunk save discriminator. No barrier, array escape, extra
 * native owner or strong snapshot root; never use for acceptance timing. */
public final class RegionSaveAgent {
    private static final Object LOCK = new Object();
    private static final int CAPACITY = 65536;
    private static final String[] BUFFER = new String[CAPACITY];
    private static int buffered;
    private static long dropped;
    static { Runtime.getRuntime().addShutdownHook(new Thread(RegionSaveAgent::flush, "region-diagnostic-flush")); }
    private static final AtomicLong ORDER = new AtomicLong();
    private static final List<Tracked> SNAPSHOTS = new ArrayList<>();
    private static final List<ObjectIdentity> IDENTITIES = new ArrayList<>();
    private static final ConcurrentHashMap<String, List<Long>> TAPES = new ConcurrentHashMap<>();
    private static final Set<String> POSITIONS = Set.of("62,-46", "62,-47", "62,-48", "63,-48", "1,-1", "1,2", "2,2", "0,0");
    private record Tracked(long id, WeakReference<SerializableChunkData> value) {}
    private record ObjectIdentity(long id, WeakReference<Object> value) {}

    public static long identity(Object value) {
        if (value == null) return 0;
        synchronized (LOCK) {
            IDENTITIES.removeIf(entry -> entry.value.get() == null);
            for (var entry : IDENTITIES) if (entry.value.get() == value) return entry.id;
            long id = ORDER.incrementAndGet();
            IDENTITIES.add(new ObjectIdentity(id, new WeakReference<>(value)));
            return id;
        }
    }
    public static List<String> bufferedEvents() {
        synchronized (LOCK) { return List.copyOf(Arrays.asList(Arrays.copyOf(BUFFER, buffered))); }
    }
    public static void observe(Map<String, Object> row) {
        try { event(row); } catch (Throwable failure) { error(failure); }
    }

    private static boolean selected(ChunkPos pos) { return POSITIONS.contains(pos.x + "," + pos.z); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static Map<String, Object> bytes(byte[] bytes) throws Exception {
        var row = new TreeMap<String, Object>();
        row.put("present", true); row.put("length", bytes.length); row.put("sha256", hash(bytes));
        int nonzero = 0, minimum = 255, maximum = 0;
        for (byte b : bytes) { int value = b & 255; if (value != 0) nonzero++; minimum = Math.min(minimum, value); maximum = Math.max(maximum, value); }
        row.put("nonzeroBytes", nonzero); row.put("minimumByte", minimum); row.put("maximumByte", maximum);
        return row;
    }
    private static Map<String, Object> layer(DataLayer layer) throws Exception {
        if (layer == null) return Map.of("present", false);
        if (layer.getClass() != DataLayer.class) return Map.of("present", true, "unsupportedSubclass", layer.getClass().getName());
        // Public nibble reads do not export/invalidate its array or acquire an
        // additional native owner. Reconstruct bytes in observer-owned memory.
        byte[] values = new byte[2048];
        for (int i = 0; i < 4096; i++) values[i >> 1] |= (byte)(layer.get(i & 15, i >> 8, i >> 4 & 15) << ((i & 1) * 4));
        var row = bytes(values); row.put("empty", layer.isEmpty()); row.put("homogeneous", layer.isDefinitelyHomogenous()); row.put("layerIdentity", identity(layer));
        return row;
    }
    private static Map<String, Object> sections(SerializableChunkData data) throws Exception {
        var result = new TreeMap<String, Object>();
        for (var section : data.sectionData()) {
            if (!Set.of(2, 3, 4, 6).contains(section.y())) continue;
            result.put(section.y() + "/SkyLight", layer(section.skyLight()));
            result.put(section.y() + "/BlockLight", layer(section.blockLight()));
        }
        return result;
    }
    private static Map<String, Object> sections(CompoundTag data) throws Exception {
        var result = new TreeMap<String, Object>();
        if (data.get("sections") instanceof ListTag list) for (Tag element : list) {
            var section = (CompoundTag)element; int y = section.getByteOr("Y", (byte)-128);
            if (!Set.of(2, 3, 4, 6).contains(y)) continue;
            for (String name : List.of("SkyLight", "BlockLight")) {
                var value = section.getByteArray(name);
                result.put(y + "/" + name, value.isPresent() ? bytes(value.get()) : Map.of("present", false));
            }
        }
        return result;
    }
    private static void event(Map<String, Object> row) throws Exception {
        synchronized (LOCK) {
            long order = ORDER.incrementAndGet();
            row.put("order", order); row.put("nanoTime", System.nanoTime()); row.put("thread", Thread.currentThread().getName());
            if (Boolean.getBoolean("region.save.native")) row.put("nativeSequence", RegionNativeTrace.marker(order));
            // Serialize only immutable primitive/string metadata. Application
            // objects never enter this fixed buffer and no event performs I/O.
            if (buffered < CAPACITY) BUFFER[buffered++] = new com.google.gson.Gson().toJson(row);
            else dropped++;
        }
    }
    private static void flush() {
        try {
            if (Boolean.getBoolean("region.save.native")) RegionNativeTrace.flush();
            synchronized (LOCK) {
                Path output = Path.of(System.getProperty("audit.output"), "save-trace.jsonl");
                try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) {
                    for (int i = 0; i < buffered; i++) { writer.write(BUFFER[i]); writer.newLine(); }
                    writer.write(new com.google.gson.Gson().toJson(Map.of("event", "buffer-summary", "capacity", CAPACITY, "stored", buffered, "dropped", dropped)));
                    writer.newLine();
                }
            }
        } catch (Throwable failure) { failure.printStackTrace(); }
    }
    private static void error(Throwable failure) {
        try { event(new TreeMap<>(Map.of("event", "observer-error", "failure", failure.toString()))); }
        catch (Throwable ignored) { failure.printStackTrace(); }
    }
    private static long id(SerializableChunkData data) {
        synchronized (LOCK) {
            for (var tracked : SNAPSHOTS) if (tracked.value.get() == data) return tracked.id;
            return 0;
        }
    }
    public static void snapshot(Object value, Object world, Object chunk) {
        try {
            var level = (ServerLevel)world; var data = (SerializableChunkData)value;
            if (!level.dimension().location().toString().equals("minecraft:the_end") || !selected(data.chunkPos())) return;
            var callers = StackWalker.getInstance().walk(frames -> frames.filter(f -> f.getClassName().equals("net.minecraft.server.level.ChunkMap") || f.getClassName().startsWith("fullgeneration.")).map(f -> f.getClassName() + "." + f.getMethodName()).limit(5).toList());
            long identity;
            synchronized (LOCK) {
                SNAPSHOTS.removeIf(t -> t.value.get() == null);
                identity = ORDER.incrementAndGet(); SNAPSHOTS.add(new Tracked(identity, new WeakReference<>(data)));
            }
            var row = new TreeMap<String, Object>(); row.put("event", "snapshot"); row.put("snapshot", identity); row.put("chunk", data.chunkPos().x + "," + data.chunkPos().z);
            row.put("hasSkyLight", level.dimensionType().hasSkyLight()); row.put("status", data.chunkStatus().toString()); row.put("isLightOn", data.lightCorrect());
            row.put("callers", callers); row.put("actualSave", callers.contains("net.minecraft.server.level.ChunkMap.save")); row.put("sections", sections(data)); event(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void encoded(Object value, Object source) {
        try {
            var data = (SerializableChunkData)source; long identity = id(data); if (identity == 0) return;
            var encoded = (SerializableChunkData.Encoded)value; var row = new TreeMap<String, Object>(); row.put("event", "encoded"); row.put("snapshot", identity); row.put("chunk", data.chunkPos().x + "," + data.chunkPos().z);
            row.put("snapshotSections", sections(data)); row.put("failure", encoded.failure() == null ? "none" : encoded.failure().toString());
            if (encoded.tape() != null) {
                String digest = hash(encoded.tape()); TAPES.compute(digest, (key, old) -> { var ids = new ArrayList<Long>(old == null ? List.of() : old); ids.add(identity); return List.copyOf(ids); });
                row.put("tapeSha256", digest); row.put("encodedSections", sections(net.minecraft.nbt.NativeNbtRegionAccess.readTape(encoded.tape())));
            } else row.put("tapePresent", false);
            event(row);
        } catch (Throwable failure) { error(failure); }
    }
    public static void write(Object value, Object position, Object payload, String phase) {
        try {
            var storage = (RegionFileStorage)value; var pos = (ChunkPos)position;
            if (!storage.info().dimension().location().toString().equals("minecraft:the_end") || !selected(pos)) return;
            var row = new TreeMap<String, Object>(); row.put("event", phase); row.put("chunk", pos.x + "," + pos.z); row.put("storageType", storage.info().type());
            if (payload instanceof byte[] tape) {
                String digest = hash(tape); row.put("tapeSha256", digest); row.put("snapshots", TAPES.getOrDefault(digest, List.of()));
                if (phase.endsWith("enter")) row.put("sections", sections(net.minecraft.nbt.NativeNbtRegionAccess.readTape(tape)));
            } else if (payload instanceof CompoundTag tag) row.put("sections", sections(tag));
            else row.put("clear", true);
            event(row);
        } catch (Throwable failure) { error(failure); }
    }
}
