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
        manager.setSamplerParameteri(sampler, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_CLAMP_TO_EDGE);

        assertEquals(sampler, manager.boundSampler(3));
        assertEquals(VulkanicAPI.GL_CLAMP_TO_EDGE, manager.samplerStateSnapshot(sampler).wrapS());

        manager.deleteSampler(sampler);

        assertFalse(manager.hasSampler(sampler));
        assertNull(manager.samplerStateSnapshot(sampler));
        assertEquals(0, manager.boundSampler(3));
    }

    @Test
    void textureBindingSnapshotsAreImmutable() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        int texture = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);
        int sampler = manager.createSampler();
        manager.bindLegacyTexture2D(3, texture);
        manager.bindSampler(3, sampler);
        manager.setSamplerParameteri(sampler, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_CLAMP_TO_EDGE);

        TextureBindingSnapshot snapshot = manager.textureBindingSnapshot(3);

        manager.unbindLegacyTexture2D(3);
        manager.bindSampler(3, 0);
        manager.setSamplerParameteri(sampler, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_REPEAT);

        assertEquals(3, snapshot.unit());
        assertEquals(texture, snapshot.texture2D());
        assertEquals(sampler, snapshot.sampler());
        assertEquals(VulkanicAPI.GL_CLAMP_TO_EDGE, snapshot.samplerState().wrapS());
        assertEquals(0, manager.textureBindingSnapshot(3).texture2D());
        assertEquals(0, manager.textureBindingSnapshot(3).sampler());
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

        ManagedImageSnapshot snapshot = manager.managedImageSnapshot(1L);
        assertEquals(1L, snapshot.imageHandle());
        assertEquals(2L, snapshot.memoryHandle());
        assertEquals(3L, snapshot.defaultViewHandle());
        assertTrue(manager.isManagedExtraImageViewTracked(4L));

        manager.unregisterManagedTexture(1L);
        assertNull(manager.managedImageSnapshot(1L));
        assertTrue(manager.untrackManagedImageView(4L));
        assertFalse(manager.untrackManagedImageView(4L));
    }

    @Test
    void fallbackTextureTracksRegisteredObjectAndClearsOnDeletion() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        int texture2d = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);
        int texture3d = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_3D);

        manager.setLegacyFallbackSamplerTextureId(texture2d);
        manager.setLegacyFallbackSamplerTextureId(VulkanicAPI.GL_TEXTURE_3D, texture3d);

        assertEquals(texture2d, manager.legacyFallbackSamplerTextureId());
        assertEquals(texture2d, manager.legacyFallbackSamplerTextureId(VulkanicAPI.GL_TEXTURE_2D));
        assertEquals(texture3d, manager.legacyFallbackSamplerTextureId(VulkanicAPI.GL_TEXTURE_3D));
        assertSame(manager.getLegacyTexture(texture2d), manager.legacyFallbackSamplerTexture());
        assertSame(manager.getLegacyTexture(texture3d), manager.legacyFallbackSamplerTexture(VulkanicAPI.GL_TEXTURE_3D));

        manager.clearLegacyFallbackSamplerTextureIdIfMatches(texture2d);

        assertEquals(0, manager.legacyFallbackSamplerTextureId());
        assertNull(manager.legacyFallbackSamplerTexture());
        assertEquals(texture3d, manager.legacyFallbackSamplerTextureId(VulkanicAPI.GL_TEXTURE_3D));

        manager.clearLegacyFallbackSamplerTextureIdIfMatches(texture3d);

        assertEquals(0, manager.legacyFallbackSamplerTextureId(VulkanicAPI.GL_TEXTURE_3D));
        assertNull(manager.legacyFallbackSamplerTexture(VulkanicAPI.GL_TEXTURE_3D));
    }

    @Test
    void proxyAndTexelBufferBindingsUseExplicitOperations() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        int texture = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);

        manager.bindLegacyTexture2D(2, texture);
        manager.setProxyTexture2DLevel(0, new TextureLevelInfo(16, 8, VulkanicAPI.GL_RGBA8));
        manager.setLegacyTexelBufferBinding(texture, new LegacyTexelBufferBinding(VulkanicAPI.GL_RGBA8, 9, 10L));

        assertEquals(texture, manager.boundLegacyTexture2D(2));
        assertEquals(16, manager.proxyTexture2DLevel(0).width);
        assertEquals(8, manager.proxyTexture2DLevel(0).height);
        assertEquals(9, manager.legacyTexelBufferBinding(texture).legacyBufferId);
        assertEquals(10L, manager.legacyTexelBufferBinding(texture).vkBufferViewHandle);

        assertEquals(10L, manager.removeLegacyTexelBufferBinding(texture).vkBufferViewHandle);
        manager.removeProxyTexture2DLevel(0);
        manager.unbindLegacyTexture2D(2);

        assertNull(manager.legacyTexelBufferBinding(texture));
        assertNull(manager.proxyTexture2DLevel(0));
        assertEquals(0, manager.boundLegacyTexture2D(2));
    }

    @Test
    void deletionAndInvalidationRemoveBindingsWithoutExposingMaps() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        int texture = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);
        manager.bindLegacyTexture2D(0, texture);
        manager.bindLegacyTexture2D(1, texture);
        manager.bindLegacyImage(0, new LegacyImageBinding(0, texture, 0, false, 0, 0, 0));
        manager.setLegacyTexelBufferBinding(texture, new LegacyTexelBufferBinding(0, 2, 3L));

        manager.unbindLegacyTextureFromAllUnits(texture);
        manager.unbindLegacyImage(0);
        LegacyTexelBufferBinding removed = manager.removeLegacyTexelBufferBinding(texture);

        assertEquals(0, manager.boundLegacyTexture2D(0));
        assertEquals(0, manager.boundLegacyTexture2D(1));
        assertNull(manager.legacyImageBinding(0));
        assertEquals(3L, removed.vkBufferViewHandle);
    }

    @Test
    void shutdownCleanupClearsAllOwnedCaches() {
        VulkanTextureResourceManager manager = new VulkanTextureResourceManager();
        int texture = manager.createLegacyTexture(VulkanicAPI.GL_TEXTURE_2D);
        int sampler = manager.createSampler();
        manager.bindSampler(1, sampler);
        manager.bindLegacyTexture2D(1, texture);
        manager.bindLegacyImage(0, new LegacyImageBinding(0, texture, 0, false, 0, 0, 0));
        manager.setLegacyTexelBufferBinding(texture, new LegacyTexelBufferBinding(0, 2, 3L));
        manager.setProxyTexture2DLevel(0, new TextureLevelInfo(1, 1, VulkanicAPI.GL_RGBA8));
        manager.registerManagedTexture(10L, 11L, 12L);
        manager.trackManagedImageView(13L);
        manager.setLegacyFallbackSamplerTextureId(texture);

        manager.clearAll();

        assertFalse(manager.hasLegacyTextures());
        assertEquals(0, manager.legacyTexture2DBindingCountForTests());
        assertEquals(0, manager.legacyImageBindingCountForTests());
        assertEquals(0, manager.legacyTexelBufferBindingCountForTests());
        assertEquals(0, manager.proxyTexture2DLevelCountForTests());
        assertEquals(0, manager.managedImageCountForTests());
        assertEquals(0, manager.managedExtraImageViewCountForTests());
        assertEquals(0, manager.virtualSamplerCountForTests());
        assertEquals(0, manager.boundSamplerCountForTests());
        assertEquals(0, manager.legacyFallbackSamplerTextureId());
        assertEquals(0, manager.legacyFallbackSamplerTextureId(VulkanicAPI.GL_TEXTURE_3D));
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
