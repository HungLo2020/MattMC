package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.minecraft.client.dev.GraphicsAuditResourceReload.Sequence.Action.*;

class GraphicsAuditResourceReloadTest {
    @Test
    void reloadCompletionAndTwoUnobstructedPresentationsAreRequired() {
        var sequence = new GraphicsAuditResourceReload.Sequence();
        assertEquals(RELOAD, sequence.afterPresentation(true, false));
        assertFalse(sequence.complete());
        assertEquals(WAIT, sequence.afterPresentation(false, false));
        assertEquals(WAIT, sequence.afterPresentation(true, true));
        assertEquals(WAIT, sequence.afterPresentation(true, false));
        assertEquals(WAIT, sequence.afterPresentation(true, true));
        assertEquals(WAIT, sequence.afterPresentation(true, false));
        assertFalse(sequence.complete());
        assertEquals(CAPTURE, sequence.afterPresentation(true, false));
        assertTrue(sequence.complete());
    }
}
