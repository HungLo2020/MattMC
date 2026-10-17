package net.minecraft.world.level.lighting;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeLightSaveCustodyTest {
    private static SectionPos TARGET, NEIGHBOR;
    private static ChunkPos COLUMN;
    private static LightChunkGetter SOURCE;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        TARGET = SectionPos.of(0, 1, 0); NEIGHBOR = SectionPos.of(1, 1, 0); COLUMN = new ChunkPos(0, 0);
        SOURCE = new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x, int z) { return null; }
            public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
        };
    }


    private static LevelLightEngine retired(DataLayer layer) {
        var engine = new LevelLightEngine(SOURCE, true, false);
        publish(engine, layer);
        engine.updateSectionStatus(NEIGHBOR, true);
        engine.runLightUpdates();
        assertNull(engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(TARGET), "propagation still retires the empty section");
        return engine;
    }
    private static void publish(LevelLightEngine engine, DataLayer layer) {
        engine.setLightEnabled(COLUMN, true);
        engine.updateSectionStatus(NEIGHBOR, false);
        engine.queueSectionData(LightLayer.BLOCK, TARGET, layer);
        engine.runLightUpdates();
        assertSame(layer, engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(TARGET));
    }
    @Test void actualNeighborRetirementKeepsLastPublishedSaveIdentityUntilColumnRetires() {
        var layer = new DataLayer(9); var engine = retired(layer);
        assertSame(layer, engine.dataLayerForSave(LightLayer.BLOCK, TARGET));
        assertEquals(9, engine.dataLayerForSave(LightLayer.BLOCK, TARGET).get(0, 0, 0));
        engine.setLightEnabled(COLUMN, false);
        assertNull(engine.dataLayerForSave(LightLayer.BLOCK, TARGET));
    }
    @Test void laterPublicationSupersedesOlderRetiredDataWithoutAnEarlyByteCopy() {
        var first = new DataLayer(3); var engine = retired(first);
        var second = new DataLayer(7); publish(engine, second);
        assertSame(second, engine.dataLayerForSave(LightLayer.BLOCK, TARGET));
        engine.updateSectionStatus(NEIGHBOR, true); engine.runLightUpdates();
        assertSame(second, engine.dataLayerForSave(LightLayer.BLOCK, TARGET));
        second.fill(5);
        assertEquals(5, engine.dataLayerForSave(LightLayer.BLOCK, TARGET).get(0, 0, 0), "genuine aliases still mutate the actual primary identity");
    }
    @Test void unpublishedCowClearIsTheActualSaveSourceWhenItsSectionRetires() {
        var engine = new LevelLightEngine(SOURCE, true, false); var original = new DataLayer(9); publish(engine, original);
        var storage = ((BlockLightEngine)engine.getLayerListener(LightLayer.BLOCK)).storage;
        var writable = storage.getDataLayerToWrite(TARGET.asLong()); writable.fill(0);
        assertNotSame(original, writable); assertEquals(9, original.get(0, 0, 0));
        engine.updateSectionStatus(NEIGHBOR, true); engine.runLightUpdates();
        assertNull(engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(TARGET));
        var saved = engine.dataLayerForSave(LightLayer.BLOCK, TARGET);
        assertSame(writable, saved, "save must use actual removed COW identity, not the older visible layer");
        assertTrue(saved.isEmpty(), "serialization must omit the cleared field instead of restoring stale light");
    }
    @Test void disabledColumnRetirementCannotBeResurrectedByReenableBeforePublication() {
        var engine = new LevelLightEngine(SOURCE, true, false); var layer = new DataLayer(9); publish(engine, layer);
        var block = (BlockLightEngine)engine.getLayerListener(LightLayer.BLOCK); var storage = block.storage;
        engine.setLightEnabled(COLUMN, false); engine.updateSectionStatus(NEIGHBOR, true);
        storage.markNewInconsistencies(block);
        assertSame(layer, block.getDataLayerData(TARGET), "the real prior visible publication still exists");
        engine.setLightEnabled(COLUMN, true); storage.swapSectionMap();
        assertNull(block.getDataLayerData(TARGET));
        assertNull(engine.dataLayerForSave(LightLayer.BLOCK, TARGET), "a new enabled column must not adopt a layer actually retired while disabled");
    }
    @Test void escapedArrayAliasRemainsTheActualSaveSourceAndCopyIsIndependent() {
        byte[] bytes = new byte[2048]; bytes[0] = 0x34;
        var layer = new DataLayer(bytes); var engine = retired(layer);
        bytes[0] = 0x65;
        var selected = engine.dataLayerForSave(LightLayer.BLOCK, TARGET);
        assertSame(layer, selected); assertEquals(5, selected.get(0, 0, 0));
        var copy = selected.copy(); bytes[0] = 0x21;
        assertEquals(5, copy.get(0, 0, 0)); assertEquals(1, selected.get(0, 0, 0));
    }
    @Test void virtualEmptyAndCopyEffectsKeepTheirOrderAndMayRetireTheColumnThenThrow() {
        var calls = new ArrayList<String>();
        final class CustomLayer extends DataLayer {
            Runnable action;
            CustomLayer() { super(11); }
            @Override public boolean isEmpty() { calls.add("isEmpty"); return super.isEmpty(); }
            @Override public DataLayer copy() { calls.add("copy"); action.run(); throw new IllegalStateException("original custom copy"); }
        }
        var layer = new CustomLayer(); var engine = retired(layer);
        layer.action = () -> { engine.setLightEnabled(COLUMN, false); calls.add("retired"); };
        calls.clear();
        var selected = engine.dataLayerForSave(LightLayer.BLOCK, TARGET);
        assertSame(layer, selected); assertFalse(selected.isEmpty());
        var failure = assertThrows(IllegalStateException.class, selected::copy);
        assertEquals("original custom copy", failure.getMessage());
        assertEquals(List.of("isEmpty", "copy", "retired"), calls);
        assertNull(engine.dataLayerForSave(LightLayer.BLOCK, TARGET));
        assertEquals(11, selected.get(0, 0, 0), "original VM reference survives native retirement and failure");
    }
    private record WeakScene(LevelLightEngine engine, WeakReference<DataLayer> layer) {}
    private static WeakScene weakScene() {
        var layer = new DataLayer(6); return new WeakScene(retired(layer), new WeakReference<>(layer));
    }
    @Test void heldEngineKeepsRetiredLayerOnlyUntilItsRealColumnRetirement() throws Exception {
        var scene = weakScene();
        System.gc(); Thread.sleep(50);
        assertNotNull(scene.layer.get(), "native save custody retains the real unobserved identity");
        scene.engine.setLightEnabled(COLUMN, false);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (scene.layer.get() != null && System.nanoTime() < deadline) { System.gc(); Thread.sleep(25); }
        assertNull(scene.layer.get(), "holding the engine must not keep a retired column's layer alive");
        assertNull(scene.engine.dataLayerForSave(LightLayer.BLOCK, TARGET));
    }
}
