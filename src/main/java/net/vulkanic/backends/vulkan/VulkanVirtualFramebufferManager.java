package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns GL-style virtual framebuffer identity and attachment bookkeeping for the
 * Vulkan backend. It does not resolve Vulkan images, choose layouts, create
 * render passes, or issue Vulkan commands.
 */
final class VulkanVirtualFramebufferManager {
    private static final int GL_NONE = 0;
    private static final int GL_BACK = 0x0405;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;

    private final AtomicInteger nextVirtualFramebufferId = new AtomicInteger(1);
    private final Set<Integer> virtualFramebuffers = ConcurrentHashMap.newKeySet();
    private final Map<Integer, VirtualFramebufferState> framebufferStates = new ConcurrentHashMap<>();
    private final Map<FramebufferTexturePairKey, Integer> implicitFramebufferByTexturePair = new ConcurrentHashMap<>();

    int createFramebuffer() {
        int id = nextVirtualFramebufferId.getAndIncrement();
        virtualFramebuffers.add(id);
        framebufferStates.put(id, new VirtualFramebufferState());
        return id;
    }

    boolean isFramebuffer(int framebuffer) {
        return framebuffer != 0 && virtualFramebuffers.contains(framebuffer);
    }

    boolean isDefaultOrKnownFramebuffer(int framebuffer) {
        return framebuffer == 0 || virtualFramebuffers.contains(framebuffer);
    }

    void deleteFramebuffer(int framebuffer) {
        virtualFramebuffers.remove(framebuffer);
        framebufferStates.remove(framebuffer);
        releaseImplicitFramebufferBinding(framebuffer);
    }

    void recordAttachment(int framebuffer, int attachment, int texture) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        if (state == null) {
            return;
        }

