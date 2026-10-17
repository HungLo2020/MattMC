package net.minecraft.server.level;

import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Actual public import producer and actual VM resource roots, Current only. */
public final class VerifyCoordinatorIdentityBindings {
    private static Object storage(VerifyThreadedLighting.Fixture fixture, LightLayer channel) throws Exception {
        return VerifyThreadedLighting.field(fixture.engine.getLayerListener(channel), "storage");
    }
    private static Object pins(VerifyThreadedLighting.Fixture fixture, LightLayer channel) throws Exception {
        Object map = VerifyThreadedLighting.field(VerifyThreadedLighting.field(storage(fixture, channel), "updatingSectionData"), "nativeMap");
        return VerifyThreadedLighting.field(map, "pins");
    }
    private static void queue(VerifyThreadedLighting.Fixture fixture, DataLayer layer) throws Exception {
        for (LightLayer channel : LightLayer.values()) for (int y = 1; y <= 2; y++)
            fixture.engine.queueSectionData(channel, SectionPos.of(0, y, 0), layer);
        fixture.transport();
        VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]);
    }
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            Object resources = pins(fixture, LightLayer.BLOCK);
            if (resources != pins(fixture, LightLayer.SKY)) throw new AssertionError("Level engines split actual identity resources");
            fixture.engine.retainData(new ChunkPos(0, 0), true);
            DataLayer input = new DataLayer(3);
            Map<?, ?> identities = (Map<?, ?>)VerifyThreadedLighting.field(resources, "identities");
            for (int mode = 0; mode < 3; mode++) {
                if (mode == 1) input.getData()[0] = (byte)0x55;
                if (mode == 2) input.fill(7);
                queue(fixture, input);
                Object identity = identities.get(input);
                if (identity == null || ((Number)VerifyThreadedLighting.field(identity, "references")).intValue() != 1)
                    throw new AssertionError("Repeated imports prepared duplicate actual VM binding slots");
                for (LightLayer channel : LightLayer.values()) {
                    Map<?, ?> queued = (Map<?, ?>)VerifyThreadedLighting.field(storage(fixture, channel), "queuedSections");
                    if (queued.size() != 2) throw new AssertionError("Actual entry reuse changed queued membership");
                    for (int y = 1; y <= 2; y++) if (queued.get(SectionPos.of(0, y, 0).asLong()) != input)
                        throw new AssertionError("Actual entry reuse changed public layer identity");
                }
                System.out.println("IDENTITY_BINDING mode=" + mode + " shared=true slots=1 aliases=true");
            }
            queue(fixture, null);
            if (identities.containsKey(input)) throw new AssertionError("Final native entry retirement retained its VM binding");
            System.out.println("IDENTITY_BINDING retired=true");
        }
    }
}
