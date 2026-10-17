package net.minecraft.world.level.lighting;

import java.lang.ref.WeakReference;
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

/** A genuine custom layer can refer back to its engine. A cleanup must not root that cycle. */
public final class VerifyLightMapCleanupCycle {
    static final class Layer extends DataLayer {
        final LevelLightEngine engine;
        Layer(LevelLightEngine engine) { super(6); this.engine = engine; }
    }
    record Scene(WeakReference<LevelLightEngine> engine, WeakReference<DataLayer> layer) {}
    static Scene scene() {
        var source = new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x, int z) { return null; }
            public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
        };
        var engine = new LevelLightEngine(source, true, false); var layer = new Layer(engine);
        engine.setLightEnabled(new ChunkPos(0, 0), true); engine.updateSectionStatus(SectionPos.of(1, 1, 0), false);
        engine.queueSectionData(LightLayer.BLOCK, SectionPos.of(0, 1, 0), layer); engine.runLightUpdates();
        if (engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(0, 1, 0)) != layer) throw new AssertionError("Actual custom layer missing");
        return new Scene(new WeakReference<>(engine), new WeakReference<>(layer));
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); var scene = scene();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((!scene.engine.refersTo(null) || !scene.layer.refersTo(null)) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(25); }
        System.out.println("MAP_CLEANUP_CYCLE engine_collected=" + scene.engine.refersTo(null) + " layer_collected=" + scene.layer.refersTo(null));
    }
}
