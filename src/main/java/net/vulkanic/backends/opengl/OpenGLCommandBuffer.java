package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicShader;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43C;

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
    
    // Depth state operations
    @Override
    public void enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
    
    @Override
    public void disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }
    
    @Override
    public void setDepthFunc(int func) {
        GL11.glDepthFunc(func);
    }
    
    @Override
    public void setDepthMask(boolean mask) {
        GL11.glDepthMask(mask);
    }
    
    // Blend state operations
    @Override
    public void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }
    
    @Override
    public void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    @Override
    public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    // Cull state operations
    @Override
    public void enableCull() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }
    
    @Override
    public void disableCull() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }
    
    // Color operations
    @Override
    public void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }
    
    // Texture operations
    @Override
    public void setActiveTexture(int textureUnit) {
        GL13.glActiveTexture(textureUnit);
    }
    
    @Override
    public void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }
    
    @Override
    public void setTexParameter(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }
    
    @Override
    public void setPixelStore(int pname, int param) {
        GL11.glPixelStorei(pname, param);
    }
    
    // Polygon offset operations
    @Override
    public void enablePolygonOffset() {
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    }
    
    @Override
    public void disablePolygonOffset() {
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }
    
    @Override
    public void setPolygonOffset(float factor, float units) {
        GL11.glPolygonOffset(factor, units);
    }
    
    // Color logic operations
    @Override
    public void enableColorLogicOp() {
        GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
    }
    
    @Override
    public void disableColorLogicOp() {
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
    }
    
    @Override
    public void setLogicOp(int op) {
        GL11.glLogicOp(op);
    }
    
    // Polygon mode operation
    @Override
    public void setPolygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }
    
    // Draw operations
    @Override
    public void drawArrays(int mode, int first, int count) {
        GL11.glDrawArrays(mode, first, count);
    }
    
    @Override
    public void drawElements(int mode, int count, int type, long indices) {
        // Note: Iris tessellation hooks are preserved in GlStateManager
        GL43C.glDrawElements(mode, count, type, indices);
    }
    
    // Vertex attribute operations
    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void enableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }
    
    // Texture gen/delete operations
    @Override
    public int genTexture() {
        return GL11.glGenTextures();
    }
    
    @Override
    public void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }
    
    // Texture image operations
    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, 
                           int border, int format, int type, java.nio.ByteBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }
    
    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
                              int format, int type, long pixels) {
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }
    
    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
                              int format, int type, java.nio.ByteBuffer pixels) {
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }
    
    // Read pixels operation
    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Override
    public void submit() {
        // No-op for OpenGL - commands are immediate
    }
}
