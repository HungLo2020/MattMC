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
        String deferredModeUpdate = "DhApiRenderProxy.INSTANCE.setDeferTransparentRendering(Iris.isPackInUseQuick())";
        assertTrue(levelHookSource.contains(deferredModeUpdate),
            "DH level render hook should publish Iris deferred mode before ClientApi chooses the DH render pass");
        assertTrue(levelHookSource.indexOf(deferredModeUpdate) < levelHookSource.indexOf("ClientApi.INSTANCE.renderLods()"),
            "DH level render hook should publish Iris deferred mode before ClientApi chooses the DH render pass");
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
        assertTrue(lodSource.contains("VulkanicAPI.createRenderPass(() -> label, this.activeFramebufferId"),
            "DH LOD render pass should target the active DH framebuffer");
        assertTrue(lodSource.contains("!VulkanicAPI.isVulkanBackendSelected()"),
            "DH LOD render pass bridge should be disabled for OpenGL");

        assertTrue(screenQuadSource.contains("try (RenderPass ignored = createVulkanCompatibilityRenderPass())"),
            "DH full-screen quad draws should execute inside a Vulkan compatibility render pass");
        assertTrue(screenQuadSource.contains("VulkanicAPI.getDrawFramebufferBinding()"),
            "DH full-screen quad render pass should target the current draw framebuffer");
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
        Path vulkanicApi = SRC_MAIN_JAVA.resolve("net/vulkanic/VulkanicAPI.java");
        String glProxySource = readSourceWithoutComments(glProxy);
        String dhCompatSource = readSourceWithoutComments(dhCompat);
        String vulkanicApiSource = readSourceWithoutComments(vulkanicApi);

        assertTrue(glProxySource.contains("if (vulkanBackend)")
                && glProxySource.contains("this.vertexAttribDivisorSupported = true")
                && glProxySource.contains("this.instancedArraysSupported = true"),
            "DH GLProxy should expose Vulkan instancing support so Iris generic DH rendering stays on the indirect path");
        assertTrue(dhCompatSource.contains("DepthCopyStrategy strategy = DepthCopyStrategy.fastestDepthSnapshot(false)"),
            "Iris DH translucent depth snapshots should use the depth-copy strategy instead of copyTexImage2D");
        assertTrue(dhCompatSource.contains("depthTexNoTranslucentFramebuffer.addDepthAttachmentBypass(depthTexNoTranslucent.getTextureId())"),
            "Iris DH translucent depth snapshots should have a depth-only destination framebuffer for Vulkan blits");
        assertTrue(vulkanicApiSource.contains("direct -> direct.setVertexAttribDivisor(ctx, index, divisor)"),
            "Instanced attribute divisors should route to the direct Vulkan implementation");
        assertTrue(vulkanicApiSource.contains("private static void drawIndexedInstancedRaw")
                && vulkanicApiSource.contains("direct -> direct.drawIndexedInstanced(ctx, mode, count, type, indices, instanceCount)"),
            "Instanced indexed draws should route to the direct Vulkan implementation");
    }
}
