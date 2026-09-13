package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditEquipmentCapturePacingTest {
    @Test void waitsOnlyBeforeUpcomingNaturalClockTarget() {
        assertEquals(27_000_000L, GraphicsAuditEquipmentCapturePacing.delayNanos(17900, 18008));
        assertEquals(0, GraphicsAuditEquipmentCapturePacing.delayNanos(18008, 18008));
        assertEquals(0, GraphicsAuditEquipmentCapturePacing.delayNanos(18009, 18008));
    }
    @Test void preservesCycleWrapAndFiftyMillisecondBound() {
        assertEquals(27_000_000L, GraphicsAuditEquipmentCapturePacing.delayNanos(329900, 8));
        assertEquals(50_000_000L, GraphicsAuditEquipmentCapturePacing.delayNanos(10000, 10200));
        assertEquals(0, GraphicsAuditEquipmentCapturePacing.delayNanos(10000, 10201));
        assertEquals(27_000_000L, GraphicsAuditEquipmentCapturePacing.delayNanos(659900, 8));
    }
    @Test void rejectsInvalidClocksAndTargetsWithoutOverflow() {
        assertEquals(0, GraphicsAuditEquipmentCapturePacing.delayNanos(-1, 10000));
        assertEquals(0, GraphicsAuditEquipmentCapturePacing.delayNanos(10000, -1));
        assertEquals(0, GraphicsAuditEquipmentCapturePacing.delayNanos(10000, 330000));
        long target = Math.floorMod(Long.MAX_VALUE, 330000L);
        assertEquals(0, GraphicsAuditEquipmentCapturePacing.delayNanos(Long.MAX_VALUE, target));
    }
}
