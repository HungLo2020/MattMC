package net.vulkanic.diagnostics;

import net.vulkanic.VulkanicRenderTargetCompatibility;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureView;
import net.vulkanic.diagnostics.RenderTargetContentDiagnostics.DiagnosticTextureContentHash;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Stateless formatting helpers for Vulkan-specific diagnostics.
 *
 * <p>This class intentionally does not own Vulkan resources or execute Vulkan
 * commands. Numeric Vulkan constants are mirrored here only for diagnostic
 * labels, keeping backend implementation references out of diagnostics.</p>
 */
public final class VulkanBackendDiagnosticFormatting {
    private static final int VK_IMAGE_LAYOUT_UNDEFINED = 0;
    private static final int VK_IMAGE_LAYOUT_GENERAL = 1;
    private static final int VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL = 2;
    private static final int VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL = 3;
    private static final int VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL = 4;
    private static final int VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL = 5;
    private static final int VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL = 6;
    private static final int VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL = 7;
    private static final int VK_IMAGE_LAYOUT_PRESENT_SRC_KHR = 1000001002;
    private static final int VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT = 1000339000;

    private static final int VK_ACCESS_SHADER_READ_BIT = 0x00000020;
    private static final int VK_ACCESS_COLOR_ATTACHMENT_READ_BIT = 0x00000080;
    private static final int VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT = 0x00000100;
    private static final int VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT = 0x00000200;
    private static final int VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT = 0x00000400;
    private static final int VK_ACCESS_TRANSFER_READ_BIT = 0x00000800;
    private static final int VK_ACCESS_TRANSFER_WRITE_BIT = 0x00001000;
    private static final int VK_ACCESS_MEMORY_READ_BIT = 0x00008000;
    private static final int VK_ACCESS_MEMORY_WRITE_BIT = 0x00010000;

    private static final int VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT = 0x00000001;
    private static final int VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT = 0x00000080;
    private static final int VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT = 0x00000100;
    private static final int VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT = 0x00000200;
    private static final int VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT = 0x00000400;
    private static final int VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT = 0x00000800;
    private static final int VK_PIPELINE_STAGE_TRANSFER_BIT = 0x00001000;
    private static final int VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT = 0x00002000;
    private static final int VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT = 0x00008000;
    private static final int VK_PIPELINE_STAGE_ALL_COMMANDS_BIT = 0x00010000;

    private VulkanBackendDiagnosticFormatting() {
    }

    public static DiagnosticTextureContentHash unavailableTextureReadback(
        String logicalResource,
        @Nullable VulkanicTexture texture,
        @Nullable VulkanicTextureView textureView,
        String reason
    ) {
        return DiagnosticTextureContentHash.unavailable(logicalResource, texture, textureView, reason);
    }

    public static String unavailableTextureLifecycle(String logicalResource, String reason) {
        return "backend=vulkan,logicalResource=" + VulkanicDiagnostics.sanitizeLabel(logicalResource)
            + ",lifecycle=unavailable:" + VulkanicDiagnostics.sanitizeLabel(reason);
    }

    public static String textureLifecycle(
        String logicalResource,
        int legacyTextureId,
        long imageHandle,
        long imageViewHandle,
        long defaultViewHandle,
        int vkFormat,
        int trackedLayout,
        int trackedStageMask,
        int trackedAccessMask,
        int producerAttachmentLayout,
        int consumerShaderReadLayout
    ) {
        return "backend=vulkan"
            + ",logicalResource=" + VulkanicDiagnostics.sanitizeLabel(logicalResource)
            + ",legacyTextureId=" + legacyTextureId
            + ",image=0x" + Long.toHexString(imageHandle)
            + ",view=0x" + Long.toHexString(imageViewHandle)
            + ",defaultView=0x" + Long.toHexString(defaultViewHandle)
            + ",vkFormat=0x" + Integer.toHexString(vkFormat)
            + ",trackedLayout=" + imageLayoutName(trackedLayout) + "(0x" + Integer.toHexString(trackedLayout) + ")"
            + ",stageMask=0x" + Integer.toHexString(trackedStageMask)
            + ",accessMask=0x" + Integer.toHexString(trackedAccessMask)
            + ",producerAttachmentLayout=" + imageLayoutName(producerAttachmentLayout)
            + ",producerStage=COLOR_ATTACHMENT_OUTPUT"
            + ",producerAccess=COLOR_ATTACHMENT_WRITE"
            + ",consumerShaderReadLayout=" + imageLayoutName(consumerShaderReadLayout)
            + ",consumerStage=FRAGMENT_SHADER"
            + ",consumerAccess=SHADER_READ"
            + ",queueOwnership=ignored";
    }

