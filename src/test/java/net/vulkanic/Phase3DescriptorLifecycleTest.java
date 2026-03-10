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
import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.opengl.OpenGLBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 prep descriptor pool/set lifecycle seam.
 */
public class Phase3DescriptorLifecycleTest {

    @Test
    public void testGraphicsBackendHasDescriptorLifecycleMethods() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod(
            "createDescriptorPool",
            DescriptorPoolDescriptor.class));
        assertNotNull(GraphicsBackend.class.getMethod(
            "allocateDescriptorSet",
            DescriptorPoolHandle.class,
            PipelineDescriptor.class));
        assertNotNull(GraphicsBackend.class.getMethod(
            "updateDescriptorSet",
            DescriptorSetHandle.class,
            PipelineResourceBindings.class));
        assertNotNull(GraphicsBackend.class.getMethod(
            "bindDescriptorSet",
            CommandContext.class,
            PipelineHandle.class,
            PipelineDescriptor.class,
            DescriptorSetHandle.class));
        assertNotNull(GraphicsBackend.class.getMethod(
            "resetDescriptorPool",
            DescriptorPoolHandle.class));
    }

    @Test
    public void testVulkanicAPIHasDescriptorLifecycleMethods() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod(
            "createDescriptorPool",
            DescriptorPoolDescriptor.class));
        assertNotNull(VulkanicAPI.class.getMethod(
            "allocateDescriptorSet",
            DescriptorPoolHandle.class,
            PipelineDescriptor.class));
        assertNotNull(VulkanicAPI.class.getMethod(
            "updateDescriptorSet",
            DescriptorSetHandle.class,
            PipelineResourceBindings.class));
        assertNotNull(VulkanicAPI.class.getMethod(
            "bindDescriptorSet",
            CommandContext.class,
            PipelineHandle.class,
            PipelineDescriptor.class,
            DescriptorSetHandle.class));
        assertNotNull(VulkanicAPI.class.getMethod(
            "resetDescriptorPool",
            DescriptorPoolHandle.class));
    }

    @Test
    public void testDescriptorPoolDescriptorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new DescriptorPoolDescriptor(0));
        assertThrows(IllegalArgumentException.class, () -> new DescriptorPoolDescriptor(-1));
        assertDoesNotThrow(() -> new DescriptorPoolDescriptor(1));
    }

    @Test
    public void testOpenGLDescriptorPoolAllocateUpdateResetLifecycle() {
        OpenGLBackend backend = new OpenGLBackend();
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        DescriptorPoolHandle pool = backend.createDescriptorPool(new DescriptorPoolDescriptor(1));
        DescriptorSetHandle descriptorSet = backend.allocateDescriptorSet(pool, descriptor);

        assertTrue(pool.isValid());
        assertTrue(descriptorSet.isValid());
        assertEquals(1, pool.allocatedSetCount());
        assertEquals(descriptor.getStableCacheKey(), descriptorSet.layoutKey());

        PipelineResourceBindings bindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(
                new OpenGLBuffer(77, VulkanicBuffer.USAGE_UNIFORM, 256), 0, 256))
            .bindTexelBuffer("CloudFaces", 3)
            .build();

        assertDoesNotThrow(() -> backend.updateDescriptorSet(descriptorSet, bindings));

        IllegalStateException exhausted = assertThrows(IllegalStateException.class,
            () -> backend.allocateDescriptorSet(pool, descriptor));
        assertTrue(exhausted.getMessage().contains("exhausted"));

        assertDoesNotThrow(() -> backend.resetDescriptorPool(pool));
        assertEquals(0, pool.allocatedSetCount());
        assertFalse(descriptorSet.isValid());

        DescriptorSetHandle secondSet = backend.allocateDescriptorSet(pool, descriptor);
        assertTrue(secondSet.isValid());
        secondSet.close();
        pool.close();
        assertFalse(pool.isValid());
    }

    @Test
    public void testDescriptorSetUpdateValidatesLayout() {
        OpenGLBackend backend = new OpenGLBackend();
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());

        DescriptorPoolHandle pool = backend.createDescriptorPool(new DescriptorPoolDescriptor(2));
        DescriptorSetHandle descriptorSet = backend.allocateDescriptorSet(pool, descriptor);

        PipelineResourceBindings invalidBindings = PipelineResourceBindings.builder()
            .bindSampler("Sampler0", 0)
            .bindUniformBuffer("Globals", new VulkanicBufferSlice(
                new OpenGLBuffer(88, VulkanicBuffer.USAGE_UNIFORM, 256), 0, 256))
            .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> backend.updateDescriptorSet(descriptorSet, invalidBindings));
        assertTrue(error.getMessage().contains("CloudFaces"));
    }

    @Test
    public void testBindDescriptorSetRejectsLayoutMismatchBeforeGLPath() {
        OpenGLBackend backend = new OpenGLBackend();

        PipelineDescriptor descriptorA = PipelineDescriptor.fromRenderPipeline(buildTestPipeline());
        PipelineDescriptor descriptorB = PipelineDescriptor.fromRenderPipeline(
            RenderPipeline.builder()
                .withLocation(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline_other"))
                .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
                .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
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

        DescriptorPoolHandle pool = backend.createDescriptorPool(new DescriptorPoolDescriptor(1));
        DescriptorSetHandle descriptorSet = backend.allocateDescriptorSet(pool, descriptorA);

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
            () -> backend.bindDescriptorSet(null, null, descriptorB, descriptorSet));
        assertTrue(mismatch.getMessage().contains("layout key mismatch"));
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
}
