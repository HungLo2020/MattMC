package net.minecraft.world.level.lighting;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
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

/** Delays an actual old published map's GC until after the last engine operation. */
public final class VerifyDelayedLightMapRetirement {
    static SectionPos TARGET, NEIGHBOR;
    static ChunkPos COLUMN;
    record Scene(LevelLightEngine engine, WeakReference<DataLayer> layer, Object[] oldMap) {}
    static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException absent) {}
        }
        throw new NoSuchFieldException(name);
    }
    static Scene scene() throws Exception {
        var source = new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x, int z) { return null; }
            public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
        };
        var engine = new LevelLightEngine(source, true, false); var layer = new DataLayer(6);
        engine.setLightEnabled(COLUMN, true); engine.updateSectionStatus(NEIGHBOR, false);
        engine.queueSectionData(LightLayer.BLOCK, TARGET, layer); engine.runLightUpdates();
        if (engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(TARGET) != layer) throw new AssertionError("Missing actual publication");
        Object storage = field(engine.getLayerListener(LightLayer.BLOCK), "storage");
        Object[] held = {field(storage, "visibleSectionData")};
        engine.updateSectionStatus(NEIGHBOR, true); engine.runLightUpdates();
        if (engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(TARGET) != null) throw new AssertionError("Section did not retire");
        engine.setLightEnabled(COLUMN, false);
        return new Scene(engine, new WeakReference<>(layer), held);
    }
    static void releaseOldMap(Scene scene) { scene.oldMap[0] = null; }
    static boolean collected(WeakReference<?> reference) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        // refersTo observes weak state without acquiring a temporary strong root.
        while (!reference.refersTo(null) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(25); }
        return reference.refersTo(null);
    }
    static Object nativeMap(LevelLightEngine engine) throws Exception {
        Object storage = field(engine.getLayerListener(LightLayer.BLOCK), "storage");
        try { return field(field(storage, "updatingSectionData"), "nativeMap"); }
        catch (NoSuchFieldException frozen) { return null; }
    }
    static String roots(Object map) throws Exception {
        if (map == null) return "native=false";
        Object pins = field(map, "pins");
        int roots;
        synchronized (pins) { roots = ((Map<?, ?>)field(pins, "identities")).size(); }
        MemorySegment pending = (MemorySegment)field(pins, "pending");
        int retirements = (int)ValueLayout.JAVA_INT.varHandle().getAcquire(pending, 0L);
        return "native=true VM_identities=" + roots + " pending=" + retirements;
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        TARGET = SectionPos.of(0, 1, 0); NEIGHBOR = SectionPos.of(1, 1, 0); COLUMN = new ChunkPos(0, 0);
        var scene = scene();
        System.gc(); Thread.sleep(100);
        if (scene.layer.refersTo(null)) throw new AssertionError("Held actual old publication lost its layer");
        releaseOldMap(scene);
        boolean idleCollected = collected(scene.layer);
        Object map = nativeMap(scene.engine);
        System.out.println("DELAYED_MAP idle_collected=" + idleCollected + " " + roots(map));
        if (map != null && !idleCollected) {
            var drain = map.getClass().getDeclaredMethod("drainForCoordinator"); drain.setAccessible(true); drain.invoke(map);
            System.out.println("DELAYED_MAP after_one_explicit_drain_collected=" + collected(scene.layer) + " " + roots(map));
        }
        Reference.reachabilityFence(scene.engine);
    }
}
