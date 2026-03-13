package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.vulkan.VulkanBackend;
import net.vulkanic.backends.vulkan.VulkanCommandContext;
import net.vulkanic.backends.vulkan.VulkanTexture;
import net.vulkanic.backends.vulkan.VulkanTextureView;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Vulkan render-pass lifecycle wiring.
 */
public class VulkanRenderPassLifecycleTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    public void testBeginRenderPassRejectsNonVulkanContextBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.beginRenderPass(OpenGLCommandContext.IMMEDIATE, (VulkanicRenderPassDescriptor) null)
        );

        assertTrue(exception.getMessage().contains("beginRenderPass requires VulkanCommandContext"));
    }

    @Test
    public void testBeginRenderPassRejectsNullDescriptorWhenContextTypeIsValid() {
        VulkanBackend backend = new VulkanBackend();

        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> backend.beginRenderPass(new VulkanCommandContext(1L, "test-cmd"), (VulkanicRenderPassDescriptor) null)
        );

        assertTrue(exception.getMessage().contains("descriptor must not be null"));
    }

    @Test
    public void testBeginRenderPassRejectsColorAttachmentWithoutRenderAttachmentUsageBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        VulkanTextureView colorView = createView(
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8,
            16,
            16
        );
        VulkanicRenderPassDescriptor descriptor = VulkanicRenderPassDescriptor.color(
            () -> "color-usage",
            colorView,
            OptionalInt.empty()
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.beginRenderPass(new VulkanCommandContext(1L, "test-cmd"), descriptor)
        );

        assertTrue(exception.getMessage().contains("USAGE_RENDER_ATTACHMENT"));
    }

    @Test
    public void testBeginRenderPassRejectsDepthAttachmentWithoutDepthFormatBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        VulkanTextureView colorView = createView(
            VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8,
            16,
            16
        );
        VulkanTextureView depthViewWrongFormat = createView(
            VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8,
            16,
            16
        );
        VulkanicRenderPassDescriptor descriptor = VulkanicRenderPassDescriptor.colorAndDepth(
            () -> "depth-format",
            colorView,
            OptionalInt.empty(),
            depthViewWrongFormat,
            OptionalDouble.of(1.0)
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.beginRenderPass(new VulkanCommandContext(1L, "test-cmd"), descriptor)
        );

        assertTrue(exception.getMessage().contains("depth-capable texture format"));
    }

    @Test
    public void testBeginRenderPassRejectsMismatchedDepthDimensionsBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        VulkanTextureView colorView = createView(
            VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.RGBA8,
            16,
            16
        );
        VulkanTextureView depthViewDifferentSize = createView(
            VulkanicTexture.USAGE_RENDER_ATTACHMENT,
            VulkanicTextureFormat.DEPTH32,
            8,
            16
        );
        VulkanicRenderPassDescriptor descriptor = VulkanicRenderPassDescriptor.colorAndDepth(
            () -> "depth-size",
            colorView,
            OptionalInt.empty(),
            depthViewDifferentSize,
            OptionalDouble.of(1.0)
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.beginRenderPass(new VulkanCommandContext(1L, "test-cmd"), descriptor)
        );

        assertTrue(exception.getMessage().contains("dimensions must match"));
    }

    @Test
    public void testVulkanBackendSourceUsesNativeRenderPassLifecycle() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("vkCreateRenderPass"),
            "Vulkan render-pass lifecycle should create VkRenderPass objects");
        assertTrue(source.contains("vkCreateFramebuffer"),
            "Vulkan render-pass lifecycle should create VkFramebuffer objects");
        assertTrue(source.contains("vkCmdBeginRenderPass"),
            "Vulkan render-pass lifecycle should record vkCmdBeginRenderPass");
        assertTrue(source.contains("vkCmdEndRenderPass"),
            "Vulkan render-pass lifecycle should record vkCmdEndRenderPass");
        assertFalse(source.contains("Vulkan-native render pass lifecycle is not implemented yet."),
            "Vulkan render-pass lifecycle should no longer be marked unsupported");
        assertTrue(source.contains("ResolvedRenderTargets"),
            "Vulkan render target abstraction should resolve and validate attachments before native begin");
        assertTrue(source.contains("Color attachment texture must include USAGE_RENDER_ATTACHMENT"),
            "Vulkan render target abstraction should validate color attachment usage flags");
        assertTrue(source.contains("Depth attachment texture must include USAGE_RENDER_ATTACHMENT"),
            "Vulkan render target abstraction should validate depth attachment usage flags");
    }

    private static VulkanTextureView createView(int usage, VulkanicTextureFormat format, int width, int height) {
        VulkanTexture texture = new VulkanTexture(
            1L,
            2L,
            3L,
            usage,
            format,
            width,
            height,
            1,
            1,
            "test-texture",
            () -> {}
        );
        return new VulkanTextureView(texture, 4L, 0, 1, () -> {});
    }
}
