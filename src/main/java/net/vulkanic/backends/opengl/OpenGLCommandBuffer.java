package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicShader;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
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
    
    // === Phase 5: Shader/Program Operations ===
    
    @Override
    public void shaderSource(int shader, CharSequence[] source) {
        GL20.glShaderSource(shader, source);
    }
    
    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }
    
    @Override
    public int getShaderi(int shader, int pname) {
        return GL20.glGetShaderi(shader, pname);
    }
    
    @Override
    public String getShaderInfoLog(int shader, int maxLength) {
        return GL20.glGetShaderInfoLog(shader, maxLength);
    }
    
    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }
    
    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }
    
    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }
    
    @Override
    public int getProgrami(int program, int pname) {
        return GL20.glGetProgrami(program, pname);
    }
    
    @Override
    public String getProgramInfoLog(int program, int maxLength) {
        return GL20.glGetProgramInfoLog(program, maxLength);
    }
    
    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }
    
    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }
    
    // Uniform operations
    
    @Override
    public int getUniformLocation(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }
    
    @Override
    public void uniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
    }
    
    // Attribute operations
    
    @Override
    public void bindAttribLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
    }
    
    // === Phase 5: Buffer Operations ===
    
    @Override
    public void bindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }
    
    @Override
    public void bufferData(int target, java.nio.ByteBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }
    
    @Override
    public void bufferData(int target, long size, int usage) {
        GL15.glBufferData(target, size, usage);
    }
    
    @Override
    public void bufferSubData(int target, int offset, java.nio.ByteBuffer data) {
        GL15.glBufferSubData(target, (long)offset, data);
    }
    
    @Override
    public java.nio.ByteBuffer mapBufferRange(int target, int offset, int length, int access) {
        return GL30.glMapBufferRange(target, offset, length, access);
    }
    
    @Override
    public void unmapBuffer(int target) {
        GL15.glUnmapBuffer(target);
    }
    
    @Override
    public void deleteBuffer(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }
    
    // === Phase 5: VAO Operations ===
    
    @Override
    public void bindVertexArray(int array) {
        GL30.glBindVertexArray(array);
    }
    
    // === Phase 5: Framebuffer Operations ===
    
    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GL30.glBindFramebuffer(target, framebuffer);
    }
    
    @Override
    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Override
    public void deleteFramebuffer(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }
    
    // === Phase 6: Query & State Operations ===
    @Override
    public int getError() {
        return GL11.glGetError();
    }
    
    @Override
    public String getString(int name) {
        return GL11.glGetString(name);
    }
    
    @Override
    public int getInteger(int pname) {
        return GL11.glGetInteger(pname);
    }
    
    @Override
    public int getTexLevelParameteri(int target, int level, int pname) {
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }
    
    @Override
    public void blendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) {
        GL14.glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
    }
    
    // Sync operations
    @Override
    public long fenceSync(int condition, int flags) {
        return GL32.glFenceSync(condition, flags);
    }
    
    @Override
    public int clientWaitSync(long sync, int flags, long timeout) {
        return GL32.glClientWaitSync(sync, flags, timeout);
    }
    
    @Override
    public void deleteSync(long sync) {
        GL32.glDeleteSync(sync);
    }
    
    // Generic state operations
    @Override
    public void glEnable(int cap) {
        GL11.glEnable(cap);
    }
    
    @Override
    public void glDisable(int cap) {
        GL11.glDisable(cap);
    }
    
    @Override
    public void drawBuffer(int buffer) {
        GL11.glDrawBuffer(buffer);
    }
    
    @Override
    public void glClearColor(float r, float g, float b, float a) {
        GL11.glClearColor(r, g, b, a);
    }
    
    @Override
    public void glClearDepth(double depth) {
        GL11.glClearDepth(depth);
    }
    
    @Override
    public void submit() {
        // No-op for OpenGL - commands are immediate
    }
}
