package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanFramebufferRenderTargetManagerTest {
    private static final int GL_NONE = 0;
    private static final int GL_BACK = 0x0405;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_COLOR_ATTACHMENT1 = GL_COLOR_ATTACHMENT0 + 1;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_FRAMEBUFFER_UNDEFINED = 0x8219;

    @Test
    void defaultFramebufferBindingAndCompletenessAreOwnedTogether() {
        VulkanFramebufferRenderTargetManager manager = new VulkanFramebufferRenderTargetManager();

        assertEquals(0, manager.boundReadFramebuffer());
        assertEquals(0, manager.boundDrawFramebuffer());
        assertEquals(GL_FRAMEBUFFER_COMPLETE, manager.checkFramebufferStatus(GL_FRAMEBUFFER));
        assertEquals(GL_BACK, manager.readBuffer(0));
        assertArrayEquals(new int[] {GL_BACK}, manager.drawBuffers(0));
    }

    @Test
    void separateReadAndDrawBindingsCarryVirtualFramebufferState() {
        VulkanFramebufferRenderTargetManager manager = new VulkanFramebufferRenderTargetManager();
        int readFramebuffer = manager.createFramebuffer();
        int drawFramebuffer = manager.createFramebuffer();
        manager.setNamedReadBuffer(readFramebuffer, GL_COLOR_ATTACHMENT1);
        manager.setNamedDrawBuffers(drawFramebuffer, new int[] {GL_NONE, GL_COLOR_ATTACHMENT1});

        manager.bindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        manager.bindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);

        assertEquals(readFramebuffer, manager.boundReadFramebuffer());
        assertEquals(drawFramebuffer, manager.boundDrawFramebuffer());
        assertEquals(GL_COLOR_ATTACHMENT1, manager.readBuffer(readFramebuffer));
        assertArrayEquals(new int[] {GL_NONE, GL_COLOR_ATTACHMENT1}, manager.drawBuffers(drawFramebuffer));
        assertEquals(GL_FRAMEBUFFER_COMPLETE, manager.checkFramebufferStatus(GL_READ_FRAMEBUFFER));
        assertEquals(GL_FRAMEBUFFER_COMPLETE, manager.checkFramebufferStatus(GL_DRAW_FRAMEBUFFER));
    }

    @Test
    void deletedBoundFramebufferRebindsToDefaultWithoutLeavingKnownHandle() {
        VulkanFramebufferRenderTargetManager manager = new VulkanFramebufferRenderTargetManager();
        int framebuffer = manager.createFramebuffer();

        manager.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        manager.deleteFramebuffer(framebuffer);

        assertEquals(0, manager.boundReadFramebuffer());
        assertEquals(0, manager.boundDrawFramebuffer());
        assertFalse(manager.isFramebuffer(framebuffer));
        manager.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        assertEquals(GL_FRAMEBUFFER_UNDEFINED, manager.checkFramebufferStatus(GL_FRAMEBUFFER));
    }

    @Test
    void drawAndReadRoutingResolveCopyAndBlitTextures() {
        VulkanFramebufferRenderTargetManager manager = new VulkanFramebufferRenderTargetManager();
        int framebuffer = manager.createFramebuffer();
        manager.recordAttachment(framebuffer, GL_COLOR_ATTACHMENT0, 100);
        manager.recordAttachment(framebuffer, GL_COLOR_ATTACHMENT1, 101);
        manager.recordAttachment(framebuffer, VulkanicAPI.GL_DEPTH_ATTACHMENT, 200);
        manager.setNamedReadBuffer(framebuffer, GL_COLOR_ATTACHMENT1);
        manager.setNamedDrawBuffers(framebuffer, new int[] {GL_NONE, GL_COLOR_ATTACHMENT0});
        manager.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);

        assertEquals(101, manager.boundReadColorTextureForCopy());
        assertEquals(101, manager.boundReadTextureForBlit(VulkanicAPI.GL_COLOR_BUFFER_BIT));
        assertEquals(100, manager.boundDrawTextureForBlit(VulkanicAPI.GL_COLOR_BUFFER_BIT));
        assertEquals(200, manager.boundReadTextureForBlit(VulkanicAPI.GL_DEPTH_BUFFER_BIT));
        assertEquals(100, manager.textureForBlit(framebuffer, VulkanicAPI.GL_COLOR_BUFFER_BIT, GL_NONE));
    }

    @Test
    void clearRequestSnapshotsValuesAndDrawBuffersImmutably() {
        VulkanFramebufferRenderTargetManager manager = new VulkanFramebufferRenderTargetManager();
        int framebuffer = manager.createFramebuffer();
        manager.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        manager.setDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_NONE});
        manager.setClearColor(0.1f, 0.2f, 0.3f, 0.4f);
        manager.setClearDepth(0.75);
        manager.setClearStencil(7);

        VulkanFramebufferRenderTargetManager.ClearOperationRequest request =
            manager.clearRequestForBoundDrawFramebuffer(
                VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT | VulkanicAPI.GL_STENCIL_BUFFER_BIT
            );
        manager.setDrawBuffers(new int[] {GL_COLOR_ATTACHMENT1});
        manager.setClearColor(1.0f, 1.0f, 1.0f, 1.0f);

        assertEquals(framebuffer, request.framebuffer());
        assertTrue(request.clearColor());
        assertTrue(request.clearDepth());
        assertTrue(request.clearStencil());
        assertArrayEquals(new int[] {GL_COLOR_ATTACHMENT0, GL_NONE}, request.drawBuffers());
        assertEquals(0.1f, request.clearState().r());
        assertEquals(0.75, request.clearState().depth());
        assertEquals(7, request.clearState().stencil());
    }

    @Test
    void implicitFramebufferInvalidationIsCentralizedWithBindingsAndCaches() {
        VulkanFramebufferRenderTargetManager manager = new VulkanFramebufferRenderTargetManager();
        int implicit = manager.resolveFramebufferForTextures(300, 301);
        manager.bindFramebuffer(GL_FRAMEBUFFER, implicit);

        manager.releaseImplicitFramebuffersForTexture(300);

        assertFalse(manager.isFramebuffer(implicit));
        assertEquals(0, manager.implicitFramebufferCountForTests());
        assertEquals(GL_FRAMEBUFFER_UNDEFINED, manager.checkFramebufferStatus(GL_FRAMEBUFFER));
    }

    @Test
    void clearAllResetsMutableLegacyStateAndVirtualFramebuffers() {
        VulkanFramebufferRenderTargetManager manager = new VulkanFramebufferRenderTargetManager();
        int framebuffer = manager.createFramebuffer();
        manager.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        manager.setReadBuffer(GL_COLOR_ATTACHMENT1);
        manager.setDrawBuffer(GL_NONE);
        manager.setClearColor(1.0f, 0.5f, 0.25f, 0.0f);

        manager.clearAll();

        assertEquals(0, manager.boundReadFramebuffer());
        assertEquals(0, manager.boundDrawFramebuffer());
        assertEquals(0, manager.framebufferCountForTests());
        assertEquals(GL_BACK, manager.readBuffer(0));
        assertArrayEquals(new int[] {GL_BACK}, manager.drawBuffers(0));
        assertEquals(0.0f, manager.clearState().r());
        assertEquals(1.0, manager.clearState().depth());
    }
}
