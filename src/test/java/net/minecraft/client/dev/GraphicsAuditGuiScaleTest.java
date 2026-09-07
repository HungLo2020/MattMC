package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.minecraft.client.dev.GraphicsAuditGuiScale.Sequence.Action.*;

class GraphicsAuditGuiScaleTest {
    @Test
    void applyRequiresSettingAndActualScaleThenRestoresInitialScale() {
        var sequence = new GraphicsAuditGuiScale.Sequence();
        assertEquals(EDIT_UP, sequence.afterPresentation(2, 2));
        assertEquals(APPLY, sequence.afterPresentation(2, 2));
        assertEquals(WAIT, sequence.afterPresentation(3, 2));
        assertEquals(WAIT, sequence.afterPresentation(3, 3));
        assertEquals(WAIT, sequence.afterPresentation(2, 3));
        assertEquals(WAIT, sequence.afterPresentation(3, 3));
        assertEquals(EDIT_DOWN, sequence.afterPresentation(3, 3));
        assertEquals(APPLY, sequence.afterPresentation(3, 3));
        assertEquals(WAIT, sequence.afterPresentation(2, 3));
        assertEquals(WAIT, sequence.afterPresentation(2, 2));
        assertFalse(sequence.complete());
        assertEquals(CAPTURE, sequence.afterPresentation(2, 2));
        assertTrue(sequence.complete());
    }

    @Test
    void wrongInitialSettingsCannotBecomeEvidence() {
        assertThrows(IllegalStateException.class, () -> new GraphicsAuditGuiScale.Sequence().afterPresentation(1, 1));
        assertThrows(IllegalStateException.class, () -> new GraphicsAuditGuiScale.Sequence().afterPresentation(2, 3));
    }
}
