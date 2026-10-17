package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceAttachmentProducerTest {
    @Test void itemFrameBackingUsesOpaqueGeometryRatherThanEntityMesh() {
        for (String scenario : new String[]{"item-frame", "item-frame-map", "item-frame-map-rotated",
                "item-frame-map-decorated", "item-frame-item", "glow-item-frame-map",
                "item-frame-map-invisible", "glow-item-frame-item-invisible"}) {
            assertFalse(DeterministicCameraCapture.requiresSourceEntityMeshCapture(scenario), scenario);
        }
    }

    @Test void ordinaryEntityModelsRetainTheirRequiredMeshLane() {
        for (String scenario : new String[]{"chest", "bed", "zombie", "white-banner-red-cross"}) {
            assertTrue(DeterministicCameraCapture.requiresSourceEntityMeshCapture(scenario), scenario);
        }
        for (String scenario : new String[]{"", "hidden", "wind-charge"}) {
            assertFalse(DeterministicCameraCapture.requiresSourceEntityMeshCapture(scenario), scenario);
        }
    }
}
