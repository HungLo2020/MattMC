package net.vulkanic.backends.vulkan;

import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Native Vulkan pipeline-state snapshot derived from Vulkanic's portable
 * pipeline contract.
 */
record VulkanPipelineState(
    int polygonMode,
    RasterizationPolicy rasterizationPolicy,
    boolean depthBiasEnabled,
    float depthBiasConstantFactor,
    float depthBiasSlopeFactor,
    boolean depthTestEnabled,
    boolean depthWriteEnabled,
    int depthCompareOp,
    int colorWriteMask,
    boolean logicOpEnabled,
    int logicOp,
    boolean stencilTestEnabled,
    StencilFaceState frontStencil,
    StencilFaceState backStencil,
    List<ColorBlendAttachment> colorBlendAttachments
) {
    VulkanPipelineState {
        Objects.requireNonNull(rasterizationPolicy, "rasterizationPolicy must not be null");
        Objects.requireNonNull(frontStencil, "frontStencil must not be null");
        Objects.requireNonNull(backStencil, "backStencil must not be null");
        colorBlendAttachments = List.copyOf(
            Objects.requireNonNull(colorBlendAttachments, "colorBlendAttachments must not be null"));
    }

    static VulkanPipelineState from(
        PipelineDescriptor.PortableState portableState,
        int colorAttachmentCount,
        PolygonModeResolver polygonModeResolver,
        BlendStateResolver blendStateResolver
    ) {
        return from(
            portableState,
            colorAttachmentCount,
            polygonModeResolver,
            blendStateResolver,
            StencilState.disabled(),
            false
        );
    }

    static VulkanPipelineState from(
        PipelineDescriptor.PortableState portableState,
        int colorAttachmentCount,
        PolygonModeResolver polygonModeResolver,
        BlendStateResolver blendStateResolver,
        StencilState stencilState,
        boolean hasStencilAttachment
    ) {
        Objects.requireNonNull(portableState, "portableState must not be null");
        Objects.requireNonNull(polygonModeResolver, "polygonModeResolver must not be null");
        Objects.requireNonNull(blendStateResolver, "blendStateResolver must not be null");
        Objects.requireNonNull(stencilState, "stencilState must not be null");
        if (colorAttachmentCount < 0) {
            throw new IllegalArgumentException("colorAttachmentCount must be >= 0");
        }

        int colorWriteMask = colorWriteMask(portableState);
        List<ColorBlendAttachment> attachments = new ArrayList<>(colorAttachmentCount);
        for (int colorIndex = 0; colorIndex < colorAttachmentCount; colorIndex++) {
            Optional<PipelineDescriptor.BlendState> blendState =
                blendStateResolver.resolve(portableState, colorIndex);
            attachments.add(ColorBlendAttachment.from(colorWriteMask, blendState));
        }
        RasterizationPolicy rasterizationPolicy = RasterizationPolicy.from(portableState);

        return new VulkanPipelineState(
            polygonModeResolver.resolve(portableState.polygonMode()),
            rasterizationPolicy,
            portableState.depthBiasConstant() != 0.0f || portableState.depthBiasScaleFactor() != 0.0f,
            portableState.depthBiasConstant(),
            portableState.depthBiasScaleFactor(),
            portableState.depthTestFunction() != DepthTestFunction.NO_DEPTH_TEST,
            portableState.writeDepth(),
            toVkDepthCompareOp(portableState.depthTestFunction()),
            colorWriteMask,
            portableState.colorLogic() != LogicOp.NONE,
            toVkLogicOp(portableState.colorLogic()),
            stencilState.enabled() && hasStencilAttachment,
            stencilState.front(),
            stencilState.back(),
            attachments
        );
    }

    int cullMode() {
        return rasterizationPolicy.cullMode();
    }

    int frontFace() {
        return rasterizationPolicy.frontFace();
    }

    boolean requestedCull() {
        return rasterizationPolicy.requestedCull();
    }

    CullDecision cullDecision() {
        return rasterizationPolicy.cullDecision();
    }

    record RasterizationPolicy(
        boolean requestedCull,
        CullDecision cullDecision,
        int cullMode,
        int frontFace
    ) {
        private static RasterizationPolicy from(PipelineDescriptor.PortableState portableState) {
            Objects.requireNonNull(portableState, "portableState must not be null");
            int cullMode = portableState.cull()
                ? toVkCullMode(portableState.cullFaceMode())
                : VK10.VK_CULL_MODE_NONE;
            return new RasterizationPolicy(
                portableState.cull(),
                portableState.cull()
                    ? CullDecision.PORTABLE_STATE_ENABLED
                    : CullDecision.PORTABLE_STATE_DISABLED,
                cullMode,
                VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE
            );
        }
    }

    enum CullDecision {
        PORTABLE_STATE_DISABLED,
        PORTABLE_STATE_ENABLED
    }

    private static int toVkCullMode(int cullFaceMode) {
        return switch (cullFaceMode) {
            case VulkanicAPI.GL_FRONT -> VK10.VK_CULL_MODE_FRONT_BIT;
            case VulkanicAPI.GL_BACK -> VK10.VK_CULL_MODE_BACK_BIT;
            case VulkanicAPI.GL_FRONT_AND_BACK -> VK10.VK_CULL_MODE_FRONT_AND_BACK;
            default -> throw new IllegalArgumentException("Unsupported cull face mode: " + cullFaceMode);
        };
    }

    private static int colorWriteMask(PipelineDescriptor.PortableState portableState) {
        int colorWriteMask = 0;
        if (portableState.writeColor()) {
            colorWriteMask |= VK10.VK_COLOR_COMPONENT_R_BIT
                | VK10.VK_COLOR_COMPONENT_G_BIT
                | VK10.VK_COLOR_COMPONENT_B_BIT;
        }
        if (portableState.writeAlpha()) {
            colorWriteMask |= VK10.VK_COLOR_COMPONENT_A_BIT;
        }
        return colorWriteMask;
    }

    private static int toVkDepthCompareOp(DepthTestFunction func) {
        return switch (func) {
            case NO_DEPTH_TEST -> VK10.VK_COMPARE_OP_ALWAYS;
            case EQUAL_DEPTH_TEST -> VK10.VK_COMPARE_OP_EQUAL;
            case LEQUAL_DEPTH_TEST -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
            case LESS_DEPTH_TEST -> VK10.VK_COMPARE_OP_LESS;
            case GREATER_DEPTH_TEST -> VK10.VK_COMPARE_OP_GREATER;
        };
    }

    private static int toVkLogicOp(LogicOp op) {
        return switch (op) {
            case NONE -> VK10.VK_LOGIC_OP_NO_OP;
            case OR_REVERSE -> VK10.VK_LOGIC_OP_OR_REVERSE;
        };
    }

    private static int toVkCompareOp(int op) {
        return switch (op) {
            case VulkanicAPI.GL_NEVER -> VK10.VK_COMPARE_OP_NEVER;
            case VulkanicAPI.GL_LESS -> VK10.VK_COMPARE_OP_LESS;
            case VulkanicAPI.GL_EQUAL -> VK10.VK_COMPARE_OP_EQUAL;
            case VulkanicAPI.GL_LEQUAL -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
            case VulkanicAPI.GL_GREATER -> VK10.VK_COMPARE_OP_GREATER;
            case VulkanicAPI.GL_NOTEQUAL -> VK10.VK_COMPARE_OP_NOT_EQUAL;
            case VulkanicAPI.GL_GEQUAL -> VK10.VK_COMPARE_OP_GREATER_OR_EQUAL;
            case VulkanicAPI.GL_ALWAYS -> VK10.VK_COMPARE_OP_ALWAYS;
            default -> VK10.VK_COMPARE_OP_ALWAYS;
        };
    }

    private static int toVkStencilOp(int op) {
        return switch (op) {
            case VulkanicAPI.GL_ZERO -> VK10.VK_STENCIL_OP_ZERO;
            case VulkanicAPI.GL_REPLACE -> VK10.VK_STENCIL_OP_REPLACE;
            case VulkanicAPI.GL_INCR -> VK10.VK_STENCIL_OP_INCREMENT_AND_CLAMP;
            case VulkanicAPI.GL_DECR -> VK10.VK_STENCIL_OP_DECREMENT_AND_CLAMP;
            case VulkanicAPI.GL_INVERT -> VK10.VK_STENCIL_OP_INVERT;
            case VulkanicAPI.GL_INCR_WRAP -> VK10.VK_STENCIL_OP_INCREMENT_AND_WRAP;
            case VulkanicAPI.GL_DECR_WRAP -> VK10.VK_STENCIL_OP_DECREMENT_AND_WRAP;
            case VulkanicAPI.GL_KEEP -> VK10.VK_STENCIL_OP_KEEP;
            default -> VK10.VK_STENCIL_OP_KEEP;
        };
    }

    private static int toVkBlendFactor(SourceFactor factor) {
        return switch (factor) {
            case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
            case ONE -> VK10.VK_BLEND_FACTOR_ONE;
            case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            case SRC_ALPHA_SATURATE -> VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
        };
    }

    private static int toVkBlendFactor(DestFactor factor) {
        return switch (factor) {
            case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
            case ONE -> VK10.VK_BLEND_FACTOR_ONE;
            case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
        };
    }

    @FunctionalInterface
    interface PolygonModeResolver {
        int resolve(PolygonMode mode);
    }

    @FunctionalInterface
    interface BlendStateResolver {
        Optional<PipelineDescriptor.BlendState> resolve(
            PipelineDescriptor.PortableState portableState,
            int colorAttachmentIndex
        );
    }

    record ColorBlendAttachment(
        int colorWriteMask,
        boolean blendEnabled,
        int sourceColorBlendFactor,
        int destColorBlendFactor,
        int colorBlendOp,
        int sourceAlphaBlendFactor,
        int destAlphaBlendFactor,
        int alphaBlendOp
    ) {
        private static ColorBlendAttachment from(
            int colorWriteMask,
            Optional<PipelineDescriptor.BlendState> blendState
        ) {
            Objects.requireNonNull(blendState, "blendState must not be null");
            if (blendState.isEmpty()) {
                return new ColorBlendAttachment(
                    colorWriteMask,
                    false,
                    VK10.VK_BLEND_FACTOR_ONE,
                    VK10.VK_BLEND_FACTOR_ZERO,
                    VK10.VK_BLEND_OP_ADD,
                    VK10.VK_BLEND_FACTOR_ONE,
                    VK10.VK_BLEND_FACTOR_ZERO,
                    VK10.VK_BLEND_OP_ADD
                );
            }

            PipelineDescriptor.BlendState blend = blendState.get();
            return new ColorBlendAttachment(
                colorWriteMask,
                true,
                toVkBlendFactor(blend.sourceColor()),
                toVkBlendFactor(blend.destColor()),
                VK10.VK_BLEND_OP_ADD,
                toVkBlendFactor(blend.sourceAlpha()),
                toVkBlendFactor(blend.destAlpha()),
                VK10.VK_BLEND_OP_ADD
            );
        }
    }

    record StencilState(
        boolean enabled,
        StencilFaceState front,
        StencilFaceState back
    ) {
        StencilState {
            Objects.requireNonNull(front, "front must not be null");
            Objects.requireNonNull(back, "back must not be null");
        }

        static StencilState disabled() {
            StencilFaceState defaults = StencilFaceState.fromLegacyGl(
                VulkanicAPI.GL_KEEP,
                VulkanicAPI.GL_KEEP,
                VulkanicAPI.GL_KEEP,
                VulkanicAPI.GL_ALWAYS,
                0xFF,
                0xFF,
                0
            );
            return new StencilState(false, defaults, defaults);
        }

        String cacheKey(boolean hasStencilAttachment) {
            return enabled && hasStencilAttachment
                ? "stencil:on:" + front.cacheKey() + ":" + back.cacheKey()
                : "stencil:off";
        }
    }

    record StencilFaceState(
        int failOp,
        int passOp,
        int depthFailOp,
        int compareOp,
        int compareMask,
        int writeMask,
        int reference
    ) {
        static StencilFaceState fromLegacyGl(
            int failOp,
            int depthFailOp,
            int passOp,
            int compareOp,
            int compareMask,
            int writeMask,
            int reference
        ) {
            return new StencilFaceState(
                toVkStencilOp(failOp),
                toVkStencilOp(passOp),
                toVkStencilOp(depthFailOp),
                toVkCompareOp(compareOp),
                compareMask,
                writeMask,
                reference
            );
        }

        private String cacheKey() {
            return failOp + "," + passOp + "," + depthFailOp + "," + compareOp + ","
                + compareMask + "," + writeMask + "," + reference;
        }
    }
}
