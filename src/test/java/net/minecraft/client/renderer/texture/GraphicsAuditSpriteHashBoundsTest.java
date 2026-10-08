package net.minecraft.client.renderer.texture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditSpriteHashBoundsTest {
    @Test void observesVanillaAndHighResolutionFixturesWithoutUnboundedWork() {
        for (int size : new int[]{1, 16, 32, 64, 128, 256}) {
            assertTrue(SpriteContents.graphicsAuditFrameHashSupported(size, size));
        }
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 257, Integer.MAX_VALUE}) {
            assertFalse(SpriteContents.graphicsAuditFrameHashSupported(invalid, 16));
            assertFalse(SpriteContents.graphicsAuditFrameHashSupported(16, invalid));
        }
    }
}
