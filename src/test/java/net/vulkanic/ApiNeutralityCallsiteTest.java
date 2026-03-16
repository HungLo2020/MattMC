package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrails for reducing OpenGL-flavored API usage in high-traffic render callsites.
 */
public class ApiNeutralityCallsiteTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

    @Test
    public void testHighTrafficRendererCallsitesAvoidConcreteBackendCastLeaks() throws IOException {
        String graphicsBackendSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/GraphicsBackend.java"));
        String vulkanicApiSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java"));
        String commandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/systems/CommandEncoder.java"));
        String glCommandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java"));
        String gpuDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/systems/GpuDevice.java"));
        String renderTargetSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/pipeline/RenderTarget.java"));
        String mainTargetSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/pipeline/MainTarget.java"));
        String lightingSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/platform/Lighting.java"));
        String tracyFrameCaptureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/TracyFrameCapture.java"));
        String chunkSectionsToRenderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/chunk/ChunkSectionsToRender.java"));
        String compiledSectionMeshSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/chunk/CompiledSectionMesh.java"));
        String worldBorderRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/WorldBorderRenderer.java"));
        String skyRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/SkyRenderer.java"));
        String cloudRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CloudRenderer.java"));
        String cubeMapSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CubeMap.java"));
        String renderTypeSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/RenderType.java"));
        String sodiumRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/ShaderChunkRenderer.java"));
        String irisSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/Iris.java"));
        String renderSystemSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java"));
        String vulkanCompatibilityGpuDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanCompatibilityGpuDevice.java"));
        String dynamicUniformStorageSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/DynamicUniformStorage.java"));
        String gpuWarnlistManagerSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/GpuWarnlistManager.java"));
        String gameRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/GameRenderer.java"));
        String levelRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/LevelRenderer.java"));
        String minecraftTextureAtlasSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/TextureAtlas.java"));
        String shaderManagerSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/ShaderManager.java"));
        String debugEntrySystemSpecsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/components/debug/DebugEntrySystemSpecs.java"));
        String debugScreenOverlaySource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/components/DebugScreenOverlay.java"));
        String guiRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/GuiRenderer.java"));
        String minecraftSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/Minecraft.java"));
        String screenshotSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/Screenshot.java"));
        String voxelMapTextureAtlasSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/textures/TextureAtlas.java"));

        assertTrue(graphicsBackendSource.contains("default CommandEncoder createCommandEncoder()"),
            "GraphicsBackend should expose backend-owned command-encoder acquisition seam");
        assertTrue(graphicsBackendSource.contains("default GpuTexture createTexture("),
            "GraphicsBackend should expose backend-owned texture creation seams");
        assertTrue(graphicsBackendSource.contains("default GpuBuffer createBuffer("),
            "GraphicsBackend should expose backend-owned buffer creation seams");
        assertTrue(vulkanicApiSource.contains("public static CommandEncoder createCommandEncoder()"),
            "VulkanicAPI should expose backend-owned command-encoder wrapper");
        assertTrue(vulkanicApiSource.contains("return getBackend().createCommandEncoder();"),
            "VulkanicAPI command-encoder wrapper should route through backend seam");
        assertTrue(graphicsBackendSource.contains("default RenderPass createRenderPass("),
            "GraphicsBackend should expose backend-owned render-pass creation seams");
        assertTrue(vulkanicApiSource.contains("public static RenderPass createRenderPass("),
            "VulkanicAPI should expose backend-owned render-pass wrappers");
        assertTrue(vulkanicApiSource.contains("return getBackend().createRenderPass("),
            "VulkanicAPI render-pass wrappers should route through backend seam");
        assertTrue(vulkanicApiSource.contains("public static GpuTexture createTexture("),
            "VulkanicAPI should expose backend-owned texture creation wrappers");
        assertTrue(vulkanicApiSource.contains("public static GpuBuffer createBuffer("),
            "VulkanicAPI should expose backend-owned buffer creation wrappers");
        assertTrue(vulkanicApiSource.contains("return getBackend().createTexture("),
            "VulkanicAPI texture wrappers should route through backend seam");
        assertTrue(vulkanicApiSource.contains("return getBackend().createBuffer("),
            "VulkanicAPI buffer wrappers should route through backend seam");
        assertTrue(graphicsBackendSource.contains("default GpuTextureView createTextureView("),
            "GraphicsBackend should expose backend-owned texture-view creation seams");
        assertTrue(vulkanicApiSource.contains("public static GpuTextureView createTextureView("),
            "VulkanicAPI should expose backend-owned texture-view creation wrappers");
        assertTrue(vulkanicApiSource.contains("return getBackend().createTextureView("),
            "VulkanicAPI texture-view wrappers should route through backend seam");
        assertTrue(graphicsBackendSource.contains("default int getBackendMaxTextureSize()"),
            "GraphicsBackend should expose backend-owned max texture size seam");
        assertTrue(graphicsBackendSource.contains("default int getBackendUniformOffsetAlignment()"),
            "GraphicsBackend should expose backend-owned uniform offset alignment seam");
        assertTrue(graphicsBackendSource.contains("default GpuDevice.GpuDeviceInfo getBackendDeviceInfo()"),
            "GraphicsBackend should expose backend-owned device info seam");
        assertTrue(vulkanicApiSource.contains("public static int getBackendMaxTextureSize()"),
            "VulkanicAPI should expose backend-owned max texture size wrapper");
        assertTrue(vulkanicApiSource.contains("public static int getBackendUniformOffsetAlignment()"),
            "VulkanicAPI should expose backend-owned uniform offset alignment wrapper");
        assertTrue(vulkanicApiSource.contains("public static GpuDevice.GpuDeviceInfo getBackendDeviceInfo()"),
            "VulkanicAPI should expose backend-owned device info wrapper");
        assertTrue(vulkanicApiSource.contains("return getBackend().getBackendMaxTextureSize();"),
            "VulkanicAPI max texture size wrapper should route through backend seam");
        assertTrue(vulkanicApiSource.contains("return getBackend().getBackendUniformOffsetAlignment();"),
            "VulkanicAPI uniform offset alignment wrapper should route through backend seam");
        assertTrue(vulkanicApiSource.contains("return getBackend().getBackendDeviceInfo();"),
            "VulkanicAPI device info wrapper should route through backend seam");

        assertTrue(commandEncoderSource.contains("void applyPipelineState(RenderPipeline renderPipeline);"),
            "CommandEncoder should expose a backend-neutral pipeline-state seam");
        assertTrue(commandEncoderSource.contains("void invalidateCachedProgramBinding();"),
            "CommandEncoder should expose a backend-neutral cached-program invalidation seam");
        assertTrue(glCommandEncoderSource.contains("public void applyPipelineState(RenderPipeline renderPipeline)"),
            "GlCommandEncoder should implement backend-neutral pipeline-state application through the interface seam");
        assertTrue(glCommandEncoderSource.contains("public void invalidateCachedProgramBinding()"),
            "GlCommandEncoder should implement backend-neutral cached-program invalidation through the interface seam");
        assertFalse(glCommandEncoderSource.contains("public GlProgram lastProgram"),
            "GlCommandEncoder should not expose lastProgram as a public concrete-backend field");

        assertTrue(sodiumRendererSource.contains("CommandEncoder commandEncoder = VulkanicAPI.createCommandEncoder();"),
            "ShaderChunkRenderer should acquire a backend-owned CommandEncoder through VulkanicAPI seam");
        assertTrue(sodiumRendererSource.contains("commandEncoder.applyPipelineState(pass.getPipeline());"),
            "ShaderChunkRenderer should apply pipeline state through CommandEncoder");
        assertTrue(sodiumRendererSource.contains("commandEncoder.invalidateCachedProgramBinding();"),
            "ShaderChunkRenderer should invalidate cached program binding through CommandEncoder");
        assertFalse(sodiumRendererSource.contains("RenderSystem.getDevice("),
            "ShaderChunkRenderer should avoid direct RenderSystem.getDevice() acquisition after seam migration");
        assertFalse(sodiumRendererSource.contains("GlCommandEncoder"),
            "ShaderChunkRenderer should avoid concrete GlCommandEncoder dependency");

        assertTrue(irisSource.contains("VulkanicAPI.getBackendEnabledExtensions()"),
            "Iris should use backend-owned extension querying seam");
        assertFalse(irisSource.contains("((GlDevice) RenderSystem.getDevice())"),
            "Iris should avoid concrete GlDevice casts when querying enabled extensions");
        assertTrue(gpuDeviceSource.contains("record GpuDeviceInfo("),
            "GpuDevice should expose a backend-neutral device-info seam");
        assertTrue(gpuDeviceSource.contains("default GpuDeviceInfo getDeviceInfo()"),
            "GpuDevice should expose backend-neutral device-info access");
        assertTrue(gpuDeviceSource.contains("default List<String> getOptionalFeatureNames()"),
            "GpuDevice should expose backend-neutral optional feature reporting");
        assertTrue(gpuDeviceSource.contains("default boolean shouldApplyOpenGlWarnlist()"),
            "GpuDevice should expose explicit OpenGL warnlist applicability instead of requiring string comparisons");
        assertTrue(gpuWarnlistManagerSource.contains("VulkanicAPI.getBackendDeviceInfo()"),
            "GpuWarnlistManager should query backend-owned device info seam");
        assertTrue(gpuWarnlistManagerSource.contains("deviceInfo.appliesOpenGlWarnlist()"),
            "GpuWarnlistManager should use explicit warnlist applicability seam");
        assertFalse(gpuWarnlistManagerSource.contains("getBackendName().equals(\"OpenGL\")"),
            "GpuWarnlistManager should avoid backend-name string comparisons for OpenGL warnlist decisions");
        assertTrue(gameRendererSource.contains("VulkanicAPI.getBackendDeviceInfo()"),
            "GameRenderer should use backend-owned device info seam for hardware logging");
        assertFalse(gameRendererSource.contains("VulkanicAPI.getDevice().getDeviceInfo()"),
            "GameRenderer should avoid direct getDevice().getDeviceInfo() logging callsites");
        assertTrue(gameRendererSource.contains("VulkanicAPI.precompileRenderPipeline("),
            "GameRenderer should precompile UI pipelines through VulkanicAPI backend-owned seam");
        assertFalse(gameRendererSource.contains("precompilePipeline(RenderPipelines.GUI"),
            "GameRenderer should avoid direct GpuDevice.precompilePipeline usage in shared startup callsites");
        assertFalse(gameRendererSource.contains("Supports OpenGL"),
            "GameRenderer should avoid hard-coded OpenGL capability wording in shared hardware logging");
        assertTrue(shaderManagerSource.contains("VulkanicAPI.precompileRenderPipeline("),
            "ShaderManager should precompile required pipelines through VulkanicAPI backend-owned seam");
        assertTrue(shaderManagerSource.contains("VulkanicAPI.clearBackendPipelineCache()"),
            "ShaderManager should clear backend-owned pipeline caches through VulkanicAPI seam");
        assertFalse(shaderManagerSource.contains("gpuDevice.precompilePipeline("),
            "ShaderManager should avoid direct GpuDevice.precompilePipeline usage in shared reload path");
        assertFalse(shaderManagerSource.contains("gpuDevice.clearPipelineCache("),
            "ShaderManager should avoid direct GpuDevice.clearPipelineCache usage in shared reload path");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.precompileRenderPipeline(renderPipeline, biFunction);"),
            "VulkanCompatibilityGpuDevice should route precompilePipeline through backend seam");
        assertFalse(vulkanCompatibilityGpuDeviceSource.contains("compatibilityDevice.precompilePipeline("),
            "VulkanCompatibilityGpuDevice should avoid direct compatibility-device precompile delegation");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.createCommandEncoder();"),
            "VulkanCompatibilityGpuDevice should route command-encoder creation through backend seam");
        assertFalse(vulkanCompatibilityGpuDeviceSource.contains("return this.compatibilityDevice.createCommandEncoder();"),
            "VulkanCompatibilityGpuDevice should avoid direct compatibility-device command-encoder delegation");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.createTexture("),
            "VulkanCompatibilityGpuDevice should route texture creation through backend seam");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.createBuffer("),
            "VulkanCompatibilityGpuDevice should route buffer creation through backend seam");
        assertFalse(vulkanCompatibilityGpuDeviceSource.contains("return this.compatibilityDevice.createTexture("),
            "VulkanCompatibilityGpuDevice should avoid direct compatibility-device texture creation delegation");
        assertFalse(vulkanCompatibilityGpuDeviceSource.contains("return this.compatibilityDevice.createBuffer("),
            "VulkanCompatibilityGpuDevice should avoid direct compatibility-device buffer creation delegation");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.createTextureView("),
            "VulkanCompatibilityGpuDevice should route texture-view creation through backend seam");
        assertFalse(vulkanCompatibilityGpuDeviceSource.contains("return this.compatibilityDevice.createTextureView("),
            "VulkanCompatibilityGpuDevice should avoid direct compatibility-device texture-view creation delegation");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.getBackendMaxTextureSize();"),
            "VulkanCompatibilityGpuDevice should route max texture size queries through backend seam");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.getBackendUniformOffsetAlignment();"),
            "VulkanCompatibilityGpuDevice should route uniform offset alignment queries through backend seam");
        assertTrue(vulkanCompatibilityGpuDeviceSource.contains("return this.backend.getBackendDeviceInfo();"),
            "VulkanCompatibilityGpuDevice should route device info through backend seam");
        assertFalse(vulkanCompatibilityGpuDeviceSource.contains("return this.compatibilityDevice.getMaxTextureSize();"),
            "VulkanCompatibilityGpuDevice should avoid direct compatibility-device max texture size queries");
        assertFalse(vulkanCompatibilityGpuDeviceSource.contains("return this.compatibilityDevice.getUniformOffsetAlignment();"),
            "VulkanCompatibilityGpuDevice should avoid direct compatibility-device uniform alignment queries");
        assertTrue(renderTargetSource.contains("VulkanicAPI.createCommandEncoder()"),
            "RenderTarget should acquire command encoders via backend-owned VulkanicAPI seam");
        assertFalse(renderTargetSource.contains("VulkanicAPI.getDevice().createCommandEncoder()"),
            "RenderTarget should avoid direct getDevice().createCommandEncoder() calls");
        assertTrue(renderTargetSource.contains("VulkanicAPI.createRenderPass("),
            "RenderTarget should create render passes through backend-owned VulkanicAPI seam");
        assertFalse(renderTargetSource.contains("VulkanicAPI.getDevice()"),
            "RenderTarget should avoid direct getDevice() usage in render-pass setup");
        assertTrue(renderTargetSource.contains("VulkanicAPI.getBackendMaxTextureSize()"),
            "RenderTarget should query max texture size through backend-owned VulkanicAPI seam");
        assertFalse(renderTargetSource.contains("gpuDevice.getMaxTextureSize()"),
            "RenderTarget should avoid direct device max texture size queries");
        assertTrue(chunkSectionsToRenderSource.contains("VulkanicAPI.createRenderPass("),
            "ChunkSectionsToRender should create render passes through backend-owned VulkanicAPI seam");
        assertFalse(chunkSectionsToRenderSource.contains("VulkanicAPI.getDevice()"),
            "ChunkSectionsToRender should avoid direct getDevice() usage for render-pass creation");
        assertTrue(worldBorderRendererSource.contains("VulkanicAPI.createRenderPass("),
            "WorldBorderRenderer should create render passes through backend-owned VulkanicAPI seam");
        assertTrue(worldBorderRendererSource.contains("VulkanicAPI.createBuffer("),
            "WorldBorderRenderer should allocate buffers through backend-owned VulkanicAPI seam");
        assertFalse(worldBorderRendererSource.contains("VulkanicAPI.getDevice()"),
            "WorldBorderRenderer should avoid direct getDevice() usage in draw path");
        assertTrue(skyRendererSource.contains("VulkanicAPI.createRenderPass("),
            "SkyRenderer should create render passes through backend-owned VulkanicAPI seam");
        assertFalse(skyRendererSource.contains("VulkanicAPI.getDevice()"),
            "SkyRenderer should avoid direct getDevice() usage in sky draw passes");
        assertTrue(cloudRendererSource.contains("VulkanicAPI.createRenderPass("),
            "CloudRenderer should create render passes through backend-owned VulkanicAPI seam");
        assertFalse(cloudRendererSource.contains("VulkanicAPI.getDevice()"),
            "CloudRenderer should avoid direct getDevice() usage in cloud draw path");
        assertTrue(cubeMapSource.contains("VulkanicAPI.createRenderPass("),
            "CubeMap should create render passes through backend-owned VulkanicAPI seam");
        assertFalse(cubeMapSource.contains("VulkanicAPI.getDevice()"),
            "CubeMap should avoid direct getDevice() usage in panorama pass");
        assertTrue(renderTypeSource.contains("VulkanicAPI.createRenderPass("),
            "RenderType should create immediate-mode render passes through backend-owned VulkanicAPI seam");
        assertFalse(renderTypeSource.contains("VulkanicAPI.getDevice()"),
            "RenderType should avoid direct getDevice() usage in immediate draw path");
        assertTrue(debugScreenOverlaySource.contains("VulkanicAPI.createRenderPass("),
            "DebugScreenOverlay should create render passes through backend-owned VulkanicAPI seam");
        assertFalse(debugScreenOverlaySource.contains("VulkanicAPI.getDevice()"),
            "DebugScreenOverlay should avoid direct getDevice() usage in 3d crosshair draw path");
        assertTrue(guiRendererSource.contains("VulkanicAPI.createRenderPass("),
            "GuiRenderer should create render passes through backend-owned VulkanicAPI seam");
        assertTrue(guiRendererSource.contains("VulkanicAPI.createTexture(\"UI items atlas\""),
            "GuiRenderer should allocate item atlas textures through backend-owned VulkanicAPI seam");
        assertTrue(guiRendererSource.contains("VulkanicAPI.createTextureView(this.itemsAtlas)"),
            "GuiRenderer should allocate item atlas texture views through backend-owned VulkanicAPI seam");
        assertFalse(guiRendererSource.contains("GpuDevice gpuDevice = VulkanicAPI.getDevice();"),
            "GuiRenderer should avoid local direct getDevice() variables for atlas setup");
        assertTrue(compiledSectionMeshSource.contains("VulkanicAPI.createBuffer("),
            "CompiledSectionMesh should allocate section buffers through backend-owned VulkanicAPI seam");
        assertFalse(compiledSectionMeshSource.contains("VulkanicAPI.getDevice()"),
            "CompiledSectionMesh should avoid direct getDevice() usage in mesh upload path");
        assertTrue(mainTargetSource.contains("VulkanicAPI.getBackendMaxTextureSize()"),
            "MainTarget should query max texture size through backend-owned VulkanicAPI seam");
        assertFalse(mainTargetSource.contains("VulkanicAPI.getDevice().getMaxTextureSize()"),
            "MainTarget should avoid direct getDevice().getMaxTextureSize() queries");
        assertTrue(guiRendererSource.contains("VulkanicAPI.getBackendMaxTextureSize()"),
            "GuiRenderer should query max texture size through backend-owned VulkanicAPI seam");
        assertFalse(guiRendererSource.contains("VulkanicAPI.getDevice().getMaxTextureSize()"),
            "GuiRenderer should avoid direct getDevice().getMaxTextureSize() queries");
        assertTrue(minecraftTextureAtlasSource.contains("VulkanicAPI.getBackendMaxTextureSize()"),
            "Minecraft TextureAtlas should query max texture size through backend-owned VulkanicAPI seam");
        assertFalse(minecraftTextureAtlasSource.contains("VulkanicAPI.getDevice().getMaxTextureSize()"),
            "Minecraft TextureAtlas should avoid direct getDevice().getMaxTextureSize() queries");
        assertTrue(voxelMapTextureAtlasSource.contains("VulkanicAPI.getBackendMaxTextureSize()"),
            "VoxelMap TextureAtlas should query max texture size through backend-owned VulkanicAPI seam");
        assertFalse(voxelMapTextureAtlasSource.contains("VulkanicAPI.getDevice().getMaxTextureSize()"),
            "VoxelMap TextureAtlas should avoid direct getDevice().getMaxTextureSize() queries");
        assertTrue(dynamicUniformStorageSource.contains("VulkanicAPI.getBackendUniformOffsetAlignment()"),
            "DynamicUniformStorage should use backend-owned uniform offset alignment seam");
        assertTrue(lightingSource.contains("VulkanicAPI.getBackendUniformOffsetAlignment()"),
            "Lighting should use backend-owned uniform offset alignment seam");
        assertFalse(dynamicUniformStorageSource.contains("gpuDevice.getUniformOffsetAlignment()"),
            "DynamicUniformStorage should avoid direct device uniform alignment queries");
        assertFalse(lightingSource.contains("gpuDevice.getUniformOffsetAlignment()"),
            "Lighting should avoid direct device uniform alignment queries");
        assertTrue(tracyFrameCaptureSource.contains("VulkanicAPI.createCommandEncoder()"),
            "TracyFrameCapture should acquire command encoders via backend-owned VulkanicAPI seam");
        assertFalse(tracyFrameCaptureSource.contains("VulkanicAPI.getDevice().createCommandEncoder()"),
            "TracyFrameCapture should avoid direct getDevice().createCommandEncoder() calls");
        assertTrue(levelRendererSource.contains("VulkanicAPI.createCommandEncoder()"),
            "LevelRenderer should acquire command encoders via backend-owned VulkanicAPI seam");
        assertFalse(levelRendererSource.contains("VulkanicAPI.getDevice().createCommandEncoder()"),
            "LevelRenderer should avoid direct getDevice().createCommandEncoder() calls");
        assertTrue(screenshotSource.contains("VulkanicAPI.createCommandEncoder()"),
            "Screenshot should acquire command encoders via backend-owned VulkanicAPI seam");
        assertFalse(screenshotSource.contains("VulkanicAPI.getDevice().createCommandEncoder()"),
            "Screenshot should avoid direct getDevice().createCommandEncoder() calls");
        assertTrue(debugEntrySystemSpecsSource.contains("VulkanicAPI.getBackendDeviceInfo()"),
            "DebugEntrySystemSpecs should use backend-owned device info seam for display strings");
        assertFalse(debugEntrySystemSpecsSource.contains("VulkanicAPI.getDevice().getDeviceInfo()"),
            "DebugEntrySystemSpecs should avoid direct getDevice().getDeviceInfo() queries");
        assertFalse(debugEntrySystemSpecsSource.contains("gpuDevice.getVersion()"),
            "DebugEntrySystemSpecs should avoid raw version string composition when backend-neutral device info helpers exist");
        assertTrue(minecraftSource.contains("getBackendOptionalFeatureNames()"),
            "Minecraft should log backend-owned optional rendering features instead of device-wrapper extension metadata");
        assertFalse(minecraftSource.contains("Using optional rendering extensions:"),
            "Minecraft should avoid OpenGL extension wording in shared startup logging");
        assertTrue(renderSystemSource.contains("VulkanicAPI.createRendererDevice("),
            "RenderSystem should create renderer devices through the backend-neutral VulkanicAPI seam");
        assertFalse(renderSystemSource.contains("new GlDevice("),
            "RenderSystem should avoid hard-coded GlDevice construction in the shared startup path");
        assertFalse(renderSystemSource.contains("OpenGL Vendor:"),
            "RenderSystem should avoid direct OpenGL startup logging in the shared startup path");
    }

    @Test
    public void testPrimitiveAndPolygonLegacyMappingsRemainAvailable() {
        assertTrue(VulkanicPrimitiveMode.fromLegacyGlConstant(VulkanicAPI.GL_TRIANGLES).isPresent(),
            "Primitive mode mapping should recognize GL_TRIANGLES");
        assertTrue(VulkanicPrimitiveMode.fromLegacyGlConstant(VulkanicAPI.GL_PATCHES).isPresent(),
            "Primitive mode mapping should recognize GL_PATCHES for tessellation draw routing");
        assertTrue(VulkanicPolygonFace.fromLegacyGlConstant(VulkanicAPI.GL_FRONT_AND_BACK).isPresent(),
            "Polygon-face mapping should recognize GL_FRONT_AND_BACK");
        assertTrue(VulkanicPolygonMode.fromLegacyGlConstant(VulkanicAPI.GL_FILL).isPresent(),
            "Polygon-mode mapping should recognize GL_FILL");
        assertTrue(VulkanicIndexType.fromLegacyGlConstant(VulkanicAPI.GL_UNSIGNED_INT).isPresent(),
            "Index-type mapping should recognize GL_UNSIGNED_INT");
        assertTrue(VulkanicVertexAttributeType.fromLegacyGlConstant(VulkanicAPI.GL_FLOAT).isPresent(),
            "Vertex attribute type mapping should recognize GL_FLOAT");
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_CLAMP_TO_EDGE).isPresent(),
            "Texture parameter value mapping should recognize GL_CLAMP_TO_EDGE");
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_COMPARE_REF_TO_TEXTURE).isPresent(),
            "Texture parameter value mapping should recognize GL_COMPARE_REF_TO_TEXTURE");
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_LINEAR_MIPMAP_NEAREST).isPresent(),
            "Texture parameter value mapping should recognize GL_LINEAR_MIPMAP_NEAREST");
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR).isPresent(),
            "Texture parameter value mapping should recognize GL_NEAREST_MIPMAP_LINEAR");
        assertTrue(VulkanicTextureSwizzleComponent.fromLegacyGlConstant(VulkanicAPI.GL_RED).isPresent(),
            "Texture swizzle component mapping should recognize GL_RED");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_2D).isPresent(),
            "Uniform reflection type mapping should recognize GL_SAMPLER_2D");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_CUBE).isPresent(),
            "Uniform reflection type mapping should recognize GL_SAMPLER_CUBE");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_UNSIGNED_INT_VEC3).isPresent(),
            "Uniform reflection type mapping should recognize GL_UNSIGNED_INT_VEC3");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_BOOL_VEC4).isPresent(),
            "Uniform reflection type mapping should recognize GL_BOOL_VEC4");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_IMAGE_2D_ARRAY).isPresent(),
            "Uniform reflection type mapping should recognize GL_IMAGE_2D_ARRAY");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_INT_IMAGE_2D_ARRAY).isPresent(),
            "Uniform reflection type mapping should recognize GL_INT_IMAGE_2D_ARRAY");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_2D)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_SAMPLER_2D"))
                .isSampler(),
            "GL_SAMPLER_2D reflection type should classify as sampler");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_CUBE)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_SAMPLER_CUBE"))
                .isSampler(),
            "GL_SAMPLER_CUBE reflection type should classify as sampler");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_IMAGE_2D_ARRAY)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_IMAGE_2D_ARRAY"))
                .isImage(),
            "GL_IMAGE_2D_ARRAY reflection type should classify as image");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_INT_IMAGE_2D_ARRAY)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_INT_IMAGE_2D_ARRAY"))
                .isImage(),
            "GL_INT_IMAGE_2D_ARRAY reflection type should classify as image");
    }

    @Test
    public void testProgramUniformsTypeIntrospectionUsesTypedReflectionHelper() throws IOException {
        String relative = "net/irisshaders/iris/gl/program/ProgramUniforms.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertTrue(source.contains("activeUniformInfo.reflectionType()"),
            "ProgramUniforms should resolve reflected type through ActiveUniformInfo.reflectionType typed metadata: " + relative);
        assertTrue(source.contains("activeUniformInfo.reflectionTypeName()"),
            "ProgramUniforms should use ActiveUniformInfo.reflectionTypeName for unsupported-type diagnostics: " + relative);
        assertTrue(source.contains("type.isSampler()"),
            "ProgramUniforms should classify sampler uniforms using VulkanicUniformReflectionType.isSampler(): " + relative);
        assertTrue(source.contains("type.isImage()"),
            "ProgramUniforms should classify image uniforms using VulkanicUniformReflectionType.isImage(): " + relative);
        assertTrue(source.contains("VulkanicAPI.getActiveUniforms("),
            "ProgramUniforms should enumerate reflected uniforms through VulkanicAPI.getActiveUniforms typed metadata seam: " + relative);
        assertFalse(source.contains("IrisRenderSystem.getActiveUniform("),
            "ProgramUniforms should avoid IrisRenderSystem active-uniform wrapper usage in typed reflection path: " + relative);
        assertFalse(source.contains("VulkanicProgramParameterName.ACTIVE_UNIFORMS"),
            "ProgramUniforms should avoid direct ACTIVE_UNIFORMS query plumbing when typed active-uniform enumeration helper is available: " + relative);
        assertFalse(source.contains("activeUniformInfo.legacyType()"),
            "ProgramUniforms should avoid direct legacy active-uniform type usage when typed reflection metadata is available: " + relative);

        Pattern rawTypeComparisonPattern = Pattern.compile("type\\s*==\\s*VulkanicAPI\\.GL_");
        assertFalse(rawTypeComparisonPattern.matcher(source).find(),
            "ProgramUniforms should avoid raw reflected type comparisons against VulkanicAPI.GL_* constants: " + relative);

        Pattern hardcodedSamplerSwitchPattern = Pattern.compile("case\\s+SAMPLER_1D|case\\s+SAMPLER_2D|case\\s+SAMPLER_3D");
        assertFalse(hardcodedSamplerSwitchPattern.matcher(source).find(),
            "ProgramUniforms sampler classification should avoid hardcoded sampler enum cases in favor of typed helpers: " + relative);
    }

    @Test
    public void testActiveUniformReflectionCallsitesPreferTypedMetadataHelpers() throws IOException {
        List<String> allowedReflectionSeamFiles = List.of(
            "net/vulkanic/VulkanicAPI.java",
            "net/irisshaders/iris/gl/IrisRenderSystem.java"
        );

        List<String> offenders = new java.util.ArrayList<>();

        try (var paths = Files.walk(SRC_MAIN_JAVA)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    Path relativePath = SRC_MAIN_JAVA.relativize(path);
                    String relative = relativePath.toString().replace('\\', '/');

                    if (allowedReflectionSeamFiles.contains(relative)) {
                        return;
                    }

                    try {
                        String source = Files.readString(path);
                        if (source.contains("VulkanicAPI.getActiveUniform(")
                            || source.contains("VulkanicAPI.retrieveActiveUniformBlockName(")
                            || source.contains("IrisRenderSystem.getActiveUniform(")) {
                            offenders.add(relative);
                        }
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
        }

        assertTrue(offenders.isEmpty(),
            "Active-uniform reflection callsites should use typed metadata helpers (getActiveUniforms/getActiveUniformBlocks); offenders: " + offenders);
    }

    @Test
    public void testKeyRenderersAvoidRawPolygonModeConstants() throws IOException {
        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/renderer/DebugRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/TestRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("setPolygonMode(ctx, VulkanicAPI.GL_FRONT_AND_BACK"),
                "Renderer should use typed polygon-mode overloads: " + relative);
        }
    }

    @Test
    public void testKeyRenderersAvoidRawDrawModeConstants() throws IOException {
        Pattern rawDrawModePattern = Pattern.compile("draw(?:Arrays|Elements|IndexedInstanced)\\s*\\([^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/renderer/ScreenQuad.java",
            "com/seibel/distanthorizons/core/render/renderer/TestRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/DebugRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java",
            "net/vulkanic/backends/opengl/OpenGLRenderPass.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawDrawModePattern.matcher(source).find(),
                "Renderer should use typed draw-mode overloads: " + relative);
        }
    }

    @Test
    public void testKeyRenderersAvoidRawVertexAttributeTypeConstants() throws IOException {
        Pattern rawVertexAttribPattern = Pattern.compile("setVertexAttrib(?:I)?Pointer\\s*\\([^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawVertexAttribPattern.matcher(source).find(),
                "Renderer should use typed vertex-attribute types: " + relative);
        }
    }

    @Test
    public void testDhTextureSetupAvoidsRawTextureParameterValueConstants() throws IOException {
        Pattern rawTextureParamValuePattern = Pattern.compile("texParameteri\\s*\\([^\\n]*VulkanicTextureParameterName\\.[A-Z_]+,\\s*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/glObject/texture/DhColorTexture.java",
            "com/seibel/distanthorizons/core/render/glObject/texture/DHDepthTexture.java",
            "com/seibel/distanthorizons/core/render/renderer/DhFadeRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/VanillaFadeRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/FogRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/SSAORenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawTextureParamValuePattern.matcher(source).find(),
                "Renderer should use typed texture-parameter values: " + relative);
        }
    }

    @Test
    public void testDhRenderersUseTypedViewportAndClearColorQueryHelpers() throws IOException {
        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/glObject/GLState.java",
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("GL_VIEWPORT"),
                "Renderer state queries should avoid raw GL_VIEWPORT constants: " + relative);
            assertFalse(source.contains("GL_COLOR_CLEAR_VALUE"),
                "Renderer state queries should avoid raw GL_COLOR_CLEAR_VALUE constants: " + relative);
        }
    }

    @Test
    public void testIrisTextureParameterCallsitesAvoidRawPnameAndValueConstants() throws IOException {
        Pattern rawIrisTextureParamPattern = Pattern.compile("IrisRenderSystem\\.texParameteri\\s*\\([^\\n]*VulkanicAPI\\.GL_TEXTURE_[A-Z_]+[^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "net/irisshaders/iris/gl/IrisRenderSystem.java",
            "net/irisshaders/iris/shadows/ShadowCompositeRenderer.java",
            "net/irisshaders/iris/shadows/ShadowRenderer.java",
            "net/irisshaders/iris/pipeline/FinalPassRenderer.java",
            "net/irisshaders/iris/pipeline/CompositeRenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawIrisTextureParamPattern.matcher(source).find(),
                "Iris texture setup should use typed texture parameter APIs: " + relative);
        }
    }

    @Test
    public void testIrisPbrUtilitiesUseTypedViewportAndClearColorQueryHelpers() throws IOException {
        String relative = "net/irisshaders/iris/pbr/util/TextureManipulationUtil.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("GL_VIEWPORT"),
            "Iris PBR utilities should avoid raw GL_VIEWPORT constants: " + relative);
        assertFalse(source.contains("GL_COLOR_CLEAR_VALUE"),
            "Iris PBR utilities should avoid raw GL_COLOR_CLEAR_VALUE constants: " + relative);
    }

    @Test
    public void testIrisSwizzleSetupAvoidsRawSwizzlePnameConstants() throws IOException {
        List<String> files = List.of(
            "net/irisshaders/iris/gl/IrisRenderSystem.java",
            "net/irisshaders/iris/shadows/ShadowRenderer.java",
            "net/irisshaders/iris/pipeline/programs/ExtendedShader.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("GL_TEXTURE_SWIZZLE_RGBA"),
                "Iris swizzle setup should use typed swizzle helpers: " + relative);
        }
    }

    @Test
    public void testIrisComputeSynchronizationAvoidsRawMemoryBarrierBitmasks() throws IOException {
        Pattern rawIrisBarrierPattern = Pattern.compile("IrisRenderSystem\\.memoryBarrier\\s*\\([^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "net/irisshaders/iris/gl/program/Program.java",
            "net/irisshaders/iris/gl/program/ComputeProgram.java",
            "net/irisshaders/iris/pathways/colorspace/ColorSpaceComputeConverter.java",
            "net/irisshaders/iris/shadows/ShadowCompositeRenderer.java",
            "net/irisshaders/iris/pipeline/CompositeRenderer.java",
            "net/irisshaders/iris/pipeline/FinalPassRenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawIrisBarrierPattern.matcher(source).find(),
                "Iris compute synchronization should use typed resource-barrier helpers: " + relative);
        }
    }

    @Test
    public void testSelectedShaderStatusQueriesAvoidRawProgramAndShaderPnames() throws IOException {
        Pattern rawStatusQueryPattern = Pattern.compile("(?:getProgramParameter|getShaderParameter|getProgramiv)\\s*\\([^\\n]*(?:VulkanicAPI\\.GL_|35714|35713)");

        List<String> files = List.of(
            "net/irisshaders/iris/gl/program/ComputeProgram.java",
            "net/irisshaders/iris/gl/program/ProgramUniforms.java",
            "net/irisshaders/iris/gl/shader/GlShader.java",
            "net/irisshaders/iris/gl/shader/ProgramCreator.java",
            "net/irisshaders/iris/pipeline/programs/ShaderCreator.java",
            "net/irisshaders/iris/pipeline/programs/ShaderMap.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java",
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java",
            "net/sodium/client/gl/shader/GlShader.java",
            "net/sodium/client/gl/shader/GlProgram.java",
            "net/blaze3d/opengl/GlProgram.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawStatusQueryPattern.matcher(source).find(),
                "Shader/program status queries should use typed parameter names: " + relative);
        }
    }

    @Test
    public void testSelectedShaderCompilationAndLinkChecksUseStatusHelpers() throws IOException {
        List<String> programFiles = List.of(
            "net/blaze3d/opengl/GlProgram.java",
            "net/irisshaders/iris/gl/shader/ProgramCreator.java",
            "net/irisshaders/iris/pipeline/programs/ShaderMap.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java",
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java",
            "net/sodium/client/gl/shader/GlProgram.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java"
        );

        for (String relative : programFiles) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("isProgramLinkSuccessful("),
                "Program link checks should route through VulkanicAPI.isProgramLinkSuccessful helper: " + relative);
            assertFalse(source.contains("!= VulkanicAPI.GL_TRUE"),
                "Program link checks should avoid raw GL_TRUE comparisons: " + relative);
            assertFalse(source.contains("== VulkanicAPI.GL_FALSE"),
                "Program link checks should avoid raw GL_FALSE comparisons: " + relative);
            assertFalse(source.contains("!= 1"),
                "Program link checks should avoid raw numeric GL_TRUE comparisons: " + relative);
        }

        List<String> shaderFiles = List.of(
            "net/blaze3d/opengl/GlDevice.java",
            "net/irisshaders/iris/gl/shader/GlShader.java",
            "net/irisshaders/iris/pipeline/programs/ShaderCreator.java",
            "net/sodium/client/gl/shader/GlShader.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java"
        );

        for (String relative : shaderFiles) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("isShaderCompileSuccessful("),
                "Shader compile checks should route through VulkanicAPI.isShaderCompileSuccessful helper: " + relative);
            assertFalse(source.contains("!= VulkanicAPI.GL_TRUE"),
                "Shader compile checks should avoid raw GL_TRUE comparisons: " + relative);
            assertFalse(source.contains("== VulkanicAPI.GL_FALSE"),
                "Shader compile checks should avoid raw GL_FALSE comparisons: " + relative);
            assertFalse(source.contains("!= 1"),
                "Shader compile checks should avoid raw numeric GL_TRUE comparisons: " + relative);
        }
    }

    @Test
    public void testGlProgramUsesTypedActiveUniformBlockParameterName() throws IOException {
        String relative = "net/blaze3d/opengl/GlProgram.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("getProgramParameter(VulkanicAPI.getCommandContext(), this.programId, 35382)"),
            "GlProgram should avoid raw numeric active-uniform-block pname queries: " + relative);
        assertTrue(source.contains("VulkanicAPI.getActiveUniformBlocks("),
            "GlProgram should route active uniform block reflection metadata through VulkanicAPI.getActiveUniformBlocks seam: " + relative);
        assertFalse(source.contains("retrieveActiveUniformBlockName("),
            "GlProgram should avoid low-level per-block name queries when typed block metadata helper is available: " + relative);
    }

    @Test
    public void testSelectedSsboBufferOperationsAvoidRawShaderStorageTargetConstants() throws IOException {
        Pattern rawSsboTargetPattern = Pattern.compile("(?:IrisRenderSystem|VulkanicAPI)\\.(?:bindBufferBase|bufferStorage|clearBufferSubData|bufferSubData)\\s*\\([^\\n]*VulkanicAPI\\.GL_SHADER_STORAGE_BUFFER");

        List<String> files = List.of(
            "net/irisshaders/iris/pipeline/IrisRenderingPipeline.java",
            "net/irisshaders/iris/gl/buffer/ShaderStorageBuffer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawSsboTargetPattern.matcher(source).find(),
                "SSBO operations should use typed buffer-target APIs: " + relative);
        }
    }

    @Test
    public void testSelectedIrisCallsitesAvoidExplicitTexture2dTargetArguments() throws IOException {
        List<String> files = List.of(
            "net/irisshaders/iris/pipeline/programs/ExtendedShader.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java",
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java",
            "net/irisshaders/iris/pbr/TextureTracker.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("TextureType.TEXTURE_2D.getGlType()"),
                "Iris callsite should use typed/default-2D overloads instead of explicit GL target arguments: " + relative);
        }
    }

    @Test
    public void testSelectedIrisTextureIdCallsitesPreferTypedCoreApiHelper() throws IOException {
        List<String> files = List.of(
            "net/irisshaders/iris/pipeline/CustomTextureManager.java",
            "net/irisshaders/iris/targets/RenderTargets.java",
            "net/irisshaders/iris/pipeline/FinalPassRenderer.java",
            "net/irisshaders/iris/pipeline/IrisRenderingPipeline.java",
            "net/irisshaders/iris/pbr/TextureTracker.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("VulkanicAPI.getTextureHandle("),
                "Selected Iris texture-id callsites should avoid direct VulkanicAPI.getTextureHandle usage: " + relative);
            assertTrue(source.contains("VulkanicCoreAPI.textureId("),
                "Selected Iris texture-id callsites should route through VulkanicCoreAPI.textureId helper: " + relative);
        }

        String coreApiRelative = "net/vulkanic/VulkanicCoreAPI.java";
        String coreApiSource = Files.readString(SRC_MAIN_JAVA.resolve(coreApiRelative));

        assertTrue(coreApiSource.contains("public static int textureId(GpuTextureView textureView)"),
            "VulkanicCoreAPI should expose textureId(GpuTextureView) for typed texture-view callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("return textureId(textureView.texture());"),
            "VulkanicCoreAPI texture-view helper should delegate through typed texture helper: " + coreApiRelative);
    }

    @Test
    public void testGlCommandEncoderPrefersTypedTextureIdHelper() throws IOException {
        String relative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("VulkanicAPI.getTextureHandle("),
            "GlCommandEncoder should avoid direct VulkanicAPI.getTextureHandle usage in frontend callsites: " + relative);
        assertTrue(source.contains("VulkanicCoreAPI.textureId("),
            "GlCommandEncoder should resolve texture IDs through VulkanicCoreAPI.textureId typed helper: " + relative);
    }

    @Test
    public void testSelectedContextHotspotsPreferLocalCommandContextHelper() throws IOException {
        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java",
            "net/sodium/client/gl/shader/GlProgram.java",
            "com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java"
        );

        Pattern inlineContextPattern = Pattern.compile("VulkanicAPI\\.[A-Za-z0-9_]+\\s*\\(\\s*VulkanicAPI\\.getCommandContext\\s*\\(");

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("private static CommandContext commandContext()"),
                "Context-heavy frontend wrappers should centralize context acquisition behind a helper: " + relative);
            assertTrue(source.contains("return VulkanicAPI.getCommandContext();"),
                "Context helper should delegate to VulkanicAPI.getCommandContext for command-context retrieval: " + relative);
            assertFalse(inlineContextPattern.matcher(source).find(),
                "Context-heavy frontend wrappers should avoid inline VulkanicAPI.getCommandContext() in VulkanicAPI calls: " + relative);
        }
    }

    @Test
    public void testGraphicsBackendExposesTypedSeamsForClearLogicAndUniformLocations() throws IOException {
        String relative = "net/vulkanic/GraphicsBackend.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertTrue(source.contains("default void clearBuffers(CommandContext ctx, VulkanicClearBuffer... buffers)"),
            "GraphicsBackend should expose typed clear-buffer overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default void setLogicOp(CommandContext ctx, VulkanicLogicOp opcode)"),
            "GraphicsBackend should expose typed logic-op overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default VulkanicUniformLocation resolveUniformLocation("),
            "GraphicsBackend should expose a typed uniform-location resolver at the backend boundary: " + relative);
        assertTrue(source.contains("default VulkanicShaderHandle createShaderHandle(CommandContext ctx, VulkanicShaderStage shaderStage)"),
            "GraphicsBackend should expose typed shader-handle creation seams at the backend boundary: " + relative);
        assertTrue(source.contains("default VulkanicProgramHandle createShaderProgramHandle(CommandContext ctx)"),
            "GraphicsBackend should expose typed program-handle creation seams at the backend boundary: " + relative);
        assertTrue(source.contains("default void attachShader(CommandContext ctx, VulkanicProgramHandle program, VulkanicShaderHandle shader)"),
            "GraphicsBackend should expose typed shader/program attachment seams at the backend boundary: " + relative);
        assertTrue(source.contains("default void bindUniformBufferRange(CommandContext ctx, VulkanicBufferTarget target"),
            "GraphicsBackend should expose typed uniform-buffer range binding overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default void texBuffer(CommandContext ctx, VulkanicTextureTarget target"),
            "GraphicsBackend should expose typed tex-buffer target overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default void uploadTexture2D(")
                && source.contains("VulkanicTextureUploadFormat uploadFormat"),
            "GraphicsBackend should expose typed texture-upload format overloads at the backend boundary: " + relative);
    }

    @Test
    public void testCenterDepthSamplerUsesTypedTextureUploadFormatSeam() throws IOException {
        String centerDepthRelative = "net/irisshaders/iris/pathways/CenterDepthSampler.java";
        String centerDepthSource = Files.readString(SRC_MAIN_JAVA.resolve(centerDepthRelative));

        assertTrue(centerDepthSource.contains("VulkanicTextureUploadFormat.RED32_SFLOAT"),
            "CenterDepthSampler should express center-depth texture upload intent through typed Vulkanic upload formats: " + centerDepthRelative);
        assertFalse(centerDepthSource.contains("PixelType.FLOAT.getGlFormat()"),
            "CenterDepthSampler should avoid raw GL pixel-type constants for center-depth texture setup: " + centerDepthRelative);

        String irisRenderSystemRelative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String irisRenderSystemSource = Files.readString(SRC_MAIN_JAVA.resolve(irisRenderSystemRelative));
        assertTrue(irisRenderSystemSource.contains("VulkanicTextureUploadFormat uploadFormat"),
            "IrisRenderSystem should expose typed texture-upload overloads for backend-neutral callsites: " + irisRenderSystemRelative);
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.uploadTexture2D(VulkanicAPI.getCommandContext(), target, level, uploadFormat"),
            "IrisRenderSystem typed texture-upload overloads should route through VulkanicAPI typed seams: " + irisRenderSystemRelative);
    }

    @Test
    public void testBackendOwnedDeviceIdentitySeamsAreUsedByShaderAndDebugCallsites() throws IOException {
        String standardMacrosRelative = "net/irisshaders/iris/gl/shader/StandardMacros.java";
        String standardMacrosSource = Files.readString(SRC_MAIN_JAVA.resolve(standardMacrosRelative));
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getBackendVendorName()"),
            "StandardMacros should use backend-owned vendor identity seam instead of RenderSystem device metadata: " + standardMacrosRelative);
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getBackendRendererName()"),
            "StandardMacros should use backend-owned renderer identity seam instead of RenderSystem device metadata: " + standardMacrosRelative);
        assertFalse(standardMacrosSource.contains("RenderSystem.getDevice().getVendor()"),
            "StandardMacros should avoid direct RenderSystem.getDevice().getVendor() callsites: " + standardMacrosRelative);
        assertFalse(standardMacrosSource.contains("RenderSystem.getDevice().getRenderer()"),
            "StandardMacros should avoid direct RenderSystem.getDevice().getRenderer() callsites: " + standardMacrosRelative);

        String irisRelative = "net/irisshaders/iris/Iris.java";
        String irisSource = Files.readString(SRC_MAIN_JAVA.resolve(irisRelative));
        assertTrue(irisSource.contains("VulkanicAPI.getBackendEnabledExtensions()"),
            "Iris debug callback setup should use backend-owned extension seam: " + irisRelative);
        assertFalse(irisSource.contains("RenderSystem.getDevice().getEnabledExtensions()"),
            "Iris debug callback setup should avoid direct RenderSystem device extension access: " + irisRelative);

        String minecraftRelative = "net/minecraft/client/Minecraft.java";
        String minecraftSource = Files.readString(SRC_MAIN_JAVA.resolve(minecraftRelative));
        assertTrue(minecraftSource.contains("VulkanicAPI.getBackendOptionalFeatureNames()"),
            "Minecraft startup/system-report diagnostics should use backend-owned optional-feature seam: " + minecraftRelative);
        assertFalse(minecraftSource.contains("VulkanicAPI.getDevice().getOptionalFeatureNames()"),
            "Minecraft startup/system-report diagnostics should avoid device-wrapper optional-feature calls: " + minecraftRelative);

        String compatDeviceRelative = "net/vulkanic/backends/vulkan/VulkanCompatibilityGpuDevice.java";
        String compatDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve(compatDeviceRelative));
        assertTrue(compatDeviceSource.contains("this.backend.getBackendVendorName()"),
            "Vulkan compatibility device should source vendor metadata from backend-owned seam: " + compatDeviceRelative);
        assertTrue(compatDeviceSource.contains("this.backend.getBackendRendererName()"),
            "Vulkan compatibility device should source renderer metadata from backend-owned seam: " + compatDeviceRelative);
        assertTrue(compatDeviceSource.contains("this.backend.getBackendEnabledExtensions()"),
            "Vulkan compatibility device should source extension diagnostics from backend-owned seam: " + compatDeviceRelative);
        assertTrue(compatDeviceSource.contains("this.backend.getBackendOptionalFeatureNames()"),
            "Vulkan compatibility device should source optional feature diagnostics from backend-owned seam: " + compatDeviceRelative);
        assertFalse(compatDeviceSource.contains("this.compatibilityDevice.getVendor()"),
            "Vulkan compatibility device should avoid leaking compatibility-device vendor metadata: " + compatDeviceRelative);
        assertFalse(compatDeviceSource.contains("this.compatibilityDevice.getRenderer()"),
            "Vulkan compatibility device should avoid leaking compatibility-device renderer metadata: " + compatDeviceRelative);
    }

    @Test
    public void testSpirvAndPipelineLayoutPrepSeamsExist() throws IOException {
        String pipelineDescriptorRelative = "net/vulkanic/PipelineDescriptor.java";
        String pipelineDescriptorSource = Files.readString(SRC_MAIN_JAVA.resolve(pipelineDescriptorRelative));

        assertTrue(pipelineDescriptorSource.contains("fromPortableStateAndSpirvModules("),
            "PipelineDescriptor should expose portable-state + SPIR-V module factory seam for Vulkan pipeline bring-up: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("fromRenderPipelineAndSpirvModules("),
            "PipelineDescriptor should expose RenderPipeline + SPIR-V module factory seam for staged migration: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("record PushConstantRange("),
            "PipelineDescriptor should expose push-constant layout metadata seam for future Vulkan pipeline layouts: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("getPipelineCompilationKey()"),
            "PipelineDescriptor should expose deterministic compilation-key seam including SPIR-V/push-constant metadata: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("withResourceLayout("),
            "PipelineDescriptor should expose explicit reflected resource-layout override seam for Vulkan descriptor-layout prep: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("hasExplicitResourceLayout()"),
            "PipelineDescriptor should expose explicit-layout presence seam for migration-safe callsite behavior: " + pipelineDescriptorRelative);

        String vulkanicApiRelative = "net/vulkanic/VulkanicAPI.java";
        String vulkanicApiSource = Files.readString(SRC_MAIN_JAVA.resolve(vulkanicApiRelative));
        assertTrue(vulkanicApiSource.contains("public static PipelineHandle createPipeline(")
            && vulkanicApiSource.contains("PipelineDescriptor.PortableState portableState")
            && vulkanicApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicAPI should expose a SPIR-V-aware portable pipeline creation seam: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("createPipelineDescriptor(")
            && vulkanicApiSource.contains("PipelineDescriptor.PortableState portableState")
            && vulkanicApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicAPI should expose a SPIR-V-aware descriptor creation seam: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("deriveResourceLayoutFromProgramReflection("),
            "VulkanicAPI should expose reflection-derived pipeline resource-layout seams for Vulkan descriptor-layout preparation: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("withReflectedResourceLayout("),
            "VulkanicAPI should expose descriptor enrichment seam from linked-program reflection metadata: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("java.util.Set<VulkanicShaderStage> stages"),
            "VulkanicAPI reflection/layout prep seams should support explicit stage-visibility metadata for Vulkan descriptor layout synthesis: " + vulkanicApiRelative);

        String coreApiRelative = "net/vulkanic/VulkanicCoreAPI.java";
        String coreApiSource = Files.readString(SRC_MAIN_JAVA.resolve(coreApiRelative));
        assertTrue(coreApiSource.contains("public static PipelineHandle createPipeline(")
            && coreApiSource.contains("PipelineDescriptor.PortableState portableState")
            && coreApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicCoreAPI should mirror SPIR-V-aware portable pipeline creation seam for typed frontend callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("public static PipelineDescriptor createPipelineDescriptor(")
            && coreApiSource.contains("PipelineDescriptor.PortableState portableState")
            && coreApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicCoreAPI should mirror SPIR-V-aware descriptor creation seam for typed frontend callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("deriveResourceLayoutFromProgramReflection("),
            "VulkanicCoreAPI should mirror reflection-derived resource-layout seams for typed frontend callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("withReflectedResourceLayout("),
            "VulkanicCoreAPI should mirror descriptor enrichment from reflection metadata: " + coreApiRelative);
        assertTrue(coreApiSource.contains("java.util.Set<VulkanicShaderStage> stages"),
            "VulkanicCoreAPI should mirror explicit stage-visibility reflection/layout seams for typed frontend callsites: " + coreApiRelative);
    }

    @Test
    public void testSelectedShaderLifecycleHotspotsUseTypedHandleSeams() throws IOException {
        String sodiumShaderRelative = "net/sodium/client/gl/shader/GlShader.java";
        String sodiumShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(sodiumShaderRelative));
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, type.stage)"),
            "Sodium GlShader should create shaders via typed handle seam: " + sodiumShaderRelative);
        assertTrue(sodiumShaderSource.contains("ShaderWorkarounds.safeShaderSource(handle, parsedShader.src())"),
            "Sodium GlShader should upload shader source via typed handle seam: " + sodiumShaderRelative);
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(this.handle()))"),
            "Sodium GlShader should delete shaders via typed handle seam: " + sodiumShaderRelative);

        String irisShaderRelative = "net/irisshaders/iris/gl/shader/GlShader.java";
        String irisShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(irisShaderRelative));
        assertTrue(irisShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, type.stage)"),
            "Iris GlShader should create shaders via typed handle seam: " + irisShaderRelative);
        assertTrue(irisShaderSource.contains("ShaderWorkarounds.safeShaderSource(handle, src)"),
            "Iris GlShader should upload shader source via typed handle seam: " + irisShaderRelative);
        assertTrue(irisShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(this.getGlId()))"),
            "Iris GlShader should delete shaders via typed handle seam: " + irisShaderRelative);

        String sodiumProgramRelative = "net/sodium/client/gl/shader/GlProgram.java";
        String sodiumProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(sodiumProgramRelative));
        assertTrue(sodiumProgramSource.contains("VulkanicAPI.createShaderProgramHandle(commandContext())"),
            "Sodium GlProgram should create programs via typed handle seam: " + sodiumProgramRelative);
        assertTrue(sodiumProgramSource.contains("VulkanicAPI.attachShader(commandContext(), this.program, VulkanicShaderHandle.of(shader.handle()))"),
            "Sodium GlProgram should attach shaders via typed handle seam: " + sodiumProgramRelative);

        String programCreatorRelative = "net/irisshaders/iris/gl/shader/ProgramCreator.java";
        String programCreatorSource = Files.readString(SRC_MAIN_JAVA.resolve(programCreatorRelative));
        assertTrue(programCreatorSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Iris ProgramCreator should create programs via typed handle seam: " + programCreatorRelative);
        assertTrue(programCreatorSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(shader.getHandle()))"),
            "Iris ProgramCreator should attach shaders via typed handle seam: " + programCreatorRelative);

        String shaderCreatorRelative = "net/irisshaders/iris/pipeline/programs/ShaderCreator.java";
        String shaderCreatorSource = Files.readString(SRC_MAIN_JAVA.resolve(shaderCreatorRelative));
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Iris ShaderCreator should create programs via typed handle seam: " + shaderCreatorRelative);
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(s))"),
            "Iris ShaderCreator should attach shaders via typed handle seam: " + shaderCreatorRelative);
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.createShaderHandle(ctx, shaderType.stage)"),
            "Iris ShaderCreator should create shaders via typed handle seam: " + shaderCreatorRelative);

        String dhGenericRelative = "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java";
        String dhGenericSource = Files.readString(SRC_MAIN_JAVA.resolve(dhGenericRelative));
        assertTrue(dhGenericSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "IrisGenericRenderProgram should create programs via typed handle seam: " + dhGenericRelative);
        assertTrue(dhGenericSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(id))"),
            "IrisGenericRenderProgram should delete programs via typed handle seam: " + dhGenericRelative);

        String dhLodRelative = "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java";
        String dhLodSource = Files.readString(SRC_MAIN_JAVA.resolve(dhLodRelative));
        assertTrue(dhLodSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "IrisLodRenderProgram should create programs via typed handle seam: " + dhLodRelative);
        assertTrue(dhLodSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(id))"),
            "IrisLodRenderProgram should delete programs via typed handle seam: " + dhLodRelative);

        String blazeGlProgramRelative = "net/blaze3d/opengl/GlProgram.java";
        String blazeGlProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(blazeGlProgramRelative));
        assertTrue(blazeGlProgramSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Blaze3D GlProgram should create programs via typed handle seam: " + blazeGlProgramRelative);
        assertTrue(blazeGlProgramSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(glShaderModule.getShaderId()))"),
            "Blaze3D GlProgram should attach shaders via typed handle seam: " + blazeGlProgramRelative);
        assertTrue(blazeGlProgramSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(this.programId))"),
            "Blaze3D GlProgram should delete programs via typed handle seam: " + blazeGlProgramRelative);

        String blazeShaderModuleRelative = "net/blaze3d/opengl/GlShaderModule.java";
        String blazeShaderModuleSource = Files.readString(SRC_MAIN_JAVA.resolve(blazeShaderModuleRelative));
        assertTrue(blazeShaderModuleSource.contains("net.vulkanic.VulkanicShaderHandle.of(this.shaderId)"),
            "Blaze3D GlShaderModule should delete shaders via typed handle seam: " + blazeShaderModuleRelative);

        String blazeDeviceRelative = "net/blaze3d/opengl/GlDevice.java";
        String blazeDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve(blazeDeviceRelative));
        assertTrue(blazeDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderHandle(ctx, toVulkanicShaderStage(shaderCompilationKey.type))"),
            "Blaze3D GlDevice should create shaders via typed handle seam in shader compilation path: " + blazeDeviceRelative);
        assertTrue(blazeDeviceSource.contains("net.irisshaders.iris.gl.shader.ShaderWorkarounds.safeShaderSource(shader, string2)"),
            "Blaze3D GlDevice should upload shader source via typed handle seam in shader compilation path: " + blazeDeviceRelative);
        assertTrue(blazeDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Blaze3D GlDevice should create programs via typed handle seam in AMD workaround path: " + blazeDeviceRelative);

        String dhShaderRelative = "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java";
        String dhShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(dhShaderRelative));
        assertTrue(dhShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, stage)")
                || dhShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, type)"),
            "Distant Horizons Shader should create shaders via typed handle seam when stage mapping is available: " + dhShaderRelative);
        assertFalse(dhShaderSource.contains("VulkanicAPI.createShader(ctx, type)"),
            "Distant Horizons Shader should avoid raw createShader(int) callsites now that typed-handle creation is available: " + dhShaderRelative);
        assertTrue(dhShaderSource.contains("VulkanicAPI.deleteShader(ctx, VulkanicShaderHandle.of(this.id))"),
            "Distant Horizons Shader should delete shaders via typed handle seam: " + dhShaderRelative);

        String dhShaderProgramRelative = "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java";
        String dhShaderProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(dhShaderProgramRelative));
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Distant Horizons ShaderProgram should create programs via typed handle seam: " + dhShaderProgramRelative);
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(vertShader.id))"),
            "Distant Horizons ShaderProgram should attach vertex shaders via typed handle seam: " + dhShaderProgramRelative);
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(fragShader.id))"),
            "Distant Horizons ShaderProgram should attach fragment shaders via typed handle seam: " + dhShaderProgramRelative);
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.deleteProgram(ctx, VulkanicProgramHandle.of(this.id))"),
            "Distant Horizons ShaderProgram should delete programs via typed handle seam: " + dhShaderProgramRelative);

        String partialShaderRelative = "net/irisshaders/iris/pipeline/programs/PartialShader.java";
        String partialShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(partialShaderRelative));
        assertTrue(partialShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(s))"),
            "Iris PartialShader should delete detached shaders via typed handle seam: " + partialShaderRelative);
    }

    @Test
    public void testSelectedHotspotsUseTypedUniformLocationAndLogicOpSeams() throws IOException {
        String shaderProgramRelative = "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java";
        String shaderProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(shaderProgramRelative));
        assertTrue(shaderProgramSource.contains("VulkanicAPI.resolveUniformLocation("),
            "ShaderProgram should resolve uniforms via typed location helper instead of raw location lookups: " + shaderProgramRelative);

        String glProgramRelative = "net/sodium/client/gl/shader/GlProgram.java";
        String glProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(glProgramRelative));
        assertTrue(glProgramSource.contains("VulkanicAPI.resolveUniformLocation("),
            "Sodium GlProgram should resolve uniforms via typed location helper instead of raw location lookups: " + glProgramRelative);

        String encoderRelative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String encoderSource = Files.readString(SRC_MAIN_JAVA.resolve(encoderRelative));
        assertTrue(encoderSource.contains("VulkanicAPI.setLogicOp(ctx, VulkanicLogicOp.OR_REVERSE)"),
            "GlCommandEncoder should use typed VulkanicLogicOp routing for OR_REVERSE logic-op setup: " + encoderRelative);
    }

    @Test
    public void testIrisBufferBlendToggleUsesTypedCapabilityEnum() throws IOException {
        String relative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("setIndexedEnabled(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_BLEND"),
            "Iris indexed blend toggles should use VulkanicCapability.BLEND: " + relative);
    }

    @Test
    public void testGlCommandEncoderMipRangeUsesTypedTextureParameterNames() throws IOException {
        String relative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        Pattern rawMipParameterPattern = Pattern.compile("setTextureParameter\\s*\\([^\\n]*GL_TEXTURE_(?:BASE_LEVEL|MAX_LEVEL)");
        assertFalse(rawMipParameterPattern.matcher(source).find(),
            "GlCommandEncoder mip parameter setup should use typed texture-parameter names: " + relative);
    }

    @Test
    public void testIrisGlSamplerUsesTypedSamplerParameterEnums() throws IOException {
        String relative = "net/irisshaders/iris/gl/sampler/GlSampler.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        Pattern rawSamplerCallPattern = Pattern.compile("samplerParameteri\\s*\\([^\\n]*GL_");
        assertFalse(rawSamplerCallPattern.matcher(source).find(),
            "Iris GlSampler should use typed sampler parameter enums instead of raw GL constants: " + relative);
        assertFalse(source.contains("private static final int GL_TEXTURE_"),
            "Iris GlSampler should not define raw GL texture parameter constants: " + relative);
    }

    @Test
    public void testBlaze3dGlTextureFlushModeUsesTypedTextureParameterEnums() throws IOException {
        String relative = "net/blaze3d/opengl/GlTexture.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        Pattern rawNumericTextureParamPattern = Pattern.compile("iris\\$texParameterDSA\\s*\\([^\\n]*\\b(?:10240|10241|10242|10243|9728|9729|9984|9985|9986|9987)\\b");
        assertFalse(rawNumericTextureParamPattern.matcher(source).find(),
            "GlTexture flush-mode parameter path should use typed texture parameter names/values: " + relative);
    }

    @Test
    public void testIrisDepthCopyStrategyAvoidsExplicitTexture2dTargets() throws IOException {
        String relative = "net/irisshaders/iris/gl/texture/DepthCopyStrategy.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("VulkanicAPI.GL_TEXTURE_2D"),
            "DepthCopyStrategy should use typed/default-2D helper overloads instead of explicit GL texture target constants: " + relative);
    }

    @Test
    public void testIrisTextureWrapperDefaultsUseTypedTexture2dHelpers() throws IOException {
        String relative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("generateMipmaps(texture, VulkanicAPI.GL_TEXTURE_2D)"),
            "Iris wrapper defaults should route through typed 2D helper overloads for mipmaps: " + relative);
        assertFalse(source.contains("texImage2D(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texImage2D: " + relative);
        assertFalse(source.contains("texParameteriv(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texParameteriv: " + relative);
        assertFalse(source.contains("setTextureSwizzleRgba(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for swizzle setup: " + relative);
        assertFalse(source.contains("copyTexSubImage2D(destTexture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for copyTexSubImage2D: " + relative);
        assertFalse(source.contains("texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texParameteri: " + relative);
        assertFalse(source.contains("texParameterf(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texParameterf: " + relative);
        assertFalse(source.contains("getTexParameteri(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for getTexParameteri: " + relative);
    }

    @Test
    public void testIrisTextureBindingAndCreationDefaultsUseTypedTexture2dHelpers() throws IOException {
        String relative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("dsaState.bindTextureToUnit(VulkanicAPI.GL_TEXTURE_2D, unit, texture)"),
            "Iris bindTextureToUnit 2D default should route through typed texture-target helper overloads: " + relative);
        assertFalse(source.contains("return dsaState.createTexture(VulkanicAPI.GL_TEXTURE_2D);"),
            "Iris createTexture2D should route through typed texture-target helper overloads: " + relative);
        assertFalse(source.contains("if (glType == VulkanicAPI.GL_TEXTURE_2D)"),
            "Iris bindTextureForSetup should avoid raw GL_TEXTURE_2D comparisons when typed target mapping is available: " + relative);
        assertFalse(source.contains("if (target == VulkanicAPI.GL_TEXTURE_2D)"),
            "Iris DSA bindTextureToUnit paths should avoid raw GL_TEXTURE_2D comparisons when typed target mapping is available: " + relative);
    }

    @Test
    public void testBlaze3dTextureStatePathsAvoidLegacyTargetUnwrapping() throws IOException {
        String commandEncoderRelative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String commandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve(commandEncoderRelative));

        assertFalse(commandEncoderSource.contains("texture.flushModeChanges(textureTarget.toLegacyGlTarget())"),
            "GlCommandEncoder should use typed texture-target overload for flushModeChanges: " + commandEncoderRelative);
        assertFalse(commandEncoderSource.contains("q = VulkanicAPI.GL_TEXTURE_2D;"),
            "GlCommandEncoder write-to-texture path should avoid explicit GL_TEXTURE_2D target locals when default-2D helpers exist: " + commandEncoderRelative);
        assertFalse(commandEncoderSource.contains("o = VulkanicAPI.GL_TEXTURE_2D;"),
            "GlCommandEncoder byte-buffer upload path should avoid explicit GL_TEXTURE_2D target locals when default-2D helpers exist: " + commandEncoderRelative);
        assertFalse(commandEncoderSource.contains("glPrimitiveMode == VulkanicAPI.GL_TRIANGLES"),
            "GlCommandEncoder tessellation override should avoid raw GL_TRIANGLES comparisons when typed primitive-mode mapping is available: " + commandEncoderRelative);
        assertTrue(commandEncoderSource.contains("VulkanicPrimitiveMode.PATCHES"),
            "GlCommandEncoder tessellation override should use typed VulkanicPrimitiveMode.PATCHES routing: " + commandEncoderRelative);

        String deviceRelative = "net/blaze3d/opengl/GlDevice.java";
        String deviceSource = Files.readString(SRC_MAIN_JAVA.resolve(deviceRelative));

        assertFalse(deviceSource.contains("setTextureMaxLevel(ctx, o, m - 1)"),
            "GlDevice texture setup should use typed texture-target overloads for mip configuration: " + deviceRelative);
        assertFalse(deviceSource.contains("disableTextureCompareMode(ctx, o)"),
            "GlDevice depth texture setup should use typed texture-target overloads for compare mode: " + deviceRelative);
    }

    @Test
    public void testIrisTextureTypeDrivenCallsitesPreferTypedTargetHelpers() throws IOException {
        String samplerBindingRelative = "net/irisshaders/iris/gl/sampler/SamplerBinding.java";
        String samplerBindingSource = Files.readString(SRC_MAIN_JAVA.resolve(samplerBindingRelative));

        assertFalse(samplerBindingSource.contains("bindTextureToUnit(textureType.getGlType(), textureUnit, textureId)"),
            "SamplerBinding should route texture binding through TextureType typed helper methods: " + samplerBindingRelative);

        String glTextureRelative = "net/irisshaders/iris/gl/texture/GlTexture.java";
        String glTextureSource = Files.readString(SRC_MAIN_JAVA.resolve(glTextureRelative));

        assertFalse(glTextureSource.contains("bindTextureForSetup(target.getGlType(), getGlId())"),
            "Iris GlTexture setup should route through TextureType typed helper methods: " + glTextureRelative);
        assertFalse(glTextureSource.contains("bindTextureToUnit(target.getGlType(), unit, getGlId())"),
            "Iris GlTexture bind should route through TextureType typed helper methods: " + glTextureRelative);

        String glImageRelative = "net/irisshaders/iris/gl/image/GlImage.java";
        String glImageSource = Files.readString(SRC_MAIN_JAVA.resolve(glImageRelative));

        assertFalse(glImageSource.contains("IrisRenderSystem.createTexture(target.getGlType())"),
            "Iris GlImage creation should route through TextureType typed helper methods: " + glImageRelative);
        assertFalse(glImageSource.contains("bindTextureForSetup(target.getGlType(), getGlId())"),
            "Iris GlImage setup should route through TextureType typed helper methods: " + glImageRelative);

        String textureTypeRelative = "net/irisshaders/iris/gl/texture/TextureType.java";
        String textureTypeSource = Files.readString(SRC_MAIN_JAVA.resolve(textureTypeRelative));

        assertFalse(textureTypeSource.contains("TEXTURE_RECTANGLE(VulkanicAPI.GL_TEXTURE_3D)"),
            "TextureType.TEXTURE_RECTANGLE should map to GL_TEXTURE_RECTANGLE instead of GL_TEXTURE_3D: " + textureTypeRelative);
    }

    @Test
    public void testShaderSourceWorkaroundsUseCharSequenceUploadSeam() throws IOException {
        List<String> files = List.of(
            "net/sodium/client/gl/shader/ShaderWorkarounds.java",
            "net/irisshaders/iris/gl/shader/ShaderWorkarounds.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("uploadShaderSource"),
                "Shader source helper should route through VulkanicAPI.uploadShaderSource seam: " + relative);
            assertFalse(source.contains("pointers.address0()"),
                "Shader source helper should avoid pointer-address plumbing at callsites: " + relative);
            assertFalse(source.contains("MemoryUtil.memUTF8"),
                "Shader source helper should avoid direct UTF8 native conversion at callsites: " + relative);
        }
    }

    @Test
    public void testTextureBufferAndUploadClusterCallsitesUseVulkanicAPISeams() throws IOException {
        // Font texture cluster
        String fontTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/FontTexture.java"));
        String specialGlyphsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/glyphs/SpecialGlyphs.java"));
        String bitmapProviderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/providers/BitmapProvider.java"));
        String unihexProviderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/font/providers/UnihexProvider.java"));
        // Texture system cluster
        String dynamicTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/DynamicTexture.java"));
        String reloadableTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/ReloadableTexture.java"));
        String cubeMapTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/CubeMapTexture.java"));
        String spriteContentsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/texture/SpriteContents.java"));
        String lightTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/LightTexture.java"));
        String pictureInPictureRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/render/pip/PictureInPictureRenderer.java"));
        // Buffer allocation cluster
        String mappableRingBufferSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/MappableRingBuffer.java"));
        String perspProjBufferSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer.java"));
        String cachedPerspProjBufferSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CachedPerspectiveProjectionMatrixBuffer.java"));
        String cachedOrthoProjBufferSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/CachedOrthoProjectionMatrixBuffer.java"));
        String voxelMapOrthoProjBufferSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/util/VoxelMapCachedOrthoProjectionMatrixBuffer.java"));
        String fogRendererNewSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/fog/FogRenderer.java"));
        // Misc cluster
        String renderTargetDescriptorSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/resource/RenderTargetDescriptor.java"));
        String particleFeatureRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java"));
        String vertexFormatSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/vertex/VertexFormat.java"));

        // Font texture cluster assertions
        assertTrue(fontTextureSource.contains("VulkanicAPI.createTexture("),
            "FontTexture should allocate textures through backend-owned VulkanicAPI seam");
        assertTrue(fontTextureSource.contains("VulkanicAPI.createTextureView("),
            "FontTexture should allocate texture views through backend-owned VulkanicAPI seam");
        assertFalse(fontTextureSource.contains("VulkanicAPI.getDevice()"),
            "FontTexture should avoid direct getDevice() usage");
        assertTrue(specialGlyphsSource.contains("VulkanicAPI.createCommandEncoder()"),
            "SpecialGlyphs should upload glyph bitmaps via backend-owned VulkanicAPI createCommandEncoder seam");
        assertFalse(specialGlyphsSource.contains("VulkanicAPI.getDevice()"),
            "SpecialGlyphs should avoid direct getDevice() usage in glyph upload path");
        assertTrue(bitmapProviderSource.contains("VulkanicAPI.createCommandEncoder()"),
            "BitmapProvider should upload glyph bitmaps via backend-owned VulkanicAPI createCommandEncoder seam");
        assertFalse(bitmapProviderSource.contains("VulkanicAPI.getDevice()"),
            "BitmapProvider should avoid direct getDevice() usage in glyph upload path");
        assertTrue(unihexProviderSource.contains("VulkanicAPI.createCommandEncoder()"),
            "UnihexProvider should upload glyph bitmaps via backend-owned VulkanicAPI createCommandEncoder seam");
        assertFalse(unihexProviderSource.contains("VulkanicAPI.getDevice()"),
            "UnihexProvider should avoid direct getDevice() usage in glyph upload path");

        // Texture system cluster assertions
        assertTrue(dynamicTextureSource.contains("VulkanicAPI.createTexture("),
            "DynamicTexture should allocate textures through backend-owned VulkanicAPI seam");
        assertTrue(dynamicTextureSource.contains("VulkanicAPI.createTextureView("),
            "DynamicTexture should allocate texture views through backend-owned VulkanicAPI seam");
        assertFalse(dynamicTextureSource.contains("VulkanicAPI.getDevice()"),
            "DynamicTexture should avoid direct getDevice() usage");
        assertTrue(reloadableTextureSource.contains("VulkanicAPI.createTexture("),
            "ReloadableTexture should allocate textures through backend-owned VulkanicAPI seam");
        assertTrue(reloadableTextureSource.contains("VulkanicAPI.createTextureView("),
            "ReloadableTexture should allocate texture views through backend-owned VulkanicAPI seam");
        assertFalse(reloadableTextureSource.contains("VulkanicAPI.getDevice()"),
            "ReloadableTexture should avoid direct getDevice() usage");
        assertTrue(cubeMapTextureSource.contains("VulkanicAPI.createTexture("),
            "CubeMapTexture should allocate textures through backend-owned VulkanicAPI seam");
        assertTrue(cubeMapTextureSource.contains("VulkanicAPI.createTextureView("),
            "CubeMapTexture should allocate texture views through backend-owned VulkanicAPI seam");
        assertFalse(cubeMapTextureSource.contains("VulkanicAPI.getDevice()"),
            "CubeMapTexture should avoid direct getDevice() usage");
        assertTrue(spriteContentsSource.contains("VulkanicAPI.createCommandEncoder()"),
            "SpriteContents should upload sprite frames via backend-owned VulkanicAPI createCommandEncoder seam");
        assertFalse(spriteContentsSource.contains("VulkanicAPI.getDevice()"),
            "SpriteContents should avoid direct getDevice() usage in sprite upload path");
        assertTrue(lightTextureSource.contains("VulkanicAPI.createTexture("),
            "LightTexture should allocate lightmap texture through backend-owned VulkanicAPI seam");
        assertTrue(lightTextureSource.contains("VulkanicAPI.createTextureView("),
            "LightTexture should allocate lightmap texture view through backend-owned VulkanicAPI seam");
        assertFalse(lightTextureSource.contains("VulkanicAPI.getDevice()"),
            "LightTexture should avoid direct getDevice() usage");
        assertTrue(pictureInPictureRendererSource.contains("VulkanicAPI.createTexture("),
            "PictureInPictureRenderer should allocate UI render textures through backend-owned VulkanicAPI seam");
        assertTrue(pictureInPictureRendererSource.contains("VulkanicAPI.createTextureView("),
            "PictureInPictureRenderer should allocate UI texture views through backend-owned VulkanicAPI seam");
        assertFalse(pictureInPictureRendererSource.contains("VulkanicAPI.getDevice()"),
            "PictureInPictureRenderer should avoid direct getDevice() usage");

        // Buffer allocation cluster assertions
        assertTrue(mappableRingBufferSource.contains("VulkanicAPI.createBuffer("),
            "MappableRingBuffer should allocate ring buffers through backend-owned VulkanicAPI seam");
        assertFalse(mappableRingBufferSource.contains("VulkanicAPI.getDevice()"),
            "MappableRingBuffer should avoid direct getDevice() usage");
        assertTrue(perspProjBufferSource.contains("VulkanicAPI.createBuffer("),
            "PerspectiveProjectionMatrixBuffer should allocate projection UBOs through backend-owned VulkanicAPI seam");
        assertFalse(perspProjBufferSource.contains("VulkanicAPI.getDevice()"),
            "PerspectiveProjectionMatrixBuffer should avoid direct getDevice() usage");
        assertTrue(cachedPerspProjBufferSource.contains("VulkanicAPI.createBuffer("),
            "CachedPerspectiveProjectionMatrixBuffer should allocate projection UBOs through backend-owned VulkanicAPI seam");
        assertFalse(cachedPerspProjBufferSource.contains("VulkanicAPI.getDevice()"),
            "CachedPerspectiveProjectionMatrixBuffer should avoid direct getDevice() usage");
        assertTrue(cachedOrthoProjBufferSource.contains("VulkanicAPI.createBuffer("),
            "CachedOrthoProjectionMatrixBuffer should allocate projection UBOs through backend-owned VulkanicAPI seam");
        assertFalse(cachedOrthoProjBufferSource.contains("VulkanicAPI.getDevice()"),
            "CachedOrthoProjectionMatrixBuffer should avoid direct getDevice() usage");
        assertTrue(voxelMapOrthoProjBufferSource.contains("VulkanicAPI.createBuffer("),
            "VoxelMapCachedOrthoProjectionMatrixBuffer should allocate projection UBOs through backend-owned VulkanicAPI seam");
        assertFalse(voxelMapOrthoProjBufferSource.contains("VulkanicAPI.getDevice()"),
            "VoxelMapCachedOrthoProjectionMatrixBuffer should avoid direct getDevice() usage");
        assertTrue(fogRendererNewSource.contains("VulkanicAPI.createBuffer("),
            "FogRenderer should allocate fog UBO through backend-owned VulkanicAPI seam");
        assertFalse(fogRendererNewSource.contains("VulkanicAPI.getDevice()"),
            "FogRenderer should avoid direct getDevice() usage");

        // Misc cluster assertions
        assertTrue(renderTargetDescriptorSource.contains("VulkanicAPI.createCommandEncoder()"),
            "RenderTargetDescriptor should clear render target textures via backend-owned VulkanicAPI createCommandEncoder seam");
        assertFalse(renderTargetDescriptorSource.contains("VulkanicAPI.getDevice()"),
            "RenderTargetDescriptor should avoid direct getDevice() usage for texture clearing");
        assertTrue(particleFeatureRendererSource.contains("VulkanicAPI.createRenderPass("),
            "ParticleFeatureRenderer should create particle render passes through backend-owned VulkanicAPI seam");
        assertFalse(particleFeatureRendererSource.contains("VulkanicAPI.getDevice()"),
            "ParticleFeatureRenderer should avoid direct getDevice() usage in particle render path");
        // VertexFormat keeps getDevice() only for GraphicsWorkarounds hardware workaround detection, not resource allocation
        assertFalse(vertexFormatSource.contains("gpuDevice.createBuffer("),
            "VertexFormat should not use local gpuDevice variable for buffer allocation — use VulkanicAPI.createBuffer seam");
        assertFalse(vertexFormatSource.contains("gpuDevice.createCommandEncoder()"),
            "VertexFormat should not use local gpuDevice variable for encoder — use VulkanicAPI.createCommandEncoder seam");
        assertTrue(vertexFormatSource.contains("VulkanicAPI.createBuffer("),
            "VertexFormat should allocate immediate upload buffers through backend-owned VulkanicAPI seam");
        assertTrue(vertexFormatSource.contains("VulkanicAPI.createCommandEncoder()"),
            "VertexFormat should acquire command encoders through backend-owned VulkanicAPI seam");
    }

    @Test
    public void testIrisAndSodiumRenderClusterCallsitesUseVulkanicAPISeams() throws IOException {
        String renderTargetsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/RenderTargets.java"));
        String customTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/NativeImageBackedCustomTexture.java"));
        String noiseTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/targets/backed/NativeImageBackedNoiseTexture.java"));
        String centerDepthSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/CenterDepthSampler.java"));
        String colorSpaceSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/colorspace/ColorSpaceFragmentConverter.java"));
        String fullScreenQuadSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/FullScreenQuadRenderer.java"));
        String horizonRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pathways/HorizonRenderer.java"));
        String shadowCompositeSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowCompositeRenderer.java"));
        String shadowRenderTargetsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/shadows/ShadowRenderTargets.java"));
        String finalPassRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/FinalPassRenderer.java"));
        String compositeRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pipeline/CompositeRenderer.java"));
        String pbrAtlasTextureSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/pbr/texture/PBRAtlasTexture.java"));
        String sodiumOptionsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/sodium/client/gui/SodiumGameOptionPages.java"));
        String sodiumRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/ShaderChunkRenderer.java"));

        assertTrue(renderTargetsSource.contains("VulkanicAPI.createTexture("),
            "Iris RenderTargets should allocate depth textures through backend-owned VulkanicAPI seam");
        assertFalse(renderTargetsSource.contains("RenderSystem.getDevice("),
            "Iris RenderTargets should avoid direct RenderSystem.getDevice() texture allocation");

        assertTrue(customTextureSource.contains("VulkanicAPI.createCommandEncoder()"),
            "NativeImageBackedCustomTexture should upload textures through backend-owned VulkanicAPI command encoder seam");
        assertFalse(customTextureSource.contains("RenderSystem.getDevice("),
            "NativeImageBackedCustomTexture should avoid direct RenderSystem.getDevice() upload path");
        assertTrue(noiseTextureSource.contains("VulkanicAPI.createCommandEncoder()"),
            "NativeImageBackedNoiseTexture should upload textures through backend-owned VulkanicAPI command encoder seam");
        assertFalse(noiseTextureSource.contains("RenderSystem.getDevice("),
            "NativeImageBackedNoiseTexture should avoid direct RenderSystem.getDevice() upload path");

        assertTrue(centerDepthSource.contains("VulkanicAPI.createRenderPass("),
            "CenterDepthSampler should create its sampling pass through backend-owned VulkanicAPI render-pass seam");
        assertFalse(centerDepthSource.contains("RenderSystem.getDevice("),
            "CenterDepthSampler should avoid direct RenderSystem.getDevice() render-pass creation");
        assertTrue(colorSpaceSource.contains("VulkanicAPI.createRenderPass("),
            "ColorSpaceFragmentConverter should create its conversion pass through backend-owned VulkanicAPI render-pass seam");
        assertFalse(colorSpaceSource.contains("RenderSystem.getDevice("),
            "ColorSpaceFragmentConverter should avoid direct RenderSystem.getDevice() render-pass creation");

        assertTrue(fullScreenQuadSource.contains("VulkanicAPI.createBuffer("),
            "FullScreenQuadRenderer should allocate quad buffer through backend-owned VulkanicAPI seam");
        assertFalse(fullScreenQuadSource.contains("RenderSystem.getDevice("),
            "FullScreenQuadRenderer should avoid direct RenderSystem.getDevice() buffer allocation");
        assertTrue(horizonRendererSource.contains("VulkanicAPI.createBuffer("),
            "HorizonRenderer should allocate horizon buffers through backend-owned VulkanicAPI seam");
        assertTrue(horizonRendererSource.contains("VulkanicAPI.createRenderPass("),
            "HorizonRenderer should create sky render passes through backend-owned VulkanicAPI seam");
        assertFalse(horizonRendererSource.contains("RenderSystem.getDevice("),
            "HorizonRenderer should avoid direct RenderSystem.getDevice() resource and pass creation");

        assertTrue(shadowCompositeSource.contains("VulkanicAPI.createRenderPass("),
            "ShadowCompositeRenderer should create composite passes through backend-owned VulkanicAPI seam");
        assertFalse(shadowCompositeSource.contains("RenderSystem.getDevice("),
            "ShadowCompositeRenderer should avoid direct RenderSystem.getDevice() render-pass creation");
        assertTrue(shadowRenderTargetsSource.contains("VulkanicAPI.createTexture("),
            "ShadowRenderTargets should allocate depth targets through backend-owned VulkanicAPI seam");
        assertFalse(shadowRenderTargetsSource.contains("RenderSystem.getDevice("),
            "ShadowRenderTargets should avoid direct RenderSystem.getDevice() texture allocation");

        assertTrue(finalPassRendererSource.contains("VulkanicAPI.createRenderPass("),
            "FinalPassRenderer should create final-pass render passes through backend-owned VulkanicAPI seam");
        assertFalse(finalPassRendererSource.contains("RenderSystem.getDevice("),
            "FinalPassRenderer should avoid direct RenderSystem.getDevice() render-pass creation");
        assertTrue(compositeRendererSource.contains("VulkanicAPI.createRenderPass("),
            "CompositeRenderer should create composite passes through backend-owned VulkanicAPI seam");
        assertFalse(compositeRendererSource.contains("RenderSystem.getDevice("),
            "CompositeRenderer should avoid direct RenderSystem.getDevice() render-pass creation");

        assertTrue(pbrAtlasTextureSource.contains("VulkanicAPI.createTexture("),
            "PBRAtlasTexture should allocate atlas textures through backend-owned VulkanicAPI seam");
        assertFalse(pbrAtlasTextureSource.contains("RenderSystem.getDevice("),
            "PBRAtlasTexture should avoid direct RenderSystem.getDevice() texture allocation");

        assertTrue(sodiumOptionsSource.contains("VulkanicAPI.createCommandEncoder()"),
            "SodiumGameOptionPages should clear cloud framebuffer through backend-owned VulkanicAPI command encoder seam");
        assertFalse(sodiumOptionsSource.contains("RenderSystem.getDevice("),
            "SodiumGameOptionPages should avoid direct RenderSystem.getDevice() clear path");
        assertTrue(sodiumRendererSource.contains("VulkanicAPI.createCommandEncoder()"),
            "ShaderChunkRenderer should acquire a backend-owned CommandEncoder through VulkanicAPI seam");
        assertFalse(sodiumRendererSource.contains("RenderSystem.getDevice("),
            "ShaderChunkRenderer should avoid direct RenderSystem.getDevice() command encoder acquisition");
    }
}
