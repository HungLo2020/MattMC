package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackend;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.opengl.EXTDebugLabel;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
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
    public void bindTexture(int textureId) {
        int activeTexUnit = GlStateManager.activeTexture;
        if (textureId != GlStateManager.TEXTURES[activeTexUnit].binding) {
            GlStateManager.TEXTURES[activeTexUnit].binding = textureId;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }
    }
    
    @Deprecated
    @Override
    public void bindTexture(int target, int textureId) {
        GL11.glBindTexture(target, textureId);
    }
    
    @Deprecated
    @Override
    public void generateMipmap(int target) {
        GL30.glGenerateMipmap(target);
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
    
    @Deprecated
    @Override
    public void setColorWriteMask(boolean r, boolean g, boolean b, boolean a) {
        GL11.glColorMask(r, g, b, a);
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
    
    /**
     * Clears buffers with explicit command context.
     * This is the Vulkan-compatible implementation for buffer clearing.
     * 
     * OpenGL implementation: Direct mapping to glClear() (context is validated but not used)
     * Vulkan implementation: Will map to vkCmdClearAttachments() or render pass clear
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param mask Bitwise OR of buffer masks to clear
     */
    @Override
    public void clear(CommandContext ctx, int mask) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glClear(mask);
    }
    
    /**
     * Draws primitives from vertex arrays with explicit command context.
     * This is the Vulkan-compatible implementation for non-indexed drawing.
     * 
     * OpenGL implementation: Direct mapping to glDrawArrays() (context is validated but not used)
     * Vulkan implementation: Will map to vkCmdDraw(ctx.getHandle(), count, 1, first, 0)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param mode Primitive topology
     * @param first Starting vertex index
     * @param count Number of vertices to draw
     */
    @Override
    public void drawArrays(CommandContext ctx, int mode, int first, int count) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDrawArrays(mode, first, count);
    }
    
    /**
     * Draws indexed primitives with explicit command context.
     * This is the Vulkan-compatible implementation for indexed drawing.
     * 
     * OpenGL implementation: Direct mapping to glDrawElements() (context is validated but not used)
     * Vulkan implementation: Will map to vkCmdDrawIndexed(ctx.getHandle(), count, 1, 0, 0, 0)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param mode Primitive topology
     * @param count Number of indices to draw
     * @param type Data type of indices
     * @param indices Offset in bytes from start of index buffer
     */
    @Override
    public void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDrawElements(mode, count, type, indices);
    }
    
    @Override
    public void renderIndexedInstancedWithBase(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    @Override
    public void renderIndexedWithBase(CommandContext ctx, int mode, int count, int type, long indices, int baseVertex) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }
    
    @Override
    public void renderIndexedInstanced(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }
    
    @Override
    public void renderArraysInstanced(CommandContext ctx, int mode, int first, int count, int instanceCount) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
    
    /**
     * Binds a shader program with explicit command context.
     * This is the Vulkan-compatible implementation for shader binding.
     * 
     * OpenGL implementation: Direct mapping to glUseProgram() (context is validated but not used)
     * Vulkan implementation: Will be handled by vkCmdBindPipeline() with pre-compiled pipelines
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param programId The shader program ID to bind
     */
    @Override
    public void bindShaderProgram(CommandContext ctx, int programId) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUseProgram(programId);
    }
    
    /**
     * Sets the depth write mask with explicit command context.
     * This is the Vulkan-compatible implementation for depth write control.
     * 
     * OpenGL implementation: Direct mapping to glDepthMask() (context is validated but not used)
     * Vulkan implementation: Will be part of pipeline state creation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param enabled true to enable depth writes, false to disable
     */
    @Override
    public void setDepthWriteMask(CommandContext ctx, boolean enabled) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDepthMask(enabled);
    }
    
    /**
     * Sets the color write mask with explicit command context.
     * This is the Vulkan-compatible implementation for color write control.
     * 
     * OpenGL implementation: Direct mapping to glColorMask() (context is validated but not used)
     * Vulkan implementation: Will be part of pipeline state creation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param r true to enable red channel writes
     * @param g true to enable green channel writes
     * @param b true to enable blue channel writes
     * @param a true to enable alpha channel writes
     */
    @Override
    public void setColorWriteMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glColorMask(r, g, b, a);
    }
    
    /**
     * Sets the depth comparison function with explicit command context.
     * This is the Vulkan-compatible implementation for depth testing.
     * 
     * OpenGL implementation: Direct mapping to glDepthFunc() (context is validated but not used)
     * Vulkan implementation: Will be part of pipeline state creation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param func The depth comparison function
     */
    @Override
    public void setDepthFunc(CommandContext ctx, int func) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDepthFunc(func);
    }
    
    /**
     * Sets the blend function with explicit command context.
     * This is the Vulkan-compatible implementation for blending control.
     * 
     * OpenGL implementation: Direct mapping to glBlendFuncSeparate() (context is validated but not used)
     * Vulkan implementation: Will be part of pipeline state creation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param srcRgb Source RGB blend factor
     * @param dstRgb Destination RGB blend factor
     * @param srcAlpha Source alpha blend factor
     * @param dstAlpha Destination alpha blend factor
     */
    @Override
    public void setBlendFunc(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    /**
     * Binds a buffer object with explicit command context.
     * This is the Vulkan-compatible implementation for buffer binding.
     * 
     * OpenGL implementation: Direct mapping to glBindBuffer() (context is validated but not used)
     * Vulkan implementation: Will use vkCmdBindVertexBuffers() or descriptor sets
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param target The buffer binding target
     * @param buffer The buffer object ID
     */
    @Override
    public void bindBuffer(CommandContext ctx, int target, int buffer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL15.glBindBuffer(target, buffer);
    }
    
    /**
     * Enables blending with explicit command context.
     * This is the Vulkan-compatible implementation for blend control.
     * 
     * OpenGL implementation: Direct mapping to glEnable(GL_BLEND) (context is validated but not used)
     * Vulkan implementation: Will be part of pipeline state creation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     */
    @Override
    public void enableBlend(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glEnable(GL11.GL_BLEND);
    }
    
    /**
     * Disables blending with explicit command context.
     * This is the Vulkan-compatible implementation for blend control.
     * 
     * OpenGL implementation: Direct mapping to glDisable(GL_BLEND) (context is validated but not used)
     * Vulkan implementation: Will be part of pipeline state creation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     */
    @Override
    public void disableBlend(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    /**
     * Enables a generic capability with explicit command context.
     * This is the Vulkan-compatible implementation for capability control.
     * 
     * OpenGL implementation: Direct mapping to glEnable(cap) (context is validated but not used)
     * Vulkan implementation: Will map to pipeline state or dynamic state based on capability
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param cap The capability to enable
     */
    @Override
    public void enable(CommandContext ctx, int cap) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glEnable(cap);
    }
    
    /**
     * Disables a generic capability with explicit command context.
     * This is the Vulkan-compatible implementation for capability control.
     * 
     * OpenGL implementation: Direct mapping to glDisable(cap) (context is validated but not used)
     * Vulkan implementation: Will map to pipeline state or dynamic state based on capability
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param cap The capability to disable
     */
    @Override
    public void disable(CommandContext ctx, int cap) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDisable(cap);
    }
    
    /**
     * Activates a texture unit with explicit command context.
     * This is the Vulkan-compatible implementation for texture unit selection.
     * 
     * OpenGL implementation: Direct mapping to glActiveTexture(unit) (context is validated but not used)
     * Vulkan implementation: Will be abstracted through descriptor sets
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param unit The texture unit to activate
     */
    @Override
    public void activateTextureUnit(CommandContext ctx, int unit) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL13.glActiveTexture(unit);
    }
    
    /**
     * Generates mipmaps with explicit command context.
     * This is the Vulkan-compatible implementation for mipmap generation.
     * 
     * OpenGL implementation: Direct mapping to glGenerateMipmap() (context is validated but not used)
     * Vulkan implementation: Will use vkCmdBlitImage() for mip chain generation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param target The texture target
     */
    @Override
    public void generateMipmap(CommandContext ctx, int target) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glGenerateMipmap(target);
    }
    
    /**
     * Binds a texture with explicit command context.
     * This is the Vulkan-compatible implementation for texture binding.
     * 
     * OpenGL implementation: Direct mapping to glBindTexture(GL_TEXTURE_2D, textureId)
     * Vulkan implementation: Will bind through descriptor sets
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param textureId The texture ID to bind
     */
    @Override
    public void bindTexture(CommandContext ctx, int textureId) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        int activeTexUnit = GlStateManager.activeTexture;
        if (textureId != GlStateManager.TEXTURES[activeTexUnit].binding) {
            GlStateManager.TEXTURES[activeTexUnit].binding = textureId;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }
    }
    
    /**
     * Binds a texture to a specific target with explicit command context.
     * This is the Vulkan-compatible implementation for texture binding.
     * 
     * OpenGL implementation: Direct mapping to glBindTexture(target, textureId)
     * Vulkan implementation: Will bind through descriptor sets
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param target The texture target
     * @param textureId The texture ID to bind
     */
    @Override
    public void bindTexture(CommandContext ctx, int target, int textureId) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glBindTexture(target, textureId);
    }
    
    /**
     * Sets pixel storage mode with explicit command context.
     * This is the Vulkan-compatible implementation for pixel storage control.
     * 
     * OpenGL implementation: Direct mapping to glPixelStorei()
     * Vulkan implementation: Will be handled through buffer copy parameters
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param pname The pixel storage parameter name
     * @param value The value to set
     */
    @Override
    public void setPixelStoreMode(CommandContext ctx, int pname, int value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glPixelStorei(pname, value);
    }
    
    /**
     * Attaches a framebuffer with explicit command context.
     * This is the Vulkan-compatible implementation for framebuffer binding.
     * 
     * OpenGL implementation: Direct mapping to glBindFramebuffer()
     * Vulkan implementation: Will be handled through render pass begin
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param target The framebuffer target
     * @param fbo The framebuffer object ID
     */
    @Override
    public void attachFramebuffer(CommandContext ctx, int target, int fbo) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glBindFramebuffer(target, fbo);
    }
    
    /**
     * Attaches a texture to a framebuffer with explicit command context.
     * This is the Vulkan-compatible implementation for framebuffer texture attachment.
     * 
     * OpenGL implementation: Direct mapping to glFramebufferTexture2D()
     * Vulkan implementation: Textures are attached during framebuffer creation
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param target The framebuffer target
     * @param attachment The attachment point
     * @param textarget The texture target
     * @param texture The texture ID
     * @param level The mipmap level
     */
    @Override
    public void attachTextureToFramebuffer(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    /**
     * Configures a texture parameter with explicit command context.
     * This is the Vulkan-compatible implementation for texture parameter setting.
     * 
     * OpenGL implementation: Direct mapping to glTexParameteri()
     * Vulkan implementation: Texture parameters are set through sampler objects
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param target The texture target
     * @param pname The parameter name
     * @param param The parameter value
     */
    @Override
    public void configureTextureParameter(CommandContext ctx, int target, int pname, int param) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glTexParameteri(target, pname, param);
    }
    
    /**
     * Removes a texture with explicit command context.
     * This is the Vulkan-compatible implementation for texture deletion.
     * 
     * OpenGL implementation: Direct mapping to glDeleteTextures()
     * Vulkan implementation: Will use vkDestroyImage/vkDestroyImageView
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param texture The texture ID to delete
     */
    @Override
    public void removeTexture(CommandContext ctx, int texture) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDeleteTextures(texture);
    }
    
    /**
     * Configures polygon mode with explicit command context.
     * This is the Vulkan-compatible implementation for polygon rasterization mode.
     * 
     * OpenGL implementation: Direct mapping to glPolygonMode()
     * Vulkan implementation: Part of pipeline state
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param face Which faces to apply to
     * @param mode The rasterization mode
     */
    @Override
    public void configurePolygonMode(CommandContext ctx, int face, int mode) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glPolygonMode(face, mode);
    }
    
    /**
     * Creates a texture with explicit command context.
     * This is the Vulkan-compatible implementation for texture creation.
     * 
     * OpenGL implementation: Direct mapping to glGenTextures()
     * Vulkan implementation: Will use vkCreateImage() and vkCreateImageView()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @return The newly created texture ID
     */
    @Override
    public int createTexture(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glGenTextures();
    }
    
    /**
     * Configures polygon offset with explicit command context.
     * This is the Vulkan-compatible implementation for depth offset.
     * 
     * OpenGL implementation: Direct mapping to glPolygonOffset()
     * Vulkan implementation: Part of pipeline state (depthBias)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param factor Scale factor for depth slope
     * @param units Constant depth offset value
     */
    @Override
    public void configurePolygonOffset(CommandContext ctx, float factor, float units) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glPolygonOffset(factor, units);
    }
    
    /**
     * Configures logical operation with explicit command context.
     * This is the Vulkan-compatible implementation for logic ops.
     * 
     * OpenGL implementation: Direct mapping to glLogicOp()
     * Vulkan implementation: Part of pipeline state
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param opcode The logical operation code
     */
    @Override
    public void configureLogicOp(CommandContext ctx, int opcode) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glLogicOp(opcode);
    }
    
    /**
     * Sets clear depth value with explicit command context.
     * This is the Vulkan-compatible implementation for depth clear value.
     * 
     * OpenGL implementation: Direct mapping to glClearDepth()
     * Vulkan implementation: Clear values specified in render pass begin
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param depth The depth clear value
     */
    @Override
    public void setClearDepthValue(CommandContext ctx, double depth) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glClearDepth(depth);
    }
    
    /**
     * Sets clear color value with explicit command context.
     * This is the Vulkan-compatible implementation for color clear value.
     * 
     * OpenGL implementation: Direct mapping to glClearColor()
     * Vulkan implementation: Clear values specified in render pass begin
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param red Red component
     * @param green Green component
     * @param blue Blue component
     * @param alpha Alpha component
     */
    @Override
    public void setClearColorValue(CommandContext ctx, float red, float green, float blue, float alpha) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glClearColor(red, green, blue, alpha);
    }
    
    /**
     * Selects draw buffer with explicit command context.
     * This is the Vulkan-compatible implementation for draw buffer selection.
     * 
     * OpenGL implementation: Direct mapping to glDrawBuffer()
     * Vulkan implementation: Specified in render pass attachment descriptions
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param mode The draw buffer mode
     */
    @Override
    public void selectDrawBuffer(CommandContext ctx, int mode) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glDrawBuffer(mode);
    }
    
    /**
     * Allocates buffer object with explicit command context.
     * This is the Vulkan-compatible implementation for buffer creation.
     * 
     * OpenGL implementation: Direct mapping to glGenBuffers()
     * Vulkan implementation: Will use vkCreateBuffer()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @return The newly created buffer object ID
     */
    @Override
    public int allocateBufferObject(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL15.glGenBuffers();
    }
    
    /**
     * Releases buffer object with explicit command context.
     * This is the Vulkan-compatible implementation for buffer deletion.
     * 
     * OpenGL implementation: Direct mapping to glDeleteBuffers()
     * Vulkan implementation: Will use vkDestroyBuffer()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param buf The buffer object ID to release
     */
    @Override
    public void releaseBufferObject(CommandContext ctx, int buf) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL15.glDeleteBuffers(buf);
    }
    
    /**
     * Creates vertex array object with explicit command context.
     * This is the Vulkan-compatible implementation for VAO creation.
     * 
     * OpenGL implementation: Direct mapping to glGenVertexArrays()
     * Vulkan implementation: State baked into pipeline (no direct equivalent)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @return The newly created vertex array object ID
     */
    @Override
    public int createVertexArrayObject(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL30.glGenVertexArrays();
    }
    
    /**
     * Generates framebuffer object with explicit command context.
     * This is the Vulkan-compatible implementation for FBO creation.
     * 
     * OpenGL implementation: Direct mapping to glGenFramebuffers()
     * Vulkan implementation: Will use vkCreateFramebuffer()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @return The newly created framebuffer object ID
     */
    @Override
    public int generateFramebufferObject(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL30.glGenFramebuffers();
    }
    
    /**
     * Destroys framebuffer object with explicit command context.
     * This is the Vulkan-compatible implementation for FBO deletion.
     * 
     * OpenGL implementation: Direct mapping to glDeleteFramebuffers()
     * Vulkan implementation: Will use vkDestroyFramebuffer()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param fbo The framebuffer object ID to destroy
     */
    @Override
    public void destroyFramebufferObject(CommandContext ctx, int fbo) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glDeleteFramebuffers(fbo);
    }
    
    /**
     * Selects vertex array object with explicit command context.
     * This is the Vulkan-compatible implementation for VAO binding.
     * 
     * OpenGL implementation: Direct mapping to glBindVertexArray()
     * Vulkan implementation: State baked into pipeline (no direct equivalent)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param vao The vertex array object ID to bind
     */
    @Override
    public void selectVertexArray(CommandContext ctx, int vao) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glBindVertexArray(vao);
    }
    
    /**
     * Fills buffer with data with explicit command context.
     * This is the Vulkan-compatible implementation for buffer data upload.
     * 
     * OpenGL implementation: Direct mapping to glBufferData()
     * Vulkan implementation: Will use vkCmdUpdateBuffer() or memory mapping
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param tgt The buffer binding target
     * @param dat The data to upload
     * @param usg Usage hint for the buffer
     */
    @Override
    public void fillBufferWithData(CommandContext ctx, int tgt, java.nio.ByteBuffer dat, int usg) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL15.glBufferData(tgt, dat, usg);
    }
    
    /**
     * Allocates buffer storage with explicit command context.
     * This is the Vulkan-compatible implementation for buffer allocation.
     * 
     * OpenGL implementation: Direct mapping to glBufferData() with null data
     * Vulkan implementation: Will use vkCreateBuffer()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param tgt The buffer binding target
     * @param sz The size in bytes to allocate
     * @param usg Usage hint for the buffer
     */
    @Override
    public void fillBufferWithSize(CommandContext ctx, int tgt, long sz, int usg) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL15.glBufferData(tgt, sz, usg);
    }
    
    /**
     * Checks for errors with explicit command context.
     * This is the Vulkan-compatible implementation for error checking.
     * 
     * OpenGL implementation: Direct mapping to glGetError()
     * Vulkan implementation: Will query validation layers
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @return The error code, or NO_ERROR (0) if no error occurred
     */
    @Override
    public int checkForErrors(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glGetError();
    }
    
    @Override
    public void attachBuffer(CommandContext ctx, int target, int buffer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL15.glBindBuffer(target, buffer);
    }
    
    @Override
    public int pollErrorCode(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glGetError();
    }
    
    @Override
    public void readFramebufferPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, long pixels) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Override
    public int queryTextureLevelParameter(CommandContext ctx, int target, int level, int pname) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }
    
    /**
     * Updates buffer subregion with explicit command context.
     * This is the Vulkan-compatible implementation for partial buffer updates.
     * 
     * OpenGL implementation: Direct mapping to glBufferSubData()
     * Vulkan implementation: Will use vkCmdUpdateBuffer() or staging buffer
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param tgt The buffer binding target
     * @param off Offset in bytes
     * @param dat The data to upload
     */
    @Override
    public void fillBufferSubregion(CommandContext ctx, int tgt, long off, java.nio.ByteBuffer dat) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL15.glBufferSubData(tgt, off, dat);
    }
    
    /**
     * Maps buffer region with explicit command context.
     * This is the Vulkan-compatible implementation for buffer memory mapping.
     * 
     * OpenGL implementation: Direct mapping to glMapBufferRange()
     * Vulkan implementation: Will use vkMapMemory()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param tgt The buffer binding target
     * @param off Offset in bytes
     * @param len Length in bytes
     * @param acc Access flags
     * @return ByteBuffer providing access to mapped memory
     */
    @Override
    public java.nio.ByteBuffer mapBufferRegion(CommandContext ctx, int tgt, int off, int len, int acc) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL30.glMapBufferRange(tgt, off, len, acc);
    }
    
    /**
     * Unmaps buffer with explicit command context.
     * This is the Vulkan-compatible implementation for buffer unmapping.
     * 
     * OpenGL implementation: Direct mapping to glUnmapBuffer()
     * Vulkan implementation: Will use vkUnmapMemory()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param tgt The buffer binding target
     */
    @Override
    public void unmapBufferData(CommandContext ctx, int tgt) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL15.glUnmapBuffer(tgt);
    }
    
    /**
     * Copies framebuffer region (blit) with explicit command context.
     * This is the Vulkan-compatible implementation for framebuffer blitting.
     * 
     * OpenGL implementation: Direct mapping to glBlitFramebuffer()
     * Vulkan implementation: Will use vkCmdBlitImage()
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param srcX0 Source minimum X
     * @param srcY0 Source minimum Y
     * @param srcX1 Source maximum X
     * @param srcY1 Source maximum Y
     * @param dstX0 Destination minimum X
     * @param dstY0 Destination minimum Y
     * @param dstX1 Destination maximum X
     * @param dstY1 Destination maximum Y
     * @param msk Buffer mask
     * @param flt Filter mode
     */
    @Override
    public void copyFramebufferRegion(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, 
                                      int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
    }
    
    /**
     * Transfers 2D texture image data with explicit command context.
     * This is the Vulkan-compatible implementation for texture uploads.
     * 
     * OpenGL implementation: Direct mapping to glTexImage2D()
     * Vulkan implementation: Will use vkCmdCopyBufferToImage() with staging buffer
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param tgt Texture target
     * @param lvl Mipmap level
     * @param intfmt Internal format
     * @param w Width
     * @param h Height
     * @param bdr Border (must be 0)
     * @param fmt Pixel format
     * @param typ Pixel type
     * @param pix Pixel data buffer
     */
    @Override
    public void transferTexture2DImage(CommandContext ctx, int tgt, int lvl, int intfmt, int w, int h, 
                                       int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glTexImage2D(tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    @Override
    public void transferTexture2DSubregion(CommandContext ctx, int tgt, int lvl, int xoff, int yoff, 
                                           int w, int h, int fmt, int typ, long pix) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glTexSubImage2D(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Override
    public void transferTexture2DSubregionBuf(CommandContext ctx, int tgt, int lvl, int xoff, int yoff, 
                                              int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glTexSubImage2D(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    /**
     * Creates a shader object of the specified type.
     * Uses CommandContext for future Vulkan compatibility.
     */
    @Override
    public int constructShaderObject(CommandContext ctx, int shaderType) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glCreateShader(shaderType);
    }
    
    /**
     * Deletes a shader object.
     * Uses CommandContext for future Vulkan compatibility.
     */
    @Override
    public void disposeShaderObject(CommandContext ctx, int shader) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glDeleteShader(shader);
    }
    
    /**
     * Compiles shader source code.
     * Uses CommandContext for future Vulkan compatibility.
     */
    @Override
    public void compileShaderSource(CommandContext ctx, int shader) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glCompileShader(shader);
    }
    
    /**
     * Creates a program object.
     * Uses CommandContext for future Vulkan compatibility.
     */
    @Override
    public int constructProgramObject(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glCreateProgram();
    }
    
    /**
     * Deletes a program object.
     * Uses CommandContext for future Vulkan compatibility.
     */
    @Override
    public void disposeProgramObject(CommandContext ctx, int program) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glDeleteProgram(program);
    }
    
    /**
     * Uploads shader source code using CommandContext for Vulkan compatibility.
     */
    @Override
    public void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, stringCount, pointerBufferAddress, lengthsPointer);
    }
    
    /**
     * Uploads shader source code (native version) using CommandContext for Vulkan compatibility.
     */
    @Override
    public void uploadShaderSourceNative(CommandContext ctx, int shader, int count, long strings, long length) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, count, strings, length);
    }
    
    /**
     * Attaches shader to program using CommandContext for Vulkan compatibility.
     */
    @Override
    public void attachShaderToProgram(CommandContext ctx, int program, int shader) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glAttachShader(program, shader);
    }
    
    /**
     * Links program object using CommandContext for Vulkan compatibility.
     */
    @Override
    public void linkProgramBinary(CommandContext ctx, int program) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glLinkProgram(program);
    }
    
    /**
     * Detaches shader from program using CommandContext for Vulkan compatibility.
     */
    @Override
    public void glDetachShader(CommandContext ctx, int program, int shader) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL32C.glDetachShader(program, shader);
    }
    
    /**
     * Binds a vertex attribute variable name to a specific attribute index with explicit command context.
     * This is the Vulkan-compatible implementation for attribute binding.
     * 
     * OpenGL implementation: Direct mapping to glBindAttribLocation()
     * Vulkan implementation: Attribute locations specified in SPIR-V via layout(location=X)
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param program The program object ID
     * @param index The attribute index to bind to
     * @param name The name of the vertex attribute variable
     */
    @Override
    public void bindAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glBindAttribLocation(program, index, name);
    }
    
    /**
     * Queries the location of a vertex attribute variable with explicit command context.
     * This is the Vulkan-compatible implementation for attribute location queries.
     * 
     * OpenGL implementation: Direct mapping to glGetAttribLocation()
     * Vulkan implementation: Reflection or pre-defined attribute locations
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param program The linked program object ID
     * @param name The name of the vertex attribute variable
     * @return The attribute location/index, or -1 if not found
     */
    @Override
    public int getAttributeLocation(CommandContext ctx, int program, CharSequence name) {
        // Validate context is immediate mode for OpenGL)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glGetAttribLocation(program, name);
    }
    
    /**
     * Queries the location of a uniform variable with explicit command context.
     * This is the Vulkan-compatible implementation for uniform location queries.
     * 
     * OpenGL implementation: Direct mapping to glGetUniformLocation()
     * Vulkan implementation: Descriptor set bindings or reflection
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param program The linked program object ID
     * @param name The name of the uniform variable
     * @return The uniform location, or -1 if not found
     */
    @Override
    public int locateUniformVariable(CommandContext ctx, int program, CharSequence name) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glGetUniformLocation(program, name);
    }
    
    /**
     * Sets a single integer uniform value with explicit command context.
     * This is the Vulkan-compatible implementation for uniform updates.
     * 
     * OpenGL implementation: Direct mapping to glUniform1i()
     * Vulkan implementation: Push constants or descriptor set updates
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param location The uniform location
     * @param value The integer value to assign
     */
    @Override
    public void assignUniformInteger(CommandContext ctx, int location, int value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform1i(location, value);
    }
    
    /**
     * Sets a single float uniform value with explicit command context.
     * This is the Vulkan-compatible implementation for uniform updates.
     * 
     * OpenGL implementation: Direct mapping to glUniform1f()
     * Vulkan implementation: Push constants or descriptor set updates
     * 
     * @param ctx Command context (must be immediate mode for OpenGL)
     * @param location The uniform location
     * @param value The float value to assign
     */
    @Override
    public void assignUniformFloat(CommandContext ctx, int location, float value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform1f(location, value);
    }
    
    @Override
    public void assignUniformFloat3(CommandContext ctx, int location, float x, float y, float z) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform3f(location, x, y, z);
    }
    
    @Override
    public void assignUniformInteger3(CommandContext ctx, int location, int x, int y, int z) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform3i(location, x, y, z);
    }
    
    @Override
    public void assignUniformFloat4(CommandContext ctx, int location, float x, float y, float z, float w) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform4f(location, x, y, z, w);
    }
    
    @Override
    public void assignUniformMatrix4(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniformMatrix4fv(location, transpose, value);
    }
    
    @Override
    public void activateVertexAttributeArray(CommandContext ctx, int index) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glEnableVertexAttribArray(index);
    }
    
    @Override
    public void assignUniformFloat2(CommandContext ctx, int location, float x, float y) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform2f(location, x, y);
    }
    
    @Override
    public void assignUniformInteger2(CommandContext ctx, int location, int x, int y) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform2i(location, x, y);
    }
    
    @Override
    public void copyTexture2DSubImage(CommandContext ctx, int target, int level, int xoffset, int yoffset, 
                                      int x, int y, int width, int height) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Override
    public void readPixelsFromFramebuffer(CommandContext ctx, int x, int y, int width, int height, 
                                          int format, int type, float[] pixels) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Override
    public void setMemoryBarrier(CommandContext ctx, int barriers) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL42.glMemoryBarrier(barriers);
    }
    
    @Override
    public void clearFloatBuffer(CommandContext ctx, int buffer, int drawbuffer, float[] values) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glClearBufferfv(buffer, drawbuffer, values);
    }
    
    @Override
    public void clearIntegerBuffer(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glClearBufferiv(buffer, drawbuffer, values);
    }
    
    @Override
    public void configureVertexAttributeIntegerPointer(CommandContext ctx, int index, int size, int type,
                                                       int stride, long pointer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void setStaticViewport(CommandContext ctx, int x, int y, int width, int height) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glViewport(x, y, width, height);
    }
    
    @Override
    public void configureVertexAttributePointer(CommandContext ctx, int index, int size, int type,
                                               boolean normalized, int stride, long pointer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void deactivateVertexAttributeArray(CommandContext ctx, int index) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glDisableVertexAttribArray(index);
    }
    
    @Override
    public void assignUniformMatrix3(CommandContext ctx, int location, boolean transpose, FloatBuffer value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniformMatrix3fv(location, transpose, value);
    }
    
    @Override
    public void assignUniformMatrix3Array(CommandContext ctx, int location, boolean transpose, float[] value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniformMatrix3fv(location, transpose, value);
    }
    
    @Override
    public void setBlendEquation(CommandContext ctx, int mode) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glBlendEquation(mode);
    }
    
    @Override
    public int queryShaderParameter(CommandContext ctx, int shader, int pname) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glGetShaderi(shader, pname);
    }
    
    @Override
    public String retrieveShaderInfoLog(CommandContext ctx, int shader) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glGetShaderInfoLog(shader);
    }
    
    @Override
    public void bindVertexArray(CommandContext ctx, int array) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glBindVertexArray(array);
    }
    
    @Override
    public void attachVertexBuffer(CommandContext ctx, int bindingIndex, int buffer, long offset, int stride) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBVertexAttribBinding.glBindVertexBuffer(bindingIndex, buffer, offset, stride);
    }
    
    @Override
    public void associateVertexAttrib(CommandContext ctx, int attribIndex, int bindingIndex) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribBinding(attribIndex, bindingIndex);
    }
    
    @Override
    public void createBufferObjects(CommandContext ctx, int[] buffers) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = GL15.glGenBuffers();
        }
    }
    
    @Override
    public int createSingleBufferObject(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL15.glGenBuffers();
    }
    
    @Override
    public int generateQueryObject(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL15.glGenQueries();
    }
    
    @Override
    public void initiateQuery(CommandContext ctx, int target, int id) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glBeginQuery(target, id);
    }
    
    @Override
    public void concludeQuery(CommandContext ctx, int target) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glEndQuery(target);
    }
    
    @Override
    public void disposeQueryObject(CommandContext ctx, int id) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL15.glDeleteQueries(id);
    }
    
    @Override
    public int retrieveQueryObjectInt(CommandContext ctx, int id, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL15.glGetQueryObjecti(id, pname);
    }
    
    @Override
    public long retrieveQueryObjectInt64(CommandContext ctx, int id, int pname) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL33.glGetQueryObjecti64(id, pname);
    }
    
    @Override
    public void labelDebugObject(CommandContext ctx, int identifier, int name, String label) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL43.glObjectLabel(identifier, name, label);
    }
    
    @Override
    public String retrieveActiveUniformBlockName(CommandContext ctx, int program, int uniformBlockIndex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        return GL31.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    @Override
    public void enterDebugGroup(CommandContext ctx, int source, int id, CharSequence message) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL43.glPushDebugGroup(source, id, message);
    }
    
    @Override
    public void exitDebugGroup(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL43.glPopDebugGroup();
    }
    
    @Override
    public void labelObjectExt(CommandContext ctx, int type, int object, String label) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        EXTDebugLabel.glLabelObjectEXT(type, object, label);
    }
    
    @Override
    public void multiDrawElementsBaseVertex(CommandContext ctx, int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    @Override
    public void createBufferStorage(CommandContext ctx, int target, long size, int flags) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL44.glBufferStorage(target, size, flags);
    }
    
    @Override
    public void createBufferStorage(CommandContext ctx, int target, ByteBuffer data, int flags) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL44.glBufferStorage(target, data, flags);
    }
    
    @Override
    public void specifyVertexAttribFormat(CommandContext ctx, int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL43.glVertexAttribFormat(attribIndex, size, type, normalized, relativeOffset);
    }
    
    @Override
    public void specifyVertexAttribIFormat(CommandContext ctx, int attribIndex, int size, int type, int relativeOffset) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL43.glVertexAttribIFormat(attribIndex, size, type, relativeOffset);
    }
    
    @Override
    public void attachBufferToTexture(CommandContext ctx, int target, int internalFormat, int buffer) {
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        GL31.glTexBuffer(target, internalFormat, buffer);
    }
    
    // ================================================================================
    // DEPRECATED METHODS - OpenGL immediate-mode implementations
    // ================================================================================
    
    
    
    // Direct State Access buffer operations
    @Deprecated
    @Override
    public int createBufferDSA() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers();
    }
    
    @Deprecated
    @Override
    public void namedBufferDataDSA(int buffer, long size, int usage) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData(buffer, size, usage);
    }
    
    @Deprecated
    @Override
    public void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData(buffer, data, usage);
    }
    
    @Deprecated
    @Override
    public void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferSubData(buffer, offset, data);
    }
    
    @Deprecated
    @Override
    public void namedBufferStorageDSA(int buffer, long size, int flags) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage(buffer, size, flags);
    }
    
    @Deprecated
    @Override
    public void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage(buffer, data, flags);
    }
    
    @Deprecated
    @Override
    public java.nio.ByteBuffer mapNamedBufferRangeDSA(int buffer, long offset, long length, int access) {
        return org.lwjgl.opengl.ARBDirectStateAccess.glMapNamedBufferRange(buffer, offset, length, access);
    }
    
    @Deprecated
    @Override
    public void unmapNamedBufferDSA(int buffer) {
        org.lwjgl.opengl.ARBDirectStateAccess.glUnmapNamedBuffer(buffer);
    }
    
    @Deprecated
    @Override
    public void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length) {
        org.lwjgl.opengl.ARBDirectStateAccess.glFlushMappedNamedBufferRange(buffer, offset, length);
    }
    
    @Deprecated
    @Override
    public void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // Direct State Access framebuffer operations
    @Deprecated
    @Override
    public int createFramebufferDSA() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    @Deprecated
    @Override
    public void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Deprecated
    @Override
    public void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                                                      dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    
    
    @Deprecated
    @Override
    public void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        GL11.glTexImage2D(tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    
    @Deprecated
    @Override
    public void unmapBufferData(int tgt) {
        GL15.glUnmapBuffer(tgt);
    }
    
    
    @Deprecated
    @Override
    public void linkProgramBinary(int program) {
        GL20.glLinkProgram(program);
    }
    
    @Deprecated
    @Override
    public void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, stringCount, pointerBufferAddress, lengthsPointer);
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
    
    // CommandContext versions of debug and capability methods
    @Override
    public boolean supportsKhrDebug(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL.getCapabilities().GL_KHR_debug;
    }
    
    @Override
    public boolean supportsArbDebugOutput(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_debug_output;
    }
    
    @Override
    public void setupKhrDebugSystem(CommandContext ctx, int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
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
    public void setupArbDebugSystem(CommandContext ctx, int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
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
    public boolean hasBufferStorageExtension(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_buffer_storage;
    }
    
    @Override
    public boolean hasVertexAttribBindingExtension(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL.getCapabilities().GL_ARB_vertex_attrib_binding;
    }
    
    @Override
    public int querySyncStatus(CommandContext ctx, long sync, int pname, java.nio.IntBuffer length) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
        return org.lwjgl.opengl.GL32C.glGetSynci(sync, pname, length);
    }
    
    @Override
    public GraphicsCapabilities obtainGraphicsCapabilities(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
        return convertCapabilities(org.lwjgl.opengl.GL.getCapabilities());
    }
    
    @Override
    public GraphicsCapabilities initializeGraphicsCapabilities(CommandContext ctx) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
        return convertCapabilities(org.lwjgl.opengl.GL.createCapabilities());
    }
    
    @Override
    public boolean checkFunctionAvailable(CommandContext ctx, String functionName) {
        if (!ctx.isImmediate()) {
            throw new IllegalStateException("OpenGL backend requires immediate-mode CommandContext");
        }
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
        org.lwjgl.opengl.GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Deprecated
    @Override
    public void deleteVertexArray(int vertexArray) {
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(vertexArray);
    }
    
    @Deprecated
    @Override
    public void flushMappedBufferRange(int target, long offset, long length) {
        org.lwjgl.opengl.GL30.glFlushMappedBufferRange(target, offset, length);
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
    
    @Deprecated
    @Override
    public void glGetIntegerv(int pname, int[] params) {
        org.lwjgl.opengl.GL32C.glGetIntegerv(pname, params);
    }
    
    @Deprecated
    @Override
    public void glGetFloatv(int pname, float[] params) {
        org.lwjgl.opengl.GL32C.glGetFloatv(pname, params);
    }
    
    @Deprecated
    @Override
    public void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix) {
        org.lwjgl.opengl.GL32C.glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, float[] matrix) {
        org.lwjgl.opengl.GL32C.glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Deprecated
    @Override
    public void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        org.lwjgl.opengl.GL32C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    @Deprecated
    @Override
    public void glUniform1f(int location, float v0) {
        org.lwjgl.opengl.GL32C.glUniform1f(location, v0);
    }
    
    @Deprecated
    @Override
    public void glUniform2f(int location, float v0, float v1) {
        org.lwjgl.opengl.GL32C.glUniform2f(location, v0, v1);
    }
    
    @Deprecated
    @Override
    public void glUniform2i(int location, int v0, int v1) {
        org.lwjgl.opengl.GL32C.glUniform2i(location, v0, v1);
    }
    
    @Deprecated
    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        org.lwjgl.opengl.GL32C.glUniform3f(location, v0, v1, v2);
    }
    
    @Deprecated
    @Override
    public void glUniform3i(int location, int v0, int v1, int v2) {
        org.lwjgl.opengl.GL32C.glUniform3i(location, v0, v1, v2);
    }
    
    @Deprecated
    @Override
    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        org.lwjgl.opengl.GL32C.glUniform4f(location, v0, v1, v2, v3);
    }
    
    @Deprecated
    @Override
    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        org.lwjgl.opengl.GL32C.glUniform4i(location, v0, v1, v2, v3);
    }
    
    @Deprecated
    @Override
    public void glTexParameteriv(int target, int pname, int[] params) {
        org.lwjgl.opengl.GL32C.glTexParameteriv(target, pname, params);
    }
    
    @Deprecated
    @Override
    public void glTexParameterf(int target, int pname, float param) {
        org.lwjgl.opengl.GL32C.glTexParameterf(target, pname, param);
    }
    
    @Deprecated
    @Override
    public String glGetProgramInfoLog(int program) {
        return org.lwjgl.opengl.GL32C.glGetProgramInfoLog(program);
    }
    
    @Deprecated
    @Override
    public String glGetShaderInfoLog(int shader) {
        return org.lwjgl.opengl.GL32C.glGetShaderInfoLog(shader);
    }
    
    @Deprecated
    @Override
    public void glDrawBuffers(int[] buffers) {
        org.lwjgl.opengl.GL32C.glDrawBuffers(buffers);
    }
    
    @Deprecated
    @Override
    public void glReadBuffer(int buffer) {
        org.lwjgl.opengl.GL32C.glReadBuffer(buffer);
    }
    
    @Deprecated
    @Override
    public void glClearBufferuiv(int buffer, int drawbuffer, int[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferuiv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    @Override
    public String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return org.lwjgl.opengl.GL32C.glGetActiveUniform(program, index, size, type, name);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, float[] data, int usage) {
        org.lwjgl.opengl.GL32C.glBufferData(target, data, usage);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, int[] data, int usage) {
        org.lwjgl.opengl.GL32C.glBufferData(target, data, usage);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, java.nio.ByteBuffer data, int usage) {
        org.lwjgl.opengl.GL32C.glBufferData(target, data, usage);
    }
    
    @Deprecated
    @Override
    public void glBufferData(int target, long size, int usage) {
        org.lwjgl.opengl.GL32C.glBufferData(target, size, usage);
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
    public void glBindBufferBase(int target, int index, int buffer) {
        org.lwjgl.opengl.GL43C.glBindBufferBase(target, index, buffer);
    }
    
    @Deprecated
    @Override
    public void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        org.lwjgl.opengl.GL32C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    @Deprecated
    @Override
    public void glDetachShader(int program, int shader) {
        org.lwjgl.opengl.GL32C.glDetachShader(program, shader);
    }
    
    @Deprecated
    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        org.lwjgl.opengl.GL32C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Deprecated
    @Override
    public int glGetTexParameteri(int target, int pname) {
        return org.lwjgl.opengl.GL32C.glGetTexParameteri(target, pname);
    }
    
    @Deprecated
    @Override
    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.OpenGL42 || caps.GL_ARB_shader_image_load_store) {
            org.lwjgl.opengl.GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
        } else {
            org.lwjgl.opengl.EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
        }
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
    
    @Deprecated
    @Override
    public void glGenBuffers(int[] buffers) {
        org.lwjgl.opengl.GL43C.glGenBuffers(buffers);
    }
    
    @Deprecated
    @Override
    public void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        org.lwjgl.opengl.GL43C.glClearBufferSubData(target, internalformat, offset, size, format, type, data);
    }
    
    @Deprecated
    @Override
    public void glGetProgramiv(int program, int pname, int[] params) {
        org.lwjgl.opengl.GL32C.glGetProgramiv(program, pname, params);
    }
    
    @Deprecated
    @Override
    public void glDispatchCompute(int workX, int workY, int workZ) {
        org.lwjgl.opengl.GL45C.glDispatchCompute(workX, workY, workZ);
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
        GL11.glBlendFunc(sfactor, dfactor);
    }
    
    @Deprecated
    @Override
    public void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    @Override
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return org.lwjgl.opengl.GL32C.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Deprecated
    @Override
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        org.lwjgl.opengl.GL32C.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Deprecated
    @Override
    public int glGenSamplers() {
        return org.lwjgl.opengl.GL33C.glGenSamplers();
    }
    
    @Deprecated
    @Override
    public void glDeleteSamplers(int sampler) {
        org.lwjgl.opengl.GL33C.glDeleteSamplers(sampler);
    }
    
    @Deprecated
    @Override
    public void glBindSampler(int unit, int sampler) {
        org.lwjgl.opengl.GL33C.glBindSampler(unit, sampler);
    }
    
    @Deprecated
    @Override
    public void glBindSamplers(int first, int[] samplers) {
        org.lwjgl.opengl.GL45C.glBindSamplers(first, samplers);
    }
    
    @Deprecated
    @Override
    public void glSamplerParameteri(int sampler, int pname, int param) {
        org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, pname, param);
    }
    
    @Deprecated
    @Override
    public void glSamplerParameterf(int sampler, int pname, float param) {
        org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, pname, param);
    }
    
    @Deprecated
    @Override
    public void glSamplerParameteriv(int sampler, int pname, int[] params) {
        org.lwjgl.opengl.GL33C.glSamplerParameteriv(sampler, pname, params);
    }
    
    @Deprecated
    @Override
    public int glGetInteger(int pname) {
        return org.lwjgl.opengl.GL32C.glGetInteger(pname);
    }
    
    @Deprecated
    @Override
    public void glDeleteBuffers(int buffer) {
        org.lwjgl.opengl.GL43C.glDeleteBuffers(buffer);
    }
    
    @Deprecated
    @Override
    public void glPolygonMode(int face, int mode) {
        org.lwjgl.opengl.GL43C.glPolygonMode(face, mode);
    }
    
    @Deprecated
    @Override
    public void glViewport(int x, int y, int width, int height) {
        org.lwjgl.opengl.GL11.glViewport(x, y, width, height);
    }
    
    @Deprecated
    @Override
    public void glDispatchComputeIndirect(long offset) {
        org.lwjgl.opengl.GL43C.glDispatchComputeIndirect(offset);
    }
    
    @Deprecated
    @Override
    public void glBindBuffer(int target, int buffer) {
        org.lwjgl.opengl.GL46C.glBindBuffer(target, buffer);
    }
    
    @Deprecated
    @Override
    public String glGetStringi(int name, int index) {
        return org.lwjgl.opengl.GL46C.glGetStringi(name, index);
    }
    
    @Deprecated
    @Override
    public void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth) {
        org.lwjgl.opengl.GL46C.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel, dstX, dstY, dstZ, width, height, depth);
    }
    
    @Deprecated
    @Override
    public int glCheckFramebufferStatus(int target) {
        return org.lwjgl.opengl.GL46C.glCheckFramebufferStatus(target);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        org.lwjgl.opengl.GL46C.glUniformMatrix3fv(location, transpose, value);
    }
    
    @Deprecated
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
        org.lwjgl.opengl.GL46C.glUniformMatrix3fv(location, transpose, value);
    }
    
    @Deprecated
    @Override
    public void glClearColor(float r, float g, float b, float a) {
        org.lwjgl.opengl.GL46C.glClearColor(r, g, b, a);
    }
    
    @Deprecated
    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        return org.lwjgl.opengl.GL46C.glGetAttribLocation(program, name);
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
    
    @Deprecated
    @Override
    public void glGenerateTextureMipmap(int texture) {
        org.lwjgl.opengl.ARBDirectStateAccess.glGenerateTextureMipmap(texture);
    }
    
    @Deprecated
    @Override
    public void glTextureParameteri(int texture, int pname, int param) {
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }
    
    @Deprecated
    @Override
    public void glTextureParameterf(int texture, int pname, float param) {
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }
    
    @Deprecated
    @Override
    public void glTextureParameteriv(int texture, int pname, int[] params) {
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteriv(texture, pname, params);
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
    
    @Deprecated
    @Override
    public int glGetTextureParameteri(int texture, int pname) {
        return org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
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
        org.lwjgl.opengl.GL32.glBlendEquation(mode);
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
        org.lwjgl.opengl.GL43C.glBindVertexBuffer(bindingindex, buffer, offset, stride);
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
    public void setupDebugMessageCallback(java.io.PrintStream stream) {
        org.lwjgl.opengl.GLUtil.setupDebugMessageCallback(stream);
    }
    
    // Capability checking methods
    
    @Deprecated
    @Override
    public boolean checkOpenGL32Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities(OpenGLCommandContext.IMMEDIATE);
        return caps.OpenGL32;
    }
    
    @Deprecated
    @Override
    public boolean checkOpenGL33Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities(OpenGLCommandContext.IMMEDIATE);
        return caps.OpenGL33;
    }
    
    @Deprecated
    @Override
    public boolean checkARBInstancedArraysSupport() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities(OpenGLCommandContext.IMMEDIATE);
        return caps.GL_ARB_instanced_arrays;
    }
    
    @Deprecated
    @Override
    public long getNamedBufferDataPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities(OpenGLCommandContext.IMMEDIATE);
        return caps.glNamedBufferData;
    }
    
    @Deprecated
    @Override
    public long getBufferStoragePointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities(OpenGLCommandContext.IMMEDIATE);
        return caps.glBufferStorage;
    }
    
    @Deprecated
    @Override
    public long getBindVertexBufferPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities(OpenGLCommandContext.IMMEDIATE);
        return caps.glBindVertexBuffer;
    }
    
    @Deprecated
    @Override
    public long getVertexAttribBindingPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities(OpenGLCommandContext.IMMEDIATE);
        return caps.glVertexAttribBinding;
    }
    
    // Additional GL query and state methods
    
    
    // Additional texture methods
    
    @Deprecated
    @Override
    public int glGenTextures() {
        return org.lwjgl.opengl.GL11.glGenTextures();
    }
    
    // ===========================
    // Phase 12: Shader Query & State Retrieval Methods (CommandContext-aware)
    // ===========================
    
    @Override
    public int queryProgramParameter(CommandContext ctx, int program, int pname) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glGetProgrami(program, pname);
    }
    
    @Override
    public String retrieveProgramInfoLog(CommandContext ctx, int program) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glGetProgramInfoLog(program);
    }
    
    @Override
    public int queryIntegerState(CommandContext ctx, int pname) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glGetInteger(pname);
    }
    
    @Override
    public void activateShaderProgram(CommandContext ctx, int program) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUseProgram(program);
    }
    
    @Override
    public void destroyShaderProgram(CommandContext ctx, int program) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glDeleteProgram(program);
    }
    
    // Phase 14 implementations
    
    @Override
    public void deleteVertexArray(CommandContext ctx, int array) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glDeleteVertexArrays(array);
    }
    
    @Override
    public void configureVertexAttribute(CommandContext ctx, int index, int size, int type, boolean normalized, int stride, long pointer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void configureVertexAttributeInteger(CommandContext ctx, int index, int size, int type, int stride, long pointer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void activateVertexAttribute(CommandContext ctx, int index) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glEnableVertexAttribArray(index);
    }
    
    @Override
    public void deactivateVertexAttribute(CommandContext ctx, int index) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glDisableVertexAttribArray(index);
    }
    
    @Override
    public void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL33.glVertexAttribDivisor(index, divisor);
    }
    
    @Override
    public void assignUniformFloat2v(CommandContext ctx, int location, float[] value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform2fv(location, value);
    }
    
    @Override
    public void assignUniformFloat3v(CommandContext ctx, int location, float[] value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform3fv(location, value);
    }
    
    @Override
    public void assignUniformFloat4v(CommandContext ctx, int location, float[] value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniform4fv(location, value);
    }
    
    @Override
    public void assignUniformMatrix4f(CommandContext ctx, int location, java.nio.FloatBuffer matrix) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniformMatrix4fv(location, false, matrix);
    }
    
    @Override
    public void assignUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer value) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glUniformMatrix4fv(location, transpose, value);
    }
    
    @Override
    public int locateUniformBlock(CommandContext ctx, int program, String uniformBlockName) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL31.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Override
    public void bindUniformBlock(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Override
    public void attachUniformBufferRange(CommandContext ctx, int target, int index, int buffer, long offset, long size) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glBindBufferRange(target, index, buffer, offset, size);
    }
    
    @Override
    public void bindUniformBufferBase(CommandContext ctx, int binding, int bufferId) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, bufferId);
    }
    
    @Override
    public void bindFragmentDataLocation(CommandContext ctx, int program, int colorNumber, CharSequence name) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glBindFragDataLocation(program, colorNumber, name);
    }
    
    @Override
    public void queryFloatState(CommandContext ctx, int pname, float[] params) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glGetFloatv(pname, params);
    }
    
    @Override
    public void setReadBuffer(CommandContext ctx, int mode) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glReadBuffer(mode);
    }
    
    @Override
    public void setDrawBuffers(CommandContext ctx, int[] bufs) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glDrawBuffers(bufs);
    }
    
    @Override
    public void glBufferStorage(CommandContext ctx, int target, long size, int flags) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL44.glBufferStorage(target, size, flags);
    }
    
    @Override
    public void glBufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL44.glBufferStorage(target, data, flags);
    }
    
    @Override
    public java.nio.ByteBuffer glMapBufferRange(CommandContext ctx, int target, long offset, long length, int access) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL30.glMapBufferRange(target, offset, length, access);
    }
    
    @Override
    public void glDispatchCompute(CommandContext ctx, int workX, int workY, int workZ) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL43.glDispatchCompute(workX, workY, workZ);
    }
    
    @Override
    public void glFramebufferTexture2D(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public void glBindImageTexture(CommandContext ctx, int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }
    
    @Override
    public void glBindSampler(CommandContext ctx, int unit, int sampler) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.GL33.glBindSampler(unit, sampler);
    }
    
    @Override
    public long createFenceSync(CommandContext ctx, int condition, int flags) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL32.glFenceSync(condition, flags);
    }
    
    @Override
    public int waitForSync(CommandContext ctx, long sync, int flags, long timeout) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL32.glClientWaitSync(sync, flags, timeout);
    }
    
    @Override
    public void destroySync(CommandContext ctx, long sync) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL32.glDeleteSync(sync);
    }
    
    @Override
    public String getBackendName() {
        return "OpenGL";
    }
    
    // Phase 18: Info query and capability methods with CommandContext
    
    @Override
    public String queryStringInfo(CommandContext ctx, int pname) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glGetString(pname);
    }
    
    @Override
    public Object getGLCapabilities(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL.getCapabilities();
    }
    
    @Override
    public int glGetInteger(CommandContext ctx, int pname) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glGetInteger(pname);
    }
    
    @Override
    public void glGetIntegerv(CommandContext ctx, int pname, int[] params) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glGetIntegerv(pname, params);
    }
    
    @Override
    public String glGetStringi(CommandContext ctx, int pname, int index) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL30.glGetStringi(pname, index);
    }
    
    @Override
    public String glGetProgramInfoLog(CommandContext ctx, int program) {
        return retrieveProgramInfoLog(ctx, program);
    }
    
    @Override
    public String glGetShaderInfoLog(CommandContext ctx, int shader) {
        return retrieveShaderInfoLog(ctx, shader);
    }
    
    // ========================================================================
    // DSA Buffer Operations with CommandContext
    // ========================================================================
    
    @Override
    public int createBufferDSA(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers();
    }
    
    @Override
    public void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData(buffer, size, usage);
    }
    
    @Override
    public void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData(buffer, data, usage);
    }
    
    @Override
    public void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferSubData(buffer, offset, data);
    }
    
    @Override
    public void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage(buffer, size, flags);
    }
    
    @Override
    public void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage(buffer, data, flags);
    }
    
    @Override
    public java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return org.lwjgl.opengl.ARBDirectStateAccess.glMapNamedBufferRange(buffer, offset, length, access);
    }
    
    @Override
    public void unmapNamedBufferDSA(CommandContext ctx, int buffer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glUnmapNamedBuffer(buffer);
    }
    
    @Override
    public void flushMappedNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glFlushMappedNamedBufferRange(buffer, offset, length);
    }
    
    @Override
    public void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // ========================================================================
    // DSA Framebuffer Operations with CommandContext
    // ========================================================================
    
    @Override
    public int createFramebufferDSA(CommandContext ctx) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    @Override
    public void namedFramebufferTextureDSA(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Override
    public void blitNamedFramebufferDSA(CommandContext ctx, int readFramebuffer, int drawFramebuffer, 
                                        int srcX0, int srcY0, int srcX1, int srcY1,
                                        int dstX0, int dstY0, int dstX1, int dstY1, 
                                        int mask, int filter) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, 
                                                                       srcX0, srcY0, srcX1, srcY1,
                                                                       dstX0, dstY0, dstX1, dstY1, 
                                                                       mask, filter);
    }
    
    // ========================================================================
    // Non-DSA Buffer Operations with CommandContext
    // ========================================================================
    
    @Override
    public void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Override
    public void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL30.glFlushMappedBufferRange(target, offset, length);
    }
    
    // ========================================================================
    // Phase 35: State Query and Blend/Stencil Operations with CommandContext
    // ========================================================================
    
    @Override
    public boolean glIsEnabled(CommandContext ctx, int cap) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glIsEnabled(cap);
    }
    
    @Override
    public boolean glIsFramebuffer(CommandContext ctx, int framebuffer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL30.glIsFramebuffer(framebuffer);
    }
    
    @Override
    public boolean glIsTexture(CommandContext ctx, int texture) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL11.glIsTexture(texture);
    }
    
    @Override
    public boolean glIsVertexArray(CommandContext ctx, int array) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL30.glIsVertexArray(array);
    }
    
    @Override
    public boolean glIsProgram(CommandContext ctx, int program) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL20.glIsProgram(program);
    }
    
    @Override
    public boolean glIsBuffer(CommandContext ctx, int buffer) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        return GL15.glIsBuffer(buffer);
    }
    
    @Override
    public void glBlendEquationSeparate(CommandContext ctx, int modeRGB, int modeAlpha) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }
    
    @Override
    public void glStencilFunc(CommandContext ctx, int func, int ref, int mask) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glStencilFunc(func, ref, mask);
    }
    
    @Override
    public void glCullFace(CommandContext ctx, int mode) {
        // Validate context is immediate mode (OpenGL requirement)
        if (!ctx.isImmediate()) {
            throw new IllegalArgumentException("OpenGL backend requires immediate-mode CommandContext");
        }
        
        GL11.glCullFace(mode);
    }
}
