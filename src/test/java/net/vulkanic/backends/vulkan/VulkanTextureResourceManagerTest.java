package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanTextureResourceManagerTest {
    @Test
    void createsLooksUpAndRemovesLegacyTextures() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();

        int texture = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);

        LegacyTextureObject object = manager.getLegacyTexture(texture);
        assertEquals(texture, object.id);
        assertEquals(VulkanicAPI.GL_TEXTURE_2D, object.target);
        assertTrue(manager.hasLegacyTexture(texture));

        assertSame(object, manager.removeLegacyTexture(texture));
        assertFalse(manager.hasLegacyTexture(texture));
        assertEquals(0, manager.legacyTextureCountForTests());
    }

    @Test
    void samplerOwnershipTracksStateAndClearsUnitBindingsOnDelete() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();

        int sampler = manager.createSampler();
        manager.bindSampler(3, sampler);
        manager.virtualSamplerStates.get(sampler).setParameteri(VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_CLAMP_TO_EDGE);

        assertEquals(sampler, manager.boundSamplerPerUnit.get(3));
        assertEquals(VulkanicAPI.GL_CLAMP_TO_EDGE, manager.virtualSamplerStates.get(sampler).wrapS);

        manager.deleteSampler(sampler);

        assertFalse(manager.virtualSamplers.contains(sampler));
        assertFalse(manager.virtualSamplerStates.containsKey(sampler));
        assertFalse(manager.boundSamplerPerUnit.containsKey(3));
    }

    @Test
    void clearingLegacyStorageInvalidatesDefaultViewAndStorageMetadata() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        LegacyTextureObject texture = manager.getLegacyTexture(manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D));
        texture.imageHandle = 10L;
        texture.memoryHandle = 20L;
        texture.defaultViewHandle = 30L;
        texture.imageUsageFlags = VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
        texture.feedbackLoopCapable = true;
        texture.width = 64;
        texture.height = 32;
        texture.depth = 2;
        texture.mipLevels = 4;
        texture.levels.put(0, new TextureLevelInfo(64, 32, VulkanicAPI.GL_RGBA8));

        manager.clearLegacyTextureStorage(texture);

        assertEquals(VK10.VK_NULL_HANDLE, texture.imageHandle);
        assertEquals(VK10.VK_NULL_HANDLE, texture.memoryHandle);
        assertEquals(VK10.VK_NULL_HANDLE, texture.defaultViewHandle);
        assertEquals(0, texture.imageUsageFlags);
        assertFalse(texture.feedbackLoopCapable);
        assertEquals(0, texture.width);
        assertEquals(0, texture.height);
        assertEquals(1, texture.depth);
        assertEquals(1, texture.mipLevels);
        assertTrue(texture.levels.isEmpty());
    }

    @Test
    void textureReplacementKeepsIdentityButReplacesStorageMetadata() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        int textureId = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);
        LegacyTextureObject original = manager.getLegacyTexture(textureId);
        original.defaultViewHandle = 100L;

        manager.clearLegacyTextureStorage(original);
        LegacyTextureObject replacement = manager.getOrCreateLegacyTexture(textureId, VulkanicAPI.GL_TEXTURE_CUBE_MAP);
        replacement.defaultViewHandle = 200L;

        assertSame(original, replacement);
        assertEquals(200L, manager.getLegacyTexture(textureId).defaultViewHandle);
    }

    @Test
    void virtualFramebufferInvalidationHookReceivesDeletedTexture() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        List<Integer> invalidated = new ArrayList<>();

        manager.invalidateVirtualFramebuffersForTexture(77, invalidated::add);

        assertEquals(List.of(77), invalidated);
    }

    @Test
    void managedImageAndViewCachesRemainConsistent() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();

        manager.registerManagedTexture(1L, 2L, 3L);
        manager.trackManagedImageView(4L);

        assertEquals(2L, manager.managedImageAllocations.get(1L));
        assertEquals(3L, manager.managedImageDefaultViews.get(1L));
        assertTrue(manager.managedExtraImageViews.contains(4L));

        manager.unregisterManagedTexture(1L);
        assertNull(manager.managedImageAllocations.get(1L));
        assertNull(manager.managedImageDefaultViews.get(1L));
        assertTrue(manager.untrackManagedImageView(4L));
        assertFalse(manager.untrackManagedImageView(4L));
    }

    @Test
    void shutdownCleanupClearsAllOwnedCaches() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        int texture = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);
        int sampler = manager.createSampler();
        manager.bindSampler(1, sampler);
        manager.legacyTexture2DBindingsByUnit.put(1, texture);
        manager.legacyImageBindingsByUnit.put(0, new LegacyImageBinding(0, texture, 0, false, 0, 0, 0));
        manager.legacyTexelBufferBindingsByTextureId.put(texture, new LegacyTexelBufferBinding(0, 2, 3L));
        manager.proxyTexture2DLevels.put(0, new TextureLevelInfo(1, 1, VulkanicAPI.GL_RGBA8));
        manager.registerManagedTexture(10L, 11L, 12L);
        manager.trackManagedImageView(13L);
        manager.setLegacyFallbackSamplerTextureId(texture);

        manager.clearAll();

        assertFalse(manager.hasLegacyTextures());
        assertTrue(manager.legacyTexture2DBindingsByUnit.isEmpty());
        assertTrue(manager.legacyImageBindingsByUnit.isEmpty());
        assertTrue(manager.legacyTexelBufferBindingsByTextureId.isEmpty());
        assertTrue(manager.proxyTexture2DLevels.isEmpty());
        assertTrue(manager.managedImageAllocations.isEmpty());
        assertTrue(manager.managedImageDefaultViews.isEmpty());
        assertTrue(manager.managedExtraImageViews.isEmpty());
        assertTrue(manager.virtualSamplers.isEmpty());
        assertTrue(manager.virtualSamplerStates.isEmpty());
        assertTrue(manager.boundSamplerPerUnit.isEmpty());
        assertEquals(0, manager.legacyFallbackSamplerTextureId());
    }

    @Test
    void allocatedTextureIdsRemainUniqueAcrossManualRegistrations() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();

        int first = manager.allocateLegacyTextureId();
        LegacyTextureObject object = new LegacyTextureObject(first, VulkanicAPI.GL_TEXTURE_2D);
        assertNull(manager.putLegacyTextureIfAbsent(first, object));

        int second = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);

        assertNotEquals(first, second);
        assertSame(object, manager.getLegacyTexture(first));
        assertEquals(second, manager.getLegacyTexture(second).id);
    }
}
