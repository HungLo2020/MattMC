package net.minecraft.world.level.lighting;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** More than one retirement batch from one actual late-released map. */
public final class VerifyDelayedLightMapBatchRetirement {
    record Scene(LevelLightEngine engine, List<WeakReference<DataLayer>> layers, Object[] oldMap) {}
    static Scene scene(int columns) throws Exception {
        var source = new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x, int z) { return null; }
            public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
        };
        var engine = new LevelLightEngine(source, true, false);
        var values = new ArrayList<DataLayer>(); var weak = new ArrayList<WeakReference<DataLayer>>();
        for (int i = 0; i < columns; i++) {
            int x = i * 4; var layer = new DataLayer(6); values.add(layer); weak.add(new WeakReference<>(layer));
            engine.setLightEnabled(new ChunkPos(x, 0), true); engine.updateSectionStatus(SectionPos.of(x + 1, 1, 0), false);
            engine.queueSectionData(LightLayer.BLOCK, SectionPos.of(x, 1, 0), layer);
        }
        engine.runLightUpdates();
        for (int i = 0; i < columns; i++) if (engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(i * 4, 1, 0)) != values.get(i))
            throw new AssertionError("Missing actual initial map entry " + i);
        Object storage = VerifyDelayedLightMapRetirement.field(engine.getLayerListener(LightLayer.BLOCK), "storage");
        Object[] held = {VerifyDelayedLightMapRetirement.field(storage, "visibleSectionData")};
        for (int i = 0; i < columns; i++) engine.updateSectionStatus(SectionPos.of(i * 4 + 1, 1, 0), true);
        engine.runLightUpdates();
        for (int i = 0; i < columns; i++) {
            if (engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(i * 4, 1, 0)) != null) throw new AssertionError("Section remains visible " + i);
            engine.setLightEnabled(new ChunkPos(i * 4, 0), false);
        }
        return new Scene(engine, weak, held);
    }
    static int remaining(List<WeakReference<DataLayer>> layers) {
        int count = 0; for (var weak : layers) if (!weak.refersTo(null)) count++; return count;
    }
    static int collect(List<WeakReference<DataLayer>> layers) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (remaining(layers) != 0 && System.nanoTime() < deadline) { System.gc(); Thread.sleep(25); }
        return remaining(layers);
    }
    static int pending(Object map) throws Exception {
        Object pins = VerifyDelayedLightMapRetirement.field(map, "pins");
        return (int)ValueLayout.JAVA_INT.varHandle().getAcquire((MemorySegment)VerifyDelayedLightMapRetirement.field(pins, "pending"), 0L);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); int columns = Integer.parseInt(args[0]); var scene = scene(columns);
        System.gc(); Thread.sleep(100);
        if (remaining(scene.layers) != columns) throw new AssertionError("Held map lost original layer aliases");
        scene.oldMap[0] = null; int remaining = collect(scene.layers); Object map = VerifyDelayedLightMapRetirement.nativeMap(scene.engine);
        System.out.println("DELAYED_BATCH columns=" + columns + " idle_remaining=" + remaining + " " + VerifyDelayedLightMapRetirement.roots(map));
        if (map != null && remaining != 0) {
            var drain = map.getClass().getDeclaredMethod("drainForCoordinator"); drain.setAccessible(true); drain.invoke(map);
            System.out.println("DELAYED_BATCH after_one_drain_remaining=" + collect(scene.layers) + " " + VerifyDelayedLightMapRetirement.roots(map));
            int snapshot = pending(map), batches = (int)((Integer.toUnsignedLong(snapshot) + 63) / 64);
            for (int i = 0; i < batches; i++) drain.invoke(map);
            System.out.println("DELAYED_BATCH diagnostic_snapshot=" + Integer.toUnsignedLong(snapshot) + " batches=" + batches
                + " after_snapshot_drain_remaining=" + collect(scene.layers) + " " + VerifyDelayedLightMapRetirement.roots(map));
        }
        Reference.reachabilityFence(scene.engine);
    }
}
