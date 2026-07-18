package net.vulkanic.backends.vulkan;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class VulkanPipelineCacheKeyNormalizerTest {
    private final VulkanDescriptorSetLayoutPlanner descriptorLayoutPlanner = new VulkanDescriptorSetLayoutPlanner();
    private final VulkanPipelineCreationPlanner pipelinePlanner = new VulkanPipelineCreationPlanner();

    @Test
    void differentSpirvPayloadsCannotShareGraphicsPipelineIdentity() {
        PipelineDescriptor first = descriptor("pipeline/spirv_a", bytes(1, 2, 3, 4), bytes(5, 6, 7, 8));
        PipelineDescriptor second = descriptor("pipeline/spirv_a", bytes(1, 2, 3, 9), bytes(5, 6, 7, 8));
        VulkanRenderPassCompatibilityKey compatibilityKey = defaultCompatibility();

        assertNotEquals(
            graphicsKey(first, compatibilityKey, false),
            graphicsKey(second, compatibilityKey, false)
        );
    }

    @Test
    void differentPushConstantLayoutsCannotShareGraphicsPipelineIdentity() {
        PipelineDescriptor first = descriptor("pipeline/push_constants", bytes(1), bytes(2))
            .withPushConstantRanges(List.of(new PipelineDescriptor.PushConstantRange(
                0,
                16,
                Set.of(VulkanicShaderStage.VERTEX)
            )));
        PipelineDescriptor second = descriptor("pipeline/push_constants", bytes(1), bytes(2))
            .withPushConstantRanges(List.of(new PipelineDescriptor.PushConstantRange(
                0,
                32,
                Set.of(VulkanicShaderStage.VERTEX)
            )));
        VulkanRenderPassCompatibilityKey compatibilityKey = defaultCompatibility();

        assertNotEquals(
            graphicsKey(first, compatibilityKey, false),
            graphicsKey(second, compatibilityKey, false)
        );
    }

    @Test
    void equivalentRenamedResourceLayoutsShareGraphicsPipelineIdentity() {
        PipelineDescriptor first = descriptor("pipeline/renamed_resources", bytes(1), bytes(2))
            .withResourceLayout(samplerLayout("DiffuseTexture"));
        PipelineDescriptor second = descriptor("pipeline/renamed_resources", bytes(1), bytes(2))
            .withResourceLayout(samplerLayout("AlbedoTexture"));
        VulkanRenderPassCompatibilityKey compatibilityKey = defaultCompatibility();

        assertNotEquals(first.getPipelineCompilationKey(), second.getPipelineCompilationKey());
        assertEquals(
            graphicsKey(first, compatibilityKey, false),
            graphicsKey(second, compatibilityKey, false)
        );
    }

    @Test
    void differentRenderPassCompatibilityCannotShareGraphicsPipelineIdentity() {
        PipelineDescriptor descriptor = descriptor("pipeline/render_pass", bytes(1), bytes(2));

        assertNotEquals(
            graphicsKey(
                descriptor,
                VulkanRenderPassCompatibilityKey.framebuffer(
                    List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
                    VK10.VK_FORMAT_UNDEFINED,
                    false
                ),
                false
            ),
            graphicsKey(
                descriptor,
                VulkanRenderPassCompatibilityKey.framebuffer(
                    List.of(VK10.VK_FORMAT_R16G16B16A16_SFLOAT),
                    VK10.VK_FORMAT_UNDEFINED,
                    false
                ),
                false
            )
        );
    }

    @Test
    void differentFragmentOutputInterfacesCannotShareGraphicsPipelineIdentity() {
        RenderPipeline pipeline = builder("pipeline/fragment_outputs");
        PipelineDescriptor first = PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            pipeline,
            List.of(
                module(VulkanicShaderStage.VERTEX, bytes(1), "fragment_outputs.vert"),
                module(
                    VulkanicShaderStage.FRAGMENT,
                    bytes(2),
                    "fragment_outputs.frag",
                    new VulkanicSpirvModule.FragmentOutput(0, "fragColor", "vec4")
                )
            )
        );
        PipelineDescriptor second = PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            pipeline,
            List.of(
                module(VulkanicShaderStage.VERTEX, bytes(1), "fragment_outputs.vert"),
                module(
                    VulkanicShaderStage.FRAGMENT,
                    bytes(2),
                    "fragment_outputs.frag",
                    new VulkanicSpirvModule.FragmentOutput(1, "fragColor", "vec4")
                )
            )
        );

        assertNotEquals(
            graphicsKey(first, defaultCompatibility(), false),
            graphicsKey(second, defaultCompatibility(), false)
        );
    }

    @Test
    void equivalentImmutableGraphicsPlansProduceEqualStableKeys() {
        PipelineDescriptor first = descriptor("pipeline/equivalent", bytes(1), bytes(2));
        PipelineDescriptor second = descriptor("pipeline/equivalent", bytes(1), bytes(2));
        VulkanRenderPassCompatibilityKey compatibilityKey = VulkanRenderPassCompatibilityKey.textureView(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_D32_SFLOAT,
            true
        );

        assertEquals(
            graphicsKey(first, compatibilityKey, true),
            graphicsKey(second, compatibilityKey, true)
        );
    }

    @Test
    void equivalentImmutableComputePlansProduceEqualStableKeys() {
        PipelineDescriptor first = computeDescriptor("pipeline/compute_equivalent", bytes(9));
        PipelineDescriptor second = computeDescriptor("pipeline/compute_equivalent", bytes(9));

        assertEquals(computeKey(first), computeKey(second));
    }

    @Test
    void equivalentLegacyProgramsWithDifferentObjectIdsShareGraphicsIdentity() {
        PipelineDescriptor first = descriptor("vulkanic/legacy_program/101", bytes(1), bytes(2));
        PipelineDescriptor second = descriptor("vulkanic/legacy_program/202", bytes(1), bytes(2));
        VulkanRenderPassCompatibilityKey compatibilityKey = defaultCompatibility();

        assertEquals(
            graphicsKey(first, compatibilityKey, false),
            graphicsKey(second, compatibilityKey, false)
        );
    }

    @Test
    void compatibleFramebufferObjectsDoNotSplitGraphicsIdentity() {
        PipelineDescriptor descriptor = descriptor("pipeline/framebuffer_compatible", bytes(1), bytes(2));
        VulkanRenderPassCompatibilityKey firstFramebuffer =
            VulkanRenderPassCompatibilityKey.framebuffer(
                List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
                VK10.VK_FORMAT_D32_SFLOAT,
                false
            );
        VulkanRenderPassCompatibilityKey secondFramebuffer =
            VulkanRenderPassCompatibilityKey.framebuffer(
                List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
                VK10.VK_FORMAT_D32_SFLOAT,
                false
            );

        assertEquals(
            graphicsKey(descriptor, firstFramebuffer, false),
            graphicsKey(descriptor, secondFramebuffer, false)
        );
    }

    @Test
    void equivalentLegacyComputeProgramsWithDifferentObjectIdsShareComputeIdentity() {
        PipelineDescriptor first = computeDescriptor("vulkanic/legacy_compute_program/101", bytes(9));
        PipelineDescriptor second = computeDescriptor("vulkanic/legacy_compute_program/202", bytes(9));

        assertEquals(computeKey(first), computeKey(second));
    }

    @Test
    void renamedComputeDescriptorResourcesShareComputeIdentity() {
        PipelineDescriptor first = computeDescriptor("pipeline/compute_renamed", bytes(9))
            .withResourceLayout(computeBufferLayout("Globals"));
        PipelineDescriptor second = computeDescriptor("pipeline/compute_renamed", bytes(9))
            .withResourceLayout(computeBufferLayout("SceneData"));

        assertNotEquals(first.getPipelineCompilationKey(), second.getPipelineCompilationKey());
        assertEquals(computeKey(first), computeKey(second));
    }

    @Test
    void differentComputeShadersAndPushConstantsRemainDistinct() {
        PipelineDescriptor firstShader = computeDescriptor("pipeline/compute_distinct", bytes(9));
        PipelineDescriptor secondShader = computeDescriptor("pipeline/compute_distinct", bytes(10));

        assertNotEquals(computeKey(firstShader), computeKey(secondShader));

        PipelineDescriptor firstPush = computeDescriptor("pipeline/compute_push", bytes(9))
            .withPushConstantRanges(List.of(new PipelineDescriptor.PushConstantRange(
                0,
                16,
                Set.of(VulkanicShaderStage.COMPUTE)
            )));
        PipelineDescriptor secondPush = computeDescriptor("pipeline/compute_push", bytes(9))
            .withPushConstantRanges(List.of(new PipelineDescriptor.PushConstantRange(
                0,
                32,
                Set.of(VulkanicShaderStage.COMPUTE)
            )));

        assertNotEquals(computeKey(firstPush), computeKey(secondPush));
    }

    @Test
    void graphicsAndComputeIdentitiesRemainSeparate() {
        PipelineDescriptor graphics = descriptor("pipeline/stage_separation", bytes(9), bytes(10));
        PipelineDescriptor compute = computeDescriptor("pipeline/stage_separation", bytes(9));

        assertNotEquals(
            (Object) graphicsKey(graphics, defaultCompatibility(), false),
            computeKey(compute)
        );
    }

    @Test
    void specialGraphicsVariantsRemainDistinctWhenPipelineStateDiffers() {
        PipelineDescriptor descriptor = descriptor("pipeline/special_variants", bytes(1), bytes(2));
        VulkanRenderPassCompatibilityKey feedbackLoopCompatibility =
            VulkanRenderPassCompatibilityKey.textureView(
                List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
                VK10.VK_FORMAT_UNDEFINED,
                true
            );

        assertNotEquals(
            graphicsKey(descriptor, feedbackLoopCompatibility, false),
            graphicsKey(descriptor, feedbackLoopCompatibility, true)
        );
        assertNotEquals(
            graphicsKey(
                descriptor,
                feedbackLoopCompatibility,
                false,
                (state, colorIndex) -> Optional.empty()
            ),
            graphicsKey(
                descriptor,
                feedbackLoopCompatibility,
                false,
                (state, colorIndex) -> Optional.of(new PipelineDescriptor.BlendState(
                    SourceFactor.ONE,
                    DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE,
                    DestFactor.ONE_MINUS_SRC_ALPHA
                ))
            )
        );
    }

    @Test
    void graphicsPlannerCacheInputsIncludeViewportStateAndCreationFlags() {
        PipelineDescriptor descriptor = descriptor("pipeline/cache_inputs", bytes(1), bytes(2));
        VulkanPipelineCreationPlanner.GraphicsPipelinePlan plan = graphicsPlan(
            descriptor,
            VulkanRenderPassCompatibilityKey.textureView(
                List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
                VK10.VK_FORMAT_UNDEFINED,
                true
            ),
            true
        );

        assertEquals(plan.viewportState(), plan.cacheKeyInputs().viewportState());
        assertEquals(
            EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_COLOR_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT
                | EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_DEPTH_STENCIL_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT,
            plan.cacheKeyInputs().pipelineCreateFlags()
        );
    }

    private VulkanPipelineCacheKeyNormalizer.GraphicsPipelineCacheKey graphicsKey(
        PipelineDescriptor descriptor,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        boolean feedbackLoopEnabled
    ) {
        return VulkanPipelineCacheKeyNormalizer.graphicsKey(
            graphicsPlan(descriptor, compatibilityKey, feedbackLoopEnabled),
            descriptor.getSpirvModules()
        );
    }

    private VulkanPipelineCacheKeyNormalizer.GraphicsPipelineCacheKey graphicsKey(
        PipelineDescriptor descriptor,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        boolean feedbackLoopEnabled,
        VulkanPipelineState.BlendStateResolver blendStateResolver
    ) {
        return VulkanPipelineCacheKeyNormalizer.graphicsKey(
            graphicsPlan(descriptor, compatibilityKey, feedbackLoopEnabled, blendStateResolver),
            descriptor.getSpirvModules()
        );
    }

    private VulkanPipelineCreationPlanner.GraphicsPipelinePlan graphicsPlan(
        PipelineDescriptor descriptor,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        boolean feedbackLoopEnabled
    ) {
        return graphicsPlan(
            descriptor,
            compatibilityKey,
            feedbackLoopEnabled,
            (state, colorIndex) -> state.blendState()
        );
    }

    private VulkanPipelineCreationPlanner.GraphicsPipelinePlan graphicsPlan(
        PipelineDescriptor descriptor,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        boolean feedbackLoopEnabled,
        VulkanPipelineState.BlendStateResolver blendStateResolver
    ) {
        return pipelinePlanner.planGraphics(new VulkanPipelineCreationPlanner.GraphicsPlanRequest(
            descriptor,
            descriptorLayoutPlanner.plan(descriptor.getResourceLayout()),
            compatibilityKey,
            mode -> mode == PolygonMode.WIREFRAME ? VK10.VK_POLYGON_MODE_LINE : VK10.VK_POLYGON_MODE_FILL,
            blendStateResolver,
            VulkanPipelineState.StencilState.disabled(),
            feedbackLoopEnabled
        ));
    }

    private VulkanPipelineCacheKeyNormalizer.ComputePipelineCacheKey computeKey(PipelineDescriptor descriptor) {
        return VulkanPipelineCacheKeyNormalizer.computeKey(
            pipelinePlanner.planCompute(new VulkanPipelineCreationPlanner.ComputePlanRequest(
                descriptor,
                descriptorLayoutPlanner.plan(descriptor.getResourceLayout())
            )),
            descriptor.getSpirvModules()
        );
    }

    private static PipelineDescriptor descriptor(String location, byte[] vertexBytes, byte[] fragmentBytes) {
        return PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            builder(location),
            List.of(
                module(VulkanicShaderStage.VERTEX, vertexBytes, location + ".vert"),
                module(VulkanicShaderStage.FRAGMENT, fragmentBytes, location + ".frag")
            )
        );
    }

    private static PipelineDescriptor computeDescriptor(String location, byte[] computeBytes) {
        PipelineDescriptor.PortableState state = new PipelineDescriptor.PortableState(
            ResourceLocation.withDefaultNamespace(location),
            ResourceLocation.withDefaultNamespace(location + "/vertex"),
            ResourceLocation.withDefaultNamespace(location + "/fragment"),
            java.util.Map.of(),
            Set.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            DepthTestFunction.NO_DEPTH_TEST,
            PolygonMode.FILL,
            false,
            net.vulkanic.VulkanicAPI.GL_BACK,
            true,
            true,
            false,
            net.blaze3d.platform.LogicOp.NONE,
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.TRIANGLES,
            0.0f,
            0.0f
        );
        return PipelineDescriptor.fromPortableStateAndSpirvModules(
            state,
            List.of(module(VulkanicShaderStage.COMPUTE, computeBytes, location + ".comp"))
        );
    }

    private static RenderPipeline builder(String location) {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace(location))
            .withVertexShader(ResourceLocation.withDefaultNamespace(location + "/vertex"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace(location + "/fragment"))
            .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
            .withPolygonMode(PolygonMode.FILL)
            .withCull(true)
            .withoutBlend()
            .withColorWrite(true, true)
            .withDepthWrite(true)
            .withColorLogic(net.blaze3d.platform.LogicOp.NONE)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthBias(0.0f, 0.0f)
            .build();
    }

    private static PipelineDescriptor.ResourceLayout samplerLayout(String name) {
        return new PipelineDescriptor.ResourceLayout(List.of(new PipelineDescriptor.ResourceBinding(
            0,
            0,
            name,
            PipelineDescriptor.ResourceType.SAMPLER,
            null,
            Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT)
        )));
    }

    private static PipelineDescriptor.ResourceLayout computeBufferLayout(String name) {
        return new PipelineDescriptor.ResourceLayout(List.of(new PipelineDescriptor.ResourceBinding(
            0,
            0,
            name,
            PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
            null,
            Set.of(VulkanicShaderStage.COMPUTE)
        )));
    }

    private static VulkanRenderPassCompatibilityKey defaultCompatibility() {
        return VulkanRenderPassCompatibilityKey.framebuffer(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_UNDEFINED,
            false
        );
    }

    private static VulkanicSpirvModule module(VulkanicShaderStage stage, byte[] bytes, String sourceName) {
        return new VulkanicSpirvModule(stage, "main", bytes, sourceName, "test");
    }

    private static VulkanicSpirvModule module(
        VulkanicShaderStage stage,
        byte[] bytes,
        String sourceName,
        VulkanicSpirvModule.FragmentOutput... fragmentOutputs
    ) {
        return new VulkanicSpirvModule(stage, "main", bytes, sourceName, "test")
            .withFragmentOutputs(List.of(fragmentOutputs));
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
