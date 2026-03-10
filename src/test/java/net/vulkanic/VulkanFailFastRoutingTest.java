package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Vulkan backend routing does not silently execute inherited OpenGL methods.
 */
public class VulkanFailFastRoutingTest {

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    public void testVulkanInitializationUsesFailFastProxyAndReadinessStillAccessible() {
        VulkanicAPI.initialize(VulkanicAPI.BackendType.VULKAN);

        GraphicsBackend backend = VulkanicAPI.getBackend();
        assertNotNull(backend);
        assertEquals(GraphicsBackendType.VULKAN, backend.getBackendType());
        assertTrue(Proxy.isProxyClass(backend.getClass()),
            "Vulkan backend should be wrapped in a fail-fast proxy");
        assertFalse(backend instanceof VulkanBackend,
            "Public backend should be proxy-wrapped to enforce fail-fast routing");

        VulkanReadinessReport readinessReport = VulkanicAPI.getVulkanReadinessReport();
        assertTrue(readinessReport.isVulkanBackendSelected(),
            "Readiness report should still come from raw Vulkan backend");
    }

    @Test
    public void testProxyRejectsInheritedOpenGLMethods() {
        VulkanicAPI.initialize(VulkanicAPI.BackendType.VULKAN);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> VulkanicAPI.getBackend().setDynamicViewport(null, 0, 0, 1, 1));

        assertTrue(exception.getMessage().contains("OpenGL fallback is intentionally blocked"));
    }

    @Test
    public void testOverriddenVulkanMethodStillReturnsReadinessDiagnostics() {
        VulkanicAPI.initialize(VulkanicAPI.BackendType.VULKAN);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            VulkanicAPI::beginCommandBuffer);

        assertTrue(exception.getMessage().contains("Readiness report:"),
            "Guarded native methods should still emit readiness diagnostics");
    }

    private static void resetBackendState() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        Field rawVulkanBackendField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
        rawVulkanBackendField.setAccessible(true);
        rawVulkanBackendField.set(null, null);
    }
}
