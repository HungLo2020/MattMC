package net.vulkanic.world;

import java.util.ArrayList;
import java.util.List;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowOnlyEntityCaptureTest {
    private static final String[] CAMERA_STREAMS = {"PENDING_MATERIAL_QUADS", "PENDING_PARTICLE_QUADS",
        "PENDING_TEXT_QUADS", "PENDING_SEGMENTS", "PENDING_CRACK_QUADS", "PENDING_BORDER_QUADS"};

    private static Object field(String name) throws Exception {
        var field = RustGalWorldPrimitiveRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static VulkanicGalBridge.WorldMeshInstanceRecord mesh() {
        return new VulkanicGalBridge.WorldMeshInstanceRecord(
            VulkanicGalBridge.WORLD_MESH_ENTITY_STRATUM,17,1,0,2,1,0,-1,
            new org.joml.Matrix4f().get(new float[16]),128,128,42,0,0,0,-1);
    }

    @SuppressWarnings("unchecked")
    @Test void foilRemovalKeepsCameraAndShadowOrbsAtTheirCollectedMeshBoundaries() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var meshes = (List<VulkanicGalBridge.WorldMeshInstanceRecord>) field("PENDING_MESH_INSTANCES");
        var collector = (ExperienceOrbSemanticCollector) field("ORB_SEMANTICS");
        var pendingField = ExperienceOrbSemanticCollector.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        var orbs = (List<VulkanicGalBridge.WorldExperienceOrbInstanceRecord>) pendingField.get(collector);
        var savedMeshes = List.copyOf(meshes);
        var savedOrbs = List.copyOf(orbs);
        var culling = new VulkanicGalBridge.WorldEntityCullingRecord(1,
            new VulkanicGalBridge.WorldAabbRecord(-1,-1,-1,1,1,1),null);
        VulkanicGalBridge.beginSemanticEntityCulling(culling);
        boolean captureActive = false;
        try {
            meshes.clear();
            orbs.clear();
            meshes.add(mesh());
            var cameraOrb = new VulkanicGalBridge.WorldExperienceOrbInstanceRecord(
                0x4f52420000000001L,1,1,new org.joml.Matrix4f().get(new float[16]),
                new float[]{0,0,0,1},42,culling,false);
            orbs.add(cameraOrb);
            RustGalWorldPrimitiveRenderer.beginShadowOnlyEntityCapture();
            captureActive = true;
            meshes.add(mesh());
            meshes.add(mesh().withItemFoil(new VulkanicGalBridge.StandardItemFoilRecord(0,0,0.5F)));
            var shadowOrb = new VulkanicGalBridge.WorldExperienceOrbInstanceRecord(
                0x4f52420000000002L,1,meshes.size(),new org.joml.Matrix4f().get(new float[16]),
                new float[]{0,0,0,1},43,culling,true);
            orbs.add(shadowOrb);
            assertEquals(1,RustGalWorldPrimitiveRenderer.endShadowOnlyEntityCapture());
            captureActive = false;
            assertEquals(2,meshes.size());
            var mapped = collector.remap(new int[]{0,1,2});
            assertEquals(List.of(1,2),mapped.stream().map(x->x.meshIndex()).toList());
            assertEquals(cameraOrb.meshKey(),mapped.getFirst().meshKey());
            assertFalse(mapped.getFirst().shadowOnly());
            assertSame(cameraOrb,collector.pendingInstances().getFirst());
            assertEquals(shadowOrb.meshKey(),mapped.getLast().meshKey());
            assertEquals(shadowOrb.entityCulling(),mapped.getLast().entityCulling());
            assertTrue(mapped.getLast().shadowOnly());
            assertEquals(3,shadowOrb.meshIndex(),"the originally published immutable record is unchanged");
        } finally {
            if (captureActive) RustGalWorldPrimitiveRenderer.endShadowOnlyEntityCapture();
            meshes.clear();
            meshes.addAll(savedMeshes);
            orbs.clear();
            orbs.addAll(savedOrbs);
            VulkanicGalBridge.endSemanticEntityCulling();
        }
    }

    @SuppressWarnings("unchecked")
    @Test void offCameraStreamsNeverBecomeCameraGeometryAndRetainOwnershipGap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        List<List<Object>> streams = new ArrayList<>();
        Object cameraGeometry = new Object();
        Object shadowGeometry = new Object();
        try {
            for (String name : CAMERA_STREAMS) {
                var stream = (List<Object>) field(name);
                stream.clear();
                stream.add(cameraGeometry);
                streams.add(stream);
            }
            RustGalWorldPrimitiveRenderer.beginShadowOnlyEntityCapture();
            assertThrows(IllegalStateException.class, RustGalWorldPrimitiveRenderer::beginShadowOnlyEntityCapture);
            for (var stream : streams) stream.add(shadowGeometry);
            assertEquals(0, RustGalWorldPrimitiveRenderer.endShadowOnlyEntityCapture());
            for (var stream : streams) assertEquals(List.of(cameraGeometry), stream);
            RustGalWorldPrimitiveRenderer.enqueueWorldFeatureCoverage(
                new net.minecraft.client.renderer.SubmitNodeCollection.WorldFeatureCoverageSnapshot(
                    0,0,0,0,0,2,0,0,0,0,0,0,0));
            var coverage = (VulkanicGalBridge.WorldFeatureCoverageRecord) field("pendingFeatureCoverage");
            assertEquals(3, coverage.customGeometrySubmits());
            // An ordinary later capture neither removes camera data nor loses
            // the explicit missing-family receipt from the earlier capture.
            RustGalWorldPrimitiveRenderer.beginShadowOnlyEntityCapture();
            assertEquals(0, RustGalWorldPrimitiveRenderer.endShadowOnlyEntityCapture());
            assertThrows(IllegalStateException.class, RustGalWorldPrimitiveRenderer::endShadowOnlyEntityCapture);
            for (var stream : streams) assertEquals(List.of(cameraGeometry), stream);
        } finally {
            for (var stream : streams) stream.clear();
            var count = RustGalWorldPrimitiveRenderer.class.getDeclaredField("pendingUnsupportedShadowGeometry");
            count.setAccessible(true);
            count.setInt(null,0);
            var coverage = RustGalWorldPrimitiveRenderer.class.getDeclaredField("pendingFeatureCoverage");
            coverage.setAccessible(true);
            coverage.set(null,VulkanicGalBridge.WorldFeatureCoverageRecord.empty());
        }
    }
}
