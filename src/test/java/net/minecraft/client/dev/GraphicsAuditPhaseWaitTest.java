package net.minecraft.client.dev;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GraphicsAuditPhaseWaitTest {
    @Test void waitsAreBoundedAndReadinessResetsTheBudget() {
        var wait = new GraphicsAuditPhaseWait();
        for (int i = 0; i < 512; i++) assertFalse(wait.observe(false));
        assertTrue(wait.observe(true));
        for (int i = 0; i < 512; i++) assertFalse(wait.observe(false));
        assertThrows(IllegalStateException.class, () -> wait.observe(false));
    }
    @Test void cycleBoundaryRequiresARealPositiveResourceTick() {
        assertFalse(GraphicsAuditPhaseWait.cycleBoundary(0, 24));
        assertFalse(GraphicsAuditPhaseWait.cycleBoundary(23, 24));
        assertTrue(GraphicsAuditPhaseWait.cycleBoundary(24, 24));
        assertTrue(GraphicsAuditPhaseWait.cycleBoundary(48, 24));
        assertFalse(GraphicsAuditPhaseWait.cycleBoundary(24, 0));
        assertFalse(GraphicsAuditPhaseWait.cycleBoundary(-24, 24));
    }
    @Test void capturePhaseUsesDeclaredDurationWithoutAdvancingTicks() {
        for (long tick = 1; tick <= 72; tick++) {
            for (long phase = 0; phase < 24; phase++) {
                assertEquals(tick % 24 == phase, GraphicsAuditPhaseWait.phaseMatches(tick, 24, phase));
            }
        }
        assertFalse(GraphicsAuditPhaseWait.phaseMatches(0, 24, 0));
        assertFalse(GraphicsAuditPhaseWait.phaseMatches(24, 24, 24));
        assertFalse(GraphicsAuditPhaseWait.phaseMatches(24, 24, -1));
        assertFalse(GraphicsAuditPhaseWait.phaseMatches(24, 0, 0));
    }
    @Test void invalidConfiguredPhaseFailsInsteadOfSilentlyCapturingAnotherPhase() {
        String key = "mattmc.dev.graphicsAuditMagmaCapturePhase";
        String previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(0, GraphicsAuditPhaseWait.requestedPhase(24));
            System.setProperty(key, "3");
            assertEquals(3, GraphicsAuditPhaseWait.requestedPhase(24));
            for (String invalid : new String[]{"-1", "24", "bad"}) {
                System.setProperty(key, invalid);
                assertThrows(IllegalStateException.class, () -> GraphicsAuditPhaseWait.requestedPhase(24));
            }
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
}
