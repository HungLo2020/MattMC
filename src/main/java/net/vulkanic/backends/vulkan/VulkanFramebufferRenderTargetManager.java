package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;

import java.util.Arrays;
import java.util.Objects;

/**
 * Owns the Vulkan backend's GL-style framebuffer, draw/read-buffer, and clear
 * state. It is intentionally policy-only: Vulkan image lookup, render-pass
 * creation, barriers, clears, copies, and native handle ownership stay in
 * {@link VulkanBackend.NativeSpine} and the lower-level resource managers.
 */
final class VulkanFramebufferRenderTargetManager {
    private static final int GL_NONE = 0;
    private static final int GL_BACK = 0x0405;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;
    private static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = 0x8CD0;
    private static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME = 0x8CD1;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_FRAMEBUFFER_UNDEFINED = 0x8219;
    private static final int GL_TEXTURE = 0x1702;

    private final VulkanVirtualFramebufferManager virtualFramebuffers = new VulkanVirtualFramebufferManager();

    private volatile float pendingClearR = 0.0f;
    private volatile float pendingClearG = 0.0f;
    private volatile float pendingClearB = 0.0f;
    private volatile float pendingClearA = 0.0f;
    private volatile double pendingClearDepth = 1.0;
    private volatile int pendingClearStencil = 0;

    private volatile int pendingReadBuffer = GL_BACK;
    private volatile int pendingDrawBuffer = GL_BACK;
    private volatile int boundReadFramebuffer;
    private volatile int boundDrawFramebuffer;

    VulkanVirtualFramebufferManager virtualFramebuffers() {
        return virtualFramebuffers;
    }

    int createFramebuffer() {
        return virtualFramebuffers.createFramebuffer();
    }

    boolean isFramebuffer(int framebuffer) {
        return virtualFramebuffers.isFramebuffer(framebuffer);
    }

    int checkFramebufferStatus(int target) {
        return virtualFramebuffers.isDefaultOrKnownFramebuffer(resolveBinding(target))
            ? GL_FRAMEBUFFER_COMPLETE
            : GL_FRAMEBUFFER_UNDEFINED;
    }

    void bindFramebuffer(int target, int framebuffer) {
        if (target == GL_READ_FRAMEBUFFER) {
            boundReadFramebuffer = framebuffer;
            pendingReadBuffer = virtualFramebuffers.applyReadBuffer(framebuffer, pendingReadBuffer);
        } else if (target == GL_DRAW_FRAMEBUFFER) {
            boundDrawFramebuffer = framebuffer;
            pendingDrawBuffer = virtualFramebuffers.applyDrawBuffer(framebuffer, pendingDrawBuffer);
        } else if (target == GL_FRAMEBUFFER) {
            boundReadFramebuffer = framebuffer;
            boundDrawFramebuffer = framebuffer;
            pendingReadBuffer = virtualFramebuffers.applyReadBuffer(framebuffer, pendingReadBuffer);
            pendingDrawBuffer = virtualFramebuffers.applyDrawBuffer(framebuffer, pendingDrawBuffer);
        }
    }

    void deleteFramebuffer(int framebuffer) {
        virtualFramebuffers.deleteFramebuffer(framebuffer);
        if (boundReadFramebuffer == framebuffer) {
            boundReadFramebuffer = 0;
        }
        if (boundDrawFramebuffer == framebuffer) {
            boundDrawFramebuffer = 0;
        }
    }

    int resolveBinding(int target) {
        if (target == GL_READ_FRAMEBUFFER) {
            return boundReadFramebuffer;
        }
        if (target == GL_DRAW_FRAMEBUFFER || target == GL_FRAMEBUFFER) {
            return boundDrawFramebuffer;
        }
        return 0;
    }

    int boundReadFramebuffer() {
        return boundReadFramebuffer;
    }

    int boundDrawFramebuffer() {
        return boundDrawFramebuffer;
    }

