package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiProjectionRecordTest {
    @Test
    void nonDivisibleWindowRetainsExactProjectionAndRoundedLayout() {
        var projection = new VulkanicGalBridge.GuiProjectionRecord(1280.0F / 3, 1012.0F / 3);
        projection.validateLayout(427, 338);
        assertEquals(426.66666F, projection.width());
        assertEquals(337.33334F, projection.height());
        assertThrows(IllegalArgumentException.class, () -> projection.validateLayout(426, 337));
    }

    @Test
    void malformedProjectionIsRejectedBeforeNativeSubmission() {
        for (float invalid : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                () -> new VulkanicGalBridge.GuiProjectionRecord(invalid, 240));
            assertThrows(IllegalArgumentException.class,
                () -> new VulkanicGalBridge.GuiProjectionRecord(320, invalid));
        }
    }
}
