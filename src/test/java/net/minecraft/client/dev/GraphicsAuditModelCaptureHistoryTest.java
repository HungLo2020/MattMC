package net.minecraft.client.dev;

import java.util.ArrayList;
import java.util.List;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer.ModelMeshDiagnostic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditModelCaptureHistoryTest {
    @Test void finalMetadataSerializersConsumeRetainedProducerRouteAndExecutionHistory() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
        for (String[] entry : new String[][] {
                {"appendModelMeshDiagnostics", "MODEL_CAPTURE_HISTORY"},
                {"appendModelMeshRouteDecisions", "MODEL_ROUTE_CAPTURE_HISTORY"},
                {"appendMovingMeshExecutionDiagnostics", "MODEL_EXECUTION_CAPTURE_HISTORY"}}) {
            int start = source.indexOf("private static StringBuilder " + entry[0] + "(");
            assertTrue(start >= 0);
            int end = source.indexOf("return json;", start);
            assertTrue(source.substring(start, end).contains(entry[1] + ".merge("), entry[0]);
        }
    }

    private static ModelMeshDiagnostic observation(long frame, long mesh) {
        return new ModelMeshDiagnostic(frame, "rust-vulkan-whole-frame", 1, -1,
            "minecraft:zombie", "zombie.png", mesh, 1, 1, 1, 24, 144, 6, 0,
            1280, 720, true, 10, 20, 30, 40);
    }

    @Test void selectedObservationsSurviveLongWaitWithoutInventingMissingFrames() {
        var history = new GraphicsAuditModelCaptureHistory<>(ModelMeshDiagnostic::frameIndex);
        var ring = new ArrayList<ModelMeshDiagnostic>();
        for (int frame = 1; frame <= 2000; frame++) {
            if (ring.size() == 512) ring.removeFirst();
            ring.add(observation(frame, frame));
            if (frame == 100 || frame == 2000) history.retain(frame, ring);
        }
        var retained = history.merge(ring);
        assertTrue(retained.contains(observation(100, 100)));
        assertFalse(retained.contains(observation(101, 101)));
        assertEquals(1, retained.stream().filter(v -> v.frameIndex() == 2000).count());
        assertEquals(521, retained.size());
    }

    @Test void duplicatesRemainVisibleAndConflictingOrUnboundedEvidenceFails() {
        var history = new GraphicsAuditModelCaptureHistory<>(ModelMeshDiagnostic::frameIndex);
        var duplicates = List.of(observation(10, 1), observation(10, 1));
        history.retain(10, duplicates);
        assertEquals(duplicates, history.merge(duplicates));
        assertThrows(IllegalStateException.class, () -> history.retain(11, List.of(observation(10, 2))));
        assertThrows(IllegalStateException.class, () -> history.retain(0, List.of()));
        assertThrows(IllegalStateException.class, () -> history.retain(11, java.util.Collections.nCopies(513, observation(11, 1))));
        for (int i = 1; i < 64; i++) history.retain(100 + i, List.of());
        assertThrows(IllegalStateException.class, () -> history.retain(1000, List.of()));
    }
}
