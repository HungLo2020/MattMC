package net.vulkanic.backends.vulkan;

import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.vulkanic.PipelineDescriptor;
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
    int cullMode,
    int frontFace,
    boolean depthBiasEnabled,
    float depthBiasConstantFactor,
    float depthBiasSlopeFactor,
    boolean depthTestEnabled,
    boolean depthWriteEnabled,
    int depthCompareOp,
    int colorWriteMask,
    boolean logicOpEnabled,
    int logicOp,
    List<ColorBlendAttachment> colorBlendAttachments
) {
    VulkanPipelineState {
        colorBlendAttachments = List.copyOf(
            Objects.requireNonNull(colorBlendAttachments, "colorBlendAttachments must not be null"));
    }

    static VulkanPipelineState from(
        PipelineDescriptor.PortableState portableState,
        int colorAttachmentCount,
        PolygonModeResolver polygonModeResolver,
        BlendStateResolver blendStateResolver
    ) {
        Objects.requireNonNull(portableState, "portableState must not be null");
        Objects.requireNonNull(polygonModeResolver, "polygonModeResolver must not be null");
        Objects.requireNonNull(blendStateResolver, "blendStateResolver must not be null");
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

        return new VulkanPipelineState(
            polygonModeResolver.resolve(portableState.polygonMode()),
            // The current Vulkan backend still inherits Minecraft's OpenGL-era
            // clip/winding conventions.  Static Vulkan face culling therefore
            // is not a safe translation of RenderPipeline#isCull yet; enabling
            // it globally culls valid fullscreen/world passes.  Keep the field
            // in the native state snapshot, but preserve the historical Vulkan
            // behavior until front-face/winding parity is solved end-to-end.
            VK10.VK_CULL_MODE_NONE,
            VK10.VK_FRONT_FACE_CLOCKWISE,
            portableState.depthBiasConstant() != 0.0f || portableState.depthBiasScaleFactor() != 0.0f,
            portableState.depthBiasConstant(),
            portableState.depthBiasScaleFactor(),
            portableState.depthTestFunction() != DepthTestFunction.NO_DEPTH_TEST,
            portableState.writeDepth(),
            toVkDepthCompareOp(portableState.depthTestFunction()),
            colorWriteMask,
            portableState.colorLogic() != LogicOp.NONE,
            toVkLogicOp(portableState.colorLogic()),
            attachments
        );
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
}
