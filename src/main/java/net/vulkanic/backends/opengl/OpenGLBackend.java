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
    
    @Deprecated
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
    
    @Deprecated
    @Override
    public void generateMipmap(int target) {
        generateTextureMipmap(VulkanicAPI.getImmediateContext(), target);
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
    
    @Deprecated
    @Override
    public void bindTexture(int target, int textureId) {
        bindTexture(VulkanicAPI.getImmediateContext(), target, textureId);
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
    
    // Direct State Access buffer operations
    @Deprecated
    @Override
    public int createBufferDSA() {
        return createBuffer(VulkanicAPI.getImmediateContext());
    }
    
    @Deprecated
    @Override
    public void namedBufferDataDSA(int buffer, long size, int usage) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferData(VulkanicAPI.getImmediateContext(), GL15.GL_ARRAY_BUFFER, size, usage);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferData(VulkanicAPI.getImmediateContext(), GL15.GL_ARRAY_BUFFER, data, usage);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferSubData(VulkanicAPI.getImmediateContext(), GL15.GL_ARRAY_BUFFER, offset, data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public void namedBufferStorageDSA(int buffer, long size, int flags) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferStorage(VulkanicAPI.getImmediateContext(), GL15.GL_ARRAY_BUFFER, size, flags);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags) {
        // DSA methods work on named buffers, not targets. For migration, we bind then use the target-based API
        int prevBinding = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        bufferStorage(VulkanicAPI.getImmediateContext(), GL15.GL_ARRAY_BUFFER, data, flags);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public java.nio.ByteBuffer mapNamedBufferRangeDSA(int buffer, long offset, long length, int access) {
        // DSA delegation pattern: bind → call CommandContext version → restore
        int prevBinding = org.lwjgl.opengl.GL15.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, buffer);
        java.nio.ByteBuffer result = mapBuffer(VulkanicAPI.getImmediateContext(), org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, offset, length, access);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, prevBinding);
        return result;
    }
    
    @Deprecated
    @Override
    public void unmapNamedBufferDSA(int buffer) {
        // DSA delegation pattern: bind → call CommandContext version → restore
        int prevBinding = org.lwjgl.opengl.GL15.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, buffer);
        unmapBuffer(VulkanicAPI.getImmediateContext(), org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length) {
        // DSA delegation pattern: bind → call CommandContext version → restore
        int prevBinding = org.lwjgl.opengl.GL15.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, buffer);
        flushMappedBufferRange(VulkanicAPI.getImmediateContext(), org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, offset, length);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        // Use direct ARB DSA call since binding would be complex
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // Direct State Access framebuffer operations
    @Deprecated
    @Override
    public void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level) {
        // DSA delegation pattern: bind framebuffer → call CommandContext version → restore
        int prevBinding = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, framebuffer);
        // For DSA, we use GL_TEXTURE_2D as the default textarget since DSA doesn't specify it
        framebufferTexture(VulkanicAPI.getImmediateContext(), org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, attachment, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texture, level);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, prevBinding);
    }
    
    @Deprecated
    @Override
    public void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
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
    
    @Deprecated
    @Override
    public int checkForErrors() {
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
    
    @Deprecated
    @Override
    public void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        uploadTexture2D(VulkanicAPI.getImmediateContext(), tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    @Deprecated
    @Override
    public void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix) {
        uploadTexture2DSubImage(VulkanicAPI.getImmediateContext(), tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Deprecated
    @Override
    public void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        uploadTexture2DSubImage(VulkanicAPI.getImmediateContext(), tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
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
    
    @Deprecated
    @Override
    public int allocateBufferObject() {
        return createBuffer(VulkanicAPI.getImmediateContext());
    }
    
    @Deprecated
    @Override
    public void releaseBufferObject(int buf) {
        deleteBuffer(VulkanicAPI.getImmediateContext(), buf);
    }
    
    @Deprecated
    @Override
    public void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg) {
        bufferData(VulkanicAPI.getImmediateContext(), tgt, dat, usg);
    }
    
    @Deprecated
    @Override
    public void fillBufferWithSize(int tgt, long sz, int usg) {
        bufferData(VulkanicAPI.getImmediateContext(), tgt, sz, usg);
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
    
    @Deprecated
    @Override
    public void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat) {
        bufferSubData(VulkanicAPI.getImmediateContext(), tgt, off, dat);
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
    
    @Deprecated
    @Override
    public int createVertexArrayObject() {
        return createVertexArray(VulkanicAPI.getImmediateContext());
    }
    
    @Deprecated
    @Override
    public void selectVertexArray(int vao) {
        bindVertexArray(VulkanicAPI.getImmediateContext(), vao);
    }
    
    @Override
    public java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, long offset, long length, int access) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL30.glMapBufferRange(target, offset, length, access);
    }
    
    @Deprecated
    @Override
    public java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc) {
        return mapBuffer(VulkanicAPI.getImmediateContext(), tgt, off, len, acc);
    }
    
    @Override
    public void unmapBuffer(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glUnmapBuffer(target);
    }
    
    @Deprecated
    @Override
    public void unmapBufferData(int tgt) {
        unmapBuffer(VulkanicAPI.getImmediateContext(), tgt);
    }
    
    @Deprecated
    @Override
    public int generateFramebufferObject() {
        return createFramebuffer(VulkanicAPI.getImmediateContext());
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
    
    @Deprecated
    @Override
    public void destroyFramebufferObject(int fbo) {
        deleteFramebuffer(VulkanicAPI.getImmediateContext(), fbo);
    }
    
    @Deprecated
    @Override
    public void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        blitFramebuffer(VulkanicAPI.getImmediateContext(), srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
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
    
    @Deprecated
    @Override
    public int constructShaderObject(int shaderType) {
        return createShader(VulkanicAPI.getImmediateContext(), shaderType);
    }
    
    @Override
    public void deleteShader(CommandContext ctx, int shader) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDeleteShader(shader);
    }
    
    @Deprecated
    @Override
    public void disposeShaderObject(int shader) {
        deleteShader(VulkanicAPI.getImmediateContext(), shader);
    }
    
    @Deprecated
    @Override
    public void compileShaderSource(int shader) {
        compileShader(VulkanicAPI.getImmediateContext(), shader);
    }
    
    @Deprecated
    @Override
    public int constructProgramObject() {
        return createShaderProgram(VulkanicAPI.getImmediateContext());
    }
    
    @Override
    public void deleteProgram(CommandContext ctx, int program) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDeleteProgram(program);
    }
    
    @Deprecated
    @Override
    public void disposeProgramObject(int program) {
        deleteProgram(VulkanicAPI.getImmediateContext(), program);
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
    
    @Deprecated
    @Override
    public void linkProgramBinary(int program) {
        linkProgram(VulkanicAPI.getImmediateContext(), program);
    }
    
    @Deprecated
    @Override
    public void attachShaderToProgram(int program, int shader) {
        attachShader(VulkanicAPI.getImmediateContext(), program, shader);
    }
    
    @Deprecated
    @Override
    public int queryProgramParameter(int program, int pname) {
        return getProgramParameter(VulkanicAPI.getImmediateContext(), program, pname);
    }
    
    @Deprecated
    @Override
    public int queryShaderParameter(int shader, int pname) {
        return getShaderParameter(VulkanicAPI.getImmediateContext(), shader, pname);
    }
    
    @Deprecated
    @Override
    public String retrieveProgramInfoLog(int program) {
        return getProgramInfoLog(VulkanicAPI.getImmediateContext(), program);
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
    
    @Deprecated
    @Override
    public void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer) {
        setVertexAttribPointer(VulkanicAPI.getImmediateContext(), index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void setVertexAttribIPointer(CommandContext ctx, int index, int size, int type, int stride, long pointer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Deprecated
    @Override
    public void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer) {
        setVertexAttribIPointer(VulkanicAPI.getImmediateContext(), index, size, type, stride, pointer);
    }
    
    @Deprecated
    @Override
    public void activateVertexAttribute(int index) {
        enableVertexAttribArray(VulkanicAPI.getImmediateContext(), index);
    }
    
    @Override
    public void disableVertexAttribArray(CommandContext ctx, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glDisableVertexAttribArray(index);
    }
    
    @Deprecated
    @Override
    public void deactivateVertexAttribute(int index) {
        disableVertexAttribArray(VulkanicAPI.getImmediateContext(), index);
    }
    
    @Override
    public void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33.glVertexAttribDivisor(index, divisor);
    }
    
    @Deprecated
    @Override
    public void setVertexAttribDivisor(int index, int divisor) {
        setVertexAttribDivisor(VulkanicAPI.getImmediateContext(), index, divisor);
    }
    
    @Deprecated
    @Override
    public String retrieveShaderInfoLog(int shader) {
        return getShaderInfoLog(VulkanicAPI.getImmediateContext(), shader);
    }
    
    @Deprecated
    @Override
    public int locateUniformVariable(int program, CharSequence name) {
        return getUniformLocation(VulkanicAPI.getImmediateContext(), program, name);
    }
    
    @Deprecated
    @Override
    public void assignUniformInteger(int location, int value) {
        setUniform1i(VulkanicAPI.getImmediateContext(), location, value);
    }
    
    @Override
    public void setAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL20.glBindAttribLocation(program, index, name);
    }
    
    @Deprecated
    @Override
    public void bindAttributeLocation(int program, int index, CharSequence name) {
        setAttributeLocation(VulkanicAPI.getImmediateContext(), program, index, name);
    }
    
    @Deprecated
    @Override
    public long createFenceSync(int condition, int flags) {
        return org.lwjgl.opengl.GL32.glFenceSync(condition, flags);
    }
    
    @Deprecated
    @Override
    public int waitForSync(long sync, int flags, long timeout) {
        return org.lwjgl.opengl.GL32.glClientWaitSync(sync, flags, timeout);
    }
    
    @Deprecated
    @Override
    public void destroySync(long sync) {
        org.lwjgl.opengl.GL32.glDeleteSync(sync);
    }
    
    @Deprecated
    @Override
    public int queryIntegerState(int pname) {
        return GL11.glGetInteger(pname);
    }
    
    @Deprecated
    @Override
    public String queryStringInfo(int name) {
        return GL11.glGetString(name);
    }
    
    @Deprecated
    @Override
    public int pollErrorCode() {
        return GL11.glGetError();
    }
    
    @Deprecated
    @Override
    public void readFramebufferPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Override
    public boolean isTexture(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL11.glIsTexture(texture);
    }
    
    @Deprecated
    @Override
    public int queryTextureLevelParameter(int target, int level, int pname) {
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }
    
    @Deprecated
    @Override
    public void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, stringCount, pointerBufferAddress, lengthsPointer);
    }
    
    @Override
    public void uniformBlockBinding(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Deprecated
    @Override
    public int locateUniformBlock(int program, String uniformBlockName) {
        return org.lwjgl.opengl.GL31.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Deprecated
    @Override
    public void bindUniformBlock(int program, int uniformBlockIndex, int uniformBlockBinding) {
        org.lwjgl.opengl.GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Deprecated
    @Override
    public String retrieveActiveUniformBlockName(int program, int uniformBlockIndex) {
        return org.lwjgl.opengl.GL31.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    @Deprecated
    @Override
    public int generateQueryObject() {
        return org.lwjgl.opengl.GL32C.glGenQueries();
    }
    
    @Deprecated
    @Override
    public void initiateQuery(int target, int id) {
        org.lwjgl.opengl.GL32C.glBeginQuery(target, id);
    }
    
    @Deprecated
    @Override
    public void concludeQuery(int target) {
        org.lwjgl.opengl.GL32C.glEndQuery(target);
    }
    
    @Deprecated
    @Override
    public void disposeQueryObject(int id) {
        org.lwjgl.opengl.GL32C.glDeleteQueries(id);
    }
    
    @Deprecated
    @Override
    public int retrieveQueryObjectInt(int id, int pname) {
        return org.lwjgl.opengl.GL32C.glGetQueryObjecti(id, pname);
    }
    
    @Deprecated
    @Override
    public long retrieveQueryObjectInt64(int id, int pname) {
        return org.lwjgl.opengl.ARBTimerQuery.glGetQueryObjecti64(id, pname);
    }
    
    @Deprecated
    @Override
    public void labelDebugObject(int identifier, int name, String label) {
        org.lwjgl.opengl.KHRDebug.glObjectLabel(identifier, name, label);
    }
    
    @Deprecated
    @Override
    public void enterDebugGroup(int source, int id, CharSequence message) {
        org.lwjgl.opengl.KHRDebug.glPushDebugGroup(source, id, message);
    }
    
    @Deprecated
    @Override
    public void exitDebugGroup() {
        org.lwjgl.opengl.KHRDebug.glPopDebugGroup();
    }
    
    @Deprecated
    @Override
    public void labelObjectExt(int type, int object, String label) {
        org.lwjgl.opengl.EXTDebugLabel.glLabelObjectEXT(type, object, label);
    }
    
    @Deprecated
    @Override
    public boolean supportsKhrDebug() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_KHR_debug;
    }
    
    @Deprecated
    @Override
    public boolean supportsArbDebugOutput() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_debug_output;
    }
    
    @Deprecated
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
    
    @Deprecated
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
    
    @Deprecated
    @Override
    public boolean hasBufferStorageExtension() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_buffer_storage;
    }
    
    @Deprecated
    @Override
    public boolean hasVertexAttribBindingExtension() {
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_vertex_attrib_binding;
    }
    
    @Deprecated
    @Override
    public void attachVertexBuffer(int bindingIndex, int buffer, long offset, int stride) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glBindVertexBuffer(bindingIndex, buffer, offset, stride);
    }
    
    @Deprecated
    @Override
    public void specifyVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribFormat(attribIndex, size, type, normalized, relativeOffset);
    }
    
    @Deprecated
    @Override
    public void specifyVertexAttribIFormat(int attribIndex, int size, int type, int relativeOffset) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribIFormat(attribIndex, size, type, relativeOffset);
    }
    
    @Deprecated
    @Override
    public void associateVertexAttrib(int attribIndex, int bindingIndex) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribBinding(attribIndex, bindingIndex);
    }
    
    @Deprecated
    @Override
    public void setClearDepthValue(double depth) {
        org.lwjgl.opengl.GL11.glClearDepth(depth);
    }
    
    @Deprecated
    @Override
    public void setClearColorValue(float red, float green, float blue, float alpha) {
        org.lwjgl.opengl.GL11.glClearColor(red, green, blue, alpha);
    }
    
    @Deprecated
    @Override
    public void selectDrawBuffer(int mode) {
        org.lwjgl.opengl.GL11.glDrawBuffer(mode);
    }
    
    @Deprecated
    @Override
    public void renderIndexedInstancedWithBase(int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    @Deprecated
    @Override
    public void renderIndexedWithBase(int mode, int count, int type, long indices, int baseVertex) {
        org.lwjgl.opengl.GL32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }
    
    @Deprecated
    @Override
    public void renderIndexedInstanced(int mode, int count, int type, long indices, int instanceCount) {
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }
    
    @Deprecated
    @Override
    public void renderArraysInstanced(int mode, int first, int count, int instanceCount) {
        org.lwjgl.opengl.GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
    
    @Deprecated
    @Override
    public void attachUniformBufferRange(int target, int index, int buffer, long offset, long size) {
        org.lwjgl.opengl.GL32.glBindBufferRange(target, index, buffer, offset, size);
    }
    
    @Deprecated
    @Override
    public void attachBufferToTexture(int target, int internalFormat, int buffer) {
        org.lwjgl.opengl.GL31.glTexBuffer(target, internalFormat, buffer);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat(int location, float value) {
        org.lwjgl.opengl.GL30C.glUniform1f(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat2(int location, float x, float y) {
        org.lwjgl.opengl.GL30C.glUniform2f(location, x, y);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat2v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform2fv(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat3(int location, float x, float y, float z) {
        org.lwjgl.opengl.GL30C.glUniform3f(location, x, y, z);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat3v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform3fv(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat4(int location, float x, float y, float z, float w) {
        org.lwjgl.opengl.GL30C.glUniform4f(location, x, y, z, w);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat4v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform4fv(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformMatrix4f(int location, java.nio.FloatBuffer matrix) {
        org.lwjgl.opengl.GL30C.glUniformMatrix4fv(location, false, matrix);
    }
    
    @Deprecated
    @Override
    public void bindUniformBufferBase(int bindingPoint, int bufferId) {
        org.lwjgl.opengl.GL32C.glBindBufferBase(org.lwjgl.opengl.GL32C.GL_UNIFORM_BUFFER, bindingPoint, bufferId);
    }
    
    @Deprecated
    @Override
    public void bindFragmentDataLocation(int program, int colorNumber, CharSequence name) {
        org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, colorNumber, name);
    }
    
    @Deprecated
    @Override
    public int querySyncStatus(long sync, int pname, java.nio.IntBuffer length) {
        // glGetSynci returns the sync value and writes to length buffer
        // the number of values returned (should be 1 for single integer queries)
        return org.lwjgl.opengl.GL32C.glGetSynci(sync, pname, length);
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
    
    @Deprecated
    @Override
    public GraphicsCapabilities obtainGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.getCapabilities());
    }
    
    @Deprecated
    @Override
    public GraphicsCapabilities initializeGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.createCapabilities());
    }
    
    @Deprecated
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
    
    @Deprecated
    @Override
    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        copyBufferSubData(VulkanicAPI.getImmediateContext(), readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Deprecated
    @Override
    public void deleteVertexArray(int vertexArray) {
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(vertexArray);
    }
    
    @Deprecated
    @Override
    public void flushMappedBufferRange(int target, long offset, long length) {
        flushMappedBufferRange(VulkanicAPI.getImmediateContext(), target, offset, length);
    }
    
    @Deprecated
    @Override
    public void createBufferStorage(int target, long size, int flags) {
        bufferStorage(VulkanicAPI.getImmediateContext(), target, size, flags);
    }
    
    @Deprecated
    @Override
    public void createBufferStorage(int target, ByteBuffer data, int flags) {
        bufferStorage(VulkanicAPI.getImmediateContext(), target, data, flags);
    }
    
    @Deprecated
    @Override
    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    @Deprecated
    @Override
    public void assignUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        org.lwjgl.opengl.GL20C.glUniformMatrix4fv(location, transpose, value);
    }
    
    @Deprecated
    @Override
    public String queryString(int name) {
        return org.lwjgl.opengl.GL11C.glGetString(name);
    }
    
    @Deprecated
    @Override
    public String queryStringIndexed(int name, int index) {
        return org.lwjgl.opengl.GL30C.glGetStringi(name, index);
    }
    
    @Deprecated
    @Override
    public void uploadShaderSourceNative(int shader, int count, long strings, long length) {
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, count, strings, length);
    }
    
    @Deprecated
    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        org.lwjgl.opengl.GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Deprecated
    @Override
    public void clearTexImage(int texture, int level, int format, int type, int[] data) {
        org.lwjgl.opengl.ARBClearTexture.glClearTexImage(texture, level, format, type, data);
    }
    
    @Deprecated
    @Override
    public void setMaxShaderCompilerThreads(int count) {
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.GL_KHR_parallel_shader_compile) {
            org.lwjgl.opengl.KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(count);
        } else if (caps.GL_ARB_parallel_shader_compile) {
            org.lwjgl.opengl.ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(count);
        }
    }
    
    @Deprecated
    @Override
    public GraphicsCapabilities getGraphicsCapabilities() {
        return convertCapabilities(org.lwjgl.opengl.GL.getCapabilities());
    }
    
    @Deprecated
    @Override
    public void labelObject(int identifier, int name, String label) {
        org.lwjgl.opengl.KHRDebug.glObjectLabel(identifier, name, label);
    }
    
    @Deprecated
    @Override
    public void pushDebugGroup(int source, int id, String message) {
        org.lwjgl.opengl.KHRDebug.glPushDebugGroup(source, id, message);
    }
    
    @Deprecated
    @Override
    public void popDebugGroup() {
        org.lwjgl.opengl.KHRDebug.glPopDebugGroup();
    }
    
    // Additional methods for IrisRenderSystem
    
    @Override
    public void getIntegerv(CommandContext ctx, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetIntegerv(pname, params);
    }
    
    @Deprecated
    @Override
    public void glGetIntegerv(int pname, int[] params) {
        org.lwjgl.opengl.GL32C.glGetIntegerv(pname, params);
    }
    
    @Override
    public void getFloatv(CommandContext ctx, int pname, float[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetFloatv(pname, params);
    }
    
    @Deprecated
    @Override
    public void glGetFloatv(int pname, float[] params) {
        org.lwjgl.opengl.GL32C.glGetFloatv(pname, params);
    }
    
    @Override
    public void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) {
        uploadTexture2D(VulkanicAPI.getImmediateContext(), target, level, internalformat, width, height, border, format, type, pixels);
    }
    
    @Override
    public void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix) {
        setUniformMatrix4fv(VulkanicAPI.getImmediateContext(), location, transpose, matrix);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, float[] matrix) {
        setUniformMatrix4fv(VulkanicAPI.getImmediateContext(), location, transpose, matrix);
    }
    
    @Override
    public void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    @Deprecated
    @Override
    public void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        org.lwjgl.opengl.GL32C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    @Deprecated
    @Override
    public void glUniform1f(int location, float v0) {
        setUniform1f(VulkanicAPI.getImmediateContext(), location, v0);
    }
    
    @Deprecated
    @Override
    public void glUniform2f(int location, float v0, float v1) {
        setUniform2f(VulkanicAPI.getImmediateContext(), location, v0, v1);
    }
    
    @Deprecated
    @Override
    public void glUniform2i(int location, int v0, int v1) {
        org.lwjgl.opengl.GL32C.glUniform2i(location, v0, v1);
    }
    
    @Deprecated
    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        setUniform3f(VulkanicAPI.getImmediateContext(), location, v0, v1, v2);
    }
    
    @Deprecated
    @Override
    public void glUniform3i(int location, int v0, int v1, int v2) {
        setUniform3i(VulkanicAPI.getImmediateContext(), location, v0, v1, v2);
    }
    
    @Deprecated
    @Override
    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        setUniform4f(VulkanicAPI.getImmediateContext(), location, v0, v1, v2, v3);
    }
    
    @Deprecated
    @Override
    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        org.lwjgl.opengl.GL32C.glUniform4i(location, v0, v1, v2, v3);
    }
    
    @Deprecated
    @Override
    public void glTexParameteriv(int target, int pname, int[] params) {
        texParameteriv(VulkanicAPI.getImmediateContext(), target, pname, params);
    }
    
    @Deprecated
    @Override
    public void glTexParameteri(int target, int pname, int param) {
        setTextureParameter(VulkanicAPI.getImmediateContext(), target, pname, param);
    }
    
    @Deprecated
    @Override
    public void glTexParameterf(int target, int pname, float param) {
        org.lwjgl.opengl.GL32C.glTexParameterf(target, pname, param);
    }
    
    @Deprecated
    @Override
    public String glGetProgramInfoLog(int program) {
        return getProgramInfoLog(VulkanicAPI.getImmediateContext(), program);
    }
    
    @Deprecated
    @Override
    public String glGetShaderInfoLog(int shader) {
        return getShaderInfoLog(VulkanicAPI.getImmediateContext(), shader);
    }
    
    @Deprecated
    @Override
    public void glDrawBuffers(int[] buffers) {
        drawBuffers(VulkanicAPI.getImmediateContext(), buffers);
    }
    
    @Deprecated
    @Override
    public void glReadBuffer(int buffer) {
        setReadBuffer(VulkanicAPI.getImmediateContext(), buffer);
    }
    
    @Deprecated
    @Override
    public void glClearBufferfv(int buffer, int drawbuffer, float[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferfv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    @Override
    public void glClearBufferiv(int buffer, int drawbuffer, int[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferiv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    @Override
    public void glClearBufferuiv(int buffer, int drawbuffer, int[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferuiv(buffer, drawbuffer, values);
    }
    
    @Override
    public String getActiveUniform(CommandContext ctx, int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGetActiveUniform(program, index, size, type, name);
    }
    
    @Deprecated
    @Override
    public String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getActiveUniform(VulkanicAPI.getImmediateContext(), program, index, size, type, name);
    }
    
    @Override
    public void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
        org.lwjgl.opengl.GL32C.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, float[] data, int usage) {
        bufferData(VulkanicAPI.getImmediateContext(), target, data, usage);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, int[] data, int usage) {
        bufferData(VulkanicAPI.getImmediateContext(), target, data, usage);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, java.nio.ByteBuffer data, int usage) {
        bufferData(VulkanicAPI.getImmediateContext(), target, data, usage);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, long size, int usage) {
        bufferData(VulkanicAPI.getImmediateContext(), target, size, usage);
    }
    
    @Deprecated
    @Override
    public void glBufferSubData(int target, long offset, java.nio.ByteBuffer data) {
        org.lwjgl.opengl.GL32C.glBufferSubData(target, offset, data);
    }
    
    @Deprecated
    @Override
    public void glBufferStorage(int target, long size, int flags) {
        org.lwjgl.opengl.GL45C.glBufferStorage(target, size, flags);
    }
    
    @Deprecated
    @Override
    public void glBufferStorage(int target, java.nio.ByteBuffer data, int flags) {
        org.lwjgl.opengl.GL44C.glBufferStorage(target, data, flags);
    }
    
    @Deprecated
    @Override
    public java.nio.ByteBuffer glMapBufferRange(int target, long offset, long length, int access) {
        return org.lwjgl.opengl.GL32C.glMapBufferRange(target, offset, length, access);
    }
    
    @Deprecated
    @Override
    public boolean glUnmapBuffer(int target) {
        return org.lwjgl.opengl.GL32C.glUnmapBuffer(target);
    }
    
    @Deprecated
    @Override
    public boolean glIsBuffer(int buffer) {
        return isBuffer(VulkanicAPI.getImmediateContext(), buffer);
    }
    
    @Deprecated
    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        bindBufferBase(VulkanicAPI.getImmediateContext(), target, index, buffer);
    }
    
    @Override
    public void setVertexAttrib4f(CommandContext ctx, int index, float v0, float v1, float v2, float v3) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    @Deprecated
    @Override
    public void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        org.lwjgl.opengl.GL32C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    @Deprecated
    @Override
    public void glDetachShader(int program, int shader) {
        detachShader(VulkanicAPI.getImmediateContext(), program, shader);
    }
    
    @Deprecated
    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        framebufferTexture2D(VulkanicAPI.getImmediateContext(), target, attachment, textarget, texture, level);
    }
    
    @Deprecated
    @Override
    public void glFramebufferTexture(int target, int attachment, int texture, int level) {
        org.lwjgl.opengl.GL32C.glFramebufferTexture(target, attachment, texture, level);
    }
    
    @Deprecated
    @Override
    public int glGetTexParameteri(int target, int pname) {
        return org.lwjgl.opengl.GL32C.glGetTexParameteri(target, pname);
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
    
    @Deprecated
    @Override
    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        bindImageTexture(VulkanicAPI.getImmediateContext(), unit, texture, level, layered, layer, access, format);
    }
    
    @Deprecated
    @Override
    public int glGetMaxImageUnits() {
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
    public void createBuffers(CommandContext ctx, int[] buffers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glGenBuffers(buffers);
    }
    
    @Deprecated
    @Override
    public void glGenBuffers(int[] buffers) {
        createBuffers(VulkanicAPI.getImmediateContext(), buffers);
    }
    
    @Deprecated
    @Override
    public void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        org.lwjgl.opengl.GL43C.glClearBufferSubData(target, internalformat, offset, size, format, type, data);
    }
    
    @Override
    public void getProgramiv(CommandContext ctx, int program, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.glGetProgramiv(program, pname, params);
    }
    
    @Deprecated
    @Override
    public void glGetProgramiv(int program, int pname, int[] params) {
        getProgramiv(VulkanicAPI.getImmediateContext(), program, pname, params);
    }
    
    @Deprecated
    @Override
    public void glDispatchCompute(int workX, int workY, int workZ) {
        dispatchCompute(VulkanicAPI.getImmediateContext(), workX, workY, workZ);
    }
    
    @Override
    public void memoryBarrier(CommandContext ctx, int barriers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glMemoryBarrier(barriers);
    }
    
    @Deprecated
    @Override
    public void glMemoryBarrier(int barriers) {
        memoryBarrier(VulkanicAPI.getImmediateContext(), barriers);
    }
    
    @Deprecated
    @Override
    public void glDisablei(int target, int index) {
        org.lwjgl.opengl.GL32C.glDisablei(target, index);
    }
    
    @Deprecated
    @Override
    public void glEnablei(int target, int index) {
        org.lwjgl.opengl.GL32C.glEnablei(target, index);
    }
    
    @Deprecated
    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        blendFunc(VulkanicAPI.getImmediateContext(), sfactor, dfactor);
    }
    
    @Override
    public void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    @Override
    public void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        blendFuncSeparatei(VulkanicAPI.getImmediateContext(), buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    @Override
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return getUniformBlockIndex(VulkanicAPI.getImmediateContext(), program, uniformBlockName);
    }
    
    @Deprecated
    @Override
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        uniformBlockBinding(VulkanicAPI.getImmediateContext(), program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Override
    public int createSampler(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL33C.glGenSamplers();
    }
    
    @Deprecated
    @Override
    public int glGenSamplers() {
        return createSampler(VulkanicAPI.getImmediateContext());
    }
    
    @Override
    public void deleteSampler(CommandContext ctx, int sampler) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glDeleteSamplers(sampler);
    }
    
    @Deprecated
    @Override
    public void glDeleteSamplers(int sampler) {
        deleteSampler(VulkanicAPI.getImmediateContext(), sampler);
    }
    
    @Deprecated
    @Override
    public void glBindSampler(int unit, int sampler) {
        bindSampler(VulkanicAPI.getImmediateContext(), unit, sampler);
    }
    
    @Override
    public void bindSamplers(CommandContext ctx, int first, int[] samplers) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL45C.glBindSamplers(first, samplers);
    }
    
    @Deprecated
    @Override
    public void glBindSamplers(int first, int[] samplers) {
        bindSamplers(VulkanicAPI.getImmediateContext(), first, samplers);
    }
    
    @Override
    public void setSamplerParameteri(CommandContext ctx, int sampler, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, pname, param);
    }
    
    @Deprecated
    @Override
    public void glSamplerParameteri(int sampler, int pname, int param) {
        org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, pname, param);
    }
    
    @Override
    public void setSamplerParameterf(CommandContext ctx, int sampler, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, pname, param);
    }
    
    @Deprecated
    @Override
    public void glSamplerParameterf(int sampler, int pname, float param) {
        org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, pname, param);
    }
    
    @Override
    public void setSamplerParameteriv(CommandContext ctx, int sampler, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL33C.glSamplerParameteriv(sampler, pname, params);
    }
    
    @Deprecated
    @Override
    public void glSamplerParameteriv(int sampler, int pname, int[] params) {
        org.lwjgl.opengl.GL33C.glSamplerParameteriv(sampler, pname, params);
    }
    
    @Deprecated
    @Override
    public int glGetInteger(int pname) {
        return getInteger(VulkanicAPI.getImmediateContext(), pname);
    }
    
    @Deprecated
    @Override
    public void glDeleteBuffers(int buffer) {
        deleteBuffer(VulkanicAPI.getImmediateContext(), buffer);
    }
    
    @Deprecated
    @Override
    public void glPolygonMode(int face, int mode) {
        setPolygonMode(VulkanicAPI.getImmediateContext(), face, mode);
    }
    
    @Deprecated
    @Override
    public void glViewport(int x, int y, int width, int height) {
        setViewport(VulkanicAPI.getImmediateContext(), x, y, width, height);
    }
    
    @Override
    public void dispatchComputeIndirect(CommandContext ctx, long offset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL43C.glDispatchComputeIndirect(offset);
    }
    
    @Deprecated
    @Override
    public void glDispatchComputeIndirect(long offset) {
        dispatchComputeIndirect(VulkanicAPI.getImmediateContext(), offset);
    }
    
    @Deprecated
    @Override
    public void glBindBuffer(int target, int buffer) {
        bindBuffer(VulkanicAPI.getImmediateContext(), target, buffer);
    }
    
    @Override
    public String getString(CommandContext ctx, int name, int index) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL46C.glGetStringi(name, index);
    }
    
    @Deprecated
    @Override
    public String glGetStringi(int name, int index) {
        return org.lwjgl.opengl.GL46C.glGetStringi(name, index);
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
    
    @Deprecated
    @Override
    public void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth) {
        copyImageSubData(VulkanicAPI.getImmediateContext(), srcName, srcTarget, srcLevel, srcX, srcY, srcZ, 
                        dstName, dstTarget, dstLevel, dstX, dstY, dstZ, width, height, depth);
    }
    
    @Override
    public int checkFramebufferStatus(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL46C.glCheckFramebufferStatus(target);
    }
    
    @Deprecated
    @Override
    public int glCheckFramebufferStatus(int target) {
        return checkFramebufferStatus(VulkanicAPI.getImmediateContext(), target);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        setUniformMatrix3fv(VulkanicAPI.getImmediateContext(), location, transpose, value);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
        setUniformMatrix3fv(VulkanicAPI.getImmediateContext(), location, transpose, value);
    }
    
    @Deprecated
    @Override
    public void glClearColor(float r, float g, float b, float a) {
        setClearColor(VulkanicAPI.getImmediateContext(), r, g, b, a);
    }
    
    @Deprecated
    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        return getAttributeLocation(VulkanicAPI.getImmediateContext(), program, name);
    }
    
    @Deprecated
    @Override
    public void glGenerateMipmap(int target) {
        org.lwjgl.opengl.GL32C.glGenerateMipmap(target);
    }
    
    @Deprecated
    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        org.lwjgl.opengl.GL32C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    // DSA methods
    
    @Override
    public void generateTextureMipmapDSA(CommandContext ctx, int texture) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glGenerateTextureMipmap(texture);
    }
    
    @Deprecated
    @Override
    public void glGenerateTextureMipmap(int texture) {
        generateTextureMipmapDSA(VulkanicAPI.getImmediateContext(), texture);
    }
    
    @Override
    public void textureParameteri(CommandContext ctx, int texture, int pname, int param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }
    
    @Deprecated
    @Override
    public void glTextureParameteri(int texture, int pname, int param) {
        textureParameteri(VulkanicAPI.getImmediateContext(), texture, pname, param);
    }
    
    @Override
    public void textureParameterf(CommandContext ctx, int texture, int pname, float param) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }
    
    @Deprecated
    @Override
    public void glTextureParameterf(int texture, int pname, float param) {
        textureParameterf(VulkanicAPI.getImmediateContext(), texture, pname, param);
    }
    
    @Override
    public void textureParameteriv(CommandContext ctx, int texture, int pname, int[] params) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteriv(texture, pname, params);
    }
    
    @Deprecated
    @Override
    public void glTextureParameteriv(int texture, int pname, int[] params) {
        textureParameteriv(VulkanicAPI.getImmediateContext(), texture, pname, params);
    }
    
    @Deprecated
    @Override
    public void glNamedFramebufferReadBuffer(int framebuffer, int mode) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, mode);
    }
    
    @Deprecated
    @Override
    public void glNamedFramebufferDrawBuffers(int framebuffer, int[] bufs) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }
    
    @Deprecated
    @Override
    public void glClearNamedFramebufferfv(int framebuffer, int buffer, int drawbuffer, float[] value) {
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    @Override
    public void glClearNamedFramebufferiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    @Override
    public void glClearNamedFramebufferuiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Override
    public int getTextureParameteri(CommandContext ctx, int texture, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
    }
    
    @Deprecated
    @Override
    public int glGetTextureParameteri(int texture, int pname) {
        return getTextureParameteri(VulkanicAPI.getImmediateContext(), texture, pname);
    }
    
    @Deprecated
    @Override
    public void glCopyTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Deprecated
    @Override
    public void glBindTextureUnit(int unit, int texture) {
        org.lwjgl.opengl.ARBDirectStateAccess.glBindTextureUnit(unit, texture);
    }
    
    @Deprecated
    @Override
    public int glCreateBuffers() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers();
    }
    
    @Deprecated
    @Override
    public void glNamedBufferData(int buffer, float[] data, int usage) {
        org.lwjgl.opengl.GL45C.glNamedBufferData(buffer, data, usage);
    }
    
    @Deprecated
    @Override
    public void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Deprecated
    @Override
    public void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Deprecated
    @Override
    public int glCreateFramebuffers() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    @Deprecated
    @Override
    public int glCreateTextures(int target) {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateTextures(target);
    }
    
    // Additional rendering operations
    @Deprecated
    @Override
    public void glDrawElements(int mode, int count, int type, long indices) {
        org.lwjgl.opengl.GL32.glDrawElements(mode, count, type, indices);
    }
    
    @Deprecated
    @Override
    public void glBlendEquation(int mode) {
        setBlendEquation(VulkanicAPI.getImmediateContext(), mode);
    }
    
    @Deprecated
    @Override
    public void glClearDepth(double depth) {
        org.lwjgl.opengl.GL32.glClearDepth(depth);
    }
    
    @Deprecated
    @Override
    public int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return org.lwjgl.opengl.GL32.glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }
    
    @Deprecated
    @Override
    public void glDebugMessageControl(int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.GL43C.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Deprecated
    @Override
    public void glDebugMessageControlKHR(int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.KHRDebug.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Deprecated
    @Override
    public void glDebugMessageControlARB(int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageControlARB(source, type, severity, ids, enabled);
    }
    
    @Deprecated
    @Override
    public void glDebugMessageEnableAMD(int category, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageEnableAMD(category, severity, ids, enabled);
    }
    
    // High-level debug callback wrapper implementations
    @Deprecated
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
    
    @Deprecated
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
    
    @Deprecated
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
    
    @Deprecated
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
    
    @Deprecated
    @Override
    public void clearDebugMessageCallback() {
        org.lwjgl.opengl.GL43C.glDebugMessageCallback(null, 0L);
    }
    
    @Deprecated
    @Override
    public void clearDebugMessageCallbackKHR() {
        org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(null, 0L);
    }
    
    @Deprecated
    @Override
    public void clearDebugMessageCallbackARB() {
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB(null, 0L);
    }
    
    @Deprecated
    @Override
    public void clearDebugMessageCallbackAMD() {
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageCallbackAMD(null, 0L);
    }
    
    // GL43+ vertex attribute methods
    
    @Deprecated
    @Override
    public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        bindVertexBuffer(VulkanicAPI.getImmediateContext(), bindingindex, buffer, offset, stride);
    }
    
    @Deprecated
    @Override
    public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        org.lwjgl.opengl.GL43C.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }
    
    @Deprecated
    @Override
    public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        org.lwjgl.opengl.GL43C.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
    }
    
    @Deprecated
    @Override
    public void vertexAttribBinding(int attribindex, int bindingindex) {
        org.lwjgl.opengl.GL43C.glVertexAttribBinding(attribindex, bindingindex);
    }
    
    // VAO methods
    
    @Deprecated
    @Override
    public int genVertexArrays() {
        return org.lwjgl.opengl.GL30.glGenVertexArrays();
    }
    
    @Deprecated
    @Override
    public void bindVertexArray(int array) {
        org.lwjgl.opengl.GL30.glBindVertexArray(array);
    }
    
    @Deprecated
    @Override
    public void deleteVertexArrays(int array) {
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(array);
    }
    
    // GL context capabilities
    
    @Deprecated
    @Override
    public Object getGLCapabilities() {
        return org.lwjgl.opengl.GL.getCapabilities();
    }
    
    @Deprecated
    @Override
    public void setupDebugMessageCallback(java.io.PrintStream stream) {
        org.lwjgl.opengl.GLUtil.setupDebugMessageCallback(stream);
    }
    
    // Capability checking methods
    
    @Deprecated
    @Override
    public boolean checkOpenGL32Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL32;
    }
    
    @Deprecated
    @Override
    public boolean checkOpenGL33Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL33;
    }
    
    @Deprecated
    @Override
    public boolean checkARBInstancedArraysSupport() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.GL_ARB_instanced_arrays;
    }
    
    @Deprecated
    @Override
    public long getNamedBufferDataPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glNamedBufferData;
    }
    
    @Deprecated
    @Override
    public long getBufferStoragePointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBufferStorage;
    }
    
    @Deprecated
    @Override
    public long getBindVertexBufferPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBindVertexBuffer;
    }
    
    @Deprecated
    @Override
    public long getVertexAttribBindingPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glVertexAttribBinding;
    }
    
    // Additional GL query and state methods
    
    @Deprecated
    @Override
    public boolean glIsEnabled(int cap) {
        return org.lwjgl.opengl.GL11.glIsEnabled(cap);
    }
    
    @Deprecated
    @Override
    public boolean glIsFramebuffer(int framebuffer) {
        return org.lwjgl.opengl.GL30.glIsFramebuffer(framebuffer);
    }
    
    @Deprecated
    @Override
    public boolean glIsTexture(int texture) {
        return isTexture(VulkanicAPI.getImmediateContext(), texture);
    }
    
    @Deprecated
    @Override
    public boolean glIsVertexArray(int array) {
        return org.lwjgl.opengl.GL30.glIsVertexArray(array);
    }
    
    @Deprecated
    @Override
    public boolean glIsProgram(int program) {
        return org.lwjgl.opengl.GL20.glIsProgram(program);
    }
    
    // Additional GL state methods
    
    @Deprecated
    @Override
    public void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        org.lwjgl.opengl.GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }
    
    @Deprecated
    @Override
    public void glStencilFunc(int func, int ref, int mask) {
        org.lwjgl.opengl.GL11.glStencilFunc(func, ref, mask);
    }
    
    @Deprecated
    @Override
    public void glCullFace(int mode) {
        org.lwjgl.opengl.GL11.glCullFace(mode);
    }
    
    // Additional texture methods
    
    @Deprecated
    @Override
    public int glGenTextures() {
        return org.lwjgl.opengl.GL11.glGenTextures();
    }
    
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
}
