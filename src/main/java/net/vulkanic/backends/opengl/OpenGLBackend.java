package net.vulkanic.backends.opengl;

import net.blaze3d.GpuOutOfMemoryException;
import net.blaze3d.opengl.GlStateManager;
import net.blaze3d.opengl.GlTexture;
import net.blaze3d.opengl.GlTextureView;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackend;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.framegraph.VulkanicFrameGraphBuilder;
import net.vulkanic.pipeline.PipelineDescriptor;
import net.vulkanic.pipeline.PipelineHandle;
import net.vulkanic.pipeline.VulkanicCompiledPipeline;
import net.vulkanic.resources.VulkanicBuffer;
import net.vulkanic.resources.VulkanicRenderPass;
import net.vulkanic.resources.VulkanicTexture;
import net.vulkanic.resources.VulkanicTextureFormat;
import net.vulkanic.resources.VulkanicTextureView;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * OpenGL implementation of the Vulkanic Graphics Backend.
 * This is the ONLY place where direct OpenGL calls should be made.
 */
public class OpenGLBackend implements GraphicsBackend {

    private static final Logger LOGGER = Logger.getLogger(OpenGLBackend.class.getName());

    /**
     * Reference to the active {@link net.blaze3d.opengl.GlDevice}.
     *
     * <p>Set via {@link #setGlDevice} when {@code GlDevice} initialises itself.
     * Required so that {@link #createVulkanicBuffer} can delegate to
     * {@code GlDevice}'s {@code BufferStorage} (which handles DSA, persistent
     * mapping, etc.) rather than duplicating that logic here.  Also used by
     * {@link #beginRenderPass} to look up the cached FBO from
     * {@link net.blaze3d.opengl.GlTexture#getFbo}.
     */
    @org.jetbrains.annotations.Nullable
    private net.blaze3d.opengl.GlDevice glDevice;

    /**
     * Called by {@link net.blaze3d.opengl.GlDevice} during its constructor so that
     * this backend can delegate resource creation back to it.
     * This is how Blaze3D registers as the concrete implementation provider
     * while Vulkanic owns the interface.
     */
    public void setGlDevice(net.blaze3d.opengl.GlDevice device) {
        this.glDevice = device;
    }
    
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

    // =========================================================================
    // Phase 3 — Buffer lifecycle (Step 1)
    // =========================================================================

