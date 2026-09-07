package net.minecraft.client.dev;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GraphicsAuditPresentedFrameWaitTest {
    @Test void acknowledgementDoesNotAdvanceOrWait() {
        GraphicsAuditPresentedFrameWait.await(() -> true, () -> 0L,
            () -> fail("already acknowledged frame must not wait"), 10);
    }

    @Test void waitsOnlyUntilAcknowledgement() {
        long[] now = {0};
        GraphicsAuditPresentedFrameWait.await(() -> now[0] == 3, () -> now[0], () -> now[0]++, 10);
        assertEquals(3, now[0]);
    }

    @Test void missingAcknowledgementHasAnExactBound() {
        long[] now = {0};
        assertThrows(IllegalStateException.class, () -> GraphicsAuditPresentedFrameWait.await(
            () -> false, () -> now[0], () -> now[0]++, 10));
        assertEquals(10, now[0]);
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditPresentedFrameWait.await(
            () -> false, () -> 0L, () -> {}, 0));
    }
}
