package net.sodium.client.render.chunk.translucent_sorting.trigger;

import net.minecraft.core.SectionPos;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeGfniTriggersTest {
    @Test
    void gfniMovementCrossingPlaneTriggersSection() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            SectionPos section = SectionPos.of(0, 0, 0);
            triggers.integrateSection(section, singleAlignedPlane(section, ModelQuadFacing.POS_X.ordinal(), 4.0F));

            ArrayList<Long> triggered = new ArrayList<>();
            int uniqueNormalCount = triggers.processTriggers(
                    movement(0.0, 0.0, 0.0, 5.0, 0.0, 0.0), triggered::add);

            assertEquals(1, uniqueNormalCount);
            assertEquals(1, triggered.size());
            assertEquals(section.asLong(), triggered.get(0));
        }
    }

    @Test
    void gfniReverseMovementDoesNotTrigger() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            SectionPos section = SectionPos.of(0, 0, 0);
            triggers.integrateSection(section, singleAlignedPlane(section, ModelQuadFacing.POS_X.ordinal(), 4.0F));

            ArrayList<Long> triggered = new ArrayList<>();
            int uniqueNormalCount = triggers.processTriggers(
                    movement(5.0, 0.0, 0.0, 0.0, 0.0, 0.0), triggered::add);

            assertEquals(0, uniqueNormalCount);
            assertTrue(triggered.isEmpty());
        }
    }

    @Test
    void gfniRemovalRemovesSectionFromAllNormals() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            SectionPos section = SectionPos.of(0, 0, 0);
            GeometryPlanes geometryPlanes = new GeometryPlanes();
            geometryPlanes.addAlignedPlane(section, ModelQuadFacing.POS_X.ordinal(), 4.0F);
            geometryPlanes.addAlignedPlane(section, ModelQuadFacing.POS_Y.ordinal(), 4.0F);
            triggers.integrateSection(section, geometryPlanes);

            assertEquals(2, triggers.getUniqueNormalCount());
            triggers.removeSection(section.asLong());
            assertEquals(0, triggers.getUniqueNormalCount());

            ArrayList<Long> triggered = new ArrayList<>();
            triggers.processTriggers(movement(0.0, 0.0, 0.0, 5.0, 5.0, 0.0), triggered::add);
            assertTrue(triggered.isEmpty());
        }
    }

    @Test
    void gfniCatchupChecksOnlyTheIntegratedSection() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            SectionPos first = SectionPos.of(0, 0, 0);
            SectionPos second = SectionPos.of(1, 0, 0);
            triggers.integrateSection(first, singleAlignedPlane(first, ModelQuadFacing.POS_X.ordinal(), 4.0F));
            triggers.integrateSection(second, singleAlignedPlane(second, ModelQuadFacing.POS_X.ordinal(), 4.0F));

            ArrayList<Long> triggered = new ArrayList<>();
            triggers.processCatchup(first.asLong(), movement(0.0, 0.0, 0.0, 5.0, 0.0, 0.0), triggered::add);

            assertEquals(1, triggered.size());
            assertEquals(first.asLong(), triggered.get(0));
        }
    }

    @Test
    void gfniJavaImplementationClassesWereRemoved() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/GFNITriggers.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/NormalList.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/Group.java")));
    }

    private static GeometryPlanes singleAlignedPlane(SectionPos sectionPos, int direction, float distance) {
        GeometryPlanes geometryPlanes = new GeometryPlanes();
        geometryPlanes.addAlignedPlane(sectionPos, direction, distance);
        return geometryPlanes;
    }

    private static CameraMovement movement(double startX, double startY, double startZ,
            double endX, double endY, double endZ) {
        return new CameraMovement(new Vector3d(startX, startY, startZ), new Vector3d(endX, endY, endZ));
    }
}
