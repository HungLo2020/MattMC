package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanVirtualFramebufferManagerTest {
    @Test
    void attachmentIndexZeroRemainsDistinctFromNoAttachment() {
        VulkanVirtualFramebufferManager manager = new VulkanVirtualFramebufferManager();
        int framebuffer = manager.createFramebuffer();

        manager.recordAttachment(framebuffer, VulkanicAPI.colorAttachment(0), 41);

        assertEquals(41, manager.getAttachment(framebuffer, VulkanicAPI.colorAttachment(0)));
        assertEquals(0, manager.getAttachment(framebuffer, VulkanicAPI.colorAttachment(1)));
        assertEquals(framebuffer, manager.resolveFramebufferForTextures(41, 0));
    }

    @Test
    void resolvesColorOnlyDepthOnlyAndColorDepthState() {
        VulkanVirtualFramebufferManager manager = new VulkanVirtualFramebufferManager();
        int colorOnly = manager.createFramebuffer();
        int colorDepth = manager.createFramebuffer();

        manager.recordAttachment(colorOnly, VulkanicAPI.colorAttachment(0), 11);
        manager.recordAttachment(colorDepth, VulkanicAPI.colorAttachment(0), 22);
        manager.recordAttachment(colorDepth, VulkanicAPI.GL_DEPTH_ATTACHMENT, 33);

        VulkanVirtualFramebufferManager.FramebufferSnapshot colorOnlySnapshot = manager.requireSnapshot(colorOnly);
        VulkanVirtualFramebufferManager.FramebufferSnapshot colorDepthSnapshot = manager.requireSnapshot(colorDepth);

        assertEquals(11, colorOnlySnapshot.attachment(VulkanicAPI.colorAttachment(0)));
        assertEquals(0, colorOnlySnapshot.depthAttachmentTexture());
        assertEquals(22, colorDepthSnapshot.attachment(VulkanicAPI.colorAttachment(0)));
        assertEquals(33, colorDepthSnapshot.depthAttachmentTexture());
        assertEquals(colorOnly, manager.resolveFramebufferForTextures(11, 0));
        assertEquals(colorDepth, manager.resolveFramebufferForTextures(22, 33));

        int depthOnly = manager.createFramebuffer();
        manager.recordAttachment(depthOnly, VulkanicAPI.GL_DEPTH_ATTACHMENT, 44);
        assertEquals(44, manager.requireSnapshot(depthOnly).depthAttachmentTexture());
        assertNotEquals(depthOnly, manager.resolveFramebufferForTextures(22, 33));
    }

    @Test
    void implicitFramebufferCacheReusesAndInvalidatesByTexture() {
        VulkanVirtualFramebufferManager manager = new VulkanVirtualFramebufferManager();

        int first = manager.resolveFramebufferForTextures(100, 200);
        int second = manager.resolveFramebufferForTextures(100, 200);

        assertEquals(first, second);
        assertEquals(1, manager.implicitFramebufferCountForTests());

        manager.releaseImplicitFramebuffersForTexture(100);

        assertFalse(manager.isFramebuffer(first));
        assertEquals(0, manager.implicitFramebufferCountForTests());

        int replacement = manager.resolveFramebufferForTextures(100, 200);
        assertNotEquals(first, replacement);
        assertTrue(manager.isFramebuffer(replacement));
    }

    @Test
    void attachmentReplacementPreventsStaleImplicitFramebufferReuse() {
        VulkanVirtualFramebufferManager manager = new VulkanVirtualFramebufferManager();
        int implicit = manager.resolveFramebufferForTextures(100, 0);

        manager.recordAttachment(implicit, VulkanicAPI.colorAttachment(0), 101);

        assertEquals(0, manager.implicitFramebufferCountForTests());
        assertNotEquals(implicit, manager.resolveFramebufferForTextures(100, 0));
        assertEquals(implicit, manager.resolveFramebufferForTextures(101, 0));
    }

    @Test
    void resizeLikeTextureReplacementCleansCachedImplicitFramebuffers() {
        VulkanVirtualFramebufferManager manager = new VulkanVirtualFramebufferManager();
        int oldFramebuffer = manager.resolveFramebufferForTextures(77, 88);

        manager.releaseImplicitFramebuffersForTexture(77);

        assertFalse(manager.isFramebuffer(oldFramebuffer));
        assertEquals(0, manager.framebufferCountForTests());
        assertEquals(0, manager.implicitFramebufferCountForTests());
    }

    @Test
    void deletePreventsStaleFramebufferReuse() {
        VulkanVirtualFramebufferManager manager = new VulkanVirtualFramebufferManager();
        int framebuffer = manager.resolveFramebufferForTextures(5, 6);

        manager.deleteFramebuffer(framebuffer);

        assertFalse(manager.isFramebuffer(framebuffer));
        assertEquals(0, manager.implicitFramebufferCountForTests());
        assertNotEquals(framebuffer, manager.resolveFramebufferForTextures(5, 6));
    }

    @Test
    void shutdownOrDeviceLossCleanupClearsAllVirtualState() {
        VulkanVirtualFramebufferManager manager = new VulkanVirtualFramebufferManager();
        int explicit = manager.createFramebuffer();
        int implicit = manager.resolveFramebufferForTextures(9, 10);
        manager.recordAttachment(explicit, VulkanicAPI.colorAttachment(0), 11);

        manager.clear();

        assertFalse(manager.isFramebuffer(explicit));
        assertFalse(manager.isFramebuffer(implicit));
        assertEquals(0, manager.framebufferCountForTests());
        assertEquals(0, manager.implicitFramebufferCountForTests());
        assertNull(manager.textureForBlit(explicit, VulkanicAPI.GL_COLOR_BUFFER_BIT, VulkanicAPI.colorAttachment(0)));
    }
}
