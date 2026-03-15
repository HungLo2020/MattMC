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
        String vulkanBackendSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String graphicsBackendSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/GraphicsBackend.java"));

        assertTrue(renderSystemSource.contains("initializeNativeVulkanRuntimeOnRendererStartupIfSelected"),
            "RenderSystem.initRenderer should invoke Vulkan startup initialization hook");
        assertTrue(
            renderSystemSource.indexOf("initializeNativeVulkanRuntimeOnRendererStartupIfSelected")
                < renderSystemSource.indexOf("VulkanicAPI.createRendererDevice"),
            "RenderSystem.initRenderer should initialize Vulkan runtime before backend-owned device creation"
        );
        assertTrue(renderSystemSource.contains("prepareRendererBootstrapWindowHandle"),
            "RenderSystem.initRenderer should resolve renderer bootstrap window handle through VulkanicAPI");
        assertTrue(renderSystemSource.contains("onRendererDeviceInitialized"),
            "RenderSystem.initRenderer should invoke backend-owned post-device initialization hook");
        assertFalse(renderSystemSource.contains("new GlDevice("),
            "RenderSystem.initRenderer should not hard-code GlDevice construction in shared startup path");
        assertFalse(renderSystemSource.contains("OpenGL Vendor:"),
            "RenderSystem.initRenderer should not log OpenGL vendor details directly from the shared startup path");
        assertFalse(renderSystemSource.contains("IrisRenderSystem.initRenderer()"),
            "RenderSystem.initRenderer should not directly run GL-specific renderer init hooks from the shared startup path");
        assertTrue(vulkanicApiSource.contains("public static void initializeNativeVulkanRuntimeOnRendererStartupIfSelected()"),
            "VulkanicAPI should expose startup Vulkan initialization hook");
        assertTrue(vulkanicApiSource.contains("public static GpuDevice createRendererDevice("),
            "VulkanicAPI should expose backend-owned renderer device creation");
        assertTrue(vulkanicApiSource.contains("public static void onRendererDeviceInitialized("),
            "VulkanicAPI should expose backend-owned post-device initialization hook");
        assertTrue(vulkanicApiSource.contains("getVulkanExecutionContextInfo"),
            "Startup Vulkan initialization hook should validate Vulkan execution-context availability");
        assertTrue(vulkanicApiSource.contains("getVulkanSwapchainSurfaceInfo"),
            "Startup Vulkan initialization hook should validate Vulkan swapchain/surface availability");
        assertTrue(vulkanicApiSource.contains("registerGlfwWindowHandleForVulkanSurface"),
            "VulkanicAPI should expose GLFW window registration for Vulkan NO_API surface creation");
        assertTrue(graphicsBackendSource.contains("default long prepareRendererBootstrapWindow(long mainWindowHandle)"),
            "GraphicsBackend should expose backend-owned renderer bootstrap window preparation");
        assertTrue(graphicsBackendSource.contains("default net.blaze3d.systems.GpuDevice createRendererDevice("),
            "GraphicsBackend should expose backend-owned renderer device creation");
        assertTrue(graphicsBackendSource.contains("default void onRendererDeviceInitialized(long mainWindowHandle, net.blaze3d.systems.GpuDevice gpuDevice)"),
            "GraphicsBackend should expose backend-owned post-device initialization hook");
        assertTrue(vulkanBackendSource.contains("getRegisteredGlfwWindowHandleForVulkanSurface"),
            "VulkanBackend should fallback to registered GLFW window handle when no current context exists");
        assertTrue(vulkanBackendSource.contains("new VulkanCompatibilityGpuDevice(this, compatibilityDevice)"),
            "VulkanBackend should own compatibility device creation instead of shared startup code constructing GlDevice directly");
        assertTrue(vulkanicApiSource.contains("method.isDefault()"),
            "Fail-fast Vulkan proxy should recognize default interface methods");
        assertTrue(vulkanicApiSource.contains("invokeDefaultInterfaceMethod"),
            "Fail-fast Vulkan proxy should invoke default interface methods instead of failing");
        assertTrue(vulkanBackendSource.contains("beginPrimaryCommandBuffer();"),
            "VulkanBackend should auto-begin command recording for immediate-mode compatibility operations");
        assertTrue(vulkanBackendSource.contains("submitPrimaryCommandBuffer(primaryCommandBuffer.address());"),
            "VulkanBackend frame lifecycle should auto-submit pending primary command buffers when needed");
    }

    @Test
    public void testNoApiWindowStartupSourceWiringAvoidsMainWindowContextBinding() throws Exception {
        String renderSystemSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/blaze3d/systems/RenderSystem.java"));
        String windowSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/blaze3d/platform/Window.java"));
        String minecraftSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/minecraft/client/Minecraft.java"));
        String vulkanBackendSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(renderSystemSource.contains("cleanupRendererBootstrapResources"),
            "RenderSystem should expose backend-neutral renderer bootstrap cleanup");
        assertFalse(renderSystemSource.contains("cleanupAuxiliaryOpenGlContextWindow"),
            "RenderSystem should not expose OpenGL-named bootstrap cleanup anymore");
        assertTrue(minecraftSource.contains("RenderSystem.cleanupRendererBootstrapResources();"),
            "Minecraft.close should invoke backend-neutral renderer bootstrap cleanup before window termination");
        assertTrue(vulkanBackendSource.contains("Created Vulkan compatibility bootstrap window for backend-owned renderer startup"),
            "VulkanBackend should own compatibility bootstrap window creation when Vulkan NO_API windows are used");
        assertTrue(vulkanBackendSource.contains("Destroyed Vulkan compatibility bootstrap window"),
            "VulkanBackend should own compatibility bootstrap window cleanup");
        assertTrue(windowSource.contains("if (shouldRequestNoApiWindowClientForVulkanBackend())"),
            "Window.updateVsync should no-op when Vulkan NO_API window mode is selected");
        assertTrue(windowSource.contains("registerGlfwWindowHandleForVulkanSurface"),
            "Window should register GLFW handle for Vulkan NO_API surface initialization");
        assertTrue(windowSource.contains("clearRegisteredGlfwWindowHandleForVulkanSurface"),
            "Window shutdown should clear registered GLFW handle for tidy lifecycle management");
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
