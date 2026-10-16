package fullgeneration;

import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.FlatLevelSource;

/** Complete stock request workload. The flat fixture reduces unchanged terrain
 * work; actual tasks, futures, lighting, constructors and publication stay real. */
public final class GenerationCostAudit {
    static final AtomicBoolean STARTED = new AtomicBoolean();
    static final Path OUTPUT = Path.of(System.getProperty("audit.output"));
    static volatile long sink;
    static final int WARM = Integer.getInteger("generation.costWarm", 128);
    static final int SAMPLES = Integer.getInteger("generation.costSamples", 15);
    static final int BATCH = Integer.getInteger("generation.costBatch", 16);
    static final int LOADED = Integer.getInteger("generation.costLoaded", 100_000);

    public static void capture(Object input) {
        if (STARTED.compareAndSet(false, true)) new Thread(() -> {
            MinecraftServer server = (MinecraftServer)input;
            try { run(server); }
            catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(OUTPUT.resolve("failure.txt"), failure.toString()); }
                catch (Exception ignored) {}
                server.halt(false);
            }
        }, "generation-complete-cost").start();
    }

    static ChunkPos position(int index) { return new ChunkPos(64 + index % 32, -96 + index / 32); }
    static LevelChunk consume(CompletableFuture<ChunkResult<ChunkAccess>> future, ChunkPos pos) {
        ChunkAccess chunk = future.join().orElse(null);
        GenerationAudit.consume(chunk, pos);
        return (LevelChunk)chunk;
    }
    static long compiler() { return ManagementFactory.getCompilationMXBean().getTotalCompilationTime(); }
    static long gc() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x -> Math.max(0, x.getCollectionTime())).sum(); }
    static Map<String, Object> settleCompilation() throws InterruptedException {
        long start = System.nanoTime(), stable = start, previous = compiler(), previousGc = gc();
        boolean quiet = false;
        while (System.nanoTime() - start < 15_000_000_000L) {
            Thread.sleep(100);
            long current = compiler(), currentGc = gc();
            if (current != previous || currentGc != previousGc) stable = System.nanoTime();
            previous = current; previousGc = currentGc;
            if (System.nanoTime() - stable >= 1_000_000_000L) { quiet = true; break; }
        }
        return Map.of("untimed_wait_ns", System.nanoTime() - start, "one_second_compilation_gc_quiet", quiet);
    }
    static long readLoaded(ServerChunkCache source, ChunkPos pos, CompletableFuture<ChunkResult<ChunkAccess>> expected, LevelChunk chunk, int count) {
        long checksum = 0;
        for (int i = 0; i < count; i++) {
            var value = source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
            if (value != expected || value.join().orElse(null) != chunk) throw new AssertionError("loaded future/chunk changed");
            checksum += chunk.getPos().toLong();
        }
        sink = checksum;
        return checksum;
    }

    static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        var source = level.getChunkSource();
        if (!(source.getGenerator() instanceof FlatLevelSource)) throw new AssertionError("flat fixture was not applied");
        boolean reopen = Boolean.getBoolean("generation.reopen");
        List<CompletableFuture<ChunkResult<ChunkAccess>>> requests = new ArrayList<>();
        long[] before = GenerationAudit.nativeStats();
        List<Map<String, Object>> requestSamples = new ArrayList<>();
        // Fixed warmup coordinates/work on every side. Never choose how much
        // fresh work to warm by elapsed time, which would change later inputs.
        server.submit(() -> {
            for (int i = 0; i < WARM; i++) {
                ChunkPos pos = position(i);
                source.addTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
                var future = source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
                consume(future, pos); requests.add(future);
            }
        }).get(300, TimeUnit.SECONDS);
        // Let asynchronous compilation from the fixed warmup drain. No extra
        // requests/coordinates are selected by elapsed time, and this wait is
        // outside every measurement. A timeout remains an explicit noisy result.
        var warmupSettle = settleCompilation();
        for (int sample = 0; sample < SAMPLES; sample++) {
            int offset = WARM + sample * BATCH;
            long submitted = System.nanoTime();
            Map<String, Object> row = server.submit(() -> {
                long compilation = compiler(), collection = gc(), checksum = 0;
                long start = System.nanoTime();
                for (int i = offset; i < offset + BATCH; i++) {
                    ChunkPos pos = position(i);
                    source.addTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
                    var future = source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
                    LevelChunk chunk = consume(future, pos);
                    checksum += chunk.getPos().toLong(); requests.add(future);
                }
                long elapsed = System.nanoTime() - start;
                sink = checksum;
                var result = new LinkedHashMap<String, Object>();
                result.put("ns_per_full_request", elapsed / (double)BATCH);
                result.put("compiler_ms", compiler() - compilation);
                result.put("gc_ms", gc() - collection);
                result.put("checksum", checksum);
                return result;
            }).get(300, TimeUnit.SECONDS);
            row.put("observer_submission_and_batch_ns", System.nanoTime() - submitted);
            requestSamples.add(row);
        }
        var loaded = server.submit(() -> {
            ChunkPos pos = position(WARM);
            var expected = requests.get(WARM);
            LevelChunk chunk = consume(expected, pos);
            long until = System.nanoTime() + 2_000_000_000L;
            do {
                readLoaded(source, pos, expected, chunk, 1000);
            } while (System.nanoTime() < until);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int sample = 0; sample < SAMPLES; sample++) {
                long compilation = compiler(), collection = gc();
                long start = System.nanoTime();
                long checksum = readLoaded(source, pos, expected, chunk, LOADED);
                long elapsed = System.nanoTime() - start;
                sink = checksum;
                rows.add(Map.of("ns_per_loaded_request", elapsed / (double)LOADED, "compiler_ms", compiler() - compilation,
                    "gc_ms", gc() - collection, "checksum", checksum));
            }
            return rows;
        }).get(180, TimeUnit.SECONDS);
        var hot = server.submit(() -> {
            var holder = GenerationAudit.holder(level, position(WARM));
            long until = System.nanoTime() + 2_000_000_000L;
            do { GenerationAudit.readHolder(holder, 10_000); } while (System.nanoTime() < until);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < SAMPLES; i++) {
                long compilation = compiler(), collection = gc(), start = System.nanoTime();
                long checksum = GenerationAudit.readHolder(holder, 100_000);
                rows.add(Map.of("ns_per_holder_read", (System.nanoTime() - start) / 100_000.0,
                    "compiler_ms", compiler() - compilation, "gc_ms", gc() - collection, "checksum", checksum));
            }
            return rows;
        }).get(120, TimeUnit.SECONDS);
        // All save-form observation is outside request timers. This also proves
        // the fixture is actually empty, rather than a silently rejected preset.
        var fingerprints = server.submit(() -> {
            var rows = new LinkedHashMap<String, Map<String, String>>();
            for (int i = 0; i < requests.size(); i++) {
                ChunkPos pos = position(i);
                LevelChunk chunk = consume(requests.get(i), pos);
                for (var section : chunk.getSections()) if (!section.hasOnlyAir()) throw new AssertionError("flat fixture contains non-air blocks");
                rows.put(pos.toString(), FullGenAudit.fingerprint(SerializableChunkData.copyOf(level, chunk).write(), FullGenAudit.entities(level, pos)));
            }
            return rows;
        }).get(180, TimeUnit.SECONDS);
        long[] after = GenerationAudit.nativeStats();
        if (!server.submit(() -> server.saveEverything(true, true, true)).get(180, TimeUnit.SECONDS)) throw new AssertionError("save failed");
        var row = Map.of("dimension", "minecraft:overworld", "native_before", before, "native_after", after);
        var report = new LinkedHashMap<String, Object>();
        report.put("success", true); report.put("rows", List.of(row));
        report.put("phase", reopen ? "disk-loaded-to-FULL" : "fresh-to-FULL");
        report.put("settings", Map.of("warmup_requests", WARM, "samples", SAMPLES, "requests_per_sample", BATCH, "loaded_iterations", LOADED));
        report.put("warmup_settle", warmupSettle);
        report.put("request_samples", requestSamples); report.put("loaded_samples", loaded); report.put("holder_samples", hot);
        report.put("fingerprints", fingerprints); report.put("request_count", requests.size());
        report.put("scope", "stock main-thread FULL request through actual executor/IO/stages/construction/publication and output consumption; observer submission wait recorded separately; fingerprints/save outside timers");
        Files.writeString(OUTPUT.resolve("complete.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("GENERATION_COST_COMPLETE " + report.get("phase") + " requests=" + requests.size());
        server.halt(false);
    }
}
