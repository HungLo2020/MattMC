package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.vulkan.VulkanBackend;
import net.vulkanic.backends.vulkan.VulkanCommandContext;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Vulkan legacy texture upload lifecycle wiring.
 */
public class VulkanTextureUploadLifecycleTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    public void testUploadTexture2DRejectsNonVulkanContextBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.uploadTexture2D(
                OpenGLCommandContext.IMMEDIATE,
                VulkanicAPI.GL_TEXTURE_2D,
                0,
                VulkanicAPI.GL_RGBA8,
                4,
                4,
                0,
                VulkanicAPI.GL_RGBA,
                VulkanicAPI.GL_UNSIGNED_BYTE,
                ByteBuffer.allocateDirect(4 * 4 * 4)
            )
        );

        assertTrue(exception.getMessage().contains("uploadTexture2D requires VulkanCommandContext"));
    }

    @Test
    public void testUploadTexture2DRejectsUnsupportedTargetBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.uploadTexture2D(
                new VulkanCommandContext(1L, "upload-cmd"),
                VulkanicAPI.GL_TEXTURE_3D,
                0,
                VulkanicAPI.GL_RGBA8,
                4,
                4,
                0,
                VulkanicAPI.GL_RGBA,
                VulkanicAPI.GL_UNSIGNED_BYTE,
                ByteBuffer.allocateDirect(4 * 4 * 4)
            )
        );

        assertTrue(exception.getMessage().contains("supports only GL_TEXTURE_2D/GL_PROXY_TEXTURE_2D"));
    }

    @Test
    public void testUploadTexture2DRejectsNonZeroBorderBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.uploadTexture2D(
                new VulkanCommandContext(1L, "upload-cmd"),
                VulkanicAPI.GL_TEXTURE_2D,
                0,
                VulkanicAPI.GL_RGBA8,
                4,
                4,
                1,
                VulkanicAPI.GL_RGBA,
                VulkanicAPI.GL_UNSIGNED_BYTE,
                ByteBuffer.allocateDirect(4 * 4 * 4)
            )
        );

        assertTrue(exception.getMessage().contains("border == 0"));
    }

    @Test
    public void testUploadTexture2DSubImageRejectsNullPointerBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.uploadTexture2DSubImage(
                new VulkanCommandContext(1L, "upload-cmd"),
                VulkanicAPI.GL_TEXTURE_2D,
                0,
                0,
                0,
                4,
                4,
                VulkanicAPI.GL_RGBA,
                VulkanicAPI.GL_UNSIGNED_BYTE,
                0L
            )
        );

        assertTrue(exception.getMessage().contains("pixels pointer must not be null"));
    }

    @Test
    public void testSetPixelStoreRejectsNonVulkanContextBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.setPixelStore(OpenGLCommandContext.IMMEDIATE, VulkanicAPI.GL_UNPACK_ALIGNMENT, 1)
        );

        assertTrue(exception.getMessage().contains("setPixelStore requires VulkanCommandContext"));
    }

    @Test
    public void testVulkanBackendSourceUsesNativeTextureUploadPath() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("public void uploadTexture2D("),
            "Vulkan backend should expose uploadTexture2D entrypoint");
        assertTrue(source.contains("public void uploadTexture2DSubImage("),
            "Vulkan backend should expose uploadTexture2DSubImage entrypoints");
        assertTrue(source.contains("vkCmdCopyBufferToImage"),
            "Vulkan texture upload path should record vkCmdCopyBufferToImage");
        assertTrue(source.contains("uploadLegacyTexture2D("),
            "Vulkan texture upload path should route through native legacy texture upload helper");
        assertTrue(source.contains("setPixelStore("),
            "Vulkan texture upload path should track GL unpack state for uploads");
    }
}
