package net.minecraft.client.renderer.shaders.framebuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.client.renderer.shaders.gl.GlResource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;

/**
 * Wrapper for OpenGL framebuffer objects with attachment management.
 * Follows IRIS GlFramebuffer.java structure exactly.
 * 
 * @see <a href="https://github.com/IrisShaders/Iris">IRIS Source</a>
 */
public class GlFramebuffer extends GlResource {
    private final Int2IntMap attachments;
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private boolean hasDepthAttachment;

    public GlFramebuffer() {
        super(GlStateManager.glGenFramebuffers());

        this.attachments = new Int2IntArrayMap();
        this.maxDrawBuffers = GlStateManager._getInteger(GL30C.GL_MAX_DRAW_BUFFERS);
        this.maxColorAttachments = GlStateManager._getInteger(GL30C.GL_MAX_COLOR_ATTACHMENTS);
        this.hasDepthAttachment = false;
    }

    public void addDepthAttachment(int texture) {
        int fb = getGlId();

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb);
        GlStateManager._glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, texture, 0);

        this.hasDepthAttachment = true;
    }

    public void addDepthStencilAttachment(int texture) {
        int fb = getGlId();

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb);
        GlStateManager._glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL30C.GL_TEXTURE_2D, texture, 0);

        this.hasDepthAttachment = true;
    }

    public void addColorAttachment(int index, int texture) {
        int fb = getGlId();

        if (index >= maxColorAttachments) {
            throw new IllegalArgumentException("Only " + maxColorAttachments + " color attachments are supported on this GPU");
        }

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb);
        GlStateManager._glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + index, GL30C.GL_TEXTURE_2D, texture, 0);
        
        attachments.put(index, texture);
    }

    public void noDrawBuffers() {
        int fb = getGlId();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb);
        GL20.glDrawBuffers(GL11.GL_NONE);
    }

    public void drawBuffers(int[] buffers) {
        int[] glBuffers = new int[buffers.length];
        int index = 0;

        if (buffers.length > maxDrawBuffers) {
            throw new IllegalArgumentException("Cannot write to more than " + maxDrawBuffers + " draw buffers on this GPU");
        }

        for (int buffer : buffers) {
            if (buffer >= maxColorAttachments) {
                throw new IllegalArgumentException("Only " + maxColorAttachments + " color attachments are supported on this GPU, but an attempt was made to write to a color attachment with index " + buffer);
            }

            glBuffers[index++] = GL30C.GL_COLOR_ATTACHMENT0 + buffer;
        }

        int fb = getGlId();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb);
        GL20.glDrawBuffers(glBuffers);
    }

    public void readBuffer(int buffer) {
        if (buffer >= maxColorAttachments) {
            throw new IllegalArgumentException("Only " + maxColorAttachments + " color attachments are supported on this GPU");
        }

        int fb = getGlId();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb);
        GL11.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + buffer);
    }

    public int getColorAttachment(int index) {
        return attachments.get(index);
    }

    public boolean hasDepthAttachment() {
        return hasDepthAttachment;
    }

    public void bind() {
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, getGlId());
    }

    public void bindAsReadBuffer() {
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, getGlId());
    }

    public void bindAsDrawBuffer() {
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, getGlId());
    }

    @Override
    protected void destroyInternal() {
        GlStateManager._glDeleteFramebuffers(getGlId());
    }

    public int getStatus() {
        bind();
        return GL30.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
    }

    /**
     * Checks if the framebuffer is complete.
     * @return true if framebuffer is complete
     */
    public boolean isComplete() {
        return getStatus() == GL30C.GL_FRAMEBUFFER_COMPLETE;
    }

    /**
     * Creates and attaches a depth renderbuffer.
     * @param width Width of the depth buffer
     * @param height Height of the depth buffer
     */
    public void addDepthAttachment(int width, int height) {
        int fb = getGlId();
        
        // Create depth renderbuffer
        int depthRb = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, depthRb);
        GL30.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL30C.GL_DEPTH_COMPONENT24, width, height);
        
        // Attach to framebuffer
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fb);
        GL30.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_RENDERBUFFER, depthRb);
        
        this.hasDepthAttachment = true;
    }

    /**
     * Unbinds any framebuffer (binds the default framebuffer 0).
     */
    public static void unbind() {
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
    }

    public int getId() {
        return getGlId();
    }
}