    @Override
    public VulkanicBuffer createVulkanicBuffer(int usage, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be > 0, got " + size);
        }
        if (glDevice != null) {
            // Delegate to GlDevice's BufferStorage — the authoritative path.
            // BufferStorage handles DSA, persistent mapping, and immutable storage
            // (GL_ARB_buffer_storage).  The returned GlBuffer already implements
            // VulkanicBuffer (see GlBuffer.java).
            return (VulkanicBuffer) glDevice.getBufferStorage()
                    .createBuffer(glDevice.directStateAccess(), null, usage, size);
        }
        // Fallback: before GlDevice is initialised (tests, early startup).
        int glHandle = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glHandle);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, size, toGlUsage(usage));
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return new OpenGLBuffer(null, null, usage, size, glHandle, null);
    }

    @Override
    public VulkanicBuffer createVulkanicBuffer(int usage, ByteBuffer data) {
        if (!data.hasRemaining()) {
            throw new IllegalArgumentException("Buffer data must not be empty");
        }
        int size = data.remaining();
        if (glDevice != null) {
            return (VulkanicBuffer) glDevice.getBufferStorage()
                    .createBuffer(glDevice.directStateAccess(), null, usage, data);
        }
        int glHandle = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glHandle);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, toGlUsage(usage));
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return new OpenGLBuffer(null, null, usage, size, glHandle, null);
    }

    @Override
    public void deleteVulkanicBuffer(VulkanicBuffer buffer) {
        buffer.close();
    }

    /** Maps a VulkanicBuffer usage bitmask to a GL buffer usage hint. */
    private static int toGlUsage(int usage) {
        // Prefer dynamic if either MAP_WRITE or COPY_DST is set
        if ((usage & (VulkanicBuffer.USAGE_MAP_WRITE | VulkanicBuffer.USAGE_COPY_DST)) != 0) {
            return GL15.GL_DYNAMIC_DRAW;
        }
        return GL15.GL_STATIC_DRAW;
    }

    // =========================================================================
    // Phase 3 — Texture lifecycle (Step 2)
    //
    // createVulkanicTexture is the AUTHORITATIVE texture allocation point.
    // All GL texture allocation logic lives here — in the Vulkanic backend —
    // not in GlDevice.  GlDevice.createTexture() is a thin façade that
    // converts TextureFormat → VulkanicTextureFormat and delegates here.
    // =========================================================================

    @Override
    public VulkanicTexture createVulkanicTexture(String label, int usage,
                                                   VulkanicTextureFormat format,
                                                   int width, int height,
                                                   int depthOrLayers, int mipLevels) {
        if (mipLevels < 1)     throw new IllegalArgumentException("mipLevels must be at least 1");
        if (depthOrLayers < 1) throw new IllegalArgumentException("depthOrLayers must be at least 1");
        if (width < 1)         throw new IllegalArgumentException("width must be >= 1");
        if (height < 1)        throw new IllegalArgumentException("height must be >= 1");

        boolean cubemap = (usage & 16) != 0;
        if (cubemap) {
            if (width != height) {
                throw new IllegalArgumentException(
                        "Cubemap textures must be square, but size is " + width + "x" + height);
            }
            if (depthOrLayers % 6 != 0) {
                throw new IllegalArgumentException(
                        "Cubemap textures must have layer count divisible by 6, was " + depthOrLayers);
            }
            if (depthOrLayers > 6) {
                throw new UnsupportedOperationException("Array textures are not yet supported");
            }
        } else if (depthOrLayers > 1) {
            throw new UnsupportedOperationException("Array or 3D textures are not yet supported");
        }

        GlStateManager.clearGlErrors();
        int n = GlStateManager._genTexture();
        if (label == null) label = String.valueOf(n);

        int target;
        if (cubemap) {
            // Cubemap textures must be bound to GL_TEXTURE_CUBE_MAP (34067) so that
            // glTexParameter and glTexImage2D calls apply to the correct target.
            // GlStateManager._bindTexture binds to GL_TEXTURE_2D; we route through
            // VulkanicAPI.bindTexture which accepts an explicit target.
            VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), 34067, n);
            target = 34067; // GL_TEXTURE_CUBE_MAP
        } else {
            GlStateManager._bindTexture(n);
            target = 3553; // GL_TEXTURE_2D
        }

        // Set mip-level range on the texture object:
        //   33085 = GL_TEXTURE_MAX_LEVEL  — highest accessible mip level
        //   33082 = GL_TEXTURE_MIN_LOD    — minimum LOD clamp (0 = base level)
        //   33083 = GL_TEXTURE_MAX_LOD    — maximum LOD clamp
        GlStateManager._texParameter(target, 33085, mipLevels - 1); // GL_TEXTURE_MAX_LEVEL
        GlStateManager._texParameter(target, 33082, 0);              // GL_TEXTURE_MIN_LOD
        GlStateManager._texParameter(target, 33083, mipLevels - 1); // GL_TEXTURE_MAX_LOD
        if (format.hasDepthAspect()) {
            GlStateManager._texParameter(target, 34892, 0);          // GL_TEXTURE_COMPARE_MODE = GL_NONE
        }

        int internalFmt = toGlInternalId(format);
        int externalFmt = toGlExternalId(format);
        int glType      = toGlType(format);

        if (cubemap) {
            for (int face : net.blaze3d.opengl.GlConst.CUBEMAP_TARGETS) {
                for (int mip = 0; mip < mipLevels; mip++) {
                    GlStateManager._texImage2D(face, mip, internalFmt,
                            Math.max(1, width >> mip), Math.max(1, height >> mip),
                            0, externalFmt, glType, null);
                }
            }
        } else {
            for (int mip = 0; mip < mipLevels; mip++) {
                GlStateManager._texImage2D(target, mip, internalFmt,
                        Math.max(1, width >> mip), Math.max(1, height >> mip),
                        0, externalFmt, glType, null);
            }
        }

        int err = GlStateManager._getError();
        if (err == 1285) {
            throw new GpuOutOfMemoryException(
                    "Could not allocate texture of " + width + "x" + height + " for " + label);
        } else if (err != 0) {
            throw new IllegalStateException("OpenGL error " + err);
        }

        // Map back to the legacy TextureFormat that GlTexture's superclass constructor needs.
        TextureFormat legacyFmt = switch (format) {
            case RGBA8   -> TextureFormat.RGBA8;
            case RED8    -> TextureFormat.RED8;
            case RED8I   -> TextureFormat.RED8I;
            case DEPTH32 -> TextureFormat.DEPTH32;
        };
        GlTexture glTexture = new GlTexture(usage, label, legacyFmt, width, height, depthOrLayers, mipLevels, n);
        if (glDevice != null) glDevice.debugLabels().applyLabel(glTexture);
        return glTexture;
    }

    @Override
    public VulkanicTextureView createVulkanicTextureView(VulkanicTexture texture) {
        return createVulkanicTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public VulkanicTextureView createVulkanicTextureView(VulkanicTexture texture,
                                                           int baseMipLevel, int mipLevelCount) {
        if (texture.isClosed()) {
            throw new IllegalArgumentException("Cannot create view of closed texture");
        }
        if (baseMipLevel < 0 || baseMipLevel + mipLevelCount > texture.getMipLevels()) {
            throw new IllegalArgumentException(
                    "Mip range [" + baseMipLevel + ", " + (baseMipLevel + mipLevelCount)
                    + ") is out of bounds for texture with " + texture.getMipLevels() + " mip levels");
        }
        // createVulkanicTexture always produces a GlTexture; GlTextureView implements VulkanicTextureView.
        if (texture instanceof GlTexture glTex) {
            return new GlTextureView(glTex, baseMipLevel, mipLevelCount);
        }
        // Fallback for OpenGLTexture instances created via the direct Vulkanic-only path.
        return new OpenGLTextureView((OpenGLTexture) texture, baseMipLevel, mipLevelCount);
    }

    @Override
    public void deleteVulkanicTexture(VulkanicTexture texture) {
        texture.close();
    }

    // -----------------------------------------------------------------------
    // GL format helpers (VulkanicTextureFormat → GL constants).
    // These live here — in the OpenGL backend — not in Blaze3D's GlConst.
    // -----------------------------------------------------------------------

    static int toGlInternalId(VulkanicTextureFormat fmt) {
        return switch (fmt) {
            case RGBA8   -> 0x8058; // GL_RGBA8
            case RED8    -> 0x8229; // GL_R8
            case RED8I   -> 0x8231; // GL_R8I
            case DEPTH32 -> 0x8167; // GL_DEPTH_COMPONENT32
        };
    }

    static int toGlExternalId(VulkanicTextureFormat fmt) {
        return switch (fmt) {
            case RGBA8            -> 0x1908; // GL_RGBA
            case RED8, RED8I      -> 0x1903; // GL_RED
            case DEPTH32          -> 0x1902; // GL_DEPTH_COMPONENT
        };
    }

    static int toGlType(VulkanicTextureFormat fmt) {
        return switch (fmt) {
            case RGBA8, RED8 -> 0x1401; // GL_UNSIGNED_BYTE
            case RED8I       -> 0x1400; // GL_BYTE (signed integer)
            case DEPTH32     -> 0x1406; // GL_FLOAT
        };
    }


    // =========================================================================
    // Phase 3 — Pipeline objects (Step 3)
    // =========================================================================

    @Override
    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        // Compile vertex shader
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, descriptor.getVertexShaderSource());
        GL20.glCompileShader(vertexShader);
        if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(vertexShader);
            GL20.glDeleteShader(vertexShader);
            logPipelineError("vertex shader", descriptor.getDebugLabel(), log);
            return OpenGLPipeline.INVALID;
        }

        // Compile fragment shader
        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, descriptor.getFragmentShaderSource());
        GL20.glCompileShader(fragmentShader);
        if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(fragmentShader);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            logPipelineError("fragment shader", descriptor.getDebugLabel(), log);
            return OpenGLPipeline.INVALID;
        }

        // Link program
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);

        // Shaders are no longer needed once linked
        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            logPipelineError("program link", descriptor.getDebugLabel(), log);
            return OpenGLPipeline.INVALID;
        }

        return new OpenGLPipeline(program, descriptor.getDebugLabel());
    }

    @Override
    public void deletePipeline(PipelineHandle pipeline) {
        if (pipeline instanceof OpenGLPipeline glPipeline) {
            glPipeline.delete();
        }
    }

    private static void logPipelineError(String stage, String label, String log) {
        LOGGER.severe("[Vulkanic] Pipeline '" + label + "' " + stage + " failed:\n" + log);
    }

    // =========================================================================
    // Phase 3 — Render pass (Step 4)
    // =========================================================================

    /**
     * Fallback FBO used when the colour target is an {@link OpenGLTexture} (not a
     * {@link net.blaze3d.opengl.GlTexture}).  Created lazily on first use.
     * When the target IS a GlTexture/GlTextureView, we use GlTexture's own FBO
     * cache instead (see below) to remain compatible with GlCommandEncoder.
     */
    private int renderPassFbo = 0;

    @Override
    public void beginRenderPass(CommandContext ctx, VulkanicTextureView colorTarget,
                                 OptionalInt clearColor) {
        beginRenderPass(ctx, colorTarget, clearColor, null, OptionalDouble.empty());
    }

    @Override
    public void beginRenderPass(CommandContext ctx, VulkanicTextureView colorTarget,
                                 OptionalInt clearColor,
                                 VulkanicTextureView depthTarget, OptionalDouble clearDepth) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (colorTarget.isClosed()) {
            throw new IllegalStateException("Color target texture is closed");
        }

        int fbo;
        if (colorTarget instanceof net.blaze3d.opengl.GlTextureView glColorView && glDevice != null) {
            // Use GlTexture's FBO cache so that GlCommandEncoder and Vulkanic
            // render passes share the same FBO objects.  This avoids double-binding
            // and keeps the cached FBO valid for downstream GlRenderPass operations.
            net.blaze3d.opengl.GlTexture colorTex = glColorView.texture();
            net.blaze3d.opengl.GlTexture depthTex =
                    depthTarget instanceof net.blaze3d.opengl.GlTextureView dv
                            ? dv.texture() : null;
            fbo = colorTex.getFbo(glDevice.directStateAccess(), depthTex);
        } else {
            // Fallback for OpenGLTexture targets (new Vulkanic-path textures that
            // have no GlTexture counterpart yet).
            if (renderPassFbo == 0) {
                renderPassFbo = GL30.glGenFramebuffers();
            }
            fbo = renderPassFbo;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

            int colorTexId = (int) colorTarget.getNativeHandle();
            int colorMip   = colorTarget.getBaseMipLevel();
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, colorTexId, colorMip);

            if (depthTarget != null && !depthTarget.isClosed()) {
                int depthTexId = (int) depthTarget.getNativeHandle();
                int depthMip   = depthTarget.getBaseMipLevel();
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D, depthTexId, depthMip);
            }
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        // Issue clears
        if (clearColor.isPresent()) {
            int argb = clearColor.getAsInt();
            float a = ((argb >> 24) & 0xFF) / 255.0f;
            float r = ((argb >> 16) & 0xFF) / 255.0f;
            float g = ((argb >>  8) & 0xFF) / 255.0f;
            float b = ( argb        & 0xFF) / 255.0f;
            GL11.glClearColor(r, g, b, a);
            int clearMask = GL11.GL_COLOR_BUFFER_BIT;
            if (clearDepth.isPresent()) {
                GL11.glClearDepth(clearDepth.getAsDouble());
                clearMask |= GL11.GL_DEPTH_BUFFER_BIT;
            }
            GL11.glClear(clearMask);
        } else if (clearDepth.isPresent()) {
            GL11.glClearDepth(clearDepth.getAsDouble());
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
    }

    @Override
    public void setPipeline(CommandContext ctx, PipelineHandle pipeline) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        if (!pipeline.isValid()) {
            throw new IllegalArgumentException("Cannot bind an invalid pipeline");
        }
        GL20.glUseProgram((int) pipeline.getNativeHandle());
    }

    @Override
    public void setVertexBuffer(CommandContext ctx, VulkanicBuffer buffer, long offset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, (int) buffer.getNativeHandle());
        // Offset handling would be applied via glVertexAttribPointer calls by the pipeline
    }

    @Override
    public void setIndexBuffer(CommandContext ctx, VulkanicBuffer buffer, int indexType, long offset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, (int) buffer.getNativeHandle());
    }

    @Override
    public void endRenderPass(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        // Unbind all attachments and restore the default framebuffer
        if (renderPassFbo != 0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    // =========================================================================
    // Phase 3 — Frame graph (Step 6)
    // =========================================================================

    @Override
    public void executeFrame(CommandContext ctx, VulkanicFrameGraphBuilder frame) {
        frame.execute(ctx);
    }

    // =========================================================================
    // Phase 3 — Command buffer lifecycle (Vulkan prerequisite)
    // =========================================================================

    /**
     * OpenGL backend: returns the singleton {@code IMMEDIATE} context.
     *
     * <p>OpenGL executes commands immediately — there is no command buffer to begin.
     * For the Vulkan backend this method will allocate a command buffer from the
     * pool and call {@code vkBeginCommandBuffer()}.
     */
    @Override
    public CommandContext beginCommandBuffer() {
        return OpenGLCommandContext.IMMEDIATE;
    }

    /**
     * OpenGL backend: no-op.
     *
     * <p>OpenGL commands are already executed by the time this is called.
     * For the Vulkan backend this method will call {@code vkEndCommandBuffer()}
     * followed by {@code vkQueueSubmit()}.
     */
    @Override
    public void submitCommandBuffer(CommandContext ctx) {
        // OpenGL: immediate mode — nothing to submit.
    }

    // =========================================================================
    // Phase 3 — Device info
    // =========================================================================

    @Override
    public String getImplementationInformation() {
        if (glDevice != null) return glDevice.getImplementationInformation();
        return "OpenGL (device not yet initialised)";
    }

    @Override
    public String getBackendName() {
        return "OpenGL";
    }

    @Override
    public String getVendor() {
        if (glDevice != null) return glDevice.getVendor();
        return "";
    }

    @Override
    public String getRenderer() {
        if (glDevice != null) return glDevice.getRenderer();
        return "";
    }

    @Override
    public String getApiVersion() {
        if (glDevice != null) return glDevice.getVersion();
        return "";
    }

    @Override
    public int getMaxTextureSize() {
        if (glDevice != null) return glDevice.getMaxTextureSize();
        return 1024; // safe minimum before device is ready
    }

    @Override
    public java.util.List<String> getEnabledExtensions() {
        if (glDevice != null) return glDevice.getEnabledExtensions();
        return java.util.Collections.emptyList();
    }

    // =========================================================================
    // Phase 3 — Command-encoder operations (§3b migration)
    //
    // All operations delegate to GlDevice.createCommandEncoder() — the existing
    // authoritative OpenGL implementation.  The VulkanicBuffer / VulkanicTexture
    // arguments are safely cast to their Blaze3D counterparts (GlBuffer extends
    // GpuBuffer, GlTexture extends GpuTexture) because in the OpenGL backend every
    // VulkanicBuffer IS a GlBuffer and every VulkanicTexture IS a GlTexture.
    // =========================================================================

    @Override
    public void writeToBuffer(CommandContext ctx, net.vulkanic.resources.VulkanicBufferSlice slice, ByteBuffer data) {
        requireGlDevice("writeToBuffer");
        net.blaze3d.buffers.GpuBufferSlice gpuSlice = toGpuSlice(slice);
        glDevice.createCommandEncoder().writeToBuffer(gpuSlice, data);
    }

    @Override
    public ByteBuffer mapBuffer(CommandContext ctx, net.vulkanic.resources.VulkanicBufferSlice slice, boolean read, boolean write) {
        requireGlDevice("mapBuffer");
        net.blaze3d.buffers.GpuBufferSlice gpuSlice = toGpuSlice(slice);
        net.blaze3d.buffers.GpuBuffer.MappedView view = glDevice.createCommandEncoder().mapBuffer(gpuSlice, read, write);
        return view.data();
    }

    @Override
    public void unmapBuffer(CommandContext ctx, VulkanicBuffer buffer) {
        requireGlDevice("unmapBuffer");
        // Route through the same GL call path that GlCommandEncoder's MappedView.close() uses.
        // GL_ARRAY_BUFFER (34962) is a safe general-purpose binding target for unmapping.
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, (int) buffer.getNativeHandle());
        GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void clearColorTexture(CommandContext ctx, VulkanicTexture texture, int argbColor) {
        requireGlDevice("clearColorTexture");
        glDevice.createCommandEncoder().clearColorTexture((net.blaze3d.textures.GpuTexture) texture, argbColor);
    }

    @Override
    public void clearDepthTexture(CommandContext ctx, VulkanicTexture texture, double depth) {
        requireGlDevice("clearDepthTexture");
        glDevice.createCommandEncoder().clearDepthTexture((net.blaze3d.textures.GpuTexture) texture, depth);
    }

    @Override
    public void clearColorAndDepthTextures(CommandContext ctx,
                                            VulkanicTexture color, int argbColor,
                                            VulkanicTexture depth, double depthValue) {
        requireGlDevice("clearColorAndDepthTextures");
        glDevice.createCommandEncoder().clearColorAndDepthTextures(
                (net.blaze3d.textures.GpuTexture) color, argbColor,
                (net.blaze3d.textures.GpuTexture) depth, depthValue);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void requireGlDevice(String operation) {
        if (glDevice == null) {
            throw new IllegalStateException(
                    "VulkanicAPI." + operation + "() called before GlDevice was initialised");
        }
    }

    /**
     * Converts a {@link net.vulkanic.resources.VulkanicBufferSlice} to a Blaze3D
     * {@link net.blaze3d.buffers.GpuBufferSlice} for delegation to
     * {@code GlCommandEncoder}.
     *
     * <p>This cast is safe because every {@code VulkanicBuffer} in the OpenGL backend
     * IS a {@code GlBuffer extends GpuBuffer}.
     */
    private static net.blaze3d.buffers.GpuBufferSlice toGpuSlice(net.vulkanic.resources.VulkanicBufferSlice slice) {
        return new net.blaze3d.buffers.GpuBufferSlice(
                (net.blaze3d.buffers.GpuBuffer) slice.buffer(),
                slice.offset(),
                slice.length());
    }

    // =========================================================================
    // Phase 4 — Render pass (createVulkanicRenderPass)
    //
    // Delegates to GlCommandEncoder.createRenderPass() — the existing Blaze3D
    // implementation that handles FBO binding, Iris hooks, and viewport setup.
    // GlRenderPass implements VulkanicRenderPass so the cast is safe.
    //
    // A future Vulkan backend will implement this with vkCmdBeginRenderPass
    // without any GlCommandEncoder involvement.
    // =========================================================================

    @Override
    public VulkanicRenderPass createVulkanicRenderPass(CommandContext ctx,
                                                        Supplier<String> label,
                                                        VulkanicTextureView colorTarget,
                                                        OptionalInt clearColor) {
        return createVulkanicRenderPass(ctx, label, colorTarget, clearColor, null, OptionalDouble.empty());
    }

    @Override
    public VulkanicRenderPass createVulkanicRenderPass(CommandContext ctx,
                                                        Supplier<String> label,
                                                        VulkanicTextureView colorTarget,
                                                        OptionalInt clearColor,
                                                        @Nullable VulkanicTextureView depthTarget,
                                                        OptionalDouble clearDepth) {
        requireGlDevice("createVulkanicRenderPass");
        // Casts are safe: in the OpenGL backend every VulkanicTextureView IS a GlTextureView
        // which extends GpuTextureView.  GlRenderPass implements VulkanicRenderPass.
        GpuTextureView colorView = (GpuTextureView) colorTarget;
        GpuTextureView depthView  = depthTarget != null ? (GpuTextureView) depthTarget : null;
        return (VulkanicRenderPass) glDevice.createCommandEncoder()
                .createRenderPass(label, colorView, clearColor, depthView, clearDepth);
    }

    // =========================================================================
    // Phase 4 — Pipeline compilation
    //
    // precompilePipeline is the Vulkan-critical entry point.  In Vulkan,
    // pipeline objects (VkPipeline) MUST be compiled before the first frame
    // that uses them — deferred / on-first-draw compilation is forbidden.
    // By routing all pipeline compilation through VulkanicAPI.precompilePipeline(),
    // the Vulkan backend can eagerly compile VkPipeline objects at load time.
    // =========================================================================

    @Override
    public VulkanicCompiledPipeline precompilePipeline(RenderPipeline renderPipeline,
                                                         @Nullable BiFunction<ResourceLocation, ShaderType, String> shaderSource) {
        requireGlDevice("precompilePipeline");
        // Call compilePipelineInternal() directly to avoid the delegation loop:
        // GlDevice.precompilePipeline() → VulkanicAPI.precompilePipeline() → here.
        return (VulkanicCompiledPipeline) glDevice.compilePipelineInternal(renderPipeline, shaderSource);
    }

    @Override
    public void clearPipelineCache() {
        if (glDevice != null) glDevice.clearPipelineCache();
    }
}