    void recordAttachment(int framebuffer, int attachment, int texture) {
        virtualFramebuffers.recordAttachment(framebuffer, attachment, texture);
    }

    int attachment(int framebuffer, int attachment) {
        return virtualFramebuffers.getAttachment(framebuffer, attachment);
    }

    VulkanVirtualFramebufferManager.FramebufferSnapshot requireSnapshot(int framebuffer) {
        return virtualFramebuffers.requireSnapshot(framebuffer);
    }

    void setReadBuffer(int buffer) {
        pendingReadBuffer = buffer;
    }

    void setDrawBuffer(int buffer) {
        pendingDrawBuffer = buffer;
    }

    void setDrawBuffers(int[] buffers) {
        virtualFramebuffers.setDrawBuffers(boundDrawFramebuffer, buffers);
        pendingDrawBuffer = virtualFramebuffers.drawBuffer(boundDrawFramebuffer, pendingDrawBuffer);
    }

    void setNamedDrawBuffers(int framebuffer, int[] buffers) {
        virtualFramebuffers.setDrawBuffers(framebuffer, buffers);
        if (framebuffer == boundDrawFramebuffer) {
            pendingDrawBuffer = virtualFramebuffers.drawBuffer(framebuffer, pendingDrawBuffer);
        }
    }

    void setNamedReadBuffer(int framebuffer, int buffer) {
        virtualFramebuffers.setReadBuffer(framebuffer, buffer);
        if (framebuffer == boundReadFramebuffer) {
            pendingReadBuffer = buffer;
        }
    }

    int readBuffer(int framebuffer) {
        return virtualFramebuffers.readBuffer(framebuffer, pendingReadBuffer);
    }

    int drawBuffer(int framebuffer) {
        return virtualFramebuffers.drawBuffer(framebuffer, pendingDrawBuffer);
    }

    int[] drawBuffers(int framebuffer) {
        return virtualFramebuffers.drawBuffers(framebuffer, pendingDrawBuffer);
    }

    Integer boundReadColorTextureForCopy() {
        return textureForCopy(boundReadFramebuffer, pendingReadBuffer);
    }

    Integer boundReadTextureForBlit(int mask) {
        return textureForBlit(boundReadFramebuffer, mask, pendingReadBuffer);
    }

    Integer boundDrawTextureForBlit(int mask) {
        return textureForBlit(boundDrawFramebuffer, mask, pendingDrawBuffer);
    }

    Integer textureForCopy(int framebuffer, int selectedBuffer) {
        return textureForBlit(framebuffer, VulkanicAPI.GL_COLOR_BUFFER_BIT, selectedBuffer);
    }

    Integer textureForBlit(int framebuffer, int mask, int selectedBuffer) {
        return virtualFramebuffers.textureForBlit(framebuffer, mask, selectedBuffer);
    }

    int resolveFramebufferForTextures(int colorHandle, int depthHandle) {
        return virtualFramebuffers.resolveFramebufferForTextures(colorHandle, depthHandle);
    }

    void releaseImplicitFramebuffersForTexture(int texture) {
        virtualFramebuffers.releaseImplicitFramebuffersForTexture(texture);
    }

    void clearAll() {
        boundReadFramebuffer = 0;
        boundDrawFramebuffer = 0;
        pendingReadBuffer = GL_BACK;
        pendingDrawBuffer = GL_BACK;
        pendingClearR = 0.0f;
        pendingClearG = 0.0f;
        pendingClearB = 0.0f;
        pendingClearA = 0.0f;
        pendingClearDepth = 1.0;
        pendingClearStencil = 0;
        virtualFramebuffers.clear();
    }

    void setClearColor(float r, float g, float b, float a) {
        pendingClearR = r;
        pendingClearG = g;
        pendingClearB = b;
        pendingClearA = a;
    }

    void setClearDepth(double depth) {
        pendingClearDepth = depth;
    }

    void setClearStencil(int stencil) {
        pendingClearStencil = stencil;
    }

