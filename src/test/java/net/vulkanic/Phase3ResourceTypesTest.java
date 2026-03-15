package net.vulkanic;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.backends.opengl.OpenGLBuffer;
import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.opengl.OpenGLTexture;
import net.vulkanic.backends.opengl.OpenGLTextureView;
import net.vulkanic.backends.opengl.OpenGLPipelineHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Phase 3 Vulkanic resource types.
 * Tests the abstract type hierarchy and OpenGL implementation classes
 * WITHOUT requiring an actual OpenGL context (no GL calls made).
 */
public class Phase3ResourceTypesTest {

    // ---- VulkanicBuffer / OpenGLBuffer tests --------------------------------

    @Test
    public void testVulkanicBufferUsageConstants() {
        assertEquals(1,   VulkanicBuffer.USAGE_MAP_READ);
        assertEquals(2,   VulkanicBuffer.USAGE_MAP_WRITE);
        assertEquals(32,  VulkanicBuffer.USAGE_VERTEX);
        assertEquals(64,  VulkanicBuffer.USAGE_INDEX);
        assertEquals(128, VulkanicBuffer.USAGE_UNIFORM);
    }

    @Test
    public void testOpenGLBufferCreation() {
        OpenGLBuffer buffer = new OpenGLBuffer(42, VulkanicBuffer.USAGE_VERTEX, 1024);

        assertEquals(42,   buffer.getGlHandle());
        assertEquals(1024, buffer.size());
        assertEquals(VulkanicBuffer.USAGE_VERTEX, buffer.usage());
        assertFalse(buffer.isClosed());
    }

