package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanNativeRenderTargetLifecycleManagerTest {
    @Test
    void framebufferKeysDistinguishAttachmentIdentityDimensionsAndTextureIds() {
        VulkanRenderPassKey renderPassKey = renderPassKey();
        VulkanNativeRenderTargetLifecycleManager.FramebufferKey colorZero =
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                renderPassKey,
                new long[] {10L},
                new int[] {0},
                320,
                180,
                1
            );
        VulkanNativeRenderTargetLifecycleManager.FramebufferKey noAttachment =
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                renderPassKey,
                new long[0],
                new int[0],
                320,
                180,
                1
            );
        VulkanNativeRenderTargetLifecycleManager.FramebufferKey differentSize =
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                renderPassKey,
                new long[] {10L},
                new int[] {0},
                640,
                180,
                1
            );
        VulkanNativeRenderTargetLifecycleManager.FramebufferKey differentTexture =
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                renderPassKey,
                new long[] {10L},
                new int[] {1},
                320,
                180,
                1
            );

        assertNotEquals(colorZero, noAttachment);
        assertNotEquals(colorZero, differentSize);
        assertNotEquals(colorZero, differentTexture);
    }

    @Test
    void framebufferKeyCopiesMutableAttachmentArrays() {
        VulkanRenderPassKey renderPassKey = renderPassKey();
        long[] views = {10L};
        int[] textures = {3};
        VulkanNativeRenderTargetLifecycleManager.FramebufferKey key =
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(renderPassKey, views, textures, 64, 64, 1);

        views[0] = 99L;
        textures[0] = 99;

        assertTrue(key.containsTexture(3));
        assertFalse(key.containsTexture(99));
        assertEquals(
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                renderPassKey,
                new long[] {10L},
                new int[] {3},
                64,
                64,
                1
            ),
            key
        );
    }

    @Test
    void textureInvalidationDestroysOnlyDependentFramebuffers() {
        VulkanNativeRenderTargetLifecycleManager manager = new VulkanNativeRenderTargetLifecycleManager();
        VulkanRenderPassKey renderPassKey = renderPassKey();
        manager.cacheRenderPassForTests(renderPassKey, 100L);
        manager.cacheFramebufferForTests(
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                renderPassKey,
                new long[] {10L},
                new int[] {7},
                64,
                64,
                1
            ),
            200L
        );
        manager.cacheFramebufferForTests(
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                renderPassKey,
                new long[] {11L},
                new int[] {8},
                64,
                64,
                1
            ),
            201L
        );
        RecordingDestroyer destroyer = new RecordingDestroyer();

        VulkanNativeRenderTargetLifecycleManager.InvalidationResult result =
            manager.invalidateForTexture(7, destroyer);

        assertEquals(new VulkanNativeRenderTargetLifecycleManager.InvalidationResult(0, 1), result);
        assertEquals(List.of(200L), destroyer.framebuffers);
        assertTrue(destroyer.renderPasses.isEmpty());
        assertEquals(1, manager.cachedRenderPassCountForTests());
        assertEquals(1, manager.cachedFramebufferCountForTests());
        assertTrue(manager.isCachedRenderPass(100L));
        assertFalse(manager.isCachedFramebuffer(200L));
        assertTrue(manager.isCachedFramebuffer(201L));
    }

    @Test
    void invalidationIsIdempotentAndDrainsFramebuffersBeforeRenderPasses() {
        VulkanNativeRenderTargetLifecycleManager manager = new VulkanNativeRenderTargetLifecycleManager();
        VulkanRenderPassKey firstKey = renderPassKey();
        VulkanRenderPassKey secondKey = VulkanRenderPassKey.framebuffer(
            List.of(new VulkanRenderPassKey.Attachment(2, 3, 4, 5, 6, 7)),
            null,
            false
        );
        manager.cacheRenderPassForTests(firstKey, 100L);
        manager.cacheRenderPassForTests(secondKey, 101L);
        manager.cacheFramebufferForTests(
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                firstKey,
                new long[] {10L},
                new int[] {7},
                64,
                64,
                1
            ),
            200L
        );
        manager.cacheFramebufferForTests(
            VulkanNativeRenderTargetLifecycleManager.framebufferKeyForTests(
                secondKey,
                new long[] {11L},
                new int[] {8},
                64,
                64,
                1
            ),
            201L
        );
        RecordingDestroyer destroyer = new RecordingDestroyer();

        VulkanNativeRenderTargetLifecycleManager.InvalidationResult first =
            manager.invalidateAll(destroyer);
        VulkanNativeRenderTargetLifecycleManager.InvalidationResult second =
            manager.invalidateAll(destroyer);

        assertEquals(new VulkanNativeRenderTargetLifecycleManager.InvalidationResult(2, 2), first);
        assertEquals(VulkanNativeRenderTargetLifecycleManager.InvalidationResult.empty(), second);
        assertTrue(destroyer.events.get(0).startsWith("fb:"));
        assertTrue(destroyer.events.get(1).startsWith("fb:"));
        assertTrue(destroyer.events.get(2).startsWith("rp:"));
        assertTrue(destroyer.events.get(3).startsWith("rp:"));
        assertEquals(Set.of(200L, 201L), new HashSet<>(destroyer.framebuffers));
        assertEquals(Set.of(100L, 101L), new HashSet<>(destroyer.renderPasses));
        assertEquals(0, manager.cachedRenderPassCountForTests());
        assertEquals(0, manager.cachedFramebufferCountForTests());
    }

    private static VulkanRenderPassKey renderPassKey() {
        return VulkanRenderPassKey.framebuffer(
            List.of(new VulkanRenderPassKey.Attachment(
                VK10.VK_FORMAT_R8G8B8A8_UNORM,
                VK10.VK_ATTACHMENT_LOAD_OP_LOAD,
                VK10.VK_ATTACHMENT_STORE_OP_STORE,
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
            )),
            null,
            false
        );
    }

    private static final class RecordingDestroyer implements VulkanNativeRenderTargetLifecycleManager.HandleDestroyer {
        final List<String> events = new ArrayList<>();
        final List<Long> renderPasses = new ArrayList<>();
        final List<Long> framebuffers = new ArrayList<>();

        @Override
        public void destroyRenderPass(long renderPassHandle) {
            events.add("rp:" + renderPassHandle);
            renderPasses.add(renderPassHandle);
        }

        @Override
        public void destroyFramebuffer(long framebufferHandle) {
            events.add("fb:" + framebufferHandle);
            framebuffers.add(framebufferHandle);
        }
    }
}
