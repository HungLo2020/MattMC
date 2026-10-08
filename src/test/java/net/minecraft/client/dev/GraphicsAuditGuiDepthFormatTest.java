package net.minecraft.client.dev;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import net.vulkanic.backends.opengl.OpenGLBackend;

class GraphicsAuditGuiDepthFormatTest {
    @Test void disabledObservationNeedsNoGpuContext() {
        String key = "mattmc.dev.guiItemRasterTrace";
        String previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertNull(OpenGLBackend.describeGuiDepthForAudit(null));
            System.setProperty(key, "true");
            assertEquals("status=not-opengl", OpenGLBackend.describeGuiDepthForAudit(null));
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
}
