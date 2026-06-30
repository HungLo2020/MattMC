package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DistantHorizonsCommandContextMigrationTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path SRC_MAIN_RESOURCES = PROJECT_ROOT.resolve("src/main/resources");

    private static String readSourceWithoutComments(Path file) throws IOException {
        assertTrue(Files.exists(file), file + " must exist");
        String source = Files.readString(file);
        return source
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)//.*$", "");
    }

    private static void assertNoImmediateContext(Path file) throws IOException {
        String sourceWithoutComments = readSourceWithoutComments(file);

        assertFalse(sourceWithoutComments.contains("VulkanicAPI.getImmediateContext()"),
            file + " should not hard-wire immediate OpenGL context retrieval");
    }

    private static void assertDhIntermediateTextureIsSampleable(Path file) throws IOException {
        String sourceWithoutComments = readSourceWithoutComments(file);

        assertTrue(sourceWithoutComments.contains("VulkanicAPI.isVulkanBackendSelected()")
                && sourceWithoutComments.contains("VulkanicAPI.GL_RGBA8")
                && sourceWithoutComments.contains("VulkanicAPI.GL_UNSIGNED_BYTE"),
            file + " should allocate post-process intermediate textures with a Vulkan-sampleable RGBA8 upload tuple");
        assertTrue(sourceWithoutComments.contains("VulkanicAPI.GL_RGBA16")
                && sourceWithoutComments.contains("VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4"),
            file + " should preserve the original packed OpenGL intermediate format outside Vulkan compatibility mode");
    }

    private static void assertBackendNeutralSingleContext(Path file) throws IOException {
        String sourceWithoutComments = readSourceWithoutComments(file);

        assertNoImmediateContext(file);
        assertTrue(sourceWithoutComments.contains("VulkanicAPI.getCommandContext()"),
            file + " should fetch backend-neutral command context");
    }

    private static void assertShaderUsesInheritedContext(Path file) throws IOException {
        String sourceWithoutComments = readSourceWithoutComments(file);

        assertNoImmediateContext(file);
        assertFalse(sourceWithoutComments.contains("VulkanicAPI.getCommandContext()"),
            file + " should inherit command context from AbstractShaderRenderer rather than fetching its own");
        assertTrue(sourceWithoutComments.contains("CommandContext ctx"),
            file + " should receive a command context parameter in shader hooks");
    }

    @Test
    public void testAbstractShaderRendererProvidesSharedBackendNeutralContext() throws IOException {
        Path abstractShaderRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/AbstractShaderRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(abstractShaderRenderer);

        assertNoImmediateContext(abstractShaderRenderer);
        assertTrue(sourceWithoutComments.contains("VulkanicAPI.getCommandContext()"),
            "AbstractShaderRenderer should fetch backend-neutral context once per render call");
        assertTrue(sourceWithoutComments.contains("onApplyUniforms(ctx, partialTicks)"),
            "AbstractShaderRenderer should pass shared context into uniform hook");
        assertTrue(sourceWithoutComments.contains("onRender(ctx)"),
            "AbstractShaderRenderer should pass shared context into render hook");
    }

    @Test
    public void testDhShaderPathsUseInheritedBackendNeutralContext() throws IOException {
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/SSAOApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/DhApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FadeApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/VanillaFadeShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/SSAOShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/DhFadeShader.java"));
    }

    @Test
    public void testDhCoreRenderersUseBackendNeutralContext() throws IOException {
        assertBackendNeutralSingleContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"));
        assertBackendNeutralSingleContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java"));
        assertBackendNeutralSingleContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/generic/RenderableBoxGroup.java"));
    }

    @Test
    public void testDhGlObjectLayerExposesContextAwareSeams() throws IOException {
        Path shaderProgram = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java");
        Path glState = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/GLState.java");
        Path framebuffer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/texture/DhFramebuffer.java");
        Path glBuffer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java");
        Path colorTexture = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/texture/DhColorTexture.java");
        Path depthTexture = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/texture/DHDepthTexture.java");
        Path textureState = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/DhTextureState.java");
        Path glProxy = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/GLProxy.java");
        Path abstractVertexAttribute = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/vertexAttribute/AbstractVertexAttribute.java");
        Path vertexAttributePreGL43 = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/vertexAttribute/VertexAttributePreGL43.java");
        Path vertexAttributePostGL43 = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/vertexAttribute/VertexAttributePostGL43.java");

        String shaderProgramSource = readSourceWithoutComments(shaderProgram);
        String glStateSource = readSourceWithoutComments(glState);
        String framebufferSource = readSourceWithoutComments(framebuffer);
        String glBufferSource = readSourceWithoutComments(glBuffer);
        String colorTextureSource = readSourceWithoutComments(colorTexture);
        String depthTextureSource = readSourceWithoutComments(depthTexture);
        String textureStateSource = readSourceWithoutComments(textureState);
        String glProxySource = readSourceWithoutComments(glProxy);
        String abstractVertexAttributeSource = readSourceWithoutComments(abstractVertexAttribute);
        String vertexAttributePreGL43Source = readSourceWithoutComments(vertexAttributePreGL43);
        String vertexAttributePostGL43Source = readSourceWithoutComments(vertexAttributePostGL43);

        assertNoImmediateContext(shaderProgram);
        assertNoImmediateContext(glState);
        assertNoImmediateContext(framebuffer);
        assertNoImmediateContext(glBuffer);
        assertNoImmediateContext(colorTexture);
        assertNoImmediateContext(depthTexture);
        assertNoImmediateContext(textureState);
        assertNoImmediateContext(glProxy);
        assertNoImmediateContext(abstractVertexAttribute);
        assertNoImmediateContext(vertexAttributePreGL43);
        assertNoImmediateContext(vertexAttributePostGL43);

        assertTrue(shaderProgramSource.contains("bind(CommandContext ctx)"),
            "ShaderProgram should expose explicit context-aware bind");
        assertTrue(shaderProgramSource.contains("setUniform(CommandContext ctx"),
            "ShaderProgram should expose explicit context-aware uniform updates");

        assertTrue(glStateSource.contains("saveState(CommandContext ctx)"),
            "GLState should support context-aware state capture");
        assertTrue(glStateSource.contains("restore(CommandContext ctx)"),
            "GLState should support context-aware state restore");

        assertTrue(framebufferSource.contains("bind(CommandContext ctx)"),
            "DhFramebuffer should expose context-aware bind operations");
        assertTrue(framebufferSource.contains("addDepthAttachment(CommandContext ctx"),
            "DhFramebuffer should expose context-aware attachment operations");
        assertTrue(framebufferSource.contains("VulkanicAPI.drawBuffers(ctx, glBuffers);"),
            "DhFramebuffer.drawBuffers should preserve requested color attachments instead of forcing GL_NONE");
        assertTrue(framebufferSource.contains("VulkanicAPI.drawBuffers(ctx, new int[]{VulkanicAPI.colorAttachment(textureIndex)});"),
            "DhFramebuffer.addColorAttachment should restore color drawing when a color target is first attached");

        assertTrue(glBufferSource.contains("bind(CommandContext ctx)"),
            "GLBuffer should expose context-aware bind/unbind operations");

        assertTrue(colorTextureSource.contains("resizeTexture(CommandContext ctx"),
            "DhColorTexture should route texture upload through explicit context seam");
        assertTrue(depthTextureSource.contains("resize(CommandContext ctx"),
            "DHDepthTexture should expose context-aware resize operation");
        assertTrue(depthTextureSource.contains("destroy(CommandContext ctx)"),
            "DHDepthTexture should expose context-aware destroy operation");
        assertTrue(textureStateSource.contains("bindTexture2D(CommandContext ctx"),
            "DhTextureState should expose context-aware texture binding helper");
        assertTrue(glProxySource.contains("VulkanicAPI.getCommandContext()"),
            "GLProxy should query renderer metadata through backend-neutral context");

        assertTrue(abstractVertexAttributeSource.contains("create(CommandContext ctx)"),
            "AbstractVertexAttribute should expose context-aware factory method");
        assertTrue(abstractVertexAttributeSource.contains("bind(CommandContext ctx)"),
            "AbstractVertexAttribute should expose context-aware bind operation");
        assertTrue(vertexAttributePreGL43Source.contains("VertexAttributePreGL43(CommandContext ctx)"),
            "VertexAttributePreGL43 should support context-aware construction");
        assertTrue(vertexAttributePostGL43Source.contains("VertexAttributePostGL43(CommandContext ctx)"),
            "VertexAttributePostGL43 should support context-aware construction");
    }

    @Test
    public void testDhFabricHooksDoNotBypassVulkanBackend() throws IOException {
        Path levelHook = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/fabric/hooks/DistantHorizonsLevelRenderHook.java");
        Path chunkHook = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/fabric/hooks/DistantHorizonsChunkRenderHook.java");
        String levelHookSource = readSourceWithoutComments(levelHook);
        String chunkHookSource = readSourceWithoutComments(chunkHook);

        assertFalse(levelHookSource.contains("VulkanicAPI.isVulkanBackendSelected()"),
            "DH level render hook should not skip render-state setup or LOD rendering on Vulkan");
        assertFalse(chunkHookSource.contains("VulkanicAPI.isVulkanBackendSelected()"),
            "DH chunk render hook should not skip deferred/fade LOD rendering on Vulkan");
        assertTrue(levelHookSource.contains("ClientApi.INSTANCE.renderLods()"),
            "DH level render hook should still invoke the core LOD render entrypoint");
        assertTrue(chunkHookSource.contains("ClientApi.INSTANCE.renderDeferredLodsForShaders()"),
            "DH chunk render hook should still invoke deferred LOD rendering for shader pipelines");
        String deferredModeUpdate = "DhApiRenderProxy.INSTANCE.setDeferTransparentRendering(DHCompatInternal.shouldUseShaderOverrides())";
        assertTrue(levelHookSource.contains(deferredModeUpdate),
            "DH level render hook should publish Iris deferred mode before ClientApi chooses the DH render pass");
        assertTrue(levelHookSource.indexOf(deferredModeUpdate) < levelHookSource.indexOf("ClientApi.INSTANCE.renderLods()"),
            "DH level render hook should publish Iris deferred mode before ClientApi chooses the DH render pass");
    }

    @Test
    public void testDhShaderpackOverridesRequireEnabledShaders() throws IOException {
        Path dhCompat = SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/DHCompatInternal.java");
        Path lodEvents = SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/LodRendererEvents.java");
        Path genericProgram = SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java");
        String compatSource = readSourceWithoutComments(dhCompat);
        String eventSource = readSourceWithoutComments(lodEvents);
        String genericSource = readSourceWithoutComments(genericProgram);

        assertTrue(compatSource.contains("public static boolean shouldUseShaderOverrides()"),
            "DH Iris compat should expose one shared shader-override predicate");
        assertTrue(compatSource.contains("Iris.getIrisConfig().areShadersEnabled()")
                && compatSource.contains("Iris.isPackInUseQuick()"),
            "DH shaderpack overrides should require both enabled shaders and an Iris shaderpack pipeline");
        assertTrue(eventSource.contains("DHCompatInternal.shouldUseShaderOverrides()"),
            "DH Iris event bridge should use the shared shader-override predicate");
        assertTrue(eventSource.contains("return DHCompatInternal.SHADERLESS;"),
            "DH Iris event bridge should fall back to shaderless DH compat when shaders are disabled");
        assertTrue(genericSource.contains("return DHCompatInternal.shouldUseShaderOverrides();"),
            "DH generic shader override should not stay active when shaders are disabled");
    }

    @Test
    public void testDhApplyShaderOnlyCompositesDrawnDepthPixels() throws IOException {
        Path applyShader = SRC_MAIN_RESOURCES.resolve("shaders/apply.frag");
        String sourceWithoutComments = readSourceWithoutComments(applyShader);

        assertTrue(sourceWithoutComments.contains("fragmentDepth < 1.0"),
            "DH apply shader should composite only pixels closer than the untouched clear-depth value");
        assertFalse(sourceWithoutComments.contains("fragmentDepth != 1"),
            "DH apply shader should not treat tiny Vulkan depth sampling differences as drawn LOD pixels");
        assertFalse(sourceWithoutComments.contains("1.0 - TexCoord.y"),
            "DH apply shader should not apply a Vulkan-only texture-coordinate flip; capture evidence showed that leaves the sky-water artifact in place");
    }

    @Test
    public void testDhLodTerrainRestoresColorWritesBeforeDrawing() throws IOException {
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(lodRenderer);

        int stateStart = sourceWithoutComments.indexOf("private void setGLState");
        int bindProgram = sourceWithoutComments.indexOf("this.lodRenderProgram.bind()", stateStart);
        int colorMask = sourceWithoutComments.indexOf("VulkanicAPI.setColorMask(ctx, true, true, true, true)", stateStart);
        int depthMask = sourceWithoutComments.indexOf("VulkanicAPI.setDepthWriteMask(ctx, true)", stateStart);

        assertTrue(stateStart >= 0 && bindProgram > stateStart,
            "DH LOD renderer should have a setup block before binding the terrain program");
        assertTrue(colorMask > stateStart && colorMask < bindProgram,
            "DH LOD terrain setup should restore RGBA color writes before drawing into the DH color target");
        assertTrue(depthMask > colorMask && depthMask < bindProgram,
            "DH LOD terrain setup should restore depth writes after color writes and before binding the terrain program");
    }

    @Test
    public void testDhTransparentLodPassPreservesDepthForFogComposite() throws IOException {
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(lodRenderer);

        int renderLodPass = sourceWithoutComments.indexOf(
            "private void renderLodPass(IDhApiShaderProgram shaderProgram, RenderBufferHandler lodBufferHandler, RenderParams renderEventParam, boolean opaquePass)");
        int transparentBranch = sourceWithoutComments.indexOf("if (!opaquePass)", renderLodPass);
        int transparentDepthMask = sourceWithoutComments.indexOf("VulkanicAPI.setDepthWriteMask(ctx, true)", transparentBranch);
        int transparentBlendEquation = sourceWithoutComments.indexOf("VulkanicAPI.setBlendEquation(ctx, VulkanicBlendEquation.ADD)", transparentBranch);
        int opaqueBranch = sourceWithoutComments.indexOf("else", transparentBranch);
        int opaqueDepthMask = sourceWithoutComments.indexOf("VulkanicAPI.setDepthWriteMask(ctx, true)", opaqueBranch);
        int renderPass = sourceWithoutComments.indexOf("createVulkanCompatibilityRenderPass(\"Distant Horizons LOD\")", opaqueBranch);
        int restoreDepthMask = sourceWithoutComments.indexOf("VulkanicAPI.setDepthWriteMask(ctx, true)", renderPass);

        assertTrue(renderLodPass >= 0 && transparentBranch > renderLodPass,
            "DH LOD renderer should have an explicit transparent branch in the LOD draw path");
        assertTrue(transparentDepthMask > transparentBranch && transparentDepthMask < transparentBlendEquation,
            "DH transparent LOD rendering should write depth so later fog/apply passes see the final water surface");
        assertTrue(opaqueDepthMask > opaqueBranch && opaqueDepthMask < renderPass,
            "DH opaque LOD rendering should explicitly restore depth writes before drawing opaque terrain");
        assertTrue(restoreDepthMask > renderPass,
            "DH transparent LOD rendering should restore depth writes after the transparent pass so later passes inherit sane state");
    }

    @Test
    public void testDhVulkanRenderPassUsesCurrentlyBoundDrawFramebuffer() throws IOException {
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(lodRenderer);

        int renderPassHelper = sourceWithoutComments.indexOf("private RenderPass createVulkanCompatibilityRenderPass");
        int cachedDefault = sourceWithoutComments.indexOf("int framebufferId = this.activeFramebufferId", renderPassHelper);
        int drawFramebuffer = sourceWithoutComments.indexOf("VulkanicAPI.getDrawFramebufferBinding()", renderPassHelper);
        int colorAttachmentGuard = sourceWithoutComments.indexOf(
            "VulkanicAPI.getFramebufferColorAttachment0ObjectName(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER) > 0",
            drawFramebuffer);
        int currentDrawAssignment = sourceWithoutComments.indexOf("framebufferId = drawFramebufferId", colorAttachmentGuard);
        int currentDrawDepth = sourceWithoutComments.indexOf(
            "VulkanicAPI.getFramebufferDepthAttachmentObjectName(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER) > 0",
            currentDrawAssignment);
        int createRenderPass = sourceWithoutComments.indexOf(
            "VulkanicAPI.createRenderPass(() -> label, framebufferId, framebufferHasDepthAttachment)",
            currentDrawDepth);

        assertTrue(renderPassHelper >= 0,
            "DH LOD renderer should centralize Vulkan compatibility render pass creation");
        assertTrue(cachedDefault > renderPassHelper && cachedDefault < drawFramebuffer,
            "DH Vulkan LOD render passes should default to DH's cached active framebuffer");
        assertTrue(drawFramebuffer > renderPassHelper,
            "DH Vulkan LOD render passes should use the framebuffer currently bound by DH/Iris render-pass events");
        assertTrue(colorAttachmentGuard > drawFramebuffer && colorAttachmentGuard < currentDrawAssignment,
            "DH Vulkan LOD render passes should only follow the current draw framebuffer when it has a color attachment");
        assertTrue(currentDrawAssignment > colorAttachmentGuard && currentDrawDepth > currentDrawAssignment,
            "DH Vulkan LOD render passes should copy the current draw framebuffer and its depth contract only after the color-attachment guard");
        assertTrue(createRenderPass > currentDrawDepth,
            "DH Vulkan LOD render passes should create the pass for the resolved draw framebuffer");
    }

    @Test
    public void testDhTransparentLodBuffersDrawWaterSurfaceLast() throws IOException {
        Path lodQuadBuilder = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodQuadBuilder.java");
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        Path lodBufferContainer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodBufferContainer.java");
        Path columnBox = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/ColumnBox.java");
        Path irisAccessor = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/fabric/wrappers/modAccessor/IrisAccessor.java");
        String sourceWithoutComments = readSourceWithoutComments(lodQuadBuilder);
        String rendererSource = readSourceWithoutComments(lodRenderer);
        String containerSource = readSourceWithoutComments(lodBufferContainer);
        String columnBoxSource = readSourceWithoutComments(columnBox);
        String irisAccessorSource = readSourceWithoutComments(irisAccessor);

        int nonUpOrder = sourceWithoutComments.indexOf("TRANSPARENT_NON_UP_DIRECTION_RENDER_ORDER");
        int north = sourceWithoutComments.indexOf("EDhDirection.NORTH.ordinal()", nonUpOrder);
        int south = sourceWithoutComments.indexOf("EDhDirection.SOUTH.ordinal()", nonUpOrder);
        int west = sourceWithoutComments.indexOf("EDhDirection.WEST.ordinal()", nonUpOrder);
        int east = sourceWithoutComments.indexOf("EDhDirection.EAST.ordinal()", nonUpOrder);
        int upOrder = sourceWithoutComments.indexOf("TRANSPARENT_UP_DIRECTION_RENDER_ORDER");
        int up = sourceWithoutComments.indexOf("EDhDirection.UP.ordinal()", upOrder);
        int transparentBuffers = sourceWithoutComments.indexOf(
            "makeTransparentVertexBuffers() { return this.makeVertexBuffers(this.transparentQuads, TRANSPARENT_NON_UP_DIRECTION_RENDER_ORDER);");
        int transparentUpBuffers = sourceWithoutComments.indexOf(
            "makeTransparentUpVertexBuffers() { return this.makeVertexBuffers(this.transparentQuads, TRANSPARENT_UP_DIRECTION_RENDER_ORDER, quad -> !isWater(quad));");
        int transparentWaterUpBuffers = sourceWithoutComments.indexOf("makeTransparentWaterUpVertexBuffers()");
        int materialTransparentRouting = sourceWithoutComments.indexOf("shouldUseTransparentBuffer");
        int waterTransparentRouting = sourceWithoutComments.indexOf("irisBlockMaterialId == EDhApiBlockMaterial.WATER.index", materialTransparentRouting);
        int waterOrderingHelper = rendererSource.indexOf("private boolean shouldUseVulkanNoShaderWaterOrdering()");
        int waterOrderingVulkanGuard = rendererSource.indexOf("VulkanicAPI.isVulkanBackendSelected()", waterOrderingHelper);
        int waterOrderingShaderGuard = rendererSource.indexOf("!isIrisShaderRenderingEnabled()", waterOrderingVulkanGuard);
        int rendererEnabledGuard = rendererSource.indexOf("IRIS_ACCESSOR.areShadersEnabled()", waterOrderingShaderGuard);
        int rendererPackGuard = rendererSource.indexOf("IRIS_ACCESSOR.isShaderPackInUse()", rendererEnabledGuard);
        int transparentBranch = rendererSource.indexOf("if (!opaquePass)");
        int waterOrderingGuard = rendererSource.indexOf("this.shouldUseVulkanNoShaderWaterOrdering()", transparentBranch);
        int waterOrderingRenderPass = rendererSource.indexOf("createVulkanCompatibilityRenderPass(\"Distant Horizons LOD\")", waterOrderingGuard);
        int detailDepthWritesOff = rendererSource.indexOf("VulkanicAPI.setDepthWriteMask(ctx, false)", waterOrderingRenderPass);
        int sideDraw = rendererSource.indexOf("container -> container.vbosTransparent", detailDepthWritesOff);
        int upDraw = rendererSource.indexOf("container -> container.vbosTransparentUp", sideDraw);
        int waterAlwaysDepth = rendererSource.indexOf("VulkanicAPI.setDepthFunc(ctx, VulkanicDepthCompareOp.ALWAYS)", upDraw);
        int waterDepthTestOn = rendererSource.indexOf("VulkanicAPI.setDepthTestEnabled(ctx, true)", waterAlwaysDepth);
        int waterCullOff = rendererSource.indexOf("VulkanicAPI.setCullFaceEnabled(ctx, false)", waterDepthTestOn);
        int waterDepthWritesOn = rendererSource.indexOf("VulkanicAPI.setDepthWriteMask(ctx, true)", waterCullOff);
        int waterBlendOn = rendererSource.indexOf("VulkanicAPI.setBlendEnabled(ctx, true)", waterDepthWritesOn);
        int waterDraw = rendererSource.indexOf("container -> container.vbosTransparentWaterUp", waterBlendOn);
        int restoreBlendOn = rendererSource.indexOf("VulkanicAPI.setBlendEnabled(ctx, true)", waterDraw);
        int restoreCull = rendererSource.indexOf("VulkanicAPI.setCullFaceEnabled(ctx, true)", waterDraw);
        int restoreDepthTest = rendererSource.indexOf("VulkanicAPI.setDepthTestEnabled(ctx, true)", restoreCull);
        int restoreDepthFunc = rendererSource.indexOf("VulkanicAPI.setDepthFunc(ctx, VulkanicDepthCompareOp.LESS)", restoreDepthTest);
        int defaultRenderPass = rendererSource.indexOf("Distant Horizons LOD\")", waterDraw);
        int defaultSideDraw = rendererSource.indexOf("container -> container.vbosTransparent", defaultRenderPass);
        int defaultUpDraw = rendererSource.indexOf("container -> container.vbosTransparentUp", defaultSideDraw);
        int defaultWaterDraw = rendererSource.indexOf("container -> container.vbosTransparentWaterUp", defaultUpDraw);

        assertTrue(nonUpOrder >= 0 && upOrder > nonUpOrder,
            "DH transparent LOD buffers should split side/down quads from UP water-surface quads");
        assertTrue(north > nonUpOrder && south > north && west > south && east > west,
            "DH transparent LOD side buffers should include non-UP faces before the water-surface pass");
        assertTrue(up > upOrder,
            "DH transparent UP buffers should contain only UP faces");
        assertTrue(transparentBuffers > up && transparentUpBuffers > transparentBuffers && transparentWaterUpBuffers > transparentUpBuffers,
            "DH transparent LOD buffer creation should build separate side, non-water UP, and water UP buffers");
        assertTrue(materialTransparentRouting >= 0 && waterTransparentRouting > materialTransparentRouting,
            "DH water material should route through transparent water buffers even when its color alpha is opaque");
	        assertTrue(columnBoxSource.contains("isTransparent(color, irisBlockMaterialId, transparencyEnabled)")
	                && columnBoxSource.contains("isTransparent(topData, transparencyEnabled)")
	                && columnBoxSource.contains("isTransparent(bottomData, transparencyEnabled)")
	                && columnBoxSource.contains("isWaterMaterial(irisBlockMaterialId)")
	                && columnBoxSource.contains("isWaterSurfaceOccludingMaterial(RenderDataPointUtil.getBlockMaterialId(topData))"),
	            "DH column face culling should treat water as transparent by material and preserve water top faces below non-occluding details");
        assertTrue(containerSource.contains("public GLVertexBuffer[] vbosTransparentUp")
                && containerSource.contains("public GLVertexBuffer[] vbosTransparentWaterUp")
                && containerSource.contains("builder.makeTransparentUpVertexBuffers()")
                && containerSource.contains("builder.makeTransparentWaterUpVertexBuffers()")
                && containerSource.contains("uploadBuffersDirect(this.vbosTransparentUp")
                && containerSource.contains("uploadBuffersDirect(this.vbosTransparentWaterUp"),
            "DH LOD buffer containers should own and upload non-water and water transparent UP passes independently");
        assertTrue(waterOrderingHelper >= 0 && waterOrderingVulkanGuard > waterOrderingHelper
                && waterOrderingShaderGuard > waterOrderingVulkanGuard
                && rendererEnabledGuard > waterOrderingShaderGuard && rendererPackGuard > rendererEnabledGuard,
            "DH should restrict the Vulkan water ordering workaround to Vulkan without enabled shaderpack rendering");
        assertTrue(irisAccessorSource.contains("IrisApi.getInstance().getConfig().areShadersEnabled()"),
            "DH Iris accessor should distinguish selected shader packs from shader rendering being enabled");
        int opaqueBranch = rendererSource.indexOf("if (opaquePass && useVulkanNoShaderWaterOrdering)");
        int opaqueRenderPass = rendererSource.indexOf("createVulkanCompatibilityRenderPass(\"Distant Horizons LOD\")", opaqueBranch);
        int opaqueTerrainDraw = rendererSource.indexOf("container -> container.vbos);", opaqueRenderPass);
        int opaqueWaterAlwaysDepth = rendererSource.indexOf("VulkanicAPI.setDepthFunc(ctx, VulkanicDepthCompareOp.ALWAYS)", opaqueTerrainDraw);
        int opaqueWaterCullOff = rendererSource.indexOf("VulkanicAPI.setCullFaceEnabled(ctx, false)", opaqueWaterAlwaysDepth);
        int opaqueWaterDepthWritesOn = rendererSource.indexOf("VulkanicAPI.setDepthWriteMask(ctx, true)", opaqueWaterCullOff);
        int opaqueWaterBlendOn = rendererSource.indexOf("VulkanicAPI.setBlendEnabled(ctx, true)", opaqueWaterDepthWritesOn);
        int opaqueWaterDraw = rendererSource.indexOf("container -> container.vbosTransparentWaterUp", opaqueWaterBlendOn);
        int opaqueRestoreDepthFunc = rendererSource.indexOf("VulkanicAPI.setDepthFunc(ctx, VulkanicDepthCompareOp.LESS)", opaqueWaterDraw);
        int opaqueRestoreBlendOff = rendererSource.indexOf("VulkanicAPI.setBlendEnabled(ctx, false)", opaqueRestoreDepthFunc);
        assertTrue(opaqueBranch >= 0 && opaqueRenderPass > opaqueBranch && opaqueTerrainDraw > opaqueRenderPass
                && opaqueWaterAlwaysDepth > opaqueTerrainDraw && opaqueWaterCullOff > opaqueWaterAlwaysDepth
                && opaqueWaterDepthWritesOn > opaqueWaterCullOff && opaqueWaterBlendOn > opaqueWaterDepthWritesOn
                && opaqueWaterDraw > opaqueWaterBlendOn && opaqueRestoreDepthFunc > opaqueWaterDraw
                && opaqueRestoreBlendOff > opaqueRestoreDepthFunc,
            "DH Vulkan no-shader opaque pass should seed water-surface color/depth immediately after opaque terrain and restore opaque state afterward");
        assertTrue(waterOrderingGuard > transparentBranch && waterOrderingRenderPass > waterOrderingGuard
                        && detailDepthWritesOff > waterOrderingRenderPass && sideDraw > detailDepthWritesOff && upDraw > sideDraw
                        && waterAlwaysDepth > upDraw && waterDepthTestOn > waterAlwaysDepth && waterCullOff > waterDepthTestOn
                        && waterDepthWritesOn > waterCullOff && waterBlendOn > waterDepthWritesOn
                        && waterDraw > waterBlendOn
                        && restoreBlendOn > waterDraw && restoreCull > waterDraw && restoreDepthTest > restoreCull && restoreDepthFunc > restoreDepthTest,
                    "DH Vulkan no-shader rendering should draw transparent details first, then let the final water surface own depth for fog/apply");
        assertTrue(defaultRenderPass > waterDraw && defaultSideDraw > defaultRenderPass && defaultUpDraw > defaultSideDraw && defaultWaterDraw > defaultUpDraw,
            "DH shader/OpenGL rendering should keep the normal transparent side, non-water UP, and water UP order");
    }

    @Test
    public void testDhShaderOverridesRespectTheirOwnFrameGate() throws IOException {
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        Path genericObjectRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java");
        String lodRendererSource = readSourceWithoutComments(lodRenderer);
        String genericObjectRendererSource = readSourceWithoutComments(genericObjectRenderer);

        int lodOverride = lodRendererSource.indexOf("IDhApiShaderProgram lodShaderProgramOverride");
        int lodOverrideFrameGate = lodRendererSource.indexOf("lodShaderProgramOverride.overrideThisFrame()", lodOverride);
        int lodUniformOverride = lodRendererSource.indexOf("IDhApiShaderProgram shaderProgramOverride");
        int lodUniformFrameGate = lodRendererSource.indexOf("shaderProgramOverride.overrideThisFrame()", lodUniformOverride);
        int genericOverride = genericObjectRendererSource.indexOf("IDhApiGenericObjectShaderProgram shaderProgramOverride");
        int genericOverrideFrameGate = genericObjectRendererSource.indexOf("shaderProgramOverride.overrideThisFrame()", genericOverride);

        assertTrue(lodOverride >= 0 && lodOverrideFrameGate > lodOverride,
            "DH terrain shader override selection should ask the injected override whether it should render this frame");
        assertTrue(lodUniformOverride >= 0 && lodUniformFrameGate > lodUniformOverride,
            "DH terrain shader override uniforms should only be filled when the injected override is active this frame");
        assertFalse(lodRendererSource.contains("lodShaderProgramOverride != null && lodShaderProgram.overrideThisFrame()"),
            "DH terrain shader override selection must not use the base program's always-on frame gate");
        assertTrue(genericOverride >= 0 && genericOverrideFrameGate > genericOverride,
            "DH generic object shader override selection should ask the injected override whether it should render this frame");
        assertFalse(genericObjectRendererSource.contains("shaderProgramOverride != null && shaderProgram.overrideThisFrame()"),
            "DH generic object shader override selection must not use the base program's always-on frame gate");
    }

    @Test
    public void testDhApplyShaderRestoresColorWritesBeforeCompositing() throws IOException {
        Path applyShader = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/DhApplyShader.java");
        String sourceWithoutComments = readSourceWithoutComments(applyShader);

        int renderToFrameBuffer = sourceWithoutComments.indexOf("private void renderToFrameBuffer");
        int renderToMcTexture = sourceWithoutComments.indexOf("private void renderToMcTexture");
        int firstQuad = sourceWithoutComments.indexOf("ScreenQuad.INSTANCE.render()", renderToFrameBuffer);
        int secondQuad = sourceWithoutComments.indexOf("ScreenQuad.INSTANCE.render()", renderToMcTexture);
        int firstColorMask = sourceWithoutComments.indexOf(
            "VulkanicAPI.setColorMask(ctx, true, true, true, true)", renderToFrameBuffer);
        int secondColorMask = sourceWithoutComments.indexOf(
            "VulkanicAPI.setColorMask(ctx, true, true, true, true)", renderToMcTexture);

        assertTrue(renderToFrameBuffer >= 0 && firstQuad > renderToFrameBuffer,
            "DH apply shader should have a framebuffer composite path");
        assertTrue(renderToMcTexture >= 0 && secondQuad > renderToMcTexture,
            "DH apply shader should have an MC texture composite path");
        assertTrue(firstColorMask > renderToFrameBuffer && firstColorMask < firstQuad,
            "DH framebuffer apply path should restore RGBA color writes before compositing");
        assertTrue(secondColorMask > renderToMcTexture && secondColorMask < secondQuad,
            "DH MC texture apply path should restore RGBA color writes before compositing");
    }

    @Test
    public void testDhFogApplyShaderRestoresColorWritesBeforeCompositing() throws IOException {
        Path fogApplyShader = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogApplyShader.java");
        String sourceWithoutComments = readSourceWithoutComments(fogApplyShader);

        int render = sourceWithoutComments.indexOf("protected void onRender(CommandContext ctx)");
        int colorMask = sourceWithoutComments.indexOf(
            "VulkanicAPI.setColorMask(ctx, true, true, true, true)", render);
        int quad = sourceWithoutComments.indexOf("ScreenQuad.INSTANCE.render()", render);

        assertTrue(render >= 0 && quad > render,
            "DH fog apply shader should render a fullscreen composite quad");
        assertTrue(colorMask > render && colorMask < quad,
            "DH fog apply shader should restore RGBA color writes before compositing generated fog");
    }

    @Test
    public void testDhFogShaderRestoresColorWritesBeforeGeneratingFogTexture() throws IOException {
        Path fogShader = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java");
        String sourceWithoutComments = readSourceWithoutComments(fogShader);

        int render = sourceWithoutComments.indexOf("protected void onRender(CommandContext ctx)");
        int colorMask = sourceWithoutComments.indexOf(
            "VulkanicAPI.setColorMask(ctx, true, true, true, true)", render);
        int quad = sourceWithoutComments.indexOf("ScreenQuad.INSTANCE.render()", render);

        assertTrue(render >= 0 && quad > render,
            "DH fog shader should render a fullscreen fog-generation quad");
        assertTrue(colorMask > render && colorMask < quad,
            "DH fog shader should restore RGBA color writes before generating the fog texture");
    }

    @Test
    public void testDhApplyPassMakesOffscreenLodWritesVisibleToTextureFetch() throws IOException {
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(lodRenderer);

        int barrierConstant = sourceWithoutComments.indexOf("OFFSCREEN_LOD_WRITES_VISIBLE_TO_TEXTURE_FETCH");
        int applyEvent = sourceWithoutComments.indexOf("DhApiBeforeApplyShaderRenderEvent");
        int applyRender = sourceWithoutComments.indexOf("DhApplyShader.INSTANCE.render", applyEvent);
        int barrierCall = sourceWithoutComments.indexOf(
            "VulkanicAPI.applyResourceBarriers(VulkanicAPI.getCommandContext(), OFFSCREEN_LOD_WRITES_VISIBLE_TO_TEXTURE_FETCH)",
            applyEvent);

        assertTrue(barrierConstant >= 0,
            "DH should declare an explicit Vulkan barrier for the offscreen LOD texture handoff");
        assertTrue(applyEvent >= 0 && applyRender > applyEvent,
            "DH should have an apply phase after its offscreen LOD render");
        assertTrue(barrierCall > applyEvent && barrierCall < applyRender,
            "DH should make offscreen color/depth writes visible before the apply shader samples them");
    }

    @Test
    public void testDhFogPassMakesOffscreenLodDepthVisibleToTextureFetch() throws IOException {
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(lodRenderer);

        int fogBlock = sourceWithoutComments.indexOf("Config.Client.Advanced.Graphics.Fog.enableDhFog.get()");
        int fogRender = sourceWithoutComments.indexOf("FogRenderer.INSTANCE.render(combinedMatrix, renderParams.partialTicks)", fogBlock);
        int barrierCall = sourceWithoutComments.indexOf(
            "VulkanicAPI.applyResourceBarriers(VulkanicAPI.getCommandContext(), OFFSCREEN_LOD_WRITES_VISIBLE_TO_TEXTURE_FETCH)",
            fogBlock);

        assertTrue(fogBlock >= 0 && fogRender > fogBlock,
            "DH should have a fog pass that samples the offscreen LOD depth texture");
        assertTrue(barrierCall > fogBlock && barrierCall < fogRender,
            "DH should make offscreen LOD depth writes visible before the fog shader samples them");
    }

    @Test
    public void testDhFogApplyMakesGeneratedFogVisibleToTextureFetch() throws IOException {
        Path fogRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/FogRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(fogRenderer);

        int barrierConstant = sourceWithoutComments.indexOf("FOG_WRITES_VISIBLE_TO_TEXTURE_FETCH");
        int fogGenerate = sourceWithoutComments.indexOf("FogShader.INSTANCE.render(partialTicks)");
        int barrierCall = sourceWithoutComments.indexOf(
            "VulkanicAPI.applyResourceBarriers(ctx, FOG_WRITES_VISIBLE_TO_TEXTURE_FETCH)",
            fogGenerate);
        int fogApply = sourceWithoutComments.indexOf("FogApplyShader.INSTANCE.render(partialTicks)", fogGenerate);

        assertTrue(barrierConstant >= 0,
            "DH fog renderer should declare an explicit Vulkan barrier for the generated fog texture handoff");
        assertTrue(fogGenerate >= 0 && fogApply > fogGenerate,
            "DH fog renderer should generate fog before applying it");
        assertTrue(barrierCall > fogGenerate && barrierCall < fogApply,
            "DH fog renderer should make generated fog writes visible before the apply shader samples them");
    }

    @Test
    public void testDhFogApplyShaderOnlyAppliesDrawnDepthPixels() throws IOException {
        Path fogApplyShader = SRC_MAIN_RESOURCES.resolve("shaders/fog/apply.frag");
        String sourceWithoutComments = readSourceWithoutComments(fogApplyShader);

        assertTrue(sourceWithoutComments.contains("fragmentDepth < 1.0"),
            "DH fog apply shader should apply only pixels closer than the untouched clear-depth value");
        assertFalse(sourceWithoutComments.contains("fragmentDepth != 1"),
            "DH fog apply shader should not rely on exact sampled-depth equality on Vulkan");
    }

    @Test
    public void testDhFogShaderUsesBackendCorrectDepthReconstruction() throws IOException {
        Path fogShader = SRC_MAIN_RESOURCES.resolve("shaders/fog/fog.frag");
        String sourceWithoutComments = readSourceWithoutComments(fogShader);

        int calcViewPosition = sourceWithoutComments.indexOf("vec3 calcViewPosition(float fragmentDepth)");
        int ndcSetup = sourceWithoutComments.indexOf("vec4 ndc = vec4(TexCoord.xy * 2.0 - 1.0, fragmentDepth, 1.0)", calcViewPosition);
        int depthRemap = sourceWithoutComments.indexOf("ndc.z = ndc.z * 2.0 - 1.0", ndcSetup);
        int inverseProject = sourceWithoutComments.indexOf("uInvMvmProj * ndc", depthRemap);

        assertTrue(calcViewPosition >= 0 && ndcSetup > calcViewPosition,
            "DH fog shader should reconstruct view position from sampled LOD depth");
        assertTrue(depthRemap > ndcSetup && inverseProject > depthRemap,
            "DH fog shader should remap sampled depth into the clip-space convention used by the DH inverse projection matrix");
    }

    @Test
    public void testDhFogShaderReceivesConfiguredFarFogFalloff() throws IOException {
        Path fogShader = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java");
        String sourceWithoutComments = readSourceWithoutComments(fogShader);

        assertTrue(sourceWithoutComments.contains("public int uFogFalloffType"),
            "DH fog shader wrapper should track the far-fog falloff uniform");
        assertTrue(sourceWithoutComments.contains("this.uFogFalloffType = this.shader.getUniformLocation(\"uFogFalloffType\")"),
            "DH fog shader wrapper should resolve the far-fog falloff uniform");
        assertTrue(sourceWithoutComments.contains(
                "this.shader.setUniform(ctx, this.uFogFalloffType, Config.Client.Advanced.Graphics.Fog.farFogFalloff.get().value)"),
            "DH fog shader wrapper should upload the configured far-fog falloff instead of relying on GLSL defaults");
    }

    @Test
    public void testDhBuiltInLightmapUnitMatchesActiveBackend() throws IOException {
        Path lightMapWrapper = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/common/wrappers/misc/LightMapWrapper.java");
        Path lightMapInterface = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/wrapperInterfaces/misc/ILightMapWrapper.java");
        Path terrainProgram = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/DhTerrainShaderProgram.java");
        Path genericProgram = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectShaderProgram.java");

        String wrapperSource = readSourceWithoutComments(lightMapWrapper);
        String interfaceSource = readSourceWithoutComments(lightMapInterface);
        String terrainSource = readSourceWithoutComments(terrainProgram);
        String genericSource = readSourceWithoutComments(genericProgram);

        assertTrue(interfaceSource.contains("OPENGL_LIGHTMAP_TEXTURE_UNIT = 0")
                && interfaceSource.contains("VULKAN_LIGHTMAP_TEXTURE_UNIT = 2"),
            "DH should keep the legacy OpenGL lightmap unit while using Minecraft's canonical Vulkan/Iris unit");
        assertTrue(wrapperSource.contains("VulkanicAPI.isVulkanBackendSelected() ? VULKAN_LIGHTMAP_TEXTURE_UNIT : OPENGL_LIGHTMAP_TEXTURE_UNIT"),
            "DH lightmap wrapper should bind and unbind the lightmap on the texture unit selected for the active backend");
        assertTrue(wrapperSource.contains("Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer()"),
            "DH Vulkan lightmap binding should use Minecraft's live lightmap texture-view path so Vulkan samplers see the current lightmap");
        assertFalse(wrapperSource.contains("Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer()"),
            "DH Vulkan lightmap unbind should not clear Minecraft's shared lightmap state before later renderers use it");
        assertTrue(terrainSource.contains("VulkanicAPI.isVulkanBackendSelected()")
                && terrainSource.contains("ILightMapWrapper.VULKAN_LIGHTMAP_TEXTURE_UNIT")
                && terrainSource.contains("ILightMapWrapper.OPENGL_LIGHTMAP_TEXTURE_UNIT")
                && terrainSource.contains("this.setUniform(this.uLightMap, lightmapTextureUnit)"),
            "DH built-in terrain shader should sample the same backend-selected unit that the lightmap wrapper binds");
        assertTrue(genericSource.contains("VulkanicAPI.isVulkanBackendSelected()")
                && genericSource.contains("ILightMapWrapper.VULKAN_LIGHTMAP_TEXTURE_UNIT")
                && genericSource.contains("ILightMapWrapper.OPENGL_LIGHTMAP_TEXTURE_UNIT")
                && genericSource.contains("this.setUniform(this.lightMapUniform, getLightmapTextureUnit())"),
            "DH generic/cloud shader should sample the same backend-selected unit that the lightmap wrapper binds");
        assertFalse(terrainSource.contains("ILightMapWrapper.LIGHTMAP_TEXTURE_UNIT")
                || genericSource.contains("ILightMapWrapper.LIGHTMAP_TEXTURE_UNIT")
                || wrapperSource.contains("ILightMapWrapper.LIGHTMAP_TEXTURE_UNIT"),
            "DH should not use one global lightmap texture unit across OpenGL and Vulkan built-in paths");
    }

    @Test
    public void testDhVulkanCoordinateCorrectionsStayOnProvenPaths() throws IOException {
        String standardVert = readSourceWithoutComments(SRC_MAIN_RESOURCES.resolve("shaders/standard.vert"));
        String curveVert = readSourceWithoutComments(SRC_MAIN_RESOURCES.resolve("shaders/curve.vert"));
        String vanillaFade = readSourceWithoutComments(SRC_MAIN_RESOURCES.resolve("shaders/fade/vanillaFade.frag"));

        assertTrue(standardVert.contains("#ifdef VULKANIC_BACKEND")
                && standardVert.contains("gl_Position.y = -gl_Position.y;"),
            "DH standard terrain vertices should flip Vulkan clip-space Y without changing OpenGL");
        assertTrue(curveVert.contains("#ifdef VULKANIC_BACKEND")
                && curveVert.contains("gl_Position.y = -gl_Position.y;"),
            "DH curved terrain vertices should match the standard Vulkan clip-space Y correction");
        assertTrue(standardVert.contains("light2 = max(light2, 1.0 - light2);")
                && curveVert.contains("light2 = max(light2, 1.0 - light2);"),
            "DH built-in Vulkan terrain should preserve OpenGL-visible water-adjacent LOD lighting instead of sampling black sky rows");

        assertTrue(vanillaFade.contains("vec2 mcTexCoord = TexCoord;")
                && vanillaFade.contains("mcTexCoord.y = 1.0 - mcTexCoord.y;"),
            "DH vanilla fade should sample Minecraft target textures with Vulkan-corrected Y coordinates");
        assertTrue(vanillaFade.contains("texture(uCombinedMcDhColorTexture, mcTexCoord)")
                && vanillaFade.contains("texture(uMcDepthTexture, mcTexCoord)"),
            "DH vanilla fade should apply the Vulkan texture-coordinate correction only to Minecraft color/depth inputs");
        assertTrue(vanillaFade.contains("texture(uDhColorTexture, TexCoord)")
                && vanillaFade.contains("texture(uDhDepthTexture, TexCoord)"),
            "DH vanilla fade should leave DH offscreen textures on the already-proven DH coordinate path");
    }

    @Test
    public void testDhGlProxyAllowsVulkanCompatibilityInitialization() throws IOException {
        Path glProxy = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/GLProxy.java");
        String source = readSourceWithoutComments(glProxy);

        assertTrue(source.contains("boolean vulkanBackend = VulkanicAPI.isVulkanBackendSelected()"),
            "GLProxy should explicitly detect Vulkan compatibility mode during initialization");
        assertTrue(source.contains("!vulkanBackend && GLFW.glfwGetCurrentContext() == 0L"),
            "GLProxy should keep the GLFW OpenGL-context requirement for OpenGL without blocking Vulkan");
        assertTrue(source.contains("!vulkanBackend && !VulkanicAPI.checkOpenGL32Support()"),
            "GLProxy should keep OpenGL capability enforcement for OpenGL without crashing Vulkan");
        assertTrue(source.contains("this.preferredUploadMethod = EDhApiGpuUploadMethod.DATA"),
            "GLProxy should choose the conservative DATA upload path for Vulkan compatibility mode");
        assertTrue(source.contains("return VulkanicAPI.isOnRenderThread()"),
            "GLProxy render-thread detection should use VulkanicAPI's render thread seam on Vulkan");
    }

    @Test
    public void testDhPostProcessRenderersAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/SSAORenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/FogRenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/DhFadeRenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/VanillaFadeRenderer.java"));
    }

    @Test
    public void testDhPostProcessIntermediateTexturesRemainSampleableOnVulkan() throws IOException {
        assertDhIntermediateTextureIsSampleable(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/FogRenderer.java"));
        assertDhIntermediateTextureIsSampleable(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/DhFadeRenderer.java"));
        assertDhIntermediateTextureIsSampleable(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/VanillaFadeRenderer.java"));
    }

    @Test
    public void testDhSharedAndDebugRenderPathsAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/ScreenQuad.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/DebugRenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/TestRenderer.java"));
    }

    @Test
    public void testIrisDhCompatProgramsAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java"));
    }

    @Test
    public void testIrisDhCompatEventsAndInternalsAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/LodRendererEvents.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/DHCompatInternal.java"));
    }

    @Test
    public void testDhDrawPathsOpenVulkanCompatibilityRenderPasses() throws IOException {
        Path lodRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
        Path screenQuad = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/ScreenQuad.java");
        String lodSource = readSourceWithoutComments(lodRenderer);
        String screenQuadSource = readSourceWithoutComments(screenQuad);

        assertTrue(lodSource.contains("try (RenderPass ignored = this.createVulkanCompatibilityRenderPass(\"Distant Horizons LOD\"))"),
            "DH LOD draw calls should execute inside a Vulkan compatibility render pass");
        assertTrue(lodSource.contains("VulkanicAPI.getDrawFramebufferBinding()")
                && lodSource.contains("VulkanicAPI.getFramebufferColorAttachment0ObjectName(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER) > 0")
                && lodSource.contains("int framebufferId = this.activeFramebufferId")
                && lodSource.contains("VulkanicAPI.createRenderPass(() -> label, framebufferId"),
            "DH LOD render pass should target a color-backed current draw framebuffer and fall back to the cached DH framebuffer");
        assertTrue(lodSource.contains("!VulkanicAPI.isVulkanBackendSelected()"),
            "DH LOD render pass bridge should be disabled for OpenGL");

        assertTrue(screenQuadSource.contains("try (RenderPass ignored = createVulkanCompatibilityRenderPass())"),
            "DH full-screen quad draws should execute inside a Vulkan compatibility render pass");
        assertTrue(screenQuadSource.contains("VulkanicAPI.getDrawFramebufferBinding()")
                && screenQuadSource.contains("VulkanicAPI.getFramebufferDepthAttachmentObjectName(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER) > 0"),
            "DH full-screen quad render pass should target the current draw framebuffer with its real depth attachment contract");
        assertTrue(screenQuadSource.contains("!VulkanicAPI.isVulkanBackendSelected()"),
            "DH full-screen quad render pass bridge should be disabled for OpenGL");

        Path genericRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java");
        String genericSource = readSourceWithoutComments(genericRenderer);
        assertTrue(genericSource.contains("try (RenderPass ignored = this.createVulkanCompatibilityRenderPass(framebufferId, framebufferHasDepthAttachment))"),
            "DH generic-object draw calls should execute inside a Vulkan compatibility render pass");
        assertTrue(lodSource.contains("genericRenderer.render(renderParams, profiler, true, this.activeFramebufferId, this.activeFramebufferHasDepthAttachment())")
                && lodSource.contains("genericRenderer.render(renderParams, profiler, false, this.activeFramebufferId, this.activeFramebufferHasDepthAttachment())"),
            "DH generic-object render pass should inherit LodRenderer's active DH framebuffer target");
        assertTrue(genericSource.contains("!VulkanicAPI.isVulkanBackendSelected()"),
            "DH generic-object render pass bridge should be disabled for OpenGL");

        Path commandEncoder = SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java");
        String commandEncoderSource = readSourceWithoutComments(commandEncoder);
        assertTrue(commandEncoderSource.contains("} catch (RuntimeException | Error exception) {")
                && commandEncoderSource.contains("this.inRenderPass = false;")
                && commandEncoderSource.contains("this.activeVulkanicRenderPass = null;")
                && commandEncoderSource.contains("this.activeRenderPassContext = null;")
                && commandEncoderSource.contains("this.device.debugLabels().popDebugGroup();"),
            "Failed Vulkan compatibility render-pass creation should unwind GlCommandEncoder state");
    }

    @Test
    public void testStencilTypedApiRoutesToDirectVulkanRawState() throws IOException {
        Path vulkanicApi = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String source = readSourceWithoutComments(vulkanicApi);

        assertTrue(source.contains("direct -> direct.setStencilFunc(ctx, toLegacyStencilCompareOp(func), ref, mask)"),
            "Typed stencil compare state should route to the direct Vulkan raw-state implementation");
        assertTrue(source.contains("direct -> direct.setStencilFuncSeparate(ctx, toLegacyStencilFace(face), toLegacyStencilCompareOp(func), ref, mask)"),
            "Typed per-face stencil compare state should route to the direct Vulkan raw-state implementation");
        assertTrue(source.contains("toLegacyStencilOperation(stencilFailOp)"),
            "Typed stencil operations should be converted before routing to the direct Vulkan raw-state implementation");
        assertTrue(source.contains("direct -> direct.setStencilWriteMaskSeparate(ctx, toLegacyStencilFace(face), mask)"),
            "Typed per-face stencil write masks should route to the direct Vulkan raw-state implementation");
    }

    @Test
    public void testVulkanLegacyDhDrawsBindProgramPipelineAndExplicitVertexInput() throws IOException {
        Path backend = SRC_MAIN_JAVA.resolve("net/vulkanic/backends/vulkan/VulkanBackend.java");
        Path descriptor = SRC_MAIN_JAVA.resolve("net/vulkanic/PipelineDescriptor.java");
        String backendSource = readSourceWithoutComments(backend);
        String descriptorSource = readSourceWithoutComments(descriptor);

        assertTrue(backendSource.contains("bindLegacyProgramPipelineForDraw(commandBufferHandle, mode)"),
            "Legacy DH draw calls should bind a Vulkan pipeline before issuing indexed draws");
        assertTrue(backendSource.contains("currentVirtualVaoState().setAttributeFormat"),
            "VulkanBackend should record GL43 vertex attribute format calls instead of accepting them as no-ops");
        assertTrue(backendSource.contains("currentVirtualVaoState().setAttributeBinding"),
            "VulkanBackend should record GL43 vertex attribute binding calls for pipeline vertex input");
        assertTrue(backendSource.contains("createLegacyProgramPipelineDescriptor"),
            "VulkanBackend should derive a pipeline descriptor from the currently bound standalone shader program");
        assertTrue(backendSource.contains("spine.activeRenderPassCompatibilityKey()"),
            "Legacy standalone program pipelines should compile against the active render pass contract");
        assertTrue(backendSource.contains("withVertexInputState(vertexInputState)"),
            "Legacy standalone program descriptors should carry explicit VAO-derived vertex input");
        int legacyTextureLookup = backendSource.indexOf("spine.legacyTexture2DBindingsByUnit.getOrDefault(unit, 0)");
        int irisTextureLookup = backendSource.indexOf("IrisRenderSystem.getTextureBinding(unit)", legacyTextureLookup);
        assertTrue(legacyTextureLookup >= 0 && irisTextureLookup > legacyTextureLookup,
            "Legacy standalone sampler resolution should trust Vulkanic's tracked texture-unit binding before falling back to Iris' cache");
        assertTrue(descriptorSource.contains("record VertexInputState"),
            "PipelineDescriptor should expose backend-neutral explicit vertex input metadata");
    }

    @Test
    public void testDhIrisVulkanShaderCompatibilityPathsUseNativeCapabilities() throws IOException {
        Path glProxy = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/GLProxy.java");
        Path dhCompat = SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/DHCompatInternal.java");
        Path dhCompatFacade = SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/DHCompat.java");
        Path vulkanicApi = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String glProxySource = readSourceWithoutComments(glProxy);
        String dhCompatSource = readSourceWithoutComments(dhCompat);
        String dhCompatFacadeSource = readSourceWithoutComments(dhCompatFacade);
        String vulkanicApiSource = readSourceWithoutComments(vulkanicApi);

        assertTrue(glProxySource.contains("if (vulkanBackend)")
                && glProxySource.contains("this.vertexAttribDivisorSupported = true")
                && glProxySource.contains("this.instancedArraysSupported = true"),
            "DH GLProxy should expose Vulkan instancing support so Iris generic DH rendering stays on the indirect path");
        assertTrue(dhCompatSource.contains("DepthCopyStrategy strategy = DepthCopyStrategy.fastestDepthSnapshot(false)"),
            "Iris DH translucent depth snapshots should use the depth-copy strategy instead of copyTexImage2D");
        assertTrue(dhCompatSource.contains("depthTexNoTranslucentFramebuffer.addDepthAttachmentBypass(depthTexNoTranslucent.getTextureId())"),
            "Iris DH translucent depth snapshots should have a depth-only destination framebuffer for Vulkan blits");
        assertFalse(dhCompatFacadeSource.contains("getMainDepthTextureIdForVulkanCompositeFallback"),
            "Iris DH shaderpack samplers must expose DH depth textures instead of substituting the main scene depth on Vulkan");
        assertFalse(dhCompatFacadeSource.contains("Minecraft.getInstance().getMainRenderTarget().getDepthTexture()"),
            "Iris DH shaderpack samplers must not bind the main scene depth for dhDepthTex/dhDepthTex1");
        assertTrue(vulkanicApiSource.contains("direct -> direct.setVertexAttribDivisor(ctx, index, divisor)"),
            "Instanced attribute divisors should route to the direct Vulkan implementation");
        assertTrue(vulkanicApiSource.contains("private static void drawIndexedInstancedRaw")
                && vulkanicApiSource.contains("direct -> direct.drawIndexedInstanced(ctx, mode, count, type, indices, instanceCount)"),
            "Instanced indexed draws should route to the direct Vulkan implementation");
    }
}
