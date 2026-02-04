package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.systems.RenderSystem;
import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicShader;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicTexture;
import org.lwjgl.opengl.GL11;

/**
 * OpenGL implementation of VulkanicCommandBuffer.
 */
public class OpenGLCommandBuffer implements VulkanicCommandBuffer {
    private final GpuDevice device;
    private final CommandEncoder encoder;
    private OpenGLShader currentShader;
    
    public OpenGLCommandBuffer(GpuDevice device) {
        this.device = device;
        this.encoder = device.createCommandEncoder();
        this.currentShader = null;
    }
    
    @Override
    public void beginRenderPass(VulkanicFramebuffer framebuffer) {
        RenderSystem.assertOnRenderThread();
        if (framebuffer != null) {
            setViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
        }
    }
    
    @Override
    public void endRenderPass() {
        RenderSystem.assertOnRenderThread();
    }
    
    @Override
    public void bindShader(VulkanicShader shader) {
        RenderSystem.assertOnRenderThread();
        if (shader instanceof OpenGLShader openGLShader) {
            this.currentShader = openGLShader;
            GlStateManager._glUseProgram(openGLShader.getProgramId());
        } else {
            throw new IllegalArgumentException("Shader must be an OpenGLShader");
        }
    }
    
    @Override
    public void bindVertexBuffer(VulkanicBuffer buffer) {
        RenderSystem.assertOnRenderThread();
        if (!(buffer instanceof OpenGLBuffer)) {
            throw new IllegalArgumentException("Buffer must be an OpenGLBuffer");
        }
    }
    
    @Override
    public void bindIndexBuffer(VulkanicBuffer buffer) {
        RenderSystem.assertOnRenderThread();
        if (!(buffer instanceof OpenGLBuffer)) {
            throw new IllegalArgumentException("Buffer must be an OpenGLBuffer");
        }
    }
    
    @Override
    public void bindTexture(int unit, VulkanicTexture texture) {
        RenderSystem.assertOnRenderThread();
        if (texture instanceof OpenGLTexture) {
            GlStateManager._activeTexture(33984 + unit);
        } else {
            throw new IllegalArgumentException("Texture must be an OpenGLTexture");
        }
    }
    
    @Override
    public void draw(int vertexCount) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._drawArrays(4, 0, vertexCount);
    }
    
    @Override
    public void drawIndexed(int indexCount) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._drawElements(4, indexCount, 5123, 0);
    }
    
    @Override
    public void clear(float r, float g, float b, float a) {
        RenderSystem.assertOnRenderThread();
        GL11.glClearColor(r, g, b, a);
        GlStateManager._clear(16384);
    }
    
    @Override
    public void clearDepth(float depth) {
        RenderSystem.assertOnRenderThread();
        GL11.glClearDepth(depth);
        GlStateManager._clear(256);
    }
    
    @Override
    public void setViewport(int x, int y, int width, int height) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._viewport(x, y, width, height);
    }
    
    @Override
    public void submit() {
        RenderSystem.assertOnRenderThread();
    }
}
