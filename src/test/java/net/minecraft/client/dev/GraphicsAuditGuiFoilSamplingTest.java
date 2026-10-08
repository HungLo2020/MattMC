package net.minecraft.client.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import net.vulkanic.backends.opengl.OpenGLBackend;
import org.junit.jupiter.api.Test;

class GraphicsAuditGuiFoilSamplingTest {
    @Test void disabledAndUnscopedObservationsNeedNoGpuContext() {
        String sampling = "mattmc.dev.guiLeafSamplingTrace";
        String timing = "mattmc.dev.guiItemRasterTrace";
        String oldSampling = System.getProperty(sampling), oldTiming = System.getProperty(timing);
        try {
            System.clearProperty(sampling);
            assertNull(OpenGLBackend.describeGuiFoilSamplingForAudit(null, 0));
            System.setProperty(sampling, "true");
            assertNull(OpenGLBackend.describeGuiFoilSamplingForAudit(null, 0));
            System.setProperty(timing, "true");
            GraphicsAuditGuiFoilTiming.beginItem(1, 2);
            assertNull(OpenGLBackend.describeGuiFoilSamplingForAudit(null, 0));
            GraphicsAuditGuiFoilTiming.endItem();
            GraphicsAuditGuiFoilTiming.beginItem(292, 341);
            assertEquals("status=not-opengl", OpenGLBackend.describeGuiFoilSamplingForAudit(null, 0));
        } finally {
            GraphicsAuditGuiFoilTiming.endItem();
            if (oldSampling == null) System.clearProperty(sampling); else System.setProperty(sampling, oldSampling);
            if (oldTiming == null) System.clearProperty(timing); else System.setProperty(timing, oldTiming);
        }
    }
}
