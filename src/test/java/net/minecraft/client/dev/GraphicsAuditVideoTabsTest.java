package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditVideoTabsTest {
    @Test
    void everyTabRequiresAcceptedInputAndTwoObservedPresentations() {
        var sequence = new GraphicsAuditVideoTabs.Sequence();
        assertThrows(IllegalStateException.class, () -> sequence.clicked(false));
        for (String page : new String[] {"quality", "performance", "advanced", "general"}) {
            assertEquals(page, sequence.requestedPage());
            sequence.presented(page);
            assertTrue(sequence.needsClick());
            sequence.clicked(true);
            assertThrows(IllegalStateException.class, () -> sequence.clicked(true));
            sequence.presented(page);
            sequence.presented("wrong");
            sequence.presented(page);
            assertEquals(page, sequence.requestedPage());
            sequence.presented(page);
        }
        assertTrue(sequence.complete());
        assertEquals(4, sequence.completedSteps());
        assertNull(sequence.requestedPage());
    }
}
