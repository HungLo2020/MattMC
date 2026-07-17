package net.vulkanic.backends.vulkan;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanPipelineCreationPlannerTest {
    private final VulkanDescriptorSetLayoutPlanner descriptorLayoutPlanner = new VulkanDescriptorSetLayoutPlanner();
    private final VulkanPipelineCreationPlanner planner = new VulkanPipelineCreationPlanner();

    @Test
    void worldPipelinePlansDepthCullVertexInputAndDynamicState() {
        PipelineDescriptor descriptor = graphicsDescriptor(builder("pipeline/world_entity")
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(true)
            .build());

        VulkanPipelineCreationPlanner.GraphicsPipelinePlan plan = graphicsPlan(
            descriptor,
            VulkanRenderPassCompatibilityKey.framebuffer(
                List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
                VK10.VK_FORMAT_D32_SFLOAT,
                false
            ),
            false,
            (state, colorIndex) -> state.blendState(),
            VulkanPipelineState.StencilState.disabled()
        );

        assertEquals(List.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT),
            plan.shaderStages().stream().map(VulkanPipelineCreationPlanner.ShaderStagePlan::stage).toList());
        assertEquals(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, plan.inputAssembly().topology());
        assertEquals(VK10.VK_CULL_MODE_BACK_BIT, plan.rasterizationState().cullMode());
        assertEquals(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE, plan.rasterizationState().frontFace());
        assertTrue(plan.depthStencilState().depthTestEnabled());
        assertTrue(plan.depthStencilState().depthWriteEnabled());
        assertEquals(List.of(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR), plan.dynamicStates());
        assertTrue(plan.vertexInput().attributes().size() > 1);
    }

    @Test
    void guiPipelinePlansQuadEmulationAndNoDepthTest() {
        PipelineDescriptor descriptor = graphicsDescriptor(builder("pipeline/gui")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .build());

        VulkanPipelineCreationPlanner.GraphicsPipelinePlan plan = graphicsPlan(
            descriptor,
            VulkanRenderPassCompatibilityKey.framebuffer(List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM), VK10.VK_FORMAT_UNDEFINED, false),
            false,
            (state, colorIndex) -> state.blendState(),
            VulkanPipelineState.StencilState.disabled()
        );

        assertEquals(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, plan.inputAssembly().topology());
        assertEquals(VK10.VK_CULL_MODE_BACK_BIT, plan.rasterizationState().cullMode());
        assertEquals(VK10.VK_COMPARE_OP_ALWAYS, plan.depthStencilState().depthCompareOp());
        assertEquals(false, plan.depthStencilState().depthTestEnabled());
        assertEquals(false, plan.depthStencilState().depthWriteEnabled());
    }

    @Test
    void compositePipelinePlansFeedbackLoopFlagsAndIndexedBlends() {
        PipelineDescriptor descriptor = graphicsDescriptor(builder("pipeline/composite")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withoutBlend()
            .build());

        VulkanPipelineCreationPlanner.GraphicsPipelinePlan plan = graphicsPlan(
            descriptor,
            VulkanRenderPassCompatibilityKey.textureView(
                List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM, VK10.VK_FORMAT_R16G16B16A16_SFLOAT),
                VK10.VK_FORMAT_UNDEFINED,
                true
            ),
            true,
            (state, colorIndex) -> colorIndex == 1
                ? Optional.of(new PipelineDescriptor.BlendState(
                    SourceFactor.ONE,
                    DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE,
                    DestFactor.ONE_MINUS_SRC_ALPHA
                ))
                : Optional.empty(),
            VulkanPipelineState.StencilState.disabled()
        );

        assertEquals(VK10.VK_CULL_MODE_NONE, plan.rasterizationState().cullMode());
        assertEquals(2, plan.colorBlendState().attachments().size());
        assertEquals(false, plan.colorBlendState().attachments().get(0).blendEnabled());
        assertTrue(plan.colorBlendState().attachments().get(1).blendEnabled());
        assertEquals(
            EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_COLOR_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT
                | EXTAttachmentFeedbackLoopLayout.VK_PIPELINE_CREATE_DEPTH_STENCIL_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT,
            plan.pipelineCreateFlags()
        );
        assertEquals(true, plan.renderPassCompatibility().compatibilityKey().feedbackLoop());
    }

    @Test
    void shadowPipelinePlansDepthOnlyCompatibility() {
        PipelineDescriptor descriptor = graphicsDescriptor(builder("pipeline/shadow")
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(true)
            .build());

        VulkanPipelineCreationPlanner.GraphicsPipelinePlan plan = graphicsPlan(
            descriptor,
            VulkanRenderPassCompatibilityKey.framebuffer(List.of(), VK10.VK_FORMAT_D32_SFLOAT, false),
            false,
            (state, colorIndex) -> state.blendState(),
            VulkanPipelineState.StencilState.disabled()
        );

        assertTrue(plan.colorBlendState().attachments().isEmpty());
        assertEquals(VK10.VK_COMPARE_OP_LESS_OR_EQUAL, plan.depthStencilState().depthCompareOp());
        assertEquals(VK10.VK_FORMAT_D32_SFLOAT, plan.renderPassCompatibility().compatibilityKey().depthFormat());
        assertEquals(0, plan.renderPassCompatibility().subpass());
    }

    @Test
    void explicitVertexInputPlansDeclaredAttributeLocations() {
        PipelineDescriptor descriptor = graphicsDescriptor(builder("pipeline/extended_vertex_input")
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .build())
            .withVertexInputState(new PipelineDescriptor.VertexInputState(
                List.of(new PipelineDescriptor.VertexInputBinding(0, 32, PipelineDescriptor.VertexInputRate.VERTEX)),
                List.of(
                    new PipelineDescriptor.VertexInputAttribute(10, 0, PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_SNORM, 12),
                    new PipelineDescriptor.VertexInputAttribute(12, 0, PipelineDescriptor.VertexAttributeFormat.R16G16_USCALED, 16)
                )
            ));

        VulkanPipelineCreationPlanner.GraphicsPipelinePlan plan = graphicsPlan(
            descriptor,
            VulkanRenderPassCompatibilityKey.framebuffer(List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM), VK10.VK_FORMAT_UNDEFINED, false),
            false,
            (state, colorIndex) -> state.blendState(),
            VulkanPipelineState.StencilState.disabled()
        );

        assertEquals(true, plan.vertexInput().explicit());
        assertEquals(List.of(10, 12),
            plan.vertexInput().attributes().stream().map(VulkanPipelineCreationPlanner.VertexAttributePlan::location).toList());
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_SNORM, plan.vertexInput().attributes().get(0).format());
        assertEquals(VK10.VK_FORMAT_R16G16_USCALED, plan.vertexInput().attributes().get(1).format());
    }

    @Test
    void computePipelinePlansComputeStageAndLayoutInputs() {
        PipelineDescriptor descriptor = computeDescriptor();
        VulkanDescriptorSetLayoutPlanner.DescriptorLayoutPlan descriptorLayoutPlan =
            descriptorLayoutPlanner.plan(descriptor.getResourceLayout());

        VulkanPipelineCreationPlanner.ComputePipelinePlan plan = planner.planCompute(
            new VulkanPipelineCreationPlanner.ComputePlanRequest(descriptor, descriptorLayoutPlan)
        );

        assertEquals(VulkanicShaderStage.COMPUTE, plan.shaderStage().stage());
        assertEquals(VK10.VK_SHADER_STAGE_COMPUTE_BIT, plan.shaderStage().vkStageFlag());
        assertEquals(descriptorLayoutPlan.compatibilityKey(), plan.pipelineLayoutCompatibility().descriptorSets());
        assertEquals(plan.shaderStage(), plan.cacheKeyInputs().shaderStage());
    }

    @Test
    void equivalentPipelineInputsProduceStableCachePlans() {
        RenderPipeline pipeline = builder("pipeline/stable")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build();
        PipelineDescriptor first = graphicsDescriptor(pipeline);
        PipelineDescriptor second = graphicsDescriptor(pipeline);
        VulkanRenderPassCompatibilityKey compatibilityKey =
            VulkanRenderPassCompatibilityKey.framebuffer(List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM), VK10.VK_FORMAT_UNDEFINED, false);

        VulkanPipelineCreationPlanner.GraphicsPipelinePlan firstPlan = graphicsPlan(
            first,
            compatibilityKey,
            false,
            (state, colorIndex) -> state.blendState(),
            VulkanPipelineState.StencilState.disabled()
        );
        VulkanPipelineCreationPlanner.GraphicsPipelinePlan secondPlan = graphicsPlan(
            second,
            compatibilityKey,
            false,
            (state, colorIndex) -> state.blendState(),
            VulkanPipelineState.StencilState.disabled()
        );

        assertEquals(firstPlan.cacheKeyInputs(), secondPlan.cacheKeyInputs());
        assertThrows(UnsupportedOperationException.class, () -> firstPlan.dynamicStates().clear());
        assertThrows(UnsupportedOperationException.class, () -> firstPlan.vertexInput().attributes().clear());
    }

    @Test
    void missingGraphicsShaderStageDeclarationIsRejected() {
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            builder("pipeline/duplicate_stage")
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                .build(),
            List.of(module(VulkanicShaderStage.VERTEX, "vertex.vert"))
        );

        assertThrows(IllegalArgumentException.class, () -> graphicsPlan(
            descriptor,
            VulkanRenderPassCompatibilityKey.framebuffer(List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM), VK10.VK_FORMAT_UNDEFINED, false),
            false,
            (state, colorIndex) -> state.blendState(),
            VulkanPipelineState.StencilState.disabled()
        ));
    }

    private VulkanPipelineCreationPlanner.GraphicsPipelinePlan graphicsPlan(
        PipelineDescriptor descriptor,
        VulkanRenderPassCompatibilityKey compatibilityKey,
        boolean feedbackLoopEnabled,
        VulkanPipelineState.BlendStateResolver blendStateResolver,
        VulkanPipelineState.StencilState stencilState
    ) {
        return planner.planGraphics(new VulkanPipelineCreationPlanner.GraphicsPlanRequest(
            descriptor,
            descriptorLayoutPlanner.plan(descriptor.getResourceLayout()),
            compatibilityKey,
            mode -> mode == PolygonMode.WIREFRAME ? VK10.VK_POLYGON_MODE_LINE : VK10.VK_POLYGON_MODE_FILL,
            blendStateResolver,
            stencilState,
            feedbackLoopEnabled
        ));
    }

    private static PipelineDescriptor graphicsDescriptor(RenderPipeline pipeline) {
        return PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            pipeline,
            List.of(
                module(VulkanicShaderStage.VERTEX, pipeline.getLocation() + ".vert"),
                module(VulkanicShaderStage.FRAGMENT, pipeline.getLocation() + ".frag")
            )
        );
    }

    private static RenderPipeline.Builder builder(String location) {
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
            .withDepthBias(0.0f, 0.0f);
    }

    private static PipelineDescriptor computeDescriptor() {
        PipelineDescriptor.PortableState state = new PipelineDescriptor.PortableState(
            ResourceLocation.withDefaultNamespace("pipeline/compute"),
            ResourceLocation.withDefaultNamespace("pipeline/compute/vertex"),
            ResourceLocation.withDefaultNamespace("pipeline/compute/fragment"),
            Map.of(),
            java.util.Set.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            DepthTestFunction.NO_DEPTH_TEST,
            PolygonMode.FILL,
            false,
            VulkanicAPI.GL_BACK,
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
            List.of(module(VulkanicShaderStage.COMPUTE, "compute.comp"))
        ).withResourceLayout(new PipelineDescriptor.ResourceLayout(List.of(
            new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "Globals",
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                null,
                java.util.Set.of(VulkanicShaderStage.COMPUTE)
            )
        )));
    }

    private static VulkanicSpirvModule module(VulkanicShaderStage stage, String sourceName) {
        return new VulkanicSpirvModule(stage, "main", new byte[]{0x03, 0x02, 0x23, 0x07}, sourceName, "test");
    }
}
