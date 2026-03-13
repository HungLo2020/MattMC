package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanRendererStartupInitializationTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    public void testStartupInitNoOpsWhenBackendIsUninitialized() throws Exception {
        assertDoesNotThrow(VulkanicAPI::initializeNativeVulkanRuntimeOnRendererStartupIfSelected,
            "Startup Vulkan init hook should no-op before backend selection");

        assertNull(getBackendFieldValue(),
            "Startup Vulkan init hook must not implicitly initialize backend routing");
    }

    @Test
    public void testStartupInitNoOpsForOpenGLSelection() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        assertDoesNotThrow(VulkanicAPI::initializeNativeVulkanRuntimeOnRendererStartupIfSelected,
            "Startup Vulkan init hook should no-op for OpenGL backend selection");
        assertFalse(VulkanicAPI.isNativeVulkanBackendReady(),
            "OpenGL selection should remain non-Vulkan-native after startup init hook");
    }

    @Test
    public void testStartupInitFailsHardOrExecutesWhenVulkanSelected() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);
        VulkanNativeInitializationInfo info = VulkanicAPI.initializeNativeVulkanRuntime();

        if (!info.isNativeVulkanReady()) {
            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                VulkanicAPI::initializeNativeVulkanRuntimeOnRendererStartupIfSelected,
                "Vulkan-selected startup init must fail hard when native bring-up is unavailable"
            );
            String message = failure.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("renderer startup"));
            return;
        }

        assertDoesNotThrow(VulkanicAPI::initializeNativeVulkanRuntimeOnRendererStartupIfSelected,
            "Vulkan-selected startup init should succeed when native runtime is available");

        VulkanSwapchainSurfaceInfo swapchainInfo = VulkanicAPI.getVulkanSwapchainSurfaceInfo();
        assertTrue(swapchainInfo.isAvailable(),
            "Successful Vulkan startup initialization should expose available swapchain/surface diagnostics");
    }

    @Test
    public void testVulkanBackendExposesNullGraphicsContextWithoutProxyFailure() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        long contextHandle = assertDoesNotThrow(VulkanicAPI::getGraphicsContext,
            "Vulkan backend should implement getGraphicsContext instead of failing through proxy coverage checks");
        assertEquals(0L, contextHandle,
            "Vulkan backend should report null OpenGL context handle");
    }

    @Test
    public void testRendererStartupSourceWiringInvokesStartupInitHook() throws Exception {
        String renderSystemSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/blaze3d/systems/RenderSystem.java"));
        String vulkanicApiSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/VulkanicAPI.java"));

        assertTrue(renderSystemSource.contains("initializeNativeVulkanRuntimeOnRendererStartupIfSelected"),
            "RenderSystem.initRenderer should invoke Vulkan startup initialization hook");
        assertTrue(vulkanicApiSource.contains("public static void initializeNativeVulkanRuntimeOnRendererStartupIfSelected()"),
            "VulkanicAPI should expose startup Vulkan initialization hook");
        assertTrue(vulkanicApiSource.contains("getVulkanExecutionContextInfo"),
            "Startup Vulkan initialization hook should validate Vulkan execution-context availability");
        assertTrue(vulkanicApiSource.contains("getVulkanSwapchainSurfaceInfo"),
            "Startup Vulkan initialization hook should validate Vulkan swapchain/surface availability");
    }

    private static Object getBackendFieldValue() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        return backendField.get(null);
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
