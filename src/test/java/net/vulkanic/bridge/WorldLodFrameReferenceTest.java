package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.WorldLodFrameReference;
import static org.junit.jupiter.api.Assertions.*;

class WorldLodFrameReferenceTest {
    @Test void nativeCountsCoverTheCompleteImmutableFrame() {
        var frame = new WorldLodFrameReference(7L, 3L, 16_384, 10_000, 4_000, 2_384);
        assertEquals(16_384, frame.instanceCount());
        assertEquals(frame.instanceCount(), frame.opaqueCount() + frame.transparentCount() + frame.waterCount());
        assertEquals(0L, WorldLodFrameReference.EMPTY.id());
        assertEquals(0, WorldLodFrameReference.EMPTY.instanceCount());
    }

    @Test void invalidBoundsAndIncompleteReferenceMetadataAreRejected() {
        long[][] invalid = {
            {-1, 0, 1, 1, 0, 0}, {1, -1, 1, 1, 0, 0},
            {1, 0, 16_385, 16_385, 0, 0}, {1, 0, 2, 1, 0, 0},
            {0, 1, 0, 0, 0, 0}, {0, 0, 1, 1, 0, 0},
            {1, 0, 0, 0, 0, 0}, {1, 0, 1, -1, 1, 1},
            {1, 0, 1, Integer.MAX_VALUE, Integer.MAX_VALUE, 3}
        };
        for (long[] row : invalid) {
            assertThrows(IllegalArgumentException.class, () -> new WorldLodFrameReference(
                row[0], row[1], (int)row[2], (int)row[3], (int)row[4], (int)row[5]));
        }
    }
}