        releaseImplicitFramebufferBinding(framebuffer);
        state.setAttachment(attachment, texture);
        if (attachment == GL_DEPTH_STENCIL_ATTACHMENT) {
            state.setAttachment(GL_DEPTH_ATTACHMENT, texture);
        }
    }

    int getAttachment(int framebuffer, int attachment) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        return state == null ? 0 : state.getAttachment(attachment);
    }

    int applyReadBuffer(int framebuffer, int fallbackReadBuffer) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        return state == null ? fallbackReadBuffer : state.readBuffer;
    }

    int applyDrawBuffer(int framebuffer, int fallbackDrawBuffer) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        return state == null ? fallbackDrawBuffer : state.getPrimaryDrawBuffer();
    }

    void setDrawBuffers(int framebuffer, int[] buffers) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        if (state != null) {
            state.setDrawBuffers(buffers);
        }
    }

    void setReadBuffer(int framebuffer, int mode) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        if (state != null) {
            state.readBuffer = mode;
        }
    }

    int readBuffer(int framebuffer, int fallbackReadBuffer) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        return state == null ? fallbackReadBuffer : state.readBuffer;
    }

    int drawBuffer(int framebuffer, int fallbackDrawBuffer) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        return state == null ? fallbackDrawBuffer : state.getPrimaryDrawBuffer();
    }

    int[] drawBuffers(int framebuffer, int fallbackDrawBuffer) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        return state == null ? new int[]{fallbackDrawBuffer} : state.getDrawBuffers();
    }

    Integer textureForBlit(int framebuffer, int mask, int selectedBuffer) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        if (state == null) {
            return null;
        }

        if ((mask & (VulkanicAPI.GL_DEPTH_BUFFER_BIT | VulkanicAPI.GL_STENCIL_BUFFER_BIT)) != 0) {
            int depthTexture = depthAttachmentTexture(state);
            return depthTexture == 0 ? null : depthTexture;
        }

        int attachment = selectedBuffer == GL_NONE ? GL_COLOR_ATTACHMENT0 : selectedBuffer;
        int colorTexture = state.getAttachment(attachment);
        return colorTexture == 0 ? null : colorTexture;
    }

    FramebufferSnapshot requireSnapshot(int framebuffer) {
        VirtualFramebufferState state = framebufferStates.get(framebuffer);
        if (state == null) {
            throw new IllegalArgumentException("Unknown Vulkan virtual framebuffer handle: " + framebuffer);
        }
        return new FramebufferSnapshot(framebuffer, state.attachmentSnapshot(), state.getDrawBuffers(), state.readBuffer);
    }

    int resolveFramebufferForTextures(int colorHandle, int depthHandle) {
        if (colorHandle == 0) {
            return 0;
        }

        int matchedFramebuffer = 0;
        for (Map.Entry<Integer, VirtualFramebufferState> entry : framebufferStates.entrySet()) {
            int framebuffer = entry.getKey();
            if (framebuffer == 0) {
                continue;
            }

            if (!matchesSingleColorFramebufferContract(entry.getValue(), colorHandle, depthHandle)) {
                continue;
            }

            if (matchedFramebuffer == 0 || framebuffer < matchedFramebuffer) {
                matchedFramebuffer = framebuffer;
            }
        }

        return matchedFramebuffer != 0 ? matchedFramebuffer : resolveOrCreateImplicitFramebuffer(colorHandle, depthHandle);
    }

    void releaseImplicitFramebuffersForTexture(int texture) {
        for (Map.Entry<FramebufferTexturePairKey, Integer> entry : new ArrayList<>(implicitFramebufferByTexturePair.entrySet())) {
            FramebufferTexturePairKey key = entry.getKey();
            if (key.colorTexture() != texture && key.depthTexture() != texture) {
                continue;
            }

            Integer framebuffer = entry.getValue();
            if (!implicitFramebufferByTexturePair.remove(key, framebuffer)) {
                continue;
            }

            if (framebuffer != null) {
                virtualFramebuffers.remove(framebuffer);
                framebufferStates.remove(framebuffer);
            }
        }
    }

    void clear() {
        virtualFramebuffers.clear();
        framebufferStates.clear();
        implicitFramebufferByTexturePair.clear();
    }

    int framebufferCountForTests() {
        return virtualFramebuffers.size();
    }

    int implicitFramebufferCountForTests() {
        return implicitFramebufferByTexturePair.size();
    }

    private int resolveOrCreateImplicitFramebuffer(int colorHandle, int depthHandle) {
        FramebufferTexturePairKey key = new FramebufferTexturePairKey(colorHandle, depthHandle);
        while (true) {
            Integer cachedFramebuffer = implicitFramebufferByTexturePair.get(key);
            if (cachedFramebuffer != null) {
                if (framebufferStates.containsKey(cachedFramebuffer)) {
                    return cachedFramebuffer;
                }

                implicitFramebufferByTexturePair.remove(key, cachedFramebuffer);
            }

            int framebuffer = nextVirtualFramebufferId.getAndIncrement();
            VirtualFramebufferState state = new VirtualFramebufferState();
            state.setAttachment(GL_COLOR_ATTACHMENT0, colorHandle);
            if (depthHandle != 0) {
                state.setAttachment(GL_DEPTH_ATTACHMENT, depthHandle);
            }

            virtualFramebuffers.add(framebuffer);
            framebufferStates.put(framebuffer, state);

            Integer racedFramebuffer = implicitFramebufferByTexturePair.putIfAbsent(key, framebuffer);
            if (racedFramebuffer == null) {
                return framebuffer;
            }

            virtualFramebuffers.remove(framebuffer);
            framebufferStates.remove(framebuffer);
            if (framebufferStates.containsKey(racedFramebuffer)) {
                return racedFramebuffer;
            }

            implicitFramebufferByTexturePair.remove(key, racedFramebuffer);
        }
    }

    private void releaseImplicitFramebufferBinding(int framebuffer) {
        for (Map.Entry<FramebufferTexturePairKey, Integer> entry : new ArrayList<>(implicitFramebufferByTexturePair.entrySet())) {
            Integer mappedFramebuffer = entry.getValue();
            if (mappedFramebuffer != null && mappedFramebuffer == framebuffer) {
                implicitFramebufferByTexturePair.remove(entry.getKey(), mappedFramebuffer);
            }
        }
    }

    private static boolean matchesSingleColorFramebufferContract(VirtualFramebufferState state, int colorHandle, int depthHandle) {
        int colorAttachmentCount = 0;
        for (int drawBuffer : state.getDrawBuffers()) {
            if (drawBuffer == GL_NONE) {
                continue;
            }

            int attachment = drawBuffer == GL_BACK ? GL_COLOR_ATTACHMENT0 : drawBuffer;
            if (state.getAttachment(attachment) != colorHandle) {
                return false;
            }

            colorAttachmentCount++;
            if (colorAttachmentCount > 1) {
                return false;
            }
        }

        return colorAttachmentCount == 1 && depthAttachmentTexture(state) == depthHandle;
    }

    private static int depthAttachmentTexture(VirtualFramebufferState state) {
        int depthTexture = state.getAttachment(GL_DEPTH_ATTACHMENT);
        if (depthTexture != 0) {
            return depthTexture;
        }
        return state.getAttachment(GL_DEPTH_STENCIL_ATTACHMENT);
    }

    record FramebufferSnapshot(
        int framebuffer,
        Map<Integer, Integer> attachments,
        int[] drawBuffers,
        int readBuffer
    ) {
        FramebufferSnapshot {
            attachments = Map.copyOf(attachments);
            drawBuffers = Objects.requireNonNull(drawBuffers, "drawBuffers must not be null").clone();
        }

        int attachment(int attachment) {
            return attachments.getOrDefault(attachment, 0);
        }

        int depthAttachmentTexture() {
            int depthTexture = attachment(GL_DEPTH_ATTACHMENT);
            if (depthTexture != 0) {
                return depthTexture;
            }
            return attachment(GL_DEPTH_STENCIL_ATTACHMENT);
        }

        @Override
        public int[] drawBuffers() {
            return drawBuffers.clone();
        }
    }

    private record FramebufferTexturePairKey(
        int colorTexture,
        int depthTexture
    ) {
    }

    private static final class VirtualFramebufferState {
        private final Map<Integer, Integer> attachments = new ConcurrentHashMap<>();
        private volatile int readBuffer = GL_COLOR_ATTACHMENT0;
        private volatile int[] drawBuffers = new int[]{GL_COLOR_ATTACHMENT0};

        void setAttachment(int attachment, int texture) {
            if (texture == 0) {
                attachments.remove(attachment);
            } else {
                attachments.put(attachment, texture);
            }
        }

        int getAttachment(int attachment) {
            return attachments.getOrDefault(attachment, 0);
        }

        Map<Integer, Integer> attachmentSnapshot() {
            return Map.copyOf(attachments);
        }

        int[] getDrawBuffers() {
            return drawBuffers.clone();
        }

        int getPrimaryDrawBuffer() {
            return drawBuffers.length == 0 ? GL_NONE : drawBuffers[0];
        }

        void setDrawBuffers(int[] buffers) {
            if (buffers == null || buffers.length == 0) {
                this.drawBuffers = new int[]{GL_NONE};
                return;
            }

            this.drawBuffers = buffers.clone();
        }
    }
}