    ClearStateSnapshot clearState() {
        return new ClearStateSnapshot(
            pendingClearR,
            pendingClearG,
            pendingClearB,
            pendingClearA,
            pendingClearDepth,
            pendingClearStencil
        );
    }

    ClearOperationRequest clearRequestForBoundDrawFramebuffer(int mask) {
        return new ClearOperationRequest(boundDrawFramebuffer, mask, drawBuffers(boundDrawFramebuffer), clearState());
    }

    ColorClearTarget colorClearTarget(int framebuffer, int drawbuffer) {
        int attachment = GL_COLOR_ATTACHMENT0 + drawbuffer;
        int texture = attachment(framebuffer, attachment);
        return new ColorClearTarget(framebuffer, attachment, texture);
    }

    DepthClearTarget depthClearTarget(int framebuffer) {
        int texture;
        try {
            texture = requireSnapshot(framebuffer).depthAttachmentTexture();
        } catch (IllegalArgumentException ignored) {
            texture = 0;
        }
        return new DepthClearTarget(framebuffer, texture);
    }

    int framebufferAttachmentParameter(int framebuffer, int attachment, int pname) {
        int texture = attachment(framebuffer, attachment);
        return switch (pname) {
            case GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME -> texture;
            case GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE -> texture == 0 ? GL_NONE : GL_TEXTURE;
            default -> 0;
        };
    }

    int framebufferCountForTests() {
        return virtualFramebuffers.framebufferCountForTests();
    }

    int implicitFramebufferCountForTests() {
        return virtualFramebuffers.implicitFramebufferCountForTests();
    }

    record ClearStateSnapshot(float r, float g, float b, float a, double depth, int stencil) {
        float[] colorArray() {
            return new float[] {r, g, b, a};
        }
    }

    record ClearOperationRequest(
        int framebuffer,
        int mask,
        int[] drawBuffers,
        ClearStateSnapshot clearState
    ) {
        ClearOperationRequest {
            drawBuffers = Objects.requireNonNull(drawBuffers, "drawBuffers must not be null").clone();
            Objects.requireNonNull(clearState, "clearState must not be null");
        }

        boolean clearColor() {
            return (mask & VulkanicAPI.GL_COLOR_BUFFER_BIT) != 0;
        }

        boolean clearDepth() {
            return (mask & VulkanicAPI.GL_DEPTH_BUFFER_BIT) != 0;
        }

        boolean clearStencil() {
            return (mask & VulkanicAPI.GL_STENCIL_BUFFER_BIT) != 0;
        }

        @Override
        public int[] drawBuffers() {
            return drawBuffers.clone();
        }
    }

    record ColorClearTarget(int framebuffer, int attachment, int texture) {
        boolean present() {
            return attachment != GL_NONE && texture != 0;
        }
    }

    record DepthClearTarget(int framebuffer, int texture) {
        boolean present() {
            return texture != 0;
        }
    }

    static int colorAttachmentIndex(int attachment) {
        if (attachment == GL_BACK) {
            return 0;
        }
        if (attachment >= GL_COLOR_ATTACHMENT0) {
            return attachment - GL_COLOR_ATTACHMENT0;
        }
        return -1;
    }

    static int drawBufferForAttachmentIndex(int colorAttachmentIndex) {
        return GL_COLOR_ATTACHMENT0 + colorAttachmentIndex;
    }

    static int attachmentForDrawBuffer(int drawBuffer) {
        return drawBuffer == GL_BACK ? GL_COLOR_ATTACHMENT0 : drawBuffer;
    }

    @Override
    public String toString() {
        return "VulkanFramebufferRenderTargetManager{"
            + "readFbo=" + boundReadFramebuffer
            + ", drawFbo=" + boundDrawFramebuffer
            + ", readBuffer=0x" + Integer.toHexString(pendingReadBuffer)
            + ", drawBuffer=0x" + Integer.toHexString(pendingDrawBuffer)
            + ", clear=" + Arrays.toString(clearState().colorArray())
            + '}';
    }
}
