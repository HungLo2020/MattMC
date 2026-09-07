package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditWorldMenuFixtureTest {
    @Test
    void onlyAnExplicitReadyUnobstructedWorldMayNavigate() {
        for (int flags = 0; flags < 32; flags++) {
            boolean requested = (flags & 1) != 0;
            boolean world = (flags & 2) != 0;
            boolean overlay = (flags & 4) != 0;
            boolean screen = (flags & 8) != 0;
            boolean settled = (flags & 16) != 0;
            assertEquals(flags == 19, GraphicsAuditWorldMenuFixture.shouldOpen(requested, world, overlay, screen, settled));
        }
    }
}
