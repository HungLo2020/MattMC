package net.minecraft.server.level;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Original queued-section VM monitor observed through public deferred imports. */
public final class VerifyCoordinatorImportMonitor {
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (LightLayer channel : LightLayer.values()) {
            try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
                var section = SectionPos.of(0, 1, 0);
                var input = new DataLayer(6);
                fixture.engine.queueSectionData(channel, section, input);
                fixture.transport();
                Object storage = VerifyThreadedLighting.field(fixture.engine.getLayerListener(channel), "storage");
                Map<?, ?> queued = (Map<?, ?>)VerifyThreadedLighting.field(storage, "queuedSections");
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread worker = new Thread(() -> {
                    try { VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]); }
                    catch (Throwable error) { failure.set(error); }
                }, "original-import-monitor");
                worker.setDaemon(true);
                synchronized (queued) {
                    worker.start();
                    long until = System.nanoTime() + 2_000_000_000L;
                    while (worker.isAlive() && worker.getState() != Thread.State.BLOCKED && System.nanoTime() < until) Thread.sleep(5);
                    if (worker.getState() != Thread.State.BLOCKED || fixture.pending() != 1)
                        throw new AssertionError("Deferred import bypassed original queued-section monitor: " + worker.getState(), failure.get());
                    if (queued.containsKey(section.asLong())) throw new AssertionError("Import committed before monitor acquisition");
                    System.out.println("IMPORT_MONITOR " + channel + " held=true blocked=true committed=false");
                }
                worker.join(5000);
                if (worker.isAlive() || failure.get() != null) throw new AssertionError("Import did not finish after monitor release", failure.get());
                if (fixture.pending() != 0 || queued.get(section.asLong()) != input)
                    throw new AssertionError("Released import changed identity or pending work");
                System.out.println("IMPORT_MONITOR " + channel + " held=false pending=0 alias=true");
            }
        }
    }
}
