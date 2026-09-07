package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.minecraft.client.dev.GraphicsAuditFullscreen.Sequence.Action.*;

class GraphicsAuditFullscreenTest {
    @Test
    void fullscreenMustAttachAndPresentThenRestore() {
        var sequence = new GraphicsAuditFullscreen.Sequence();
        assertEquals(ENTER, sequence.afterPresentation(false, false, 1280, 720));
        assertEquals(WAIT, sequence.afterPresentation(true, false, 1920, 1080));
        assertEquals(WAIT, sequence.afterPresentation(true, true, 0, 0));
        assertEquals(WAIT, sequence.afterPresentation(true, true, 1920, 1080));
        assertEquals(WAIT, sequence.afterPresentation(true, true, 1920, 1056));
        assertEquals(WAIT, sequence.afterPresentation(true, true, 1920, 1080));
        assertEquals(EXIT, sequence.afterPresentation(true, true, 1920, 1080));
        assertEquals(WAIT, sequence.afterPresentation(false, true, 1280, 720));
        assertEquals(WAIT, sequence.afterPresentation(false, false, 1279, 720));
        assertEquals(WAIT, sequence.afterPresentation(false, false, 1280, 720));
        assertFalse(sequence.complete());
        assertEquals(CAPTURE, sequence.afterPresentation(false, false, 1280, 720));
        assertTrue(sequence.complete());
        assertEquals(1920, sequence.fullscreenWidth());
        assertEquals(1080, sequence.fullscreenHeight());
    }
}
