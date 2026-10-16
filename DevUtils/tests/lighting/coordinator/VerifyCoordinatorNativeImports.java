package net.minecraft.server.level;

import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Actual typed row/capture admission; portable behavior and failure controls remain separate. */
public final class VerifyCoordinatorNativeImports {
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            var section = SectionPos.of(0, 1, 0);
            var input = new DataLayer(new byte[2048]);
            fixture.engine.queueSectionData(LightLayer.BLOCK, section, input);
            fixture.transport();
            Object tasks = VerifyThreadedLighting.field(fixture.engine, "lightTasks");
            Object[] positions = (Object[])VerifyThreadedLighting.field(tasks, "references");
            Object[] contexts = (Object[])VerifyThreadedLighting.field(tasks, "contexts");
            Object[] payloads = (Object[])VerifyThreadedLighting.field(tasks, "payloads");
            if (fixture.pending() != 1 || positions[0] != section || contexts[0] != fixture.engine || payloads[0] != input)
                throw new AssertionError("Import was not an actual typed row with original capture roots");
            Object storage = VerifyThreadedLighting.field(fixture.engine.getLayerListener(LightLayer.BLOCK), "storage");
            Object map = VerifyThreadedLighting.field(VerifyThreadedLighting.field(storage, "updatingSectionData"), "nativeMap");
            Object pins = VerifyThreadedLighting.field(map, "pins");
            Map<?, ?> identities = (Map<?, ?>)VerifyThreadedLighting.field(pins, "identities");
            if (identities.containsKey(input)) throw new AssertionError("Fallible import binding prepared before PRE");
            input.fill(7);
            VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]);
            Map<?, ?> queued = (Map<?, ?>)VerifyThreadedLighting.field(storage, "queuedSections");
            if (fixture.pending() != 0 || queued.get(section.asLong()) != input || input.get(0, 0, 0) != 7)
                throw new AssertionError("Typed import lost current source identity/generation");
            if (positions[0] != null || contexts[0] != null || payloads[0] != null)
                throw new AssertionError("Typed import did not retire original captures");
            System.out.println("NATIVE_IMPORT typed=true binding-at-PRE=true live-fill=7 original-alias=true retired=true");
        }
    }
}