    public static String imageLayoutName(int layout) {
        return switch (layout) {
            case VK_IMAGE_LAYOUT_UNDEFINED -> "UNDEFINED";
            case VK_IMAGE_LAYOUT_GENERAL -> "GENERAL";
            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> "COLOR_ATTACHMENT_OPTIMAL";
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> "DEPTH_STENCIL_ATTACHMENT_OPTIMAL";
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL -> "DEPTH_STENCIL_READ_ONLY_OPTIMAL";
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> "SHADER_READ_ONLY_OPTIMAL";
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> "TRANSFER_SRC_OPTIMAL";
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> "TRANSFER_DST_OPTIMAL";
            case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> "PRESENT_SRC_KHR";
            case VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT -> "ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT";
            default -> "layout-" + layout;
        };
    }

    public static String stageMaskName(int mask) {
        return bitMaskName(mask, new int[] {
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
            VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,
            VK_PIPELINE_STAGE_ALL_COMMANDS_BIT
        }, new String[] {
            "TOP_OF_PIPE",
            "FRAGMENT_SHADER",
            "EARLY_FRAGMENT_TESTS",
            "LATE_FRAGMENT_TESTS",
            "COLOR_ATTACHMENT_OUTPUT",
            "COMPUTE_SHADER",
            "TRANSFER",
            "BOTTOM_OF_PIPE",
            "ALL_GRAPHICS",
            "ALL_COMMANDS"
        });
    }

    public static String accessMaskName(int mask) {
        return bitMaskName(mask, new int[] {
            VK_ACCESS_SHADER_READ_BIT,
            VK_ACCESS_COLOR_ATTACHMENT_READ_BIT,
            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
            VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT,
            VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
            VK_ACCESS_TRANSFER_READ_BIT,
            VK_ACCESS_TRANSFER_WRITE_BIT,
            VK_ACCESS_MEMORY_READ_BIT,
            VK_ACCESS_MEMORY_WRITE_BIT
        }, new String[] {
            "SHADER_READ",
            "COLOR_ATTACHMENT_READ",
            "COLOR_ATTACHMENT_WRITE",
            "DEPTH_STENCIL_ATTACHMENT_READ",
            "DEPTH_STENCIL_ATTACHMENT_WRITE",
            "TRANSFER_READ",
            "TRANSFER_WRITE",
            "MEMORY_READ",
            "MEMORY_WRITE"
        });
    }

    private static String bitMaskName(int mask, int[] bits, String[] names) {
        if (mask == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int known = 0;
        for (int index = 0; index < bits.length; index++) {
            if ((mask & bits[index]) != 0) {
                if (!builder.isEmpty()) {
                    builder.append('|');
                }
                builder.append(names[index]);
                known |= bits[index];
            }
        }
        int unknown = mask & ~known;
        if (unknown != 0) {
            if (!builder.isEmpty()) {
                builder.append('|');
            }
            builder.append("0x").append(Integer.toHexString(unknown));
        }
        return builder.toString();
    }

    public static void emitRenderTargetParity(
        Logger logger,
        int logIndex,
        boolean equivalent,
        int framebuffer,
        String label,
        String framebufferSignature,
        String descriptorSignature,
        @Nullable RuntimeException exception
    ) {
        if (equivalent) {
            logger.info(
                "Vulkan render-target parity#{} label={} framebuffer={} equivalent=true signature={}",
                logIndex,
                label,
                framebuffer,
                framebufferSignature
            );
            return;
        }

        if (exception != null) {
            logger.warn(
                "Vulkan render-target parity#{} label={} framebuffer={} equivalent=false reason={} framebufferSignature={} descriptorSignature={}",
                logIndex,
                label,
                framebuffer,
                exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                framebufferSignature,
                descriptorSignature
            );
            return;
        }

        logger.warn(
            "Vulkan render-target parity#{} label={} framebuffer={} equivalent=false framebufferSignature={} descriptorSignature={}",
            logIndex,
            label,
            framebuffer,
            framebufferSignature,
            descriptorSignature
        );
    }

    public static void emitRenderTargetCompatibility(
        Logger logger,
        int logIndex,
        boolean compatible,
        VulkanicRenderTargetCompatibility compatibility,
        int framebuffer,
        String label,
        String framebufferSignature,
        String descriptorSignature,
        @Nullable RuntimeException exception
    ) {
        if (exception != null) {
            logger.warn(
                "Vulkan render-target compatibility#{} label={} framebuffer={} compatible=false relation={} reason={} framebufferSignature={} descriptorSignature={}",
                logIndex,
                label,
                framebuffer,
                compatibility,
                exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                framebufferSignature,
                descriptorSignature
            );
            return;
        }

        logger.info(
            "Vulkan render-target compatibility#{} label={} framebuffer={} compatible={} relation={} framebufferSignature={} descriptorSignature={}",
            logIndex,
            label,
            framebuffer,
            compatible,
            compatibility,
            framebufferSignature,
            descriptorSignature
        );
    }
}
