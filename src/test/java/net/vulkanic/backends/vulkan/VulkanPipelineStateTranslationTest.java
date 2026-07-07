package net.vulkanic.backends.vulkan;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanPipelineStateTranslationTest {

    @Test
    public void testCullStateTranslatesPortableBackFaceCullRequest() {
        VulkanPipelineState enabled = translate(state(builder().withCull(true).build()), 1);
        VulkanPipelineState disabled = translate(state(builder().withCull(false).build()), 1);

        assertTrue(enabled.requestedCull());
        assertEquals(VulkanPipelineState.CullDecision.PORTABLE_STATE_ENABLED, enabled.cullDecision());
        assertEquals(VK10.VK_CULL_MODE_BACK_BIT, enabled.cullMode());
        assertEquals(VK10.VK_CULL_MODE_NONE, disabled.cullMode());
        assertEquals(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE, enabled.frontFace());
        assertFalse(disabled.requestedCull());
        assertEquals(VulkanPipelineState.CullDecision.PORTABLE_STATE_DISABLED, disabled.cullDecision());
        assertEquals(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE, disabled.frontFace());
    }

    @Test
    public void testCullFaceModeTranslatesIntoVulkanCullMode() {
        PipelineDescriptor.PortableState base = state(builder().withCull(true).build());

        assertEquals(VK10.VK_CULL_MODE_FRONT_BIT, translate(withCullFaceMode(base, VulkanicAPI.GL_FRONT), 1).cullMode());
        assertEquals(VK10.VK_CULL_MODE_BACK_BIT, translate(withCullFaceMode(base, VulkanicAPI.GL_BACK), 1).cullMode());
        assertEquals(
            VK10.VK_CULL_MODE_FRONT_AND_BACK,
            translate(withCullFaceMode(base, VulkanicAPI.GL_FRONT_AND_BACK), 1).cullMode()
        );
    }

    @Test
    public void testGuiLikeScreenPipelinesHonorPortableCullRequest() {
        RenderPipeline guiLikePipeline = builder()
            .withLocation(ResourceLocation.withDefaultNamespace("pipeline/gui_text_like"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withCull(true)
            .build();

        VulkanPipelineState state = translate(state(guiLikePipeline), 1);

        assertTrue(state.requestedCull());
        assertEquals(VulkanPipelineState.CullDecision.PORTABLE_STATE_ENABLED, state.cullDecision());
        assertEquals(VK10.VK_CULL_MODE_BACK_BIT, state.cullMode());
        assertEquals(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE, state.frontFace());
    }

    @Test
    public void testDepthColorAndAlphaStateTranslateFromPortableState() {
        VulkanPipelineState state = translate(state(builder()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withColorWrite(true, false)
            .build()), 1);

        assertFalse(state.depthTestEnabled());
        assertFalse(state.depthWriteEnabled());
        assertEquals(VK10.VK_COMPARE_OP_ALWAYS, state.depthCompareOp());
        assertEquals(
            VK10.VK_COLOR_COMPONENT_R_BIT
                | VK10.VK_COLOR_COMPONENT_G_BIT
                | VK10.VK_COLOR_COMPONENT_B_BIT,
            state.colorWriteMask());
        assertEquals(state.colorWriteMask(), state.colorBlendAttachments().get(0).colorWriteMask());
    }

    @Test
    public void testBlendStateTranslatesForEveryColorAttachment() {
        VulkanPipelineState state = translate(state(builder()
            .withBlend(BlendFunction.TRANSLUCENT)
            .build()), 2);

        assertEquals(2, state.colorBlendAttachments().size());
        for (VulkanPipelineState.ColorBlendAttachment attachment : state.colorBlendAttachments()) {
            assertTrue(attachment.blendEnabled());
            assertEquals(VK10.VK_BLEND_FACTOR_SRC_ALPHA, attachment.sourceColorBlendFactor());
            assertEquals(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, attachment.destColorBlendFactor());
            assertEquals(VK10.VK_BLEND_FACTOR_ONE, attachment.sourceAlphaBlendFactor());
            assertEquals(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, attachment.destAlphaBlendFactor());
            assertEquals(VK10.VK_BLEND_OP_ADD, attachment.colorBlendOp());
            assertEquals(VK10.VK_BLEND_OP_ADD, attachment.alphaBlendOp());
        }
    }

    @Test
    public void testIndexedBlendResolverCanVaryAttachmentsWhenPortableBlendIsAbsent() {
        PipelineDescriptor.PortableState portableState = state(builder().withoutBlend().build());
        VulkanPipelineState state = VulkanPipelineState.from(
            portableState,
            2,
            mode -> VK10.VK_POLYGON_MODE_FILL,
            (ignoredState, colorIndex) -> colorIndex == 0
                ? Optional.empty()
                : Optional.of(new PipelineDescriptor.BlendState(
                    SourceFactor.ONE,
                    DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE,
                    DestFactor.ONE_MINUS_SRC_ALPHA
                ))
        );

        assertFalse(state.colorBlendAttachments().get(0).blendEnabled());
        assertTrue(state.colorBlendAttachments().get(1).blendEnabled());
        assertEquals(VK10.VK_BLEND_FACTOR_ONE, state.colorBlendAttachments().get(1).sourceColorBlendFactor());
        assertEquals(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, state.colorBlendAttachments().get(1).destColorBlendFactor());
    }

    @Test
    public void testStencilStateDisablesWhenRenderPassHasNoStencilAttachment() {
        PipelineDescriptor.PortableState portableState = state(builder().build());
        VulkanPipelineState.StencilState stencilState = stencilState(true);

        VulkanPipelineState state = VulkanPipelineState.from(
            portableState,
            1,
            mode -> VK10.VK_POLYGON_MODE_FILL,
            (ignoredState, colorIndex) -> Optional.empty(),
            stencilState,
            false
        );

        assertFalse(state.stencilTestEnabled());
    }

    @Test
    public void testStencilStateTranslatesFrontAndBackWhenAttachmentSupportsStencil() {
        PipelineDescriptor.PortableState portableState = state(builder().build());
        VulkanPipelineState.StencilState stencilState = stencilState(true);

        VulkanPipelineState state = VulkanPipelineState.from(
            portableState,
            1,
            mode -> VK10.VK_POLYGON_MODE_FILL,
            (ignoredState, colorIndex) -> Optional.empty(),
            stencilState,
            true
        );

        assertTrue(state.stencilTestEnabled());
        assertEquals(VK10.VK_COMPARE_OP_LESS, state.frontStencil().compareOp());
        assertEquals(VK10.VK_STENCIL_OP_REPLACE, state.frontStencil().failOp());
        assertEquals(VK10.VK_STENCIL_OP_INCREMENT_AND_WRAP, state.frontStencil().passOp());
        assertEquals(VK10.VK_STENCIL_OP_DECREMENT_AND_CLAMP, state.frontStencil().depthFailOp());
        assertEquals(0x33, state.frontStencil().compareMask());
        assertEquals(0x55, state.frontStencil().writeMask());
        assertEquals(7, state.frontStencil().reference());
        assertEquals(VK10.VK_COMPARE_OP_GREATER_OR_EQUAL, state.backStencil().compareOp());
        assertEquals(VK10.VK_STENCIL_OP_KEEP, state.backStencil().failOp());
        assertEquals(VK10.VK_STENCIL_OP_INVERT, state.backStencil().passOp());
        assertEquals(VK10.VK_STENCIL_OP_ZERO, state.backStencil().depthFailOp());
        assertEquals(0x44, state.backStencil().compareMask());
        assertEquals(0xAA, state.backStencil().writeMask());
        assertEquals(3, state.backStencil().reference());
    }

    @Test
    public void testLogicPolygonAndDepthBiasTranslateFromPortableState() {
        VulkanPipelineState state = translate(state(builder()
            .withPolygonMode(PolygonMode.WIREFRAME)
            .withDepthBias(2.5f, 1.25f)
            .withColorLogic(LogicOp.OR_REVERSE)
            .build()), 0, VK10.VK_POLYGON_MODE_LINE);

        assertEquals(VK10.VK_POLYGON_MODE_LINE, state.polygonMode());
        assertTrue(state.depthBiasEnabled());
        assertEquals(1.25f, state.depthBiasConstantFactor());
        assertEquals(2.5f, state.depthBiasSlopeFactor());
        assertTrue(state.logicOpEnabled());
        assertEquals(VK10.VK_LOGIC_OP_OR_REVERSE, state.logicOp());
        assertTrue(state.colorBlendAttachments().isEmpty());
    }

    @Test
    public void testRejectsInvalidTranslationInputs() {
        PipelineDescriptor.PortableState portableState = state(builder().build());

        assertThrows(NullPointerException.class,
            () -> VulkanPipelineState.from(null, 1, mode -> VK10.VK_POLYGON_MODE_FILL, (state, index) -> Optional.empty()));
        assertThrows(NullPointerException.class,
            () -> VulkanPipelineState.from(portableState, 1, null, (state, index) -> Optional.empty()));
        assertThrows(NullPointerException.class,
            () -> VulkanPipelineState.from(portableState, 1, mode -> VK10.VK_POLYGON_MODE_FILL, null));
        assertThrows(IllegalArgumentException.class,
            () -> VulkanPipelineState.from(portableState, -1, mode -> VK10.VK_POLYGON_MODE_FILL, (state, index) -> Optional.empty()));
    }

    private static VulkanPipelineState translate(PipelineDescriptor.PortableState state, int colorAttachmentCount) {
        return translate(state, colorAttachmentCount, VK10.VK_POLYGON_MODE_FILL);
    }

    private static VulkanPipelineState translate(
        PipelineDescriptor.PortableState state,
        int colorAttachmentCount,
        int polygonMode
    ) {
        return VulkanPipelineState.from(
            state,
            colorAttachmentCount,
            mode -> polygonMode,
            (portableState, colorIndex) -> portableState.blendState()
        );
    }

    private static PipelineDescriptor.PortableState state(RenderPipeline pipeline) {
        return PipelineDescriptor.fromRenderPipeline(pipeline).getPortableState();
    }

    private static PipelineDescriptor.PortableState withCullFaceMode(PipelineDescriptor.PortableState state, int cullFaceMode) {
        return new PipelineDescriptor.PortableState(
            state.location(),
            state.vertexShader(),
            state.fragmentShader(),
            state.shaderDefineValues(),
            state.shaderDefineFlags(),
            state.samplers(),
            state.uniforms(),
            state.blendState(),
            state.depthTestFunction(),
            state.polygonMode(),
            state.cull(),
            cullFaceMode,
            state.writeColor(),
            state.writeAlpha(),
            state.writeDepth(),
            state.colorLogic(),
            state.vertexFormat(),
            state.vertexFormatMode(),
            state.depthBiasScaleFactor(),
            state.depthBiasConstant()
        );
    }

    private static VulkanPipelineState.StencilState stencilState(boolean enabled) {
        return new VulkanPipelineState.StencilState(
            enabled,
            VulkanPipelineState.StencilFaceState.fromLegacyGl(
                VulkanicAPI.GL_REPLACE,
                VulkanicAPI.GL_DECR,
                VulkanicAPI.GL_INCR_WRAP,
                VulkanicAPI.GL_LESS,
                0x33,
                0x55,
                7
            ),
            VulkanPipelineState.StencilFaceState.fromLegacyGl(
                VulkanicAPI.GL_KEEP,
                VulkanicAPI.GL_ZERO,
                VulkanicAPI.GL_INVERT,
                VulkanicAPI.GL_GEQUAL,
                0x44,
                0xAA,
                3
            )
        );
    }

    private static RenderPipeline.Builder builder() {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline_state_translation"))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
            .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
            .withPolygonMode(PolygonMode.FILL)
            .withCull(true)
            .withoutBlend()
            .withColorWrite(true, true)
            .withDepthWrite(true)
            .withColorLogic(LogicOp.NONE)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthBias(0.0f, 0.0f);
    }
}
