package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VulkanTextureViewMetadataTest {

    @Test
    public void legacyTextureHandleDefaultsToZero() {
        VulkanTexture texture = texture();

        VulkanTextureView view = new VulkanTextureView(texture, 4L, 0, 1, () -> {
        });

        assertEquals(0, view.getLegacyTextureHandle());
    }

    @Test
    public void legacyTextureHandleIsRetainedForManagedLegacyViews() {
        VulkanTexture texture = texture();

        VulkanTextureView view = new VulkanTextureView(texture, 4L, 0, 1, 42, () -> {
        });

        assertEquals(42, view.getLegacyTextureHandle());
    }

    @Test
    public void legacyTextureHandleRejectsNegativeValues() {
        VulkanTexture texture = texture();

        assertThrows(
            IllegalArgumentException.class,
            () -> new VulkanTextureView(texture, 4L, 0, 1, -1, () -> {
            })
        );
    }

    private static VulkanTexture texture() {
        return new VulkanTexture(
            1L,
            2L,
            3L,
            VulkanicTexture.USAGE_TEXTURE_BINDING,
            VulkanicTextureFormat.RGBA8,
            16,
            16,
            1,
            1,
            "metadata-test-texture",
            () -> {
            }
        );
    }
}
