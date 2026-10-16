package fullgeneration;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.management.ManagementFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/** Early connected stock request probe. Fresh work and loaded reuse are separate.
 * This bounded probe is not the final FULL/resume/failure/cancellation campaign. */
public final class GenerationAudit {
    static final AtomicBoolean STARTED = new AtomicBoolean();
    static final Path OUTPUT = Path.of(System.getProperty("audit.output"));
    static volatile long consumed;

    public static void capture(Object server) {
        if (STARTED.compareAndSet(false, true)) new Thread(() -> {
            try { run((MinecraftServer)server); }
            catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(OUTPUT.resolve("failure.txt"), failure.toString()); }
                catch (Exception ignored) {}
                ((MinecraftServer)server).halt(false);
            }
        }, "generation-control-probe").start();
    }

    static long[] nativeStats() throws Exception {
        try {
            return (long[])Class.forName("net.minecraft.server.level.NativeChunkGeneration").getMethod("statistics").invoke(null);
        } catch (ClassNotFoundException exception) { return new long[0]; }
    }

    // Optional on the immutable original references. Outside request/read timers.
    static long[] nativeStageStats() throws Exception {
        try {
            var method = Class.forName("net.minecraft.server.level.NativeChunkGeneration").getDeclaredMethod("stageStatistics");
            method.setAccessible(true);
            return (long[])method.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException absentOnOriginal) { return new long[0]; }
    }

    static void consume(ChunkAccess chunk, ChunkPos pos) {
        if (!(chunk instanceof LevelChunk) || chunk.getPersistedStatus() != ChunkStatus.FULL || !chunk.getPos().equals(pos))
            throw new AssertionError("request returned a partial or wrong FULL chunk");
        consumed += chunk.getBlockState(pos.getMiddleBlockPosition(64)).hashCode();
    }

    static GenerationChunkHolder holder(ServerLevel level, ChunkPos pos) {
        try {
            var lookup = level.getChunkSource().chunkMap.getClass().getDeclaredMethod("getUpdatingChunkIfPresent", long.class);
            lookup.setAccessible(true);
            return (GenerationChunkHolder)lookup.invoke(level.getChunkSource().chunkMap, pos.toLong());
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
    }

    static long readHolder(GenerationChunkHolder holder, int iterations) {
        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            ChunkAccess value = holder.getChunkIfPresentUnchecked(ChunkStatus.SPAWN);
            if (value == null || value.getPersistedStatus() != ChunkStatus.FULL) throw new AssertionError("hot holder read lost its completed chunk");
            checksum += value.getPos().toLong();
        }
        consumed = checksum;
        return checksum;
    }

    static Map<String, Object> region(MinecraftServer server, ServerLevel level, boolean reopen) throws Exception {
        ChunkPos pos = new ChunkPos(64, -48);
        var source = level.getChunkSource();
        long[] before = nativeStats();
        long[] stageBefore = nativeStageStats();
        long start = System.nanoTime();
        boolean orderedFeatures = Boolean.getBoolean("generation.orderedFeatures");
        if (orderedFeatures) FullGenAudit.decorateInOrder(server, level, List.of(pos));
        var first = server.submit(() -> {
            source.addTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
            var future = source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
            consume(future.join().orElse(null), pos);
            return future;
        }).get(300, TimeUnit.SECONDS);
        long freshNanos = System.nanoTime() - start;
        var details = server.submit(() -> {
            ChunkAccess chunk = first.join().orElse(null);
            consume(chunk, pos);
            var second = source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
            if (first != second || second.join().orElse(null) != chunk) throw new AssertionError("loaded request lost future/chunk identity");
            List<Integer> aliases = new ArrayList<>();
            try {
                var lookup = source.chunkMap.getClass().getDeclaredMethod("getUpdatingChunkIfPresent", long.class);
                lookup.setAccessible(true);
                GenerationChunkHolder holder = (GenerationChunkHolder)lookup.invoke(source.chunkMap, pos.toLong());
                var slots = holder.getAllFutures();
                var shared = slots.getFirst().getSecond();
                var ids = new IdentityHashMap<Object, Integer>();
                for (var pair : slots) aliases.add(ids.computeIfAbsent(pair.getSecond(), ignored -> ids.size()));
                for (int i = 0; i < slots.size() - 1; i++) {
                    var value = slots.get(i).getSecond().join().orElse(null);
                    if (!(value instanceof ImposterProtoChunk imposter) || imposter.getWrapped() != chunk)
                        throw new AssertionError("pre-FULL slots lost the actual wrapped FULL chunk");
                    if (!reopen && slots.get(i).getSecond() != shared)
                        throw new AssertionError("fresh replacement lost shared imposter future identity");
                }
                if (shared == slots.getLast().getSecond()) throw new AssertionError("FULL slot was replaced by imposter");
                if (slots.getLast().getSecond() != first) throw new AssertionError("FULL request did not return the holder slot");
            } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
            return Map.of("fingerprints", FullGenAudit.fingerprint(SerializableChunkData.copyOf(level, chunk).write(), FullGenAudit.entities(level, pos)),
                "future_aliases", aliases);
        }).get(60, TimeUnit.SECONDS);
        int iterations = Integer.getInteger("generation.iterations", 2500);
        server.submit(() -> {
            for (int i = 0; i < 1000; i++) consume(source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true).join().orElse(null), pos);
        }).get(60, TimeUnit.SECONDS);
        List<Double> samples = new ArrayList<>();
        for (int sample = 0; sample < Integer.getInteger("generation.samples", 9); sample++) {
            samples.add(server.submit(() -> {
                long begin = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    var future = source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
                    if (future != first) throw new AssertionError("loaded future identity changed inside sample");
                    consume(future.join().orElse(null), pos);
                }
                return (System.nanoTime() - begin) / (double)iterations;
            }).get(60, TimeUnit.SECONDS));
        }
        // A real live-consumer path also matters: request timing alone can hide
        // a per-block holder lookup regression behind stage/kernel work.
        List<Map<String, Object>> holderSamples = server.submit(() -> {
            GenerationChunkHolder holder = holder(level, pos);
            long deadline = System.nanoTime() + 2_000_000_000L;
            do { readHolder(holder, 10_000); } while (System.nanoTime() < deadline);
            List<Map<String, Object>> result = new ArrayList<>();
            var compiler = ManagementFactory.getCompilationMXBean();
            for (int index = 0; index < 15; index++) {
                long compile = compiler.getTotalCompilationTime();
                long begin = System.nanoTime();
                long checksum = readHolder(holder, 100_000);
                long elapsed = System.nanoTime() - begin;
                result.add(Map.of("ns_per_read", elapsed / 100_000.0, "compiler_ms", compiler.getTotalCompilationTime() - compile, "checksum", checksum));
            }
            return result;
        }).get(60, TimeUnit.SECONDS);
        var row = new LinkedHashMap<String, Object>();
        row.put("dimension", level.dimension().location().toString());
        row.put("phase", reopen ? "disk-loaded-request-to-FULL" : "fresh-request-to-FULL");
        row.put("request_shape", orderedFeatures ? "ordered FEATURES margin then FULL; entire prepass timed" : "direct FULL request");
        row.put("request_and_consumption_ns", freshNanos);
        row.put("loaded_reuse_ns_per_request", samples);
        row.put("hot_holder_read", holderSamples);
        row.putAll(details);
        row.put("native_before", before);
        row.put("native_after", nativeStats());
        long[] stageAfter = nativeStageStats();
        row.put("native_stage_before", stageBefore);
        row.put("native_stage_after", stageAfter);
        if (Boolean.getBoolean("generation.requireNativeStageControl")) {
            if (stageBefore.length != 4 || stageAfter.length != 4)
                throw new AssertionError("actual server has no native stage controller");
            if (!reopen && (stageAfter[0] - stageBefore[0] < 2 || stageAfter[1] - stageBefore[1] < 2
                    || stageAfter[2] - stageBefore[2] < 2))
                throw new AssertionError("actual FULL request did not traverse multiple native-controlled stages");
            if (stageAfter[3] != stageBefore[3]) throw new AssertionError("successful FULL request reported fatal");
        }
        row.put("identity", "same FULL future/chunk; fresh replacement shares one pre-FULL future; disk-loaded aliases recorded separately");
        System.out.println("GENERATION_PROBE " + row.get("dimension") + " " + row.get("phase") + " ns=" + freshNanos);
        return row;
    }

    static void run(MinecraftServer server) throws Exception {
        boolean reopen = Boolean.getBoolean("generation.reopen");
        List<String> selected = Arrays.asList(System.getProperty("generation.dimensions", "minecraft:the_end").split(","));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (selected.contains(level.dimension().location().toString())) rows.add(region(server, level, reopen));
        }
        if (rows.size() != selected.size()) throw new AssertionError("requested dimension missing");
        if (!server.submit(() -> server.saveEverything(true, true, true)).get(120, TimeUnit.SECONDS))
            throw new AssertionError("save failed");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(OUTPUT.resolve("complete.json"), gson.toJson(Map.of("success", true, "rows", rows, "native_final", nativeStats())));
        System.out.println("GENERATION_PROBE_COMPLETE");
        server.halt(false);
    }
}
