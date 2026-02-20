package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackend;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * OpenGL implementation of the Vulkanic Graphics Backend.
 * This is the ONLY place where direct OpenGL calls should be made.
 */
public class OpenGLBackend implements GraphicsBackend {
    
    /** Tracks the currently bound read framebuffer (GL_READ_FRAMEBUFFER = 36008) */
    private int boundReadFbo = 0;
    /** Tracks the currently bound draw framebuffer (GL_DRAW_FRAMEBUFFER = 36009) */
    private int boundDrawFbo = 0;
    
    @Override
    public long getGraphicsContext() {
        // Platform-specific: On Windows, return the WGL context handle
        // On other platforms, this would use different APIs (GLX on Linux, CGL on macOS)
        try {
            return org.lwjgl.opengl.WGL.wglGetCurrentContext();
        } catch (UnsatisfiedLinkError e) {
            // WGL native library not available - occurs on non-Windows platforms
            // or when the native library fails to load
            return 0L;
        }
    }
    
    /**
     * Sets the dynamic viewport state with explicit command context.
     * This is the Vulkan-compatible implementation for viewport control.
     * 
     * OpenGL implementation: Direct mapping to glViewport() (context is validated but not used)
     * Vulkan implementation: Will map to vkCmdSetViewport(ctx.getHandle(), ...)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     */
    @Override
    public void setDynamicViewport(CommandContext ctx, int x, int y, int width, int height) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glViewport(x, y, width, height);
    }
    
    @Override
    public void clearBuffers(CommandContext ctx, int mask) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glClear(mask);
    }
    
    @Override
    public void setBlendEnabled(CommandContext ctx, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (enabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }
    
    @Override
    public void setIndexedEnabled(CommandContext ctx, int capability, int index, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (enabled) {
            GL30.glEnablei(capability, index);
        } else {
            GL30.glDisablei(capability, index);
        }
    }
    
    @Override
    public void setCullFaceMode(CommandContext ctx, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glCullFace(mode);
    }
    
    @Override
    public void bindShaderProgram(CommandContext ctx, int programId) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUseProgram(programId);
    }
    
    @Override
    public void setCapabilityEnabled(CommandContext ctx, int cap, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }
    
    @Override
    public void bindTexture2D(CommandContext ctx, int textureId) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        int activeTexUnit = GlStateManager.activeTexture;
        if (textureId != GlStateManager.TEXTURES[activeTexUnit].binding) {
            GlStateManager.TEXTURES[activeTexUnit].binding = textureId;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }
    }
    
    @Override
    public void bindTexture(CommandContext ctx, int target, int textureId) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glBindTexture(target, textureId);
    }
    
    @Override
    public void bindSampler(CommandContext ctx, int unit, int sampler) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL33.glBindSampler(unit, sampler);
    }
    
    @Override
    public void setDepthTest(CommandContext ctx, int func) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDepthFunc(func);
    }
    
    @Override
    public void setDepthWriteMask(CommandContext ctx, boolean enabled) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDepthMask(enabled);
    }
    
    @Override
    public void setColorMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glColorMask(r, g, b, a);
    }
    
    @Override
    public void generateTextureMipmap(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glGenerateMipmap(target);
    }
    
    @Override
    public void setPixelStore(CommandContext ctx, int pname, int value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glPixelStorei(pname, value);
    }
    
    @Override
    public void bindFramebuffer(CommandContext ctx, int target, int fbo) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBindFramebuffer(target, fbo);
        // Update internal state tracking so getBoundFramebuffer can answer without GL queries
        if (target == 36160 /* GL_FRAMEBUFFER */ || target == 36008 /* GL_READ_FRAMEBUFFER */) {
            boundReadFbo = fbo;
        }
        if (target == 36160 /* GL_FRAMEBUFFER */ || target == 36009 /* GL_DRAW_FRAMEBUFFER */) {
            boundDrawFbo = fbo;
        }
    }

    @Override
    public int getBoundFramebuffer(CommandContext ctx, int target) {
        if (target == 36008 /* GL_READ_FRAMEBUFFER */) {
            return boundReadFbo;
        }
        // GL_DRAW_FRAMEBUFFER (36009) or GL_FRAMEBUFFER (36160) — return draw binding
        return boundDrawFbo;
    }
    
    @Override
    public void bindBuffer(CommandContext ctx, int target, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBindBuffer(target, buffer);
    }
    
    @Override
    public void bindBufferBase(CommandContext ctx, int target, int index, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBindBufferBase(target, index, buffer);
    }
    
    @Override
    public void setActiveTextureUnit(CommandContext ctx, int unit) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL13.glActiveTexture(unit);
    }
    
    @Override
    public void setTextureParameter(CommandContext ctx, int target, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexParameteri(target, pname, param);
    }
    
    @Override
    public void copyTexSubImage2D(CommandContext ctx, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Override
    public int getTexParameteri(CommandContext ctx, int target, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetTexParameteri(target, pname);
    }
    
    /**
     * Sets the dynamic scissor rectangle with explicit command context.
     * This is the Vulkan-compatible implementation for scissor control.
     * 
     * OpenGL implementation: Direct mapping to glScissor() (context is validated but not used)
     * Vulkan implementation: Will map to vkCmdSetScissor(ctx.getHandle(), ...)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     */
    @Override
    public void setDynamicScissor(CommandContext ctx, int x, int y, int width, int height) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glScissor(x, y, width, height);
    }
    
    // CommandContext versions of DSA buffer operations
    @Override
    public int createBufferDSA(CommandContext ctx) {
        return createBuffer(ctx);
    }
    
    @Override
    public void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferData(ctx, GL15.GL_ARRAY_BUFFER, size, usage);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferData(ctx, GL15.GL_ARRAY_BUFFER, data, usage);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferSubData(ctx, GL15.GL_ARRAY_BUFFER, offset, data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferStorage(ctx, GL15.GL_ARRAY_BUFFER, size, flags);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferStorage(ctx, GL15.GL_ARRAY_BUFFER, data, flags);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Override
    public java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL45.glMapNamedBufferRange(buffer, offset, length, access);
    }
    
    // CommandContext versions of DSA buffer operations
    @Override
    public void unmapNamedBufferDSA(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glUnmapNamedBuffer(buffer);
    }
    
    @Override
    public void flushMappedNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glFlushMappedNamedBufferRange(buffer, offset, length);
    }
    
    @Override
    public void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    @Override
    public void namedFramebufferTextureDSA(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Override
    public void blitNamedFramebufferDSA(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                                     dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Override
    public int createTexture2D(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGenTextures();
    }
    
    @Override
    public void deleteTexture(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDeleteTextures(texture);
    }
    
    @Override
    public void drawArrays(CommandContext ctx, int mode, int first, int count) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDrawArrays(mode, first, count);
    }
    
    @Override
    public void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDrawElements(mode, count, type, indices);
    }
    
    @Override
    public void setBlendFunction(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    @Override
    public void setBlendEquation(CommandContext ctx, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL14.glBlendEquation(mode);
    }
    
    @Override
    public void setDepthFunc(CommandContext ctx, int func) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glDepthFunc(func);
    }
    
    @Override
    public void setReadBuffer(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glReadBuffer(buffer);
    }
    
    @Override
    public void framebufferTexture(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public void framebufferTexture2D(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public void drawBuffers(CommandContext ctx, int[] buffers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDrawBuffers(buffers);
    }
    
    @Override
    public void blendFunc(CommandContext ctx, int sfactor, int dfactor) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glBlendFunc(sfactor, dfactor);
    }
    
    @Override
    public int getInteger(CommandContext ctx, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetInteger(pname);
    }
    
    @Override
    public void setUniform3f(CommandContext ctx, int location, float v0, float v1, float v2) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform3f(location, v0, v1, v2);
    }
    
    @Override
    public void setClearColor(CommandContext ctx, float r, float g, float b, float a) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glClearColor(r, g, b, a);
    }
    
    @Override
    public void setClearDepth(CommandContext ctx, double depth) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glClearDepth(depth);
    }
    
    @Override
    public void setViewport(CommandContext ctx, int x, int y, int width, int height) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glViewport(x, y, width, height);
    }
    
    @Override
    public void setPolygonMode(CommandContext ctx, int face, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glPolygonMode(face, mode);
    }
    
    @Override
    public void setPolygonOffset(CommandContext ctx, float factor, float units) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glPolygonOffset(factor, units);
    }
    
    @Override
    public void setLogicOp(CommandContext ctx, int opcode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glLogicOp(opcode);
    }
    
    @Override
    public int createFramebuffer(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    @Override
    public int getError(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetError();
    }
    
    
    @Override
    public void uploadTexture2D(CommandContext ctx, int target, int level, int internalFormat, int width, int height, 
                                 int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }
    
    @Override
    public void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                         int width, int height, int format, int type, long pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    @Override
    public void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                         int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    
    
    
    @Override
    public int createBuffer(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL15.glGenBuffers();
    }
    
    @Override
    public void deleteBuffer(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glDeleteBuffers(buffer);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, java.nio.ByteBuffer data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, data, usage);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, long size, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, size, usage);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, float[] data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, data, usage);
    }
    
    @Override
    public void bufferData(CommandContext ctx, int target, int[] data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferData(target, data, usage);
    }
    
    
    
    
    
    @Override
    public void bufferSubData(CommandContext ctx, int target, long offset, java.nio.ByteBuffer data) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBufferSubData(target, offset, data);
    }
    
    @Override
    public void bufferStorage(CommandContext ctx, int target, long size, int flags) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL44.glBufferStorage(target, size, flags);
    }
    
    @Override
    public void bufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL44.glBufferStorage(target, data, flags);
    }
    
    @Override
    public void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Override
    public void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30.glFlushMappedBufferRange(target, offset, length);
    }
    
    
    @Override
    public int createVertexArray(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL30.glGenVertexArrays();
    }
    
    @Override
    public void bindVertexArray(CommandContext ctx, int vao) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBindVertexArray(vao);
    }
    
    
    
    @Override
    public java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, long offset, long length, int access) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL30.glMapBufferRange(target, offset, length, access);
    }
    
    @Override
    public void unmapBuffer(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glUnmapBuffer(target);
    }
    
    
    
    @Override
    public void deleteFramebuffer(CommandContext ctx, int fbo) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glDeleteFramebuffers(fbo);
    }
    
    @Override
    public void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, 
                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    
    
    @Override
    public int createShader(CommandContext ctx, int shaderType) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glCreateShader(shaderType);
    }
    
    @Override
    public void compileShader(CommandContext ctx, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glCompileShader(shader);
    }
    
    @Override
    public int createShaderProgram(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glCreateProgram();
    }
    
    
    @Override
    public void deleteShader(CommandContext ctx, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDeleteShader(shader);
    }
    
    
    
    
    @Override
    public void deleteProgram(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDeleteProgram(program);
    }
    
    
    @Override
    public void attachShader(CommandContext ctx, int program, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glAttachShader(program, shader);
    }
    
    @Override
    public void detachShader(CommandContext ctx, int program, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDetachShader(program, shader);
    }
    
    @Override
    public void linkProgram(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glLinkProgram(program);
    }
    
    @Override
    public int getProgramParameter(CommandContext ctx, int program, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetProgrami(program, pname);
    }
    
    @Override
    public int getShaderParameter(CommandContext ctx, int shader, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetShaderi(shader, pname);
    }
    
    @Override
    public String getProgramInfoLog(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetProgramInfoLog(program);
    }
    
    
    
    
    
    
    @Override
    public String getShaderInfoLog(CommandContext ctx, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetShaderInfoLog(shader);
    }
    
    @Override
    public int getUniformLocation(CommandContext ctx, int program, CharSequence name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetUniformLocation(program, name);
    }
    
    @Override
    public int getAttributeLocation(CommandContext ctx, int program, CharSequence name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL20.glGetAttribLocation(program, name);
    }
    
    @Override
    public void setUniform1i(CommandContext ctx, int location, int value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform1i(location, value);
    }
    
    @Override
    public void setUniform1f(CommandContext ctx, int location, float value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform1f(location, value);
    }
    
    @Override
    public void setUniform2f(CommandContext ctx, int location, float v0, float v1) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform2f(location, v0, v1);
    }
    
    @Override
    public void setUniform2i(CommandContext ctx, int location, int v0, int v1) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform2i(location, v0, v1);
    }
    
    @Override
    public void setUniform3i(CommandContext ctx, int location, int v0, int v1, int v2) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform3i(location, v0, v1, v2);
    }
    
    @Override
    public void setUniform4f(CommandContext ctx, int location, float v0, float v1, float v2, float v3) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform4f(location, v0, v1, v2, v3);
    }
    
    @Override
    public void setUniform4i(CommandContext ctx, int location, int v0, int v1, int v2, int v3) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniform4i(location, v0, v1, v2, v3);
    }
    
    @Override
    public void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniformMatrix3fv(location, transpose, matrix);
    }
    
    @Override
    public void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniformMatrix3fv(location, transpose, matrix);
    }
    
    @Override
    public void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Override
    public void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Override
    public void setVertexAttribPointer(CommandContext ctx, int index, int size, int type, boolean normalized, int stride, long pointer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void enableVertexAttribArray(CommandContext ctx, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glEnableVertexAttribArray(index);
    }
    
    @Override
    public void bindVertexBuffer(CommandContext ctx, int bindingindex, int buffer, long offset, int stride) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL43.glBindVertexBuffer(bindingindex, buffer, offset, stride);
    }
    
    
    @Override
    public void setVertexAttribIPointer(CommandContext ctx, int index, int size, int type, int stride, long pointer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    
    
    @Override
    public void disableVertexAttribArray(CommandContext ctx, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDisableVertexAttribArray(index);
    }
    
    
    @Override
    public void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33.glVertexAttribDivisor(index, divisor);
    }
    
    
    
    
    @Override
    public void setAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glBindAttribLocation(program, index, name);
    }
    
    
    @Override
    public long createFenceSync(CommandContext ctx, int condition, int flags) {
        return org.lwjgl.opengl.GL32.glFenceSync(condition, flags);
    }
    
    @Override
    public void destroySync(CommandContext ctx, long sync) {
        org.lwjgl.opengl.GL32.glDeleteSync(sync);
    }
    
    @Override
    public int waitForSync(CommandContext ctx, long sync, int flags, long timeout) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32.glClientWaitSync(sync, flags, timeout);
    }
    
    @Override
    public boolean isTexture(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glIsTexture(texture);
    }
    
    @Override
    public int getTextureLevelParameter(CommandContext ctx, int target, int level, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }
    
    @Override
    public void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, stringCount, pointerBufferAddress, lengthsPointer);
    }

    @Override
    public void uploadShaderSource(CommandContext ctx, int shader, String source) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        byte[] bytes = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length + 1);
        buf.put(bytes);
        buf.put((byte) 0);
        buf.flip();
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer ptr = stack.mallocPointer(1);
            ptr.put(buf);
            org.lwjgl.opengl.GL20C.nglShaderSource(shader, 1, ptr.address0(), 0L);
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(buf);
        }
    }
    
    @Override
    public void uniformBlockBinding(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Override
    public String retrieveActiveUniformBlockName(CommandContext ctx, int program, int uniformBlockIndex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL31.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    @Override
    public int generateQueryObject(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGenQueries();
    }
    
    @Override
    public void initiateQuery(CommandContext ctx, int target, int id) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glBeginQuery(target, id);
    }
    
    @Override
    public void concludeQuery(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glEndQuery(target);
    }
    
    @Override
    public void disposeQueryObject(CommandContext ctx, int id) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glDeleteQueries(id);
    }
    
    @Override
    public int retrieveQueryObjectInt(CommandContext ctx, int id, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGetQueryObjecti(id, pname);
    }
    
    @Override
    public long retrieveQueryObjectInt64(CommandContext ctx, int id, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBTimerQuery.glGetQueryObjecti64(id, pname);
    }
    
    @Override
    public void labelDebugObject(CommandContext ctx, int identifier, int name, String label) {
        org.lwjgl.opengl.KHRDebug.glObjectLabel(identifier, name, label);
    }
    
    @Override
    public void enterDebugGroup(CommandContext ctx, int source, int id, CharSequence message) {
        org.lwjgl.opengl.KHRDebug.glPushDebugGroup(source, id, message);
    }
    
    @Override
    public void exitDebugGroup(CommandContext ctx) {
        org.lwjgl.opengl.KHRDebug.glPopDebugGroup();
    }
    
    @Override
    public void debugMessageControl(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.GL43C.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Override
    public void debugMessageControlKHR(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.KHRDebug.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Override
    public void debugMessageControlARB(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageControlARB(source, type, severity, ids, enabled);
    }
    
    @Override
    public void debugMessageEnableAMD(CommandContext ctx, int category, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageEnableAMD(category, severity, ids, enabled);
    }
    
    @Override
    public void labelObjectExt(CommandContext ctx, int type, int object, String label) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.EXTDebugLabel.glLabelObjectEXT(type, object, label);
    }
    
    @Override
    public boolean supportsKhrDebug() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_KHR_debug;
    }
    
    @Override
    public boolean supportsArbDebugOutput() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_debug_output;
    }
    
    @Override
    public void setupKhrDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        org.lwjgl.opengl.GL11.glEnable(37600); // GL_DEBUG_OUTPUT
        if (synchronous) {
            org.lwjgl.opengl.GL11.glEnable(33346); // GL_DEBUG_OUTPUT_SYNCHRONOUS
        }
        
        // Configure message filtering based on verbosity
        java.util.List<Integer> levels = java.util.Arrays.asList(37190, 37191, 37192, 33387);
        for (int i = 0; i < levels.size(); i++) {
            boolean shouldEnable = i < verbosityLevel;
            org.lwjgl.opengl.KHRDebug.glDebugMessageControl(4352, 4352, levels.get(i), (int[])null, shouldEnable);
        }
        
        // Install callback
        org.lwjgl.opengl.GLDebugMessageCallback callback = org.lwjgl.opengl.GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String msg = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                messageHandler.accept(msg);
            }
        );
        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(
            net.blaze3d.platform.GLX.make(callback, net.blaze3d.platform.DebugMemoryUntracker::untrack), 
            0L
        );
    }
    
    @Override
    public void setupArbDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        if (synchronous) {
            org.lwjgl.opengl.GL11.glEnable(33346); // GL_DEBUG_OUTPUT_SYNCHRONOUS
        }
        
        // Configure message filtering based on verbosity  
        java.util.List<Integer> levels = java.util.Arrays.asList(37190, 37191, 37192);
        for (int i = 0; i < levels.size(); i++) {
            boolean shouldEnable = i < verbosityLevel;
            org.lwjgl.opengl.ARBDebugOutput.glDebugMessageControlARB(4352, 4352, levels.get(i), (int[])null, shouldEnable);
        }
        
        // Install callback
        org.lwjgl.opengl.GLDebugMessageARBCallback callback = org.lwjgl.opengl.GLDebugMessageARBCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String msg = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                messageHandler.accept(msg);
            }
        );
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB(
            net.blaze3d.platform.GLX.make(callback, net.blaze3d.platform.DebugMemoryUntracker::untrack),
            0L
        );
    }
    
    @Override
    public boolean hasBufferStorageExtension() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_buffer_storage;
    }
    
    @Override
    public boolean hasVertexAttribBindingExtension() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_vertex_attrib_binding;
    }
    
    @Override
    public void setDrawBuffer(CommandContext ctx, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glDrawBuffer(mode);
    }
    
    @Override
    public void drawIndexedInstancedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    @Override
    public void drawIndexedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int baseVertex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }
    
    @Override
    public void drawIndexedInstanced(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }
    
    @Override
    public void drawArraysInstanced(CommandContext ctx, int mode, int first, int count, int instanceCount) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
    
    @Override
    public void bindUniformBufferRange(CommandContext ctx, int target, int index, int buffer, long offset, long size) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32.glBindBufferRange(target, index, buffer, offset, size);
    }
    
    @Override
    public void texBuffer(CommandContext ctx, int target, int internalFormat, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL31.glTexBuffer(target, internalFormat, buffer);
    }
    
    
    @Override
    public void setUniform2fv(CommandContext ctx, int location, float[] value) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL20C.glUniform2fv(location, value);
    }
    
    @Override
    public void setUniform3fv(CommandContext ctx, int location, float[] value) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL20C.glUniform3fv(location, value);
    }
    
    @Override
    public void setUniform4fv(CommandContext ctx, int location, float[] value) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL20C.glUniform4fv(location, value);
    }
    
    @Override
    public void bindUniformBufferBase(CommandContext ctx, int bindingPoint, int bufferId) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL32C.glBindBufferBase(org.lwjgl.opengl.GL32C.GL_UNIFORM_BUFFER, bindingPoint, bufferId);
    }
    
    @Override
    public void bindFragDataLocation(CommandContext ctx, int program, int colorNumber, CharSequence name) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, colorNumber, name);
    }
    
    @Override
    public int getSynci(CommandContext ctx, long sync, int pname, java.nio.IntBuffer length) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        return org.lwjgl.opengl.GL32C.glGetSynci(sync, pname, length);
    }
    
    @Override
    public void deleteVertexArrays(CommandContext ctx, int vertexArray) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL30C.glDeleteVertexArrays(vertexArray);
    }
    
    @Override
    public void multiDrawElementsBaseVertex(CommandContext ctx, int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        if (!ctx.isImmediate()) throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    /**
     * Helper method to convert LWJGL GLCapabilities to Vulkanic GraphicsCapabilities.
     * This extracts all capability flags from the OpenGL-specific object.
     */
    private GraphicsCapabilities convertCapabilities(org.lwjgl.opengl.GLCapabilities glCaps) {
        return new GraphicsCapabilities(
            // OpenGL version flags
            glCaps.OpenGL11, glCaps.OpenGL12, glCaps.OpenGL13, glCaps.OpenGL14, glCaps.OpenGL15,
            glCaps.OpenGL20, glCaps.OpenGL21,
            glCaps.OpenGL30, glCaps.OpenGL31, glCaps.OpenGL32, glCaps.OpenGL33,
            glCaps.OpenGL40, glCaps.OpenGL41, glCaps.OpenGL42, glCaps.OpenGL43, glCaps.OpenGL44, glCaps.OpenGL45, glCaps.OpenGL46,
            // Extension flags - using camelCase for consistency
            glCaps.GL_ARB_buffer_storage, glCaps.GL_ARB_vertex_attrib_binding, glCaps.GL_ARB_direct_state_access,
            glCaps.GL_ARB_debug_output, glCaps.GL_KHR_debug, glCaps.GL_AMD_debug_output,
            glCaps.GL_KHR_no_error, glCaps.GL_EXT_debug_label, glCaps.GL_ARB_timer_query,
            glCaps.GL_KHR_parallel_shader_compile, glCaps.GL_ARB_parallel_shader_compile,
            glCaps.GL_ARB_multi_bind, glCaps.GL_ARB_tessellation_shader,
            glCaps.GL_ARB_shader_storage_buffer_object, glCaps.GL_ARB_shader_image_load_store,
            glCaps.GL_EXT_shader_image_load_store, glCaps.GL_ARB_draw_buffers_blend,
            glCaps.GL_NVX_gpu_memory_info
        );
    }
    
    @Override
    public GraphicsCapabilities obtainGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.getCapabilities());
    }
    
    @Override
    public GraphicsCapabilities initializeGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.createCapabilities());
    }
    
    @Override
    public boolean checkFunctionAvailable(String functionName) {
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        try {
            java.lang.reflect.Field field = caps.getClass().getField(functionName);
            long address = field.getLong(caps);
            return address != org.lwjgl.system.MemoryUtil.NULL;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    @Override
    public void clearTexImage(CommandContext ctx, int texture, int level, int format, int type, int[] data) {
        org.lwjgl.opengl.ARBClearTexture.glClearTexImage(texture, level, format, type, data);
    }
    
    
    @Override
    public void setMaxShaderCompilerThreads(int count) {
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.GL_KHR_parallel_shader_compile) {
            org.lwjgl.opengl.KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(count);
        } else if (caps.GL_ARB_parallel_shader_compile) {
            org.lwjgl.opengl.ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(count);
        }
    }
    
    @Override
    public GraphicsCapabilities getGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.getCapabilities());
    }
    
    
    
    
    // Additional methods for IrisRenderSystem
    
    @Override
    public void getIntegerv(CommandContext ctx, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetIntegerv(pname, params);
    }
    
    
    @Override
    public void getFloatv(CommandContext ctx, int pname, float[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetFloatv(pname, params);
    }
    
    
    @Override
    public void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    
    
    @Override
    public void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    
    
    
    @Override
    public void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    
    
    
    
    
    
    
    
    
    
    @Override
    public void texParameterf(CommandContext ctx, int target, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glTexParameterf(target, pname, param);
    }
    
    @Override
    public void texParameteri(CommandContext ctx, int target, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glTexParameteri(target, pname, param);
    }
    
    
    
    
    
    
    
    
    
    @Override
    public String getActiveUniform(CommandContext ctx, int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGetActiveUniform(program, index, size, type, name);
    }
    
    
    @Override
    public void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Override
    public void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, long pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.nglReadPixels(x, y, width, height, format, type, pixels);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    @Override
    public void setVertexAttrib4f(CommandContext ctx, int index, float v0, float v1, float v2, float v3) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    
    
    
    
    
    @Override
    public void bindImageTexture(CommandContext ctx, int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.OpenGL42 || caps.GL_ARB_shader_image_load_store) {
            org.lwjgl.opengl.GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
        } else {
            org.lwjgl.opengl.EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
        }
    }
    
    
    
    @Override
    public void createBuffers(CommandContext ctx, int[] buffers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glGenBuffers(buffers);
    }
    
    
    
    @Override
    public void getProgramiv(CommandContext ctx, int program, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetProgramiv(program, pname, params);
    }
    
    
    
    @Override
    public void memoryBarrier(CommandContext ctx, int barriers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glMemoryBarrier(barriers);
    }
    
    
    
    
    
    @Override
    public void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    
    
    
    @Override
    public int createSampler(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL33C.glGenSamplers();
    }
    
    
    @Override
    public void deleteSampler(CommandContext ctx, int sampler) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glDeleteSamplers(sampler);
    }
    
    
    
    @Override
    public void bindSamplers(CommandContext ctx, int first, int[] samplers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glBindSamplers(first, samplers);
    }
    
    
    @Override
    public void setSamplerParameteri(CommandContext ctx, int sampler, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, pname, param);
    }
    
    
    @Override
    public void setSamplerParameterf(CommandContext ctx, int sampler, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, pname, param);
    }
    
    
    @Override
    public void setSamplerParameteriv(CommandContext ctx, int sampler, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameteriv(sampler, pname, params);
    }
    
    
    
    
    
    
    @Override
    public void dispatchComputeIndirect(CommandContext ctx, long offset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glDispatchComputeIndirect(offset);
    }
    
    
    
    @Override
    public String getString(CommandContext ctx, int name, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL46C.glGetStringi(name, index);
    }
    
    @Override
    public String getString(CommandContext ctx, int name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL11C.glGetString(name);
    }
    
    
    @Override
    public void copyImageSubData(CommandContext ctx, int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, 
                                 int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, 
                                 int width, int height, int depth) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL46C.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, 
                                                    dstName, dstTarget, dstLevel, dstX, dstY, dstZ, 
                                                    width, height, depth);
    }
    
    
    @Override
    public int checkFramebufferStatus(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL46C.glCheckFramebufferStatus(target);
    }
    
    
    
    
    
    
    @Override
    public void generateMipmap(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGenerateMipmap(target);
    }
    
    
    
    // DSA methods
    
    @Override
    public void generateTextureMipmapDSA(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glGenerateTextureMipmap(texture);
    }
    
    
    @Override
    public void textureParameteri(CommandContext ctx, int texture, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }
    
    
    @Override
    public void textureParameterf(CommandContext ctx, int texture, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }
    
    
    @Override
    public void textureParameteriv(CommandContext ctx, int texture, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteriv(texture, pname, params);
    }
    
    
    @Override
    public void namedFramebufferReadBuffer(CommandContext ctx, int framebuffer, int mode) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, mode);
    }
    
    
    @Override
    public void namedFramebufferDrawBuffers(CommandContext ctx, int framebuffer, int[] bufs) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }
    
    
    @Override
    public void clearNamedFramebufferfv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, float[] value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, value);
    }
    
    
    @Override
    public void clearNamedFramebufferiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, value);
    }
    
    
    @Override
    public void clearNamedFramebufferuiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, value);
    }
    
    
    @Override
    public int getFramebufferAttachmentParameteri(CommandContext ctx, int target, int attachment, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL30.glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }
    
    @Override
    public int getTextureParameteri(CommandContext ctx, int texture, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
    }
    
    
    @Override
    public void copyTextureSubImage2D(CommandContext ctx, int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    
    @Override
    public void bindTextureUnit(CommandContext ctx, int unit, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glBindTextureUnit(unit, texture);
    }
    
    
    @Override
    public int createBuffers(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers();
    }
    
    
    @Override
    public void namedBufferData(CommandContext ctx, int buffer, float[] data, int usage) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glNamedBufferData(buffer, data, usage);
    }
    
    
    @Override
    public void blitNamedFramebuffer(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    
    @Override
    public void namedFramebufferTexture(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    
    @Override
    public int createFramebuffers(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    
    @Override
    public int createTextures(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateTextures(target);
    }
    
    
    // Additional rendering operations
    
    
    
    
    
    
    
    
    // High-level debug callback wrapper implementations
    @Override
    public void setupDebugMessageCallback(VulkanicAPI.DebugMessageCallback callback) {
        org.lwjgl.opengl.GLDebugMessageCallback proc = org.lwjgl.opengl.GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                callback.invoke(source, type, id, severity, messageStr);
            }
        );
        org.lwjgl.opengl.GL43C.glDebugMessageCallback(proc, 0L);
    }
    
    @Override
    public void setupDebugMessageCallbackKHR(VulkanicAPI.DebugMessageCallback callback) {
        org.lwjgl.opengl.GLDebugMessageCallback proc = org.lwjgl.opengl.GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message);
                callback.invoke(source, type, id, severity, messageStr);
            }
        );
        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(proc, 0L);
    }
    
    @Override
    public void setupDebugMessageCallbackARB(VulkanicAPI.DebugMessageCallbackARB callback) {
        org.lwjgl.opengl.GLDebugMessageARBCallback proc = org.lwjgl.opengl.GLDebugMessageARBCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageARBCallback.getMessage(length, message);
                callback.invoke(source, type, id, severity, messageStr);
            }
        );
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB(proc, 0L);
    }
    
    @Override
    public void setupDebugMessageCallbackAMD(VulkanicAPI.DebugMessageCallbackAMD callback) {
        org.lwjgl.opengl.GLDebugMessageAMDCallback proc = org.lwjgl.opengl.GLDebugMessageAMDCallback.create(
            (id, category, severity, length, message, userParam) -> {
                String messageStr = org.lwjgl.opengl.GLDebugMessageAMDCallback.getMessage(length, message);
                callback.invoke(id, category, severity, messageStr);
            }
        );
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageCallbackAMD(proc, 0L);
    }
    
    @Override
    public void clearDebugMessageCallback() {
        org.lwjgl.opengl.GL43C.glDebugMessageCallback(null, 0L);
    }
    
    @Override
    public void clearDebugMessageCallbackKHR() {
        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(null, 0L);
    }
    
    @Override
    public void clearDebugMessageCallbackARB() {
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB(null, 0L);
    }
    
    @Override
    public void clearDebugMessageCallbackAMD() {
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageCallbackAMD(null, 0L);
    }
    
    // GL43+ vertex attribute methods
    
    
    @Override
    public void setVertexAttribFormat(CommandContext ctx, int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }
    
    @Override
    public void setVertexAttribIFormat(CommandContext ctx, int attribindex, int size, int type, int relativeoffset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
    }
    
    @Override
    public void setVertexAttribBinding(CommandContext ctx, int attribindex, int bindingindex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glVertexAttribBinding(attribindex, bindingindex);
    }
    
    // VAO methods
    
    
    
    
    // GL context capabilities
    
    @Override
    public Object getGLCapabilities() {
        return org.lwjgl.opengl.GL.getCapabilities();
    }
    
    @Override
    public void setupDebugMessageCallback(java.io.PrintStream stream) {
        org.lwjgl.opengl.GLUtil.setupDebugMessageCallback(stream);
    }
    
    // Capability checking methods
    
    @Override
    public boolean checkOpenGL32Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL32;
    }
    
    @Override
    public boolean checkOpenGL33Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL33;
    }
    
    @Override
    public boolean checkARBInstancedArraysSupport() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.GL_ARB_instanced_arrays;
    }
    
    @Override
    public long getNamedBufferDataPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glNamedBufferData;
    }
    
    @Override
    public long getBufferStoragePointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBufferStorage;
    }
    
    @Override
    public long getBindVertexBufferPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBindVertexBuffer;
    }
    
    
    // Additional GL query and state methods
    
    
    @Override
    public boolean isFramebuffer(CommandContext ctx, int framebuffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL30.glIsFramebuffer(framebuffer);
    }
    
    
    @Override
    public boolean isVertexArray(CommandContext ctx, int array) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL30.glIsVertexArray(array);
    }
    
    @Override
    public boolean isProgram(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL20.glIsProgram(program);
    }
    
    // Additional GL state methods
    
    @Override
    public void setBlendEquationSeparate(CommandContext ctx, int modeRGB, int modeAlpha) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }
    
    @Override
    public void setStencilFunc(CommandContext ctx, int func, int ref, int mask) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glStencilFunc(func, ref, mask);
    }
    
    
    // Additional texture methods
    
    
    @Override
    public void dispatchCompute(CommandContext ctx, int workX, int workY, int workZ) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43.glDispatchCompute(workX, workY, workZ);
    }
    
    @Override
    public boolean isBuffer(CommandContext ctx, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL15.glIsBuffer(buffer);
    }
    
    @Override
    public boolean isEnabled(CommandContext ctx, int cap) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL11.glIsEnabled(cap);
    }
    
    @Override
    public void texParameteriv(CommandContext ctx, int target, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL11.glTexParameteriv(target, pname, params);
    }
    
    @Override
    public int getUniformBlockIndex(CommandContext ctx, int program, String uniformBlockName) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL31.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Override
    public int getMaxImageUnits(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.OpenGL42 || caps.GL_ARB_shader_image_load_store) {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL42C.GL_MAX_IMAGE_UNITS);
        } else if (caps.GL_EXT_shader_image_load_store) {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.EXTShaderImageLoadStore.GL_MAX_IMAGE_UNITS_EXT);
        } else {
            return 0;
        }
    }
    
    @Override
    public void clearBufferSubData(CommandContext ctx, int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glClearBufferSubData(target, internalformat, offset, size, format, type, data);
    }
    
    @Override
    public void clearBufferfv(CommandContext ctx, int buffer, int drawbuffer, float[] values) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glClearBufferfv(buffer, drawbuffer, values);
    }
    
    @Override
    public void clearBufferiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glClearBufferiv(buffer, drawbuffer, values);
    }
    
    @Override
    public void clearBufferuiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glClearBufferuiv(buffer, drawbuffer, values);
    }
}
