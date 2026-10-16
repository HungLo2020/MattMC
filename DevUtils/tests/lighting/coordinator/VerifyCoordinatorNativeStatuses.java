package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;

/** Current-only typed admission; portable status effects remain a separate oracle. */
public final class VerifyCoordinatorNativeStatuses {
    private static Long2ByteMap states(VerifyThreadedLighting.Fixture fixture, LightLayer channel) throws Exception {
        Object storage = VerifyThreadedLighting.field(fixture.engine.getLayerListener(channel), "storage");
        return (Long2ByteMap)VerifyThreadedLighting.field(storage, "sectionStates");
    }
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            var section = SectionPos.of(0, 1, 0);
            fixture.engine.updateSectionStatus(section, false); fixture.transport();
            Object tasks = VerifyThreadedLighting.field(fixture.engine, "lightTasks");
            Object[] positions = (Object[])VerifyThreadedLighting.field(tasks, "references");
            Object[] contexts = (Object[])VerifyThreadedLighting.field(tasks, "contexts");
            if (fixture.pending() != 1 || positions[0] != section || contexts[0] != fixture.engine)
                throw new AssertionError("Status was not deferred as an actual typed capture row");
            for (LightLayer channel : LightLayer.values()) if (states(fixture, channel).containsKey(section.asLong()))
                throw new AssertionError("Status committed before PRE");
            fixture.update();
            for (LightLayer channel : LightLayer.values()) if ((states(fixture, channel).get(section.asLong()) & 32) == 0)
                throw new AssertionError("Native level status did not reach actual " + channel + " owner");
            if (fixture.pending() != 0 || positions[0] != null || contexts[0] != null)
                throw new AssertionError("Status captures did not retire at POST");
            System.out.println("NATIVE_STATUS typed=true deferred=true both-owners=true retired=true");
        }
    }
}
