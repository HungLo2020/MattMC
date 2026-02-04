package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicShader;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * OpenGL implementation of VulkanicCommandBuffer.
 * This is the ONLY place in the codebase that should directly call OpenGL functions.
 */
public class OpenGLCommandBuffer implements VulkanicCommandBuffer {
    private OpenGLShader currentShader;
    
    public OpenGLCommandBuffer() {
        this.currentShader = null;
    }
    
    @Override
    public void beginRenderPass(VulkanicFramebuffer framebuffer) {
        if (framebuffer != null) {
            setViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
        }
    }
    
    @Override
    public void endRenderPass() {
        // No-op for OpenGL
    }
    
    @Override
    public void bindShader(VulkanicShader shader) {
        if (shader instanceof OpenGLShader openGLShader) {
            this.currentShader = openGLShader;
            GL20.glUseProgram(openGLShader.getProgramId());
        } else {
            throw new IllegalArgumentException("Shader must be an OpenGLShader");
        }
    }
    
    @Override
    public void bindVertexBuffer(VulkanicBuffer buffer) {
        if (!(buffer instanceof OpenGLBuffer)) {
            throw new IllegalArgumentException("Buffer must be an OpenGLBuffer");
        }
        // TODO: Implement vertex buffer binding with GL15.glBindBuffer
    }
    
    @Override
    public void bindIndexBuffer(VulkanicBuffer buffer) {
        if (!(buffer instanceof OpenGLBuffer)) {
            throw new IllegalArgumentException("Buffer must be an OpenGLBuffer");
        }
        // TODO: Implement index buffer binding with GL15.glBindBuffer
    }
    
    @Override
    public void bindTexture(int unit, VulkanicTexture texture) {
        if (texture instanceof OpenGLTexture openGLTexture) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, openGLTexture.getTextureId());
        } else {
            throw new IllegalArgumentException("Texture must be an OpenGLTexture");
        }
    }
    
    @Override
    public void draw(int vertexCount) {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
    }
    
    @Override
    public void drawIndexed(int indexCount) {
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_SHORT, 0);
    }
    
    @Override
    public void clear(float r, float g, float b, float a) {
        GL11.glClearColor(r, g, b, a);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }
    
    @Override
    public void clearDepth(float depth) {
        GL11.glClearDepth(depth);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }
    
    @Override
    public void clearColorAndDepth(float r, float g, float b, float a, float depth) {
        GL11.glClearColor(r, g, b, a);
        GL11.glClearDepth(depth);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }
    
    @Override
    public void clearBuffers(int bufferBits) {
        // Use OpenGL's current clear color/depth state - don't override it
        // This is the proper way to clear when glClearColor/glClearDepth have already been set
        GL11.glClear(bufferBits);
    }
    
    @Override
    public void setViewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }
    
    @Override
    public void setScissor(int x, int y, int width, int height) {
        GL11.glScissor(x, y, width, height);
    }
    
    @Override
    public void enableScissorTest() {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }
    
    @Override
    public void disableScissorTest() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    
    @Override
    public void submit() {
        // No-op for OpenGL - commands are immediate
    }
}
