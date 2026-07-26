package net.sodium.client.render.chunk.translucent_sorting.trigger;

import net.minecraft.core.SectionPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeDirectTriggersTest {
    @Test
    void directDistanceTriggerFiresAfterNativeThreshold() {
        try (NativeDirectTriggers triggers = NativeDirectTriggers.create()) {
            SectionPos section = SectionPos.of(0, 0, 0);
            assertFalse(triggers.integrateSection(section,
                    movement(8.0, 8.0, 8.0, 8.0, 8.0, 8.0)));
            assertEquals(1, triggers.getDirectTriggerCount());

            ArrayList<Long> triggered = new ArrayList<>();
            assertEquals(0, triggers.processTriggers(
                    movement(8.0, 8.0, 8.0, 8.95, 8.0, 8.0), triggered::add));
            assertTrue(triggered.isEmpty());

            assertEquals(1, triggers.processTriggers(
                    movement(8.95, 8.0, 8.0, 9.05, 8.0, 8.0), triggered::add));
            assertEquals(section.asLong(), triggered.get(0));
            assertEquals(1, triggers.getDirectTriggerCount());
        }
    }

    @Test
    void nativeDirectTriggerRemovalUsesSectionPosition() {
        try (NativeDirectTriggers triggers = NativeDirectTriggers.create()) {
            SectionPos section = SectionPos.of(0, 0, 0);
            triggers.integrateSection(section, movement(8.0, 8.0, 8.0, 8.0, 8.0, 8.0));
            triggers.removeSection(section.asLong());

            ArrayList<Long> triggered = new ArrayList<>();
            assertEquals(0, triggers.processTriggers(
                    movement(8.0, 8.0, 8.0, 18.0, 8.0, 8.0), triggered::add));
            assertTrue(triggered.isEmpty());
            assertEquals(0, triggers.getDirectTriggerCount());
        }
    }

    @Test
    void nativeDirectTriggerIntegrationCanCatchUpImmediately() {
        try (NativeDirectTriggers triggers = NativeDirectTriggers.create()) {
            assertTrue(triggers.integrateSection(SectionPos.of(0, 0, 0),
                    movement(8.0, 8.0, 8.0, 9.0, 8.0, 8.0)));
            assertEquals(1, triggers.getDirectTriggerCount());
        }
    }

    @Test
    void nativeDirectTriggerCatchupFromSectionCenterStaysFinite() {
        try (NativeDirectTriggers triggers = NativeDirectTriggers.create()) {
            assertTrue(triggers.integrateSection(SectionPos.of(0, 0, 0),
                    movement(8.0, 8.0, 8.0, 24.0, 8.0, 8.0)));
            assertEquals(1, triggers.getDirectTriggerCount());
        }
    }

    @Test
    void nativeDirectTriggerStatsReportCatchupAndFallbacks() {
        try (NativeDirectTriggers triggers = NativeDirectTriggers.create()) {
            assertTrue(triggers.integrateSection(SectionPos.of(0, 0, 0),
                    movement(8.0, 8.0, 8.0, 24.0, 8.0, 8.0)));

            NativeDirectTriggers.Stats stats = triggers.statsSnapshot();
            assertEquals(1, stats.integrateCalls());
            assertEquals(1, stats.catchupIntegrations());
            assertEquals(1, stats.invalidAngleInputFallbacks());
            assertTrue(stats.maxMovementDistance() >= 16.0);
        }
    }

    @Test
    void directTriggersClassWasRemovedFromJava() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/DirectTriggers.java")));
    }

    private static CameraMovement movement(double startX, double startY, double startZ,
            double endX, double endY, double endZ) {
        return new CameraMovement(new Vector3d(startX, startY, startZ), new Vector3d(endX, endY, endZ));
    }
}
