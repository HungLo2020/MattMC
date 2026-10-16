package net.minecraft.server.level;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** A disabled sky channel still captures original import arguments until POST. */
public final class VerifyCoordinatorImportLifetimes {
    private record Pending(Object tasks, WeakReference<ThreadedLevelLightEngine> engine,
            WeakReference<SectionPos> section, WeakReference<DataLayer> layer,
            VerifyThreadedLighting.Tasks dispatch, VerifyThreadedLighting.Tasks light,
            VerifyThreadedLighting.LightExecutor executor, ChunkTaskDispatcher dispatcher) implements AutoCloseable {
        public void close() { dispatcher.close(); executor.close(); }
    }
    private static Pending enqueue() throws Exception {
        var fixture = new VerifyThreadedLighting.Fixture(false, false);
        var section = SectionPos.of(0, 1, 0);
        var layer = new DataLayer(3);
        fixture.engine.queueSectionData(LightLayer.SKY, section, layer);
        fixture.transport();
        if (fixture.pending() != 1) throw new AssertionError("Disabled-channel import was not deferred");
        return new Pending(VerifyThreadedLighting.field(fixture.engine, "lightTasks"), new WeakReference<>(fixture.engine),
            new WeakReference<>(section), new WeakReference<>(layer), fixture.dispatch, fixture.light, fixture.executor, fixture.dispatcher);
    }
    private static void update(WeakReference<ThreadedLevelLightEngine> reference) throws Exception {
        ThreadedLevelLightEngine engine = reference.get();
        if (engine == null) throw new AssertionError("Pending import lost its original engine");
        VerifyThreadedLighting.invoke(engine, "runUpdate", new Class<?>[0]);
        Reference.reachabilityFence(engine);
    }
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Pending pending = enqueue()) {
            for (int i = 0; i < 5; i++) { System.gc(); Thread.sleep(20); }
            if (pending.engine.get() == null || pending.section.get() == null || pending.layer.get() == null)
                throw new AssertionError("Pending disabled import lost an original capture");
            System.out.println("IMPORT_LIFETIME pending-engine=true pending-section=true pending-layer=true");
            update(pending.engine);
            for (int i = 0; i < 150 && (pending.engine.get() != null || pending.section.get() != null || pending.layer.get() != null); i++) {
                System.gc(); Thread.sleep(20);
            }
            if (pending.engine.get() != null || pending.section.get() != null || pending.layer.get() != null)
                throw new AssertionError("Retired disabled import retained an original capture or temporary entry");
            System.out.println("IMPORT_LIFETIME retired-engine=false retired-section=false retired-layer=false");
            Reference.reachabilityFence(pending.tasks);
        }
    }
}
