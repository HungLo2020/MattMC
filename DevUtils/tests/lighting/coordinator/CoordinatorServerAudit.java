package lightcoordinator;

import com.google.gson.GsonBuilder;
import java.lang.foreign.*;
import java.lang.reflect.Field;
import java.lang.ref.Reference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/** Scalar observations only: retains no engine, map, layer or world roots. */
public final class CoordinatorServerAudit {
    private static final AtomicLongArray calls = new AtomicLongArray(2);
    private static final AtomicLongArray phases = new AtomicLongArray(8);
    private static final AtomicLong peakSkySourceBatches = new AtomicLong();
    private static final AtomicReference<String> error = new AtomicReference<>();
    private static final AtomicBoolean reopenStarted = new AtomicBoolean(), reopenPassed = new AtomicBoolean();
    private static volatile Field metadataField, layerField;

    /** Runs only after the original saved-boundary assertion has succeeded.
     * The original audit source/assertions stay intact. All edits and barriers
     * use the actual main thread and threaded light engine; no policy substitute. */
    public static void afterAwait(Object value, String check) throws Exception {
        if (!Boolean.getBoolean("playerLoading.reopen") || !check.equals("boundary light survives reopen")
                || !reopenStarted.compareAndSet(false, true)) return;
        MinecraftServer server = (MinecraftServer)value;
        ServerLevel level = server.overworld();
        BlockPos lamp = new BlockPos(335, -55, 321), roof = new BlockPos(335, 100, 321);
        var saved = server.submit(() -> new net.minecraft.world.level.block.state.BlockState[]{
            level.getBlockState(lamp), level.getBlockState(roof)}).get(120, TimeUnit.SECONDS);
        boolean restored = false;
        try {
            edit(server, level, () -> {
                level.setBlock(lamp, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
            });
            await(server, () -> level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(lamp.east()) == 0
                && level.getChunkSource().getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(roof.below()) < 15);
            edit(server, level, () -> { level.setBlock(lamp, saved[0], 3); level.setBlock(roof, saved[1], 3); });
            restored = true;
            await(server, () -> level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(lamp.east()) >= 14
                && level.getChunkSource().getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(roof.below()) == 15);
            reopenPassed.set(true);
            System.out.println("COORDINATOR_REOPEN_EFFECTS removed/restored boundary emission and roof shadow PASS");
        } finally {
            if (!restored) edit(server, level, () -> { level.setBlock(lamp, saved[0], 3); level.setBlock(roof, saved[1], 3); });
        }
    }

    private static void edit(MinecraftServer server, ServerLevel level, Runnable action) throws Exception {
        var pending = server.submit(() -> {
            action.run();
            var light = level.getChunkSource().getLightEngine();
            light.propagateLightSources(new ChunkPos(20, 20));
            return light.waitForPendingTasks(20, 20);
        }).get(120, TimeUnit.SECONDS);
        pending.get(120, TimeUnit.SECONDS);
    }

    private static void await(MinecraftServer server, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < deadline) {
            if (server.submit(condition::getAsBoolean).get(120, TimeUnit.SECONDS)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Actual reopened lighting edits did not settle");
    }

    /** Observe the SAME original native counters through the actual retained
     * Level consumer. Reflection metadata retains no engine/world resources. */
    public static void observeLevel(Object level) {
        try {
            for (String channel : new String[]{"block", "sky"}) {
                Field field = level.getClass().getDeclaredField(channel);
                field.setAccessible(true);
                Object engine = field.get(level);
                if (engine == null) continue;
                Field coordinator = engine.getClass().getSuperclass().getDeclaredField("lightCoordinator");
                coordinator.setAccessible(true);
                Object actual = coordinator.get(engine);
                if (actual == null) throw new AssertionError("Actual level has no native coordinator");
                observe(actual);
            }
        } catch (Throwable failure) {
            error.compareAndSet(null, failure.toString());
        } finally {
            Reference.reachabilityFence(level);
        }
    }

    public static void observe(Object coordinator) {
        try {
            Field metadata = metadataField, layer = layerField;
            if (metadata == null) {
                metadata = coordinator.getClass().getDeclaredField("metadata");
                metadata.setAccessible(true);
                layer = coordinator.getClass().getDeclaredField("layer");
                layer.setAccessible(true);
                layerField = layer;
                metadataField = metadata;
            }
            int channel = switch (layer.get(coordinator).toString()) {
                case "BLOCK" -> 0;
                case "SKY" -> 1;
                default -> throw new AssertionError("Unexpected light layer");
            };
            MemorySegment header = (MemorySegment)metadata.get(coordinator);
            calls.incrementAndGet(channel);
            if (channel == 1) {
                long batches = Integer.toUnsignedLong((int)ValueLayout.JAVA_INT.varHandle().getAcquire(header, 36L));
                peakSkySourceBatches.accumulateAndGet(batches, Math::max);
            }
            for (int i = 0; i < 4; i++) {
                long value = (long)ValueLayout.JAVA_LONG.varHandle().getAcquire(header, 40L + i * Long.BYTES);
                phases.accumulateAndGet(channel * 4 + i, value, Math::max);
            }
        } catch (Throwable failure) {
            error.compareAndSet(null, failure.toString());
        } finally {
            Reference.reachabilityFence(coordinator);
        }
    }

    public static void initialize() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                boolean expected = Boolean.getBoolean("lightCoordinator.expectNative");
                boolean passed = error.get() == null;
                var result = new LinkedHashMap<String, Object>();
                for (int channel = 0; channel < 2; channel++) {
                    long[] values = new long[4];
                    for (int i = 0; i < 4; i++) values[i] = phases.get(channel * 4 + i);
                    result.put(channel == 0 ? "block" : "sky", Map.of("calls", calls.get(channel), "phases", values));
                    passed &= expected ? calls.get(channel) > 0 && Arrays.stream(values).allMatch(v -> v > 0)
                                       : calls.get(channel) == 0 && Arrays.stream(values).allMatch(v -> v == 0);
                }
                result.put("expectedNative", expected);
                result.put("peakSkySourceBatches", peakSkySourceBatches.get());
                passed &= expected ? peakSkySourceBatches.get() > 0 : peakSkySourceBatches.get() == 0;
                result.put("reopenEffects", reopenPassed.get());
                if (Boolean.getBoolean("playerLoading.reopen")) passed &= reopenPassed.get();
                result.put("observerError", error.get());
                result.put("passed", passed);
                Files.writeString(Path.of(System.getProperty("playerLoading.output"), "light-coordinator.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
            } catch (Throwable failure) {
                failure.printStackTrace();
            }
        }, "light-coordinator-audit-output"));
    }
}
