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
            long geometryPlanes = singleAlignedPlane(ModelQuadFacing.POS_X.ordinal(), 4.0F);
            SectionPos section = SectionPos.of(0, 0, 0);
            try {
                triggers.integrateSection(section, geometryPlanes);

                ArrayList<Long> triggered = new ArrayList<>();
                int uniqueNormalCount = triggers.processTriggers(
                        movement(0.0, 0.0, 0.0, 5.0, 0.0, 0.0), triggered::add);

                assertEquals(1, uniqueNormalCount);
                assertEquals(1, triggered.size());
                assertEquals(section.asLong(), triggered.get(0));
            } finally {
                NativeGfniTriggers.destroyGeometryPlanes(geometryPlanes);
            }
        }
    }

    @Test
    void gfniReverseMovementDoesNotTrigger() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            long geometryPlanes = singleAlignedPlane(ModelQuadFacing.POS_X.ordinal(), 4.0F);
            SectionPos section = SectionPos.of(0, 0, 0);
            try {
                triggers.integrateSection(section, geometryPlanes);

                ArrayList<Long> triggered = new ArrayList<>();
                int uniqueNormalCount = triggers.processTriggers(
                        movement(5.0, 0.0, 0.0, 0.0, 0.0, 0.0), triggered::add);

                assertEquals(0, uniqueNormalCount);
                assertTrue(triggered.isEmpty());
            } finally {
                NativeGfniTriggers.destroyGeometryPlanes(geometryPlanes);
            }
        }
    }

    @Test
    void gfniRemovalRemovesSectionFromAllNormals() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            long geometryPlanes = NativeGfniTriggers.createGeometryPlanes();
            SectionPos section = SectionPos.of(0, 0, 0);
            try {
                NativeGfniTriggers.addAlignedGeometryPlane(geometryPlanes, ModelQuadFacing.POS_X.ordinal(), 4.0F);
                NativeGfniTriggers.addAlignedGeometryPlane(geometryPlanes, ModelQuadFacing.POS_Y.ordinal(), 4.0F);
                triggers.integrateSection(section, geometryPlanes);

                assertEquals(2, triggers.getUniqueNormalCount());
                triggers.removeSection(section.asLong());
                assertEquals(0, triggers.getUniqueNormalCount());

                ArrayList<Long> triggered = new ArrayList<>();
                triggers.processTriggers(movement(0.0, 0.0, 0.0, 5.0, 5.0, 0.0), triggered::add);
                assertTrue(triggered.isEmpty());
            } finally {
                NativeGfniTriggers.destroyGeometryPlanes(geometryPlanes);
            }
        }
    }

    @Test
    void gfniIntegrationGroupsRawPlaneRecordsInRust() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            long geometryPlanes = NativeGfniTriggers.createGeometryPlanes();
            SectionPos section = SectionPos.of(1, 0, 0);
            try {
                NativeGfniTriggers.addAlignedGeometryPlane(geometryPlanes, ModelQuadFacing.POS_X.ordinal(), 2.0F);
                NativeGfniTriggers.addAlignedGeometryPlane(geometryPlanes, ModelQuadFacing.POS_X.ordinal(), 4.0F);
                NativeGfniTriggers.addAlignedGeometryPlane(geometryPlanes, ModelQuadFacing.POS_X.ordinal(), 4.0F);
                triggers.integrateSection(section, geometryPlanes);

                assertEquals(1, triggers.getUniqueNormalCount());

                ArrayList<Long> triggered = new ArrayList<>();
                int uniqueNormalCount = triggers.processTriggers(
                        movement(17.0, 0.0, 0.0, 19.0, 0.0, 0.0), triggered::add);

                assertEquals(1, uniqueNormalCount);
                assertEquals(1, triggered.size());
                assertEquals(section.asLong(), triggered.get(0));
            } finally {
                NativeGfniTriggers.destroyGeometryPlanes(geometryPlanes);
            }
        }
    }

    @Test
    void gfniCatchupChecksOnlyTheIntegratedSection() {
        try (NativeGfniTriggers triggers = NativeGfniTriggers.create()) {
            long firstPlanes = singleAlignedPlane(ModelQuadFacing.POS_X.ordinal(), 4.0F);
            long secondPlanes = singleAlignedPlane(ModelQuadFacing.POS_X.ordinal(), 4.0F);
            SectionPos first = SectionPos.of(0, 0, 0);
            SectionPos second = SectionPos.of(1, 0, 0);
            try {
                triggers.integrateSection(first, firstPlanes);
                triggers.integrateSection(second, secondPlanes);

                ArrayList<Long> triggered = new ArrayList<>();
                triggers.processCatchup(first.asLong(), movement(0.0, 0.0, 0.0, 5.0, 0.0, 0.0), triggered::add);

                assertEquals(1, triggered.size());
                assertEquals(first.asLong(), triggered.get(0));
            } finally {
                NativeGfniTriggers.destroyGeometryPlanes(firstPlanes);
                NativeGfniTriggers.destroyGeometryPlanes(secondPlanes);
            }
        }
    }

    @Test
    void gfniJavaImplementationClassesWereRemoved() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/GFNITriggers.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/NormalList.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/NormalPlanes.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/GeometryPlanes.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/NativeGeometryPlanes.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/trigger/Group.java")));
    }

    private static long singleAlignedPlane(int direction, float distance) {
        long geometryPlanes = NativeGfniTriggers.createGeometryPlanes();
        NativeGfniTriggers.addAlignedGeometryPlane(geometryPlanes, direction, distance);
        return geometryPlanes;
    }

    private static CameraMovement movement(double startX, double startY, double startZ,
            double endX, double endY, double endZ) {
        return new CameraMovement(new Vector3d(startX, startY, startZ), new Vector3d(endX, endY, endZ));
    }
}
