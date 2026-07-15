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
                VulkanicAPI.GL_TEXTURE_BUFFER,
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
    public void testVulkanBackendSourcePreservesLegacyMipMetadataWhenGrowingStorage() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("Map<Integer, TextureLevelInfo> preservedLevels = null;"),
            "Vulkan legacy texture uploads should preserve previously defined mip metadata before recreating storage");
        assertTrue(source.contains("texture.levels.putAll(preservedLevels);"),
            "Vulkan legacy texture uploads should restore preserved mip metadata after recreating storage");
    }

    @Test
    public void testVulkanBackendSourceSupportsLegacyIrisTextureTargetsForBindAndCreate() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("isSupportedLegacyTextureBindTarget"),
            "Vulkan backend should centralize legacy bind-target validation");
        assertTrue(source.contains("target == VulkanicAPI.GL_TEXTURE_3D"),
            "Vulkan legacy compatibility target support should include GL_TEXTURE_3D for Iris image setup");
        assertTrue(source.contains("isSupportedLegacyTextureCreateTarget"),
            "Vulkan backend should validate createTextures targets through a dedicated compatibility helper");
    }

    @Test
    public void testVulkanBackendSourceSizesLegacyMipStorageUsingConfiguredMaxLevel() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("GL_TEXTURE_MAX_LEVEL"),
            "Vulkan legacy texture uploads should consider GL_TEXTURE_MAX_LEVEL to allocate the intended mip chain up front");
        assertTrue(source.contains("maxMipLevelsForExtent("),
            "Vulkan legacy texture uploads should clamp configured mip levels to valid texture extent-derived limits");
    }

    @Test
    public void testVulkanBackendSourceRetriesLegacyTextureAllocationAfterDeviceLocalOom() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("VK_ERROR_OUT_OF_DEVICE_MEMORY"),
            "Vulkan legacy texture allocation should detect device-local OOM explicitly");
        assertTrue(source.contains("retrying with memoryTypeIndex="),
            "Vulkan legacy texture allocation should retry with a compatible fallback memory type when possible");
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

    @Test
    public void testVulkanBackendSourceMakesManagedBufferCopiesVisibleBeforeUse() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("barrierAfterBufferTransferWrite"),
            "Vulkan managed buffer copy paths should centralize transfer-write visibility barriers");
        assertTrue(source.contains("VK_ACCESS_TRANSFER_WRITE_BIT"),
            "Vulkan managed buffer copy barriers should wait on transfer writes");
        assertTrue(source.contains("VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT"),
            "Vulkan managed buffer copy barriers should make copied vertex data visible");
        assertTrue(source.contains("VK_ACCESS_INDEX_READ_BIT"),
            "Vulkan managed buffer copy barriers should make copied index data visible");
        assertTrue(source.contains("VK_ACCESS_UNIFORM_READ_BIT"),
            "Vulkan managed buffer copy barriers should make copied uniform data visible");
        assertTrue(countOccurrences(source, "barrierAfterBufferTransferWrite(commandBuffer, destinationBufferHandle") >= 2,
            "Both direct data copies and buffer-to-buffer copies should publish destination-buffer contents");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = text.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
    }
}
