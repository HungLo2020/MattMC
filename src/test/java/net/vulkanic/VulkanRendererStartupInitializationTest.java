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
            if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
                assertDoesNotThrow(VulkanicAPI::initializeNativeVulkanRuntimeOnRendererStartupIfSelected,
                    "Rust whole-frame startup owns explicit bring-up diagnostics and must not re-enter Java Vulkan initialization");
                return;
            }
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
    public void testSelectedVulkanGraphicsContextCannotExposeLegacyHandle() throws Exception {
        String source = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicAPI.java"));
        int method = source.indexOf("public static long getGraphicsContext()");
        int selectedGuard = source.indexOf("isVulkanBackendSelected()", method);
        int returnZero = source.indexOf("return 0L;", selectedGuard);
        assertTrue(method >= 0 && selectedGuard > method && returnZero > selectedGuard,
            "selected Vulkan graphics-context queries must remain fail-closed before backend access");
    }

    @Test
    public void testDirectJavaVulkanBringUpIsFailClosedWhenRustOwnsPresentation() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        int method = source.indexOf("public VulkanNativeInitializationInfo initializeNativeVulkanRuntime()");
        int guard = source.indexOf("Java Vulkan native bring-up is unavailable while Rust owns whole-frame presentation.", method);
        int legacyBringUp = source.indexOf("attemptNativeBringUp();", method);
        assertTrue(method >= 0 && guard > method && legacyBringUp > guard,
            "direct Java Vulkan runtime initialization must guard Rust-owned presentation before legacy bring-up");
    }

    @Test
    public void testRustSemanticOwnershipBoundaryDoesNotInitializeIrisClasses() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/VulkanicAPI.java"));
        int start = source.indexOf("public static void ensureRustSemanticRoute()");
        int end = source.indexOf("\n\t}\n\n    public static", start);
        assertTrue(start >= 0 && end > start, "Rust semantic ownership boundary must be present");
        String method = source.substring(start, end);
        assertFalse(method.contains("net.irisshaders.iris.gl.IrisRenderSystem")
                || method.contains("net.irisshaders.iris.pbr.texture.PBRTextureManager"),
            "Rust Vulkan ownership cleanup must not initialize Iris GPU runtime classes");
    }

    @Test
    public void testSectionOcclusionGraphToleratesPrePublicationCallbacks() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/minecraft/client/renderer/SectionOcclusionGraph.java"));
        assertTrue(source.contains("GraphState graphState = this.currentGraph.get()")
                && source.contains("if (graphState == null)"),
            "terrain extraction must tolerate a visibility graph that has not published its first state");
        assertTrue(source.contains("GraphEvents graphEvents2 = currentState == null ? null : currentState.events")
                && source.contains("if (graphEvents2 != null && graphEvents2 != graphEvents)"),
            "asynchronous chunk callbacks must not dereference a pre-publication visibility graph");
    }

    @Test
    public void testIrisRenderSystemClassLoadingDoesNotQueryRuntimeSamplerLimits() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/irisshaders/iris/gl/IrisRenderSystem.java"));
        int field = source.indexOf("private static final int[] emptyArray");
        assertTrue(field >= 0, "Iris compatibility sampler array must remain declared");
        int lineEnd = source.indexOf('\n', field);
        assertTrue(lineEnd > field, "Iris sampler array declaration must be readable");
        assertFalse(source.substring(field, lineEnd).contains("SamplerLimits.get()"),
            "loading IrisRenderSystem must not query Java runtime sampler limits before Rust ownership is established");
        String limits = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/irisshaders/iris/gl/sampler/SamplerLimits.java"));
        assertTrue(limits.contains("Iris compatibility sampler limits are unavailable while Rust owns whole-frame Vulkan"),
            "Iris sampler-limit queries must fail closed instead of borrowing Rust-owned Vulkan state");
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
        assertFalse(vulkanBackendSource.contains("new VulkanCompatibilityGpuDevice(this, compatibilityDevice)"),
            "Vulkan startup must not construct a Java compatibility device; Rust semantic startup is the only renderer device");
        assertTrue(vulkanBackendSource.contains("requires the Rust Vulkan whole-frame route to be selected"),
            "Vulkan renderer-device creation must fail closed if selection state was not established");
        assertTrue(vulkanicApiSource.contains("method.isDefault()"),
            "Fail-fast Vulkan proxy should recognize default interface methods");
        assertTrue(vulkanicApiSource.contains("invokeDefaultInterfaceMethod"),
            "Fail-fast Vulkan proxy should invoke default interface methods instead of failing");
        assertTrue(vulkanBackendSource.contains("beginPrimaryCommandBuffer();"),
            "VulkanBackend should auto-begin command recording for immediate-mode compatibility operations");
        assertTrue(vulkanBackendSource.contains("submitPrimaryCommandBuffer(commandSubmissionState.primaryCommandBufferHandle());"),
            "VulkanBackend frame lifecycle should auto-submit pending primary command buffers when needed");
    }

    @Test
    public void testVulkanBackendOnRendererDeviceInitializedDoesNotWireJavaIrisLifecycleHooks() throws Exception {
        String vulkanBackendSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        // Rust owns shader-pack resources and execution; the Vulkan backend must
        // never initialize Java Iris GPU state, even in a stale compatibility
        // shell created before route selection.
        assertFalse(vulkanBackendSource.contains("IrisRenderSystem.initRenderer()"),
            "VulkanBackend must not initialize IrisRenderSystem on the Rust Vulkan path");
        assertFalse(vulkanBackendSource.contains("IrisSamplers.initRenderer()"),
            "VulkanBackend must not initialize IrisSamplers on the Rust Vulkan path");
        assertFalse(vulkanBackendSource.contains("Iris.duringRenderSystemInit()"),
            "VulkanBackend must not invoke Iris render-system lifecycle on the Rust Vulkan path");
        assertFalse(vulkanBackendSource.contains("Iris.onRenderSystemInit()"),
            "VulkanBackend must not invoke Java Iris renderer initialization on the Rust Vulkan path");

        int initHook = vulkanBackendSource.indexOf("public void onRendererDeviceInitialized");
        int cleanupHook = vulkanBackendSource.indexOf("public void cleanupRendererBootstrapResources", initHook);
        assertTrue(initHook >= 0 && cleanupHook > initHook,
            "VulkanBackend should expose a bounded onRendererDeviceInitialized hook body");
        String initHookSource = vulkanBackendSource.substring(initHook, cleanupHook);
        assertFalse(initHookSource.contains("glfwPollEvents()"),
            "VulkanBackend.onRendererDeviceInitialized must not poll GLFW events before Minecraft finishes constructing input/framerate state");
    }

    @Test
    public void testSelectedVulkanStartupReturnsBeforeIrisGpuLifecycleHooks() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        int method = source.indexOf("public void onRendererDeviceInitialized");
        int selected = source.indexOf("if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected())", method);
        int rustReturn = source.indexOf("return;", selected);
        int irisHook = source.indexOf("IrisRenderSystem.initRenderer()", selected);
        assertTrue(method >= 0 && selected > method && rustReturn > selected && irisHook < 0,
            "selected Rust Vulkan startup must contain no Java Iris GPU lifecycle branch");
    }

    @Test
    public void testWindowedVulkanBridgeConstructsNativeRustVulkanBackend() throws Exception {
        String ffiSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/rust/render/vulkanic/ffi/context.rs"));
        String backendSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/rust/render/vulkanic/backends/mod.rs"));
        assertTrue(ffiSource.contains("create_native_windowed_vulkan_backend"),
            "windowed Vulkan context creation must select the native Rust Vulkan backend");
        assertTrue(backendSource.contains("VulkanBackend::new_native_windowed"),
            "windowed Vulkan bridge must instantiate the Rust Vulkan presenter directly");
        assertFalse(ffiSource.contains("create_borrowed_opengl_backend(\n"),
            "windowed Vulkan context creation must not fall back to the borrowed OpenGL backend");
    }

    @Test
    public void testRustFfiRegistryAdmitsOnlyOneWindowedPresenter() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/rust/render/vulkanic/ffi/context.rs"));
        assertTrue(source.contains("windowed_presenter_active: bool"),
            "the Rust bridge registry must own the single-presenter admission state");
        assertTrue(source.contains("a Rust Vulkan windowed presenter is already active"),
            "a second Rust Vulkan presenter must fail closed at the FFI boundary");
        assertTrue(source.contains("registry.windowed_presenter_active = true"),
            "windowed presenter admission must claim ownership before returning the context");
        assertTrue(source.contains("registry.windowed_presenter_active = false"),
            "destroying the owning context must release presenter admission");
        assertTrue(source.contains("windowed_presenter: true"),
            "only the native windowed Vulkan context may own presenter admission");
        assertTrue(source.contains("MAX_BRIDGE_CONTEXTS: usize = 16"),
            "Rust FFI context admission must have an explicit bounded live-context limit");
        assertTrue(source.contains("LIVE_BRIDGE_CONTEXTS"),
            "Rust FFI context admission must account for contexts across bridge registries");
        assertTrue(source.contains("release_context_slot();"),
            "destroying a Rust FFI context must release its bounded admission slot");
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
        assertFalse(vulkanBackendSource.contains("Created Vulkan compatibility bootstrap window for backend-owned renderer startup"),
            "VulkanBackend must not create a hidden OpenGL compatibility bootstrap window");
        assertTrue(vulkanBackendSource.contains("Vulkan renderer bootstrap requires the Rust Vulkan whole-frame route to be selected"),
            "Vulkan bootstrap-window preparation must fail closed before selection state exists");
        assertTrue(vulkanBackendSource.contains("Destroyed Vulkan compatibility bootstrap window"),
            "VulkanBackend should own compatibility bootstrap window cleanup");
        assertTrue(windowSource.contains("if (shouldRequestNoApiWindowClientForVulkanBackend())"),
            "Window.updateVsync should no-op when Vulkan NO_API window mode is selected");
        assertTrue(windowSource.contains("registerGlfwWindowHandleForVulkanSurface"),
            "Window should register GLFW handle for Vulkan NO_API surface initialization");
        assertTrue(windowSource.contains("clearRegisteredGlfwWindowHandleForVulkanSurface"),
            "Window shutdown should clear registered GLFW handle for tidy lifecycle management");
    }

    @Test
    public void testCenterDepthVertexShaderAvoidsStandaloneUniformsForVulkan() throws Exception {
        String centerDepthVertexSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/resources/centerDepth.vsh"));
        String centerDepthFragmentSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/resources/centerDepth.fsh"));
        String centerDepthSamplerSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/irisshaders/iris/pathways/CenterDepthSampler.java"));
        String irisSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/irisshaders/iris/Iris.java"));

        assertFalse(centerDepthVertexSource.contains("uniform mat4 projection"),
            "centerDepth.vsh should avoid standalone non-opaque uniforms that fail Vulkan GLSL compilation");
        assertTrue(centerDepthVertexSource.contains("iris_Position.xy * 2.0 - 1.0"),
            "centerDepth.vsh should perform fixed fullscreen-NDC projection without runtime uniform plumbing");
        assertFalse(centerDepthFragmentSource.contains("uniform float lastFrameTime"),
            "centerDepth.fsh should avoid standalone scalar uniforms that fail Vulkan GLSL compilation");
        assertFalse(centerDepthFragmentSource.contains("uniform float decay"),
            "centerDepth.fsh should avoid standalone scalar uniforms that fail Vulkan GLSL compilation");
        assertTrue(centerDepthSamplerSource.contains("if (VulkanicAPI.isVulkanBackendSelected())"),
            "CenterDepthSampler should include a Vulkan compatibility guard to skip legacy smoothing program creation during startup");
        assertFalse(irisSource.contains("Disabling Iris shaderpack loading on the Vulkan path"),
            "Iris should not hard-disable shaderpack loading on Vulkan now that shaderpack startup is allowed to use the migrated backend path");
    }

    @Test
    public void testPanoramaShadersUseSkyboxClipProjectionAndCubemapSampling() throws Exception {
        String panoramaVertexSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/resources/assets/minecraft/shaders/core/panorama.vsh"));
        String panoramaFragmentSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/resources/assets/minecraft/shaders/core/panorama.fsh"));

        assertTrue(panoramaVertexSource.contains("vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);"),
            "panorama.vsh should generate a fullscreen triangle procedurally so Vulkan does not depend on the custom panorama vertex buffer path");
        assertTrue(panoramaVertexSource.contains("vec2 clipPos = uv * 2.0 - 1.0;"),
            "panorama.vsh should reconstruct clip-space positions from the fullscreen triangle vertices before deriving view rays");
        assertTrue(panoramaVertexSource.contains("vec3 viewDirection = vec3(clipPos.x / ProjMat[0][0], clipPos.y / ProjMat[1][1], -1.0);"),
            "panorama.vsh should reconstruct view rays from fullscreen triangle clip positions and the perspective projection scale factors");
        assertTrue(panoramaVertexSource.contains("transpose(mat3(ModelViewMat))"),
            "panorama.vsh should rotate reconstructed view rays back into cubemap space before interpolation");
        assertTrue(panoramaFragmentSource.contains("uniform sampler2D Sampler0;"),
            "panorama.fsh should sample the stacked panorama atlas through a regular 2D sampler");
        assertTrue(panoramaFragmentSource.contains("uv.y = (faceIndex + uv.y) / 6.0;"),
            "panorama.fsh should map the selected cubemap face into the stacked six-row atlas");
        assertTrue(panoramaFragmentSource.contains("fragColor = texture(Sampler0, uv);"),
            "panorama.fsh should sample the panorama atlas after selecting the correct face and UV coordinates");
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
        net.vulkanic.bridge.RustGalVulkanWholeFrameMode.deactivateRustPresentation();
        net.vulkanic.bridge.RustGalVulkanWholeFrameMode.clearVulkanBackendSelection();
    }
}
