package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditRenderingSuspensionTest {
    @Test
    void pendingSuspensionWithholdsCaptureBeforeClientOrMenuAccess() throws Exception {
        var field = GraphicsAuditRenderingSuspension.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        var active = field.get(null);
        var complete = GraphicsAuditRenderingSuspension.Sequence.class.getDeclaredField("complete");
        complete.setAccessible(true);
        boolean previous = complete.getBoolean(active);
        try {
            complete.setBoolean(active, false);
            assertFalse(GraphicsAuditRenderingSuspension.readyForCapture());
            assertTrue(GraphicsAuditWorldMenuFixture.afterRender(null),
                "incomplete fixture must withhold capture, even before the Options menu exists");
        } finally {
            complete.setBoolean(active, previous);
        }
    }

    @Test
    void waitsForReadinessThenResumesAfterExactlyTheRequestedEligibleTicks() {
        var sequence = new GraphicsAuditRenderingSuspension.Sequence(150);
        for (int tick = 0; tick < 20; tick++) {
            assertEquals(GraphicsAuditRenderingSuspension.Sequence.Action.NONE, sequence.afterTextureTick(false));
        }
        assertEquals(0, sequence.elapsed());
        assertFalse(sequence.complete());
        assertEquals(GraphicsAuditRenderingSuspension.Sequence.Action.SUSPEND, sequence.afterTextureTick(true));
        for (int tick = 1; tick < 150; tick++) {
            assertEquals(GraphicsAuditRenderingSuspension.Sequence.Action.NONE, sequence.afterTextureTick(false));
            assertEquals(tick, sequence.elapsed());
            assertFalse(sequence.complete());
        }
        assertEquals(GraphicsAuditRenderingSuspension.Sequence.Action.RESUME, sequence.afterTextureTick(false));
        assertTrue(sequence.complete());
        assertEquals(150, sequence.elapsed());
        for (int tick = 0; tick < 150; tick++) {
            assertEquals(GraphicsAuditRenderingSuspension.Sequence.Action.NONE, sequence.afterTextureTick(true));
        }
        assertEquals(150, sequence.elapsed());
    }

    @Test
    void disabledIsInertAndInvalidBoundsReject() {
        var disabled = new GraphicsAuditRenderingSuspension.Sequence(0);
        assertTrue(disabled.complete());
        assertEquals(GraphicsAuditRenderingSuspension.Sequence.Action.NONE, disabled.afterTextureTick(true));
        assertThrows(IllegalArgumentException.class, () -> new GraphicsAuditRenderingSuspension.Sequence(-1));
        assertThrows(IllegalArgumentException.class, () -> new GraphicsAuditRenderingSuspension.Sequence(1201));
    }
}