    @Test
    public void testOpenGLBufferIsInstanceOfVulkanicBuffer() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 256);
        assertInstanceOf(VulkanicBuffer.class, buffer);
    }

    @Test
    public void testOpenGLBufferSlice() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 512);
        VulkanicBufferSlice slice = buffer.slice(64, 128);

        assertSame(buffer, slice.buffer());
        assertEquals(64,  slice.offset());
        assertEquals(128, slice.length());
    }

    @Test
    public void testOpenGLBufferSliceFullRange() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 512);
        VulkanicBufferSlice slice = buffer.slice();

        assertEquals(0,   slice.offset());
        assertEquals(512, slice.length());
    }

    @Test
    public void testOpenGLBufferSliceOutOfRange() {
        OpenGLBuffer buffer = new OpenGLBuffer(1, VulkanicBuffer.USAGE_VERTEX, 256);
        assertThrows(IllegalArgumentException.class, () -> buffer.slice(200, 100));
    }

    // ---- VulkanicTextureFormat tests ----------------------------------------

    @Test
    public void testVulkanicTextureFormatHasColorAspect() {
        assertTrue(VulkanicTextureFormat.RGBA8.hasColorAspect());
        assertTrue(VulkanicTextureFormat.RED8.hasColorAspect());
        assertTrue(VulkanicTextureFormat.RED8I.hasColorAspect());
        assertFalse(VulkanicTextureFormat.DEPTH32.hasColorAspect());
    }

    @Test
    public void testVulkanicTextureFormatHasDepthAspect() {
        assertFalse(VulkanicTextureFormat.RGBA8.hasDepthAspect());
        assertFalse(VulkanicTextureFormat.RED8.hasDepthAspect());
        assertTrue(VulkanicTextureFormat.DEPTH32.hasDepthAspect());
    }

    @Test
    public void testVulkanicTextureFormatPixelSizes() {
        assertEquals(4, VulkanicTextureFormat.RGBA8.pixelSize());
        assertEquals(1, VulkanicTextureFormat.RED8.pixelSize());
        assertEquals(1, VulkanicTextureFormat.RED8I.pixelSize());
        assertEquals(4, VulkanicTextureFormat.DEPTH32.pixelSize());
    }

    // ---- OpenGLTexture tests ------------------------------------------------

    @Test
    public void testOpenGLTextureCreation() {
        OpenGLTexture tex = new OpenGLTexture(
            99, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 4, "test-texture");

        assertEquals(99,  tex.getGlHandle());
        assertEquals(256, tex.getWidth(0));
        assertEquals(128, tex.getHeight(0));
        assertEquals(1,   tex.getDepthOrLayers());
        assertEquals(4,   tex.getMipLevels());
        assertEquals(VulkanicTextureFormat.RGBA8, tex.getFormat());
        assertEquals("test-texture", tex.getLabel());
        assertFalse(tex.isClosed());
    }

    @Test
    public void testOpenGLTextureIsInstanceOfVulkanicTexture() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "tex");
        assertInstanceOf(VulkanicTexture.class, tex);
    }

    @Test
    public void testOpenGLTextureMipDimensions() {
        // Texture is 256 wide; each mip level halves the dimension
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 4, "tex");

        assertEquals(256, tex.getWidth(0)); // mip 0 = full resolution
        assertEquals(128, tex.getWidth(1)); // mip 1 = 256 >> 1
        assertEquals(64,  tex.getWidth(2)); // mip 2 = 256 >> 2
        assertEquals(32,  tex.getWidth(3)); // mip 3 = 256 >> 3
    }

    // ---- OpenGLTextureView tests --------------------------------------------

    @Test
    public void testOpenGLTextureViewCreation() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 256, 1, 4, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 1, 2);

        assertSame(tex, view.texture());
        assertSame(tex, view.openGLTexture());
        assertEquals(1, view.getBaseMipLevel());
        assertEquals(2, view.getMipLevelCount());
        assertFalse(view.isClosed());
    }

    @Test
    public void testOpenGLTextureViewIsInstanceOfVulkanicTextureView() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 0, 1);
        assertInstanceOf(VulkanicTextureView.class, view);
    }

    @Test
    public void testOpenGLTextureViewInvalidMipRange() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 3, "tex");
        assertThrows(IllegalArgumentException.class, () -> new OpenGLTextureView(tex, 2, 3));
    }

    @Test
    public void testOpenGLTextureViewClose() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 64, 64, 1, 1, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 0, 1);
        view.close();
        assertTrue(view.isClosed());
        // Closing the view must NOT close the parent texture
        assertFalse(tex.isClosed());
    }

    @Test
    public void testOpenGLTextureViewDimensions() {
        OpenGLTexture tex = new OpenGLTexture(1, VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8, 256, 128, 1, 4, "tex");
        OpenGLTextureView view = new OpenGLTextureView(tex, 1, 2);
        assertEquals(128, view.getWidth(0));
        assertEquals(64,  view.getHeight(0));
    }

    // ---- PipelineDescriptor tests ------------------------------------------

    @Test
    public void testPipelineDescriptorFromNull() {
        assertThrows(IllegalArgumentException.class,
            () -> PipelineDescriptor.fromRenderPipeline(null));
    }

    @Test
    public void testPipelineDescriptorPortableStateFromRenderPipeline() {
        RenderPipeline pipeline = buildTestPipeline();

        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(pipeline);
        PipelineDescriptor.PortableState state = descriptor.getPortableState();

        assertTrue(descriptor.hasNativeDescriptor(),
            "fromRenderPipeline() should preserve native pipeline object for OpenGL path");
        assertSame(pipeline, descriptor.requireRenderPipeline(),
            "Native descriptor path should return original RenderPipeline");

        assertEquals(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline"), state.location());
        assertEquals(ResourceLocation.withDefaultNamespace("core/test_vertex"), state.vertexShader());
        assertEquals(ResourceLocation.withDefaultNamespace("core/test_fragment"), state.fragmentShader());
        assertEquals(DepthTestFunction.GREATER_DEPTH_TEST, state.depthTestFunction());
        assertEquals(PolygonMode.WIREFRAME, state.polygonMode());
        assertFalse(state.cull());
        assertTrue(state.writeColor());
        assertFalse(state.writeAlpha());
        assertFalse(state.writeDepth());
        assertEquals(LogicOp.OR_REVERSE, state.colorLogic());
        assertEquals(VertexFormat.Mode.QUADS, state.vertexFormatMode());
        assertEquals(2.5f, state.depthBiasScaleFactor());
        assertEquals(1.25f, state.depthBiasConstant());

        assertTrue(state.shaderDefineFlags().contains("FLAG_TEST"));
        assertEquals("123", state.shaderDefineValues().get("VALUE_TEST"));
        assertEquals(1, state.samplers().size());
        assertEquals("Sampler0", state.samplers().get(0));
        assertEquals(2, state.uniforms().size());
        assertTrue(state.uniforms().stream().anyMatch(uniform ->
                uniform.name().equals("Globals") && uniform.type() == UniformType.UNIFORM_BUFFER));
        assertTrue(state.uniforms().stream().anyMatch(uniform ->
                uniform.name().equals("CloudFaces") &&
                uniform.type() == UniformType.TEXEL_BUFFER &&
                uniform.textureFormat() == TextureFormat.RED8I));
    }

    @Test
    public void testPipelineDescriptorRoundTripFromPortableState() {
        RenderPipeline original = buildTestPipeline();
        PipelineDescriptor originalDescriptor = PipelineDescriptor.fromRenderPipeline(original);

        PipelineDescriptor portableDescriptor = PipelineDescriptor.fromPortableState(
            originalDescriptor.getPortableState());

        assertFalse(portableDescriptor.hasNativeDescriptor(),
            "Portable-only descriptor should not expose native object");

        RenderPipeline reconstructed = portableDescriptor.requireRenderPipeline();
        assertEquals(original.getLocation(), reconstructed.getLocation());
        assertEquals(original.getVertexShader(), reconstructed.getVertexShader());
        assertEquals(original.getFragmentShader(), reconstructed.getFragmentShader());
        assertEquals(original.getDepthTestFunction(), reconstructed.getDepthTestFunction());
        assertEquals(original.getPolygonMode(), reconstructed.getPolygonMode());
        assertEquals(original.isCull(), reconstructed.isCull());
        assertEquals(original.isWriteColor(), reconstructed.isWriteColor());
        assertEquals(original.isWriteAlpha(), reconstructed.isWriteAlpha());
        assertEquals(original.isWriteDepth(), reconstructed.isWriteDepth());
        assertEquals(original.getColorLogic(), reconstructed.getColorLogic());
        assertEquals(original.getVertexFormatMode(), reconstructed.getVertexFormatMode());
        assertEquals(original.getDepthBiasScaleFactor(), reconstructed.getDepthBiasScaleFactor());
        assertEquals(original.getDepthBiasConstant(), reconstructed.getDepthBiasConstant());

        assertEquals(original.getSamplers(), reconstructed.getSamplers());
        assertEquals(original.getUniforms().size(), reconstructed.getUniforms().size());
        assertEquals(original.getBlendFunction().isPresent(), reconstructed.getBlendFunction().isPresent());
        assertEquals(original.getShaderDefines().values(), reconstructed.getShaderDefines().values());
        assertEquals(original.getShaderDefines().flags(), reconstructed.getShaderDefines().flags());
    }

    @Test
    public void testPipelineDescriptorResourceLayoutFromPortableState() {
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());
        PipelineDescriptor.ResourceLayout layout = descriptor.getResourceLayout();

        assertEquals(3, layout.bindings().size(),
            "Sampler + two uniforms should produce three resource bindings");

        PipelineDescriptor.ResourceBinding samplerBinding = layout.bindings().get(0);
        assertEquals(0, samplerBinding.set());
        assertEquals(0, samplerBinding.binding());
        assertEquals("Sampler0", samplerBinding.name());
        assertEquals(PipelineDescriptor.ResourceType.SAMPLER, samplerBinding.type());
        assertNull(samplerBinding.textureFormat());
        assertEquals(java.util.Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT), samplerBinding.stages());

        PipelineDescriptor.ResourceBinding uniformBinding = layout.bindings().get(1);
        assertEquals(1, uniformBinding.binding());
        assertEquals("Globals", uniformBinding.name());
        assertEquals(PipelineDescriptor.ResourceType.UNIFORM_BUFFER, uniformBinding.type());
        assertEquals(java.util.Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT), uniformBinding.stages());

        PipelineDescriptor.ResourceBinding texelBinding = layout.bindings().get(2);
        assertEquals(2, texelBinding.binding());
        assertEquals("CloudFaces", texelBinding.name());
        assertEquals(PipelineDescriptor.ResourceType.TEXEL_BUFFER, texelBinding.type());
        assertEquals(TextureFormat.RED8I, texelBinding.textureFormat());
        assertEquals(java.util.Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT), texelBinding.stages());

        assertTrue(layout.findByName("Sampler0").isPresent());
        assertTrue(layout.findByName("CloudFaces").isPresent());
        assertTrue(layout.findByName("Missing").isEmpty());
    }

    @Test
    public void testPipelineDescriptorWithExplicitResourceLayoutOverridesPortableLayout() {
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        PipelineDescriptor.ResourceLayout explicitLayout = new PipelineDescriptor.ResourceLayout(java.util.List.of(
            new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "ReflectedSampler",
                PipelineDescriptor.ResourceType.SAMPLER,
                null
            )
        ));

        PipelineDescriptor reflectedDescriptor = descriptor.withResourceLayout(explicitLayout);

        assertFalse(descriptor.hasExplicitResourceLayout(),
            "RenderPipeline-derived descriptor should start without explicit reflected layout metadata");
        assertTrue(reflectedDescriptor.hasExplicitResourceLayout(),
            "withResourceLayout should mark descriptor as carrying explicit reflected layout metadata");
        assertEquals(explicitLayout, reflectedDescriptor.getResourceLayout(),
            "Explicit reflected layout should override portable-state derived resource layout");
    }

    @Test
    public void testPipelineCompilationKeyTracksExplicitResourceLayoutMetadata() {
        PipelineDescriptor base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        PipelineDescriptor.ResourceLayout layoutA = new PipelineDescriptor.ResourceLayout(java.util.List.of(
            new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "SamplerA",
                PipelineDescriptor.ResourceType.SAMPLER,
                null
            )
        ));

        PipelineDescriptor.ResourceLayout layoutB = new PipelineDescriptor.ResourceLayout(java.util.List.of(
            new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "SamplerB",
                PipelineDescriptor.ResourceType.SAMPLER,
                null
            )
        ));

        String keyA = base.withResourceLayout(layoutA).getPipelineCompilationKey();
        String keyB = base.withResourceLayout(layoutB).getPipelineCompilationKey();

        assertNotEquals(keyA, keyB,
            "Pipeline compilation key should include explicit reflected resource-layout metadata");

        PipelineDescriptor.ResourceLayout layoutC = new PipelineDescriptor.ResourceLayout(java.util.List.of(
            new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "SamplerA",
                PipelineDescriptor.ResourceType.SAMPLER,
                null,
                java.util.Set.of(VulkanicShaderStage.FRAGMENT)
            )
        ));

        String keyC = base.withResourceLayout(layoutC).getPipelineCompilationKey();
        assertNotEquals(keyA, keyC,
            "Pipeline compilation key should include resource stage-visibility metadata");
    }

    @Test
    public void testPipelineDescriptorPortableLayoutInfersStagesFromSpirvModules() {
        PipelineDescriptor base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        PipelineDescriptor descriptor = PipelineDescriptor.fromPortableStateAndSpirvModules(
            base.getPortableState(),
            java.util.List.of(
                createTestSpirvModule(VulkanicShaderStage.VERTEX, "main", new byte[] {1, 2, 3, 4}),
                createTestSpirvModule(VulkanicShaderStage.FRAGMENT, "main", new byte[] {5, 6, 7, 8})
            )
        );

        PipelineDescriptor.ResourceLayout layout = descriptor.getResourceLayout();
        for (PipelineDescriptor.ResourceBinding binding : layout.bindings()) {
            assertEquals(
                java.util.Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT),
                binding.stages(),
                "Portable resource layout should inherit stage visibility from attached SPIR-V modules"
            );
        }
    }

    @Test
    public void testPipelineDescriptorResourceLayoutRejectsDuplicateNames() {
        PipelineDescriptor.PortableState base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline())
            .getPortableState();

        PipelineDescriptor.PortableState duplicate = new PipelineDescriptor.PortableState(
            base.location(),
            base.vertexShader(),
            base.fragmentShader(),
            base.shaderDefineValues(),
            base.shaderDefineFlags(),
            java.util.List.of("SharedName"),
            java.util.List.of(new PipelineDescriptor.UniformBinding("SharedName", UniformType.UNIFORM_BUFFER, null)),
            base.blendState(),
            base.depthTestFunction(),
            base.polygonMode(),
            base.cull(),
            base.writeColor(),
            base.writeAlpha(),
            base.writeDepth(),
            base.colorLogic(),
            base.vertexFormat(),
            base.vertexFormatMode(),
            base.depthBiasScaleFactor(),
            base.depthBiasConstant()
        );

        assertThrows(IllegalStateException.class, duplicate::toResourceLayout,
            "Duplicate sampler/uniform names must fail to ensure deterministic layout mapping");
    }

    @Test
    public void testPipelineDescriptorStableCacheKeyRoundTrip() {
        PipelineDescriptor original = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());
        PipelineDescriptor reconstructed = PipelineDescriptor.fromPortableState(original.getPortableState());

        String keyA = original.getStableCacheKey();
        String keyB = reconstructed.getStableCacheKey();

        assertEquals(keyA, keyB,
            "Stable cache key should match when portable state is semantically identical");
        assertEquals(64, keyA.length(),
            "Stable cache key should be SHA-256 hex");
    }

    @Test
    public void testPipelineDescriptorStableCacheKeyChangesWithState() {
        PipelineDescriptor base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());
        PipelineDescriptor altered = PipelineDescriptor.fromRenderPipeline(
            RenderPipeline.builder()
                .withLocation(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline_changed"))
                .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
                .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
                .withShaderDefine("FLAG_TEST")
                .withShaderDefine("VALUE_TEST", 123)
                .withSampler("Sampler0")
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("CloudFaces", UniformType.TEXEL_BUFFER, TextureFormat.RED8I)
                .withDepthTestFunction(DepthTestFunction.GREATER_DEPTH_TEST)
                .withPolygonMode(PolygonMode.WIREFRAME)
                .withCull(false)
                .withoutBlend()
                .withColorWrite(true, false)
                .withDepthWrite(false)
                .withColorLogic(LogicOp.OR_REVERSE)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthBias(2.5f, 1.25f)
                .build());

        assertNotEquals(base.getStableCacheKey(), altered.getStableCacheKey(),
            "Stable cache key should change when pipeline semantics change");
    }

    @Test
    public void testPipelineDescriptorFromPortableStateAndSpirvModules() {
        PipelineDescriptor base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        VulkanicSpirvModule vertexModule = createTestSpirvModule(
            VulkanicShaderStage.VERTEX,
            "main",
            new byte[] {0x03, 0x02, 0x23, 0x07}
        );
        VulkanicSpirvModule fragmentModule = createTestSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[] {0x07, 0x23, 0x02, 0x03}
        );

        PipelineDescriptor descriptor = PipelineDescriptor.fromPortableStateAndSpirvModules(
            base.getPortableState(),
            java.util.List.of(vertexModule, fragmentModule)
        );

        assertFalse(descriptor.hasNativeDescriptor(),
            "Portable+SPIR-V descriptor should remain backend-neutral without native descriptor");
        assertTrue(descriptor.hasSpirvModules(),
            "Portable+SPIR-V descriptor should report SPIR-V module presence");
        assertEquals(2, descriptor.getSpirvModules().size());
        assertEquals(VulkanicShaderStage.VERTEX, descriptor.getSpirvModules().get(0).stage());
        assertEquals(VulkanicShaderStage.FRAGMENT, descriptor.getSpirvModules().get(1).stage());
    }

    @Test
    public void testPipelineDescriptorRejectsDuplicateSpirvStages() {
        PipelineDescriptor base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        VulkanicSpirvModule firstVertex = createTestSpirvModule(
            VulkanicShaderStage.VERTEX,
            "main",
            new byte[] {1, 2, 3, 4}
        );
        VulkanicSpirvModule secondVertex = createTestSpirvModule(
            VulkanicShaderStage.VERTEX,
            "secondary",
            new byte[] {4, 3, 2, 1}
        );

        assertThrows(IllegalArgumentException.class,
            () -> PipelineDescriptor.fromPortableStateAndSpirvModules(
                base.getPortableState(),
                java.util.List.of(firstVertex, secondVertex)
            ));
    }

    @Test
    public void testPipelineDescriptorPushConstantRangesValidation() {
        PipelineDescriptor base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        PipelineDescriptor.PushConstantRange validRange = new PipelineDescriptor.PushConstantRange(
            0,
            64,
            java.util.Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT)
        );

        PipelineDescriptor withPushConstants = base.withPushConstantRanges(java.util.List.of(validRange));

        assertEquals(1, withPushConstants.getPushConstantRanges().size());
        assertEquals(64, withPushConstants.getPushConstantRanges().get(0).size());

        PipelineDescriptor.PushConstantRange overlapA = new PipelineDescriptor.PushConstantRange(
            0,
            32,
            java.util.Set.of(VulkanicShaderStage.VERTEX)
        );
        PipelineDescriptor.PushConstantRange overlapB = new PipelineDescriptor.PushConstantRange(
            16,
            32,
            java.util.Set.of(VulkanicShaderStage.FRAGMENT)
        );

        assertThrows(IllegalArgumentException.class,
            () -> base.withPushConstantRanges(java.util.List.of(overlapA, overlapB)),
            "Overlapping push-constant ranges should be rejected for deterministic layout derivation");
    }

    @Test
    public void testPipelineDescriptorCompilationKeyTracksSpirvAndPushConstants() {
        PipelineDescriptor base = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        VulkanicSpirvModule vertexModuleA = createTestSpirvModule(
            VulkanicShaderStage.VERTEX,
            "main",
            new byte[] {10, 20, 30, 40}
        );
        VulkanicSpirvModule fragmentModuleA = createTestSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[] {11, 21, 31, 41}
        );

        PipelineDescriptor descriptorA = PipelineDescriptor.fromPortableStateAndSpirvModules(
            base.getPortableState(),
            java.util.List.of(vertexModuleA, fragmentModuleA)
        ).withPushConstantRanges(java.util.List.of(
            new PipelineDescriptor.PushConstantRange(
                0,
                16,
                java.util.Set.of(VulkanicShaderStage.VERTEX)
            )
        ));

        PipelineDescriptor descriptorB = PipelineDescriptor.fromPortableStateAndSpirvModules(
            base.getPortableState(),
            java.util.List.of(
                createTestSpirvModule(VulkanicShaderStage.VERTEX, "main", new byte[] {10, 20, 30, 40}),
                createTestSpirvModule(VulkanicShaderStage.FRAGMENT, "main", new byte[] {11, 21, 31, 41})
            )
        ).withPushConstantRanges(java.util.List.of(
            new PipelineDescriptor.PushConstantRange(
                0,
                16,
                java.util.Set.of(VulkanicShaderStage.VERTEX)
            )
        ));

        PipelineDescriptor descriptorChanged = PipelineDescriptor.fromPortableStateAndSpirvModules(
            base.getPortableState(),
            java.util.List.of(
                createTestSpirvModule(VulkanicShaderStage.VERTEX, "main", new byte[] {10, 20, 30, 40}),
                createTestSpirvModule(VulkanicShaderStage.FRAGMENT, "main", new byte[] {99, 88, 77, 66})
            )
        ).withPushConstantRanges(java.util.List.of(
            new PipelineDescriptor.PushConstantRange(
                0,
                16,
                java.util.Set.of(VulkanicShaderStage.VERTEX)
            )
        ));

        String keyA = descriptorA.getPipelineCompilationKey();
        String keyB = descriptorB.getPipelineCompilationKey();
        String changedKey = descriptorChanged.getPipelineCompilationKey();

        assertEquals(keyA, keyB,
            "Pipeline compilation key should be deterministic for equivalent SPIR-V/push-constant inputs");
        assertNotEquals(keyA, changedKey,
            "Pipeline compilation key should change when SPIR-V payload changes");
        assertEquals(64, keyA.length(), "Pipeline compilation key should be SHA-256 hex");
    }

    @Test
    public void testPipelineResourceBindingsValidateAgainstLayout() {
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(
                new OpenGLBuffer(7, VulkanicBuffer.USAGE_UNIFORM, 256), 0, 256))
            .bindTexelBuffer("CloudFaces", 3)
            .build();

        assertDoesNotThrow(() -> bindings.validateAgainst(descriptor.getResourceLayout()));
    }

    @Test
    public void testPipelineResourceBindingsSamplerCanCarryTextureView() {
        OpenGLTexture texture = OpenGLTexture.nonOwning(
            33,
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8,
            16,
            16,
            1,
            1,
            "sampler-view-test"
        );
        OpenGLTextureView textureView = new OpenGLTextureView(texture, 0, 1);

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", textureView, 2)
            .build();

        PipelineResourceBindings.SamplerBinding samplerBinding = bindings.getSamplerBinding("Sampler0")
            .orElseThrow(() -> new IllegalStateException("Sampler binding missing"));

        assertEquals(2, samplerBinding.textureUnit());
        assertSame(textureView, samplerBinding.textureView(),
            "Sampler binding should retain the backend-neutral texture view so backends can own the actual texture bind");
    }

    @Test
    public void testPipelineResourceBindingsMissingResourceFailsValidation() {
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(
                new OpenGLBuffer(8, VulkanicBuffer.USAGE_UNIFORM, 256), 0, 256))
            .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> bindings.validateAgainst(descriptor.getResourceLayout()));
        assertTrue(error.getMessage().contains("CloudFaces"));
    }

    @Test
    public void testPipelineResourceBindingsUnknownNameFailsValidation() {
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(
                new OpenGLBuffer(9, VulkanicBuffer.USAGE_UNIFORM, 256), 0, 256))
            .bindTexelBuffer("CloudFaces", 3)
            .bindSampler("UnexpectedResource", 4)
            .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> bindings.validateAgainst(descriptor.getResourceLayout()));
        assertTrue(error.getMessage().contains("UnexpectedResource"));
    }

    @Test
    public void testPipelineResourceBindingsBuilderRejectsDuplicateNames() {
        OpenGLBuffer buffer = new OpenGLBuffer(10, VulkanicBuffer.USAGE_UNIFORM, 128);

        assertThrows(IllegalArgumentException.class,
            () -> PipelineResourceBindings.builder()
                .bindSampler("SharedName", 0)
                .bindUniformBuffer("SharedName", new VulkanicBufferSlice(buffer, 0, 64)));
    }

    @Test
    public void testPipelineDescriptorFromPortableStateNull() {
        assertThrows(IllegalArgumentException.class,
            () -> PipelineDescriptor.fromPortableState(null));
    }

    @Test
    public void testUniformBindingValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineDescriptor.UniformBinding("texelMissingFormat", UniformType.TEXEL_BUFFER, null));
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineDescriptor.UniformBinding("uniformWithFormat", UniformType.UNIFORM_BUFFER, TextureFormat.RGBA8));
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineDescriptor.ResourceBinding(0, 0, "samplerWithFormat",
                PipelineDescriptor.ResourceType.SAMPLER, TextureFormat.RGBA8));
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineDescriptor.ResourceBinding(0, 0, "texelMissingFormat",
                PipelineDescriptor.ResourceType.TEXEL_BUFFER, null));
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "samplerMissingStages",
                PipelineDescriptor.ResourceType.SAMPLER,
                null,
                java.util.Set.of()
            ));
    }

    // ---- PipelineHandle tests ----------------------------------------------

    @Test
    public void testOpenGLPipelineHandleClose() {
        PipelineHandle handle = new PipelineHandle() {
            @Override public boolean isValid() { return false; }
            @Override public void close() {}
        };
        assertInstanceOf(PipelineHandle.class, handle);
    }

    private static RenderPipeline buildTestPipeline() {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline"))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
            .withShaderDefine("FLAG_TEST")
            .withShaderDefine("VALUE_TEST", 123)
            .withSampler("Sampler0")
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("CloudFaces", UniformType.TEXEL_BUFFER, TextureFormat.RED8I)
            .withDepthTestFunction(DepthTestFunction.GREATER_DEPTH_TEST)
            .withPolygonMode(PolygonMode.WIREFRAME)
            .withCull(false)
            .withoutBlend()
            .withColorWrite(true, false)
            .withDepthWrite(false)
            .withColorLogic(LogicOp.OR_REVERSE)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .withDepthBias(2.5f, 1.25f)
            .build();
    }

    private static VulkanicSpirvModule createTestSpirvModule(
        VulkanicShaderStage stage,
        String entryPoint,
        byte[] bytes
    ) {
        return new VulkanicSpirvModule(
            stage,
            entryPoint,
            bytes,
            "unit-test-" + stage.name().toLowerCase(),
            "unit-test-compiler"
        );
    }

    // ---- VulkanicAPI.registerDevice / beginCommandBuffer tests -------------

    @Test
    public void testBeginCommandBufferReturnsSomething() {
        VulkanicAPI.initialize();
        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        assertNotNull(ctx, "beginCommandBuffer() must return a non-null context");
        assertTrue(ctx.isImmediate(), "OpenGL beginCommandBuffer() must return immediate context");
    }

    @Test
    public void testSubmitCommandBufferImmediateMode() {
        VulkanicAPI.initialize();
        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        assertDoesNotThrow(() -> VulkanicAPI.submitCommandBuffer(ctx));
    }
}
