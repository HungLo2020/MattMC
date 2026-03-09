package net.vulkanic;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Interface for graphics backend implementations.
 * This interface defines the contract that all backends (OpenGL, Vulkan) must implement.
 */
public interface GraphicsBackend {
    
    // Context operations
    /**
     * Gets the current graphics context (platform-specific).
     * On Windows, this returns the WGL context handle.
     * Returns 0 or NULL if no context is current.
     */
    long getGraphicsContext();

    /**
     * Gets the current command context for recording/submitting rendering commands.
     *
     * In OpenGL: Returns the singleton immediate command context.
     * In Vulkan: Returns the backend-managed command-buffer context for the current scope.
     *
     * @return Current command context
     */
    CommandContext getCurrentCommandContext();
    
    /**
     * Sets the dynamic viewport state for rendering.
     * 
     * In OpenGL: Maps to glViewport()
     * In Vulkan: Maps to vkCmdSetViewport() (dynamic state)
     * 
     * The viewport defines the transformation from normalized device coordinates to window coordinates.
     * This is dynamic state that can be changed per-frame or even between draw calls.
     * 
     * @param ctx Command context for recording this command
     * @param x The x coordinate of the viewport's lower-left corner
     * @param y The y coordinate of the viewport's lower-left corner
     * @param width The width of the viewport in pixels
     * @param height The height of the viewport in pixels
     */
    void setDynamicViewport(CommandContext ctx, int x, int y, int width, int height);
    
    // Scissor operations
    
    /**
     * Sets the dynamic scissor rectangle for rendering.
     * 
     * In OpenGL: Maps to glScissor()
     * In Vulkan: Maps to vkCmdSetScissor() (dynamic state)
     * 
     * The scissor test discards fragments outside the scissor rectangle.
     * This is dynamic state that can be changed per-frame or even between draw calls.
     * 
     * @param ctx Command context for recording this command
     * @param x The x coordinate of the scissor rectangle's lower-left corner
     * @param y The y coordinate of the scissor rectangle's lower-left corner
     * @param width The width of the scissor rectangle in pixels
     * @param height The height of the scissor rectangle in pixels
     */
    void setDynamicScissor(CommandContext ctx, int x, int y, int width, int height);
    
    /**
     * Clears buffers to preset values.
     * 
     * In OpenGL: Maps to glClear()
     * In Vulkan: Maps to vkCmdClearAttachments() within a render pass
     * 
     * @param ctx Command context for recording this command
     * @param mask Bitwise OR of masks indicating which buffers to clear
     */
    void clearBuffers(CommandContext ctx, int mask);
    
    /**
     * Sets blending enabled or disabled.
     * 
     * In OpenGL: Maps to glEnable/glDisable(GL_BLEND)
     * In Vulkan: Part of pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param enabled True to enable blending, false to disable
     */
    void setBlendEnabled(CommandContext ctx, boolean enabled);
    
    /**
     * Enables or disables a capability for a specific buffer.
     * 
     * In OpenGL: Maps to glEnablei/glDisablei()
     * In Vulkan: Part of pipeline state configuration
     * 
     * @param ctx Command context for recording this command
     * @param capability The capability to enable/disable (e.g., GL_BLEND)
     * @param index The buffer index
     * @param enabled True to enable, false to disable
     */
    void setIndexedEnabled(CommandContext ctx, int capability, int index, boolean enabled);
    
    /**
     * Sets the face culling mode.
     * 
     * In OpenGL: Maps to glCullFace()
     * In Vulkan: Part of pipeline rasterization state
     * 
     * @param ctx Command context for recording this command
     * @param mode The face culling mode (GL_FRONT, GL_BACK, GL_FRONT_AND_BACK)
     */
    void setCullFaceMode(CommandContext ctx, int mode);
    
    /**
     * Binds a shader program for use.
     * 
     * In OpenGL: Maps to glUseProgram()
     * In Vulkan: Will be replaced by pipeline binding
     * 
     * @param ctx Command context for recording this command
     * @param programId The shader program ID to bind
     */
    void bindShaderProgram(CommandContext ctx, int programId);
    
    /**
     * Sets a capability enabled or disabled.
     * 
     * In OpenGL: Maps to glEnable/glDisable()
     * In Vulkan: Most capabilities are part of pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param cap The capability to enable/disable
     * @param enabled True to enable, false to disable
     */
    void setCapabilityEnabled(CommandContext ctx, int cap, boolean enabled);
    
    /**
     * Binds a 2D texture to the current texture unit.
     * 
     * In OpenGL: Maps to glBindTexture(GL_TEXTURE_2D, texture)
     * In Vulkan: Part of descriptor set binding
     * 
     * @param ctx Command context for recording this command
     * @param textureId The texture ID to bind
     */
    void bindTexture2D(CommandContext ctx, int textureId);
    
    /**
     * Binds a texture to a specific target.
     * 
     * In OpenGL: Maps to glBindTexture(target, texture)
     * In Vulkan: Part of descriptor set binding
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D, GL_TEXTURE_3D, GL_TEXTURE_CUBE_MAP)
     * @param textureId The texture ID to bind
     */
    void bindTexture(CommandContext ctx, int target, int textureId);
    
    /**
     * Binds a level of a texture to an image unit.
     * 
     * In OpenGL: Maps to glBindImageTexture()
     * In Vulkan: Part of descriptor set configuration for storage images
     * 
     * @param ctx Command context for recording this command
     * @param unit The image unit index
     * @param texture The texture object ID
     * @param level The mipmap level
     * @param layered Whether the binding is layered
     * @param layer The layer to bind (if not layered)
     * @param access Access mode (read, write, or read-write)
     * @param format The image format
     */
    void bindImageTexture(CommandContext ctx, int unit, int texture, int level, boolean layered, int layer, int access, int format);
    
    /**
     * Binds a sampler object to a texture unit.
     * 
     * In OpenGL: Maps to glBindSampler()
     * In Vulkan: Part of descriptor set binding
     * 
     * @param ctx Command context for recording this command
     * @param unit The texture unit to bind the sampler to
     * @param sampler The sampler object ID to bind
     */
    void bindSampler(CommandContext ctx, int unit, int sampler);
    
    /**
     * Binds multiple sampler objects to texture units.
     * 
     * In OpenGL: Maps to glBindSamplers()
     * In Vulkan: Part of descriptor set configuration for multiple samplers
     * 
     * @param ctx Command context for recording this command
     * @param first The first texture unit to bind samplers to
     * @param samplers Array of sampler object IDs to bind
     */
    void bindSamplers(CommandContext ctx, int first, int[] samplers);
    
    /**
     * Creates a new sampler object.
     * 
     * In OpenGL: Maps to glGenSamplers()
     * In Vulkan: Maps to vkCreateSampler()
     * 
     * @param ctx Command context for recording this command
     * @return The sampler object ID
     */
    int createSampler(CommandContext ctx);
    
    /**
     * Deletes a sampler object.
     * 
     * In OpenGL: Maps to glDeleteSamplers()
     * In Vulkan: Maps to vkDestroySampler()
     * 
     * @param ctx Command context for recording this command
     * @param sampler The sampler object ID to delete
     */
    void deleteSampler(CommandContext ctx, int sampler);
    
    /**
     * Sets an integer sampler parameter.
     * 
     * In OpenGL: Maps to glSamplerParameteri()
     * In Vulkan: Part of sampler creation
     * 
     * @param ctx Command context for recording this command
     * @param sampler The sampler object
     * @param pname The parameter name (e.g., GL_TEXTURE_MIN_FILTER)
     * @param param The parameter value
     */
    void setSamplerParameteri(CommandContext ctx, int sampler, int pname, int param);
    
    /**
     * Sets a float sampler parameter.
     * 
     * In OpenGL: Maps to glSamplerParameterf()
     * In Vulkan: Part of sampler creation
     * 
     * @param ctx Command context for recording this command
     * @param sampler The sampler object
     * @param pname The parameter name (e.g., GL_TEXTURE_MAX_ANISOTROPY)
     * @param param The parameter value
     */
    void setSamplerParameterf(CommandContext ctx, int sampler, int pname, float param);
    
    /**
     * Sets an integer array sampler parameter.
     * 
     * In OpenGL: Maps to glSamplerParameteriv()
     * In Vulkan: Part of sampler creation
     * 
     * @param ctx Command context for recording this command
     * @param sampler The sampler object
     * @param pname The parameter name (e.g., GL_TEXTURE_BORDER_COLOR)
     * @param params The parameter values
     */
    void setSamplerParameteriv(CommandContext ctx, int sampler, int pname, int[] params);
    
    /**
     * Sets the depth test comparison function.
     * 
     * In OpenGL: Maps to glDepthFunc()
     * In Vulkan: Part of pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param func The depth comparison function
     */
    void setDepthTest(CommandContext ctx, int func);
    
    /**
     * Sets the depth write mask.
     * 
     * In OpenGL: Maps to glDepthMask()
     * In Vulkan: Part of pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param enabled True to enable depth writes, false to disable
     */
    void setDepthWriteMask(CommandContext ctx, boolean enabled);
    
    /**
     * Sets the color write mask.
     * 
     * In OpenGL: Maps to glColorMask()
     * In Vulkan: Part of pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param r Red channel write enabled
     * @param g Green channel write enabled
     * @param b Blue channel write enabled
     * @param a Alpha channel write enabled
     */
    void setColorMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a);
    
    /**
     * Generates mipmaps for a texture.
     * 
     * In OpenGL: Maps to glGenerateMipmap()
     * In Vulkan: Requires vkCmdBlitImage or compute shader
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     */
    void generateTextureMipmap(CommandContext ctx, int target);
    
    /**
     * Generates mipmaps for a texture using Direct State Access (DSA).
     * 
     * In OpenGL: Maps to glGenerateTextureMipmap() (DSA)
     * In Vulkan: Requires vkCmdBlitImage or compute shader
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture object ID
     */
    void generateTextureMipmapDSA(CommandContext ctx, int texture);
    
    /**
     * Sets an integer texture parameter using Direct State Access (DSA).
     * 
     * In OpenGL: Maps to glTextureParameteri() (DSA)
     * In Vulkan: Part of sampler or image view creation
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture object ID
     * @param pname The texture parameter name (e.g., GL_TEXTURE_MIN_FILTER)
     * @param param The parameter value
     */
    void textureParameteri(CommandContext ctx, int texture, int pname, int param);
    
    /**
     * Sets a float texture parameter using Direct State Access (DSA).
     * 
     * In OpenGL: Maps to glTextureParameterf() (DSA)
     * In Vulkan: Part of sampler or image view creation
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture object ID
     * @param pname The texture parameter name (e.g., GL_TEXTURE_LOD_BIAS)
     * @param param The parameter value
     */
    void textureParameterf(CommandContext ctx, int texture, int pname, float param);
    
    /**
     * Sets an integer array texture parameter using Direct State Access (DSA).
     * 
     * In OpenGL: Maps to glTextureParameteriv() (DSA)
     * In Vulkan: Part of sampler or image view creation
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture object ID
     * @param pname The texture parameter name (e.g., GL_TEXTURE_BORDER_COLOR)
     * @param params The parameter values
     */
    void textureParameteriv(CommandContext ctx, int texture, int pname, int[] params);
    
    /**
     * Gets an integer texture parameter using Direct State Access (DSA).
     * 
     * In OpenGL: Maps to glGetTextureParameteri() (DSA)
     * In Vulkan: Queries sampler or image view properties
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture object ID
     * @param pname The texture parameter name
     * @return The parameter value
     */
    int getTextureParameteri(CommandContext ctx, int texture, int pname);
    
    /**
     * Sets pixel storage mode parameters.
     * 
     * In OpenGL: Maps to glPixelStorei()
     * In Vulkan: Used during image transfers
     * 
     * @param ctx Command context for recording this command
     * @param pname The pixel storage parameter to set
     * @param value The value to set
     */
    void setPixelStore(CommandContext ctx, int pname, int value);
    
    /**
     * Binds a framebuffer object.
     * 
     * In OpenGL: Maps to glBindFramebuffer()
     * In Vulkan: Part of render pass setup
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (read, draw, or both)
     * @param fbo The framebuffer object ID
     */
    void bindFramebuffer(CommandContext ctx, int target, int fbo);
    
    /**
     * Sets the read buffer for a named framebuffer using Direct State Access.
     * 
     * In OpenGL: Maps to glNamedFramebufferReadBuffer() (DSA)
     * In Vulkan: Part of render pass configuration
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object ID
     * @param mode The read buffer mode (e.g., GL_COLOR_ATTACHMENT0)
     */
    void namedFramebufferReadBuffer(CommandContext ctx, int framebuffer, int mode);
    
    /**
     * Sets the draw buffers for a named framebuffer using Direct State Access.
     * 
     * In OpenGL: Maps to glNamedFramebufferDrawBuffers() (DSA)
     * In Vulkan: Part of render pass configuration
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object ID
     * @param bufs Array of draw buffer attachments
     */
    void namedFramebufferDrawBuffers(CommandContext ctx, int framebuffer, int[] bufs);
    
    /**
     * Clears a float buffer in a named framebuffer using Direct State Access.
     * 
     * In OpenGL: Maps to glClearNamedFramebufferfv() (DSA)
     * In Vulkan: Part of vkCmdClearAttachments
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object ID
     * @param buffer The buffer to clear (e.g., GL_COLOR, GL_DEPTH)
     * @param drawbuffer The draw buffer index
     * @param value The clear value
     */
    void clearNamedFramebufferfv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, float[] value);
    
    /**
     * Clears an integer buffer in a named framebuffer using Direct State Access.
     * 
     * In OpenGL: Maps to glClearNamedFramebufferiv() (DSA)
     * In Vulkan: Part of vkCmdClearAttachments
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object ID
     * @param buffer The buffer to clear (e.g., GL_COLOR, GL_STENCIL)
     * @param drawbuffer The draw buffer index
     * @param value The clear value
     */
    void clearNamedFramebufferiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value);
    
    /**
     * Clears an unsigned integer buffer in a named framebuffer using Direct State Access.
     * 
     * In OpenGL: Maps to glClearNamedFramebufferuiv() (DSA)
     * In Vulkan: Part of vkCmdClearAttachments
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object ID
     * @param buffer The buffer to clear (e.g., GL_COLOR)
     * @param drawbuffer The draw buffer index
     * @param value The clear value
     */
    void clearNamedFramebufferuiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value);
    
    /**
     * Queries a framebuffer attachment parameter.
     * 
     * In OpenGL: Maps to glGetFramebufferAttachmentParameteriv()
     * In Vulkan: Queries framebuffer attachment properties
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (e.g., GL_FRAMEBUFFER, GL_READ_FRAMEBUFFER, GL_DRAW_FRAMEBUFFER)
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0, GL_DEPTH_ATTACHMENT)
     * @param pname The parameter name to query (e.g., GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
     * @return The queried parameter value
     */
    int getFramebufferAttachmentParameteri(CommandContext ctx, int target, int attachment, int pname);
    
    /**
     * Binds a buffer object to a target.
     * 
     * In OpenGL: Maps to glBindBuffer()
     * In Vulkan: Buffers are bound via descriptor sets
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target
     * @param buffer The buffer object ID
     */
    void bindBuffer(CommandContext ctx, int target, int buffer);
    
    /**
     * Binds a buffer to an indexed buffer target.
     * 
     * In OpenGL: Maps to glBindBufferBase()
     * In Vulkan: Part of descriptor set binding
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_UNIFORM_BUFFER, GL_SHADER_STORAGE_BUFFER)
     * @param index The index of the binding point within the array specified by target
     * @param buffer The buffer object ID to bind
     */
    void bindBufferBase(CommandContext ctx, int target, int index, int buffer);
    
    /**
     * Sets the active texture unit.
     * 
     * In OpenGL: Maps to glActiveTexture()
     * In Vulkan: Part of descriptor set binding
     * 
     * @param ctx Command context for recording this command
     * @param unit The texture unit to activate
     */
    void setActiveTextureUnit(CommandContext ctx, int unit);
    
    /**
     * Sets a texture parameter.
     * 
     * In OpenGL: Maps to glTexParameteri()
     * In Vulkan: Part of sampler object creation
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     * @param pname The parameter name
     * @param param The parameter value
     */
    void setTextureParameter(CommandContext ctx, int target, int pname, int param);
    
    /**
     * Copies a region from the framebuffer to a texture subregion.
     * 
     * In OpenGL: Maps to glCopyTexSubImage2D()
     * In Vulkan: Maps to vkCmdCopyImageToBuffer followed by vkCmdCopyBufferToImage
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     * @param level The mipmap level
     * @param xoffset The x offset in the texture
     * @param yoffset The y offset in the texture
     * @param x The x coordinate in the framebuffer
     * @param y The y coordinate in the framebuffer
     * @param width The width of the region
     * @param height The height of the region
     */
    void copyTexSubImage2D(CommandContext ctx, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    
    /**
     * Gets a texture parameter value.
     * 
     * In OpenGL: Maps to glGetTexParameteri()
     * In Vulkan: Queries sampler or image view properties
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     * @param pname The parameter name
     * @return The parameter value
     */
    int getTexParameteri(CommandContext ctx, int target, int pname);
    
    // Direct State Access buffer operations
    // CommandContext versions of DSA buffer operations
    /**
     * Creates a buffer object using Direct State Access (DSA).
     * In OpenGL: Maps to glCreateBuffers()
     * In Vulkan: Creates a VkBuffer with appropriate parameters
     */
    int createBufferDSA(CommandContext ctx);
    
    /**
     * Allocates storage for a buffer using Direct State Access (DSA).
     * In OpenGL: Maps to glNamedBufferData()
     * In Vulkan: Allocates and binds memory to a VkBuffer
     */
    void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage);
    
    /**
     * Uploads data to a buffer using Direct State Access (DSA).
     * In OpenGL: Maps to glNamedBufferData()
     * In Vulkan: Maps memory, copies data, unmaps memory
     */
    void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage);
    
    /**
     * Updates a subset of buffer data using Direct State Access (DSA).
     * In OpenGL: Maps to glNamedBufferSubData()
     * In Vulkan: Updates buffer memory region
     */
    void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data);
    
    /**
     * Creates immutable buffer storage using Direct State Access (DSA).
     * In OpenGL: Maps to glNamedBufferStorage()
     * In Vulkan: Creates buffer with specific memory properties
     */
    void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags);
    
    /**
     * Creates immutable buffer storage with data using Direct State Access (DSA).
     * In OpenGL: Maps to glNamedBufferStorage()
     * In Vulkan: Creates buffer with specific memory properties and initial data
     */
    void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags);
    
    /**
     * Maps a range of a buffer's data store using Direct State Access (DSA).
     * 
     * In OpenGL: Maps to glMapNamedBufferRange()
     * In Vulkan: Maps to vkMapMemory() for the buffer's backing memory
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer object ID to map
     * @param offset The starting offset within the buffer's data store (bytes)
     * @param length The length of the range to map (bytes)
     * @param access Bitfield combining access flags (GL_MAP_READ_BIT, GL_MAP_WRITE_BIT, etc.)
     * @return ByteBuffer pointing to the mapped memory region, or null on failure
     */
    java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access);
    
    /**
     * Unmaps a previously mapped buffer using Direct State Access (DSA).
     * In OpenGL: Maps to glUnmapNamedBuffer()
     * In Vulkan: Unmaps memory previously mapped with vkMapMemory()
     */
    void unmapNamedBufferDSA(CommandContext ctx, int buffer);
    
    /**
     * Flushes a range of a mapped buffer using Direct State Access (DSA).
     * In OpenGL: Maps to glFlushMappedNamedBufferRange()
     * In Vulkan: Maps to vkFlushMappedMemoryRanges()
     */
    void flushMappedNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length);
    
    /**
     * Copies data between buffers using Direct State Access (DSA).
     * In OpenGL: Maps to glCopyNamedBufferSubData()
     * In Vulkan: Maps to vkCmdCopyBuffer()
     */
    void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size);
    
    // Direct State Access framebuffer operations
    /**
     * Attaches a texture to a framebuffer using Direct State Access (DSA).
     * In OpenGL: Maps to glNamedFramebufferTexture()
     * In Vulkan: Part of render pass and framebuffer setup
     */
    void namedFramebufferTextureDSA(CommandContext ctx, int framebuffer, int attachment, int texture, int level);
    
    /**
     * Blits (copies) pixels between framebuffers using Direct State Access (DSA).
     * In OpenGL: Maps to glBlitNamedFramebuffer()
     * In Vulkan: Maps to vkCmdBlitImage()
     */
    void blitNamedFramebufferDSA(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, 
                                  int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    /**
     * Creates a new 2D texture object.
     * 
     * In OpenGL: Maps to glGenTextures()
     * In Vulkan: Maps to vkCreateImage() for 2D texture
     * 
     * @param ctx Command context for recording this command
     * @return The texture object ID
     */
    int createTexture2D(CommandContext ctx);
    
    /**
     * Deletes a texture object.
     * 
     * In OpenGL: Maps to glDeleteTextures()
     * In Vulkan: Maps to vkDestroyImage()
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture object ID to delete
     */
    void deleteTexture(CommandContext ctx, int texture);
    
    /**
     * Renders primitives from array data.
     * 
     * In OpenGL: Maps to glDrawArrays()
     * In Vulkan: Maps to vkCmdDraw()
     * 
     * @param ctx Command context for recording this command
     * @param mode The kind of primitives to render
     * @param first The starting index in the enabled arrays
     * @param count The number of vertices to be rendered
     */
    void drawArrays(CommandContext ctx, int mode, int first, int count);
    
    /**
     * Renders primitives from indexed array data.
     * 
     * In OpenGL: Maps to glDrawElements()
     * In Vulkan: Maps to vkCmdDrawIndexed()
     * 
     * @param ctx Command context for recording this command
     * @param mode The kind of primitives to render
     * @param count The number of elements to be rendered
     * @param type The type of the values in indices
     * @param indices Byte offset into the bound element array buffer
     */
    void drawElements(CommandContext ctx, int mode, int count, int type, long indices);
    
    /**
     * Sets the blend function for RGB and alpha channels separately.
     * 
     * In OpenGL: Maps to glBlendFuncSeparate()
     * In Vulkan: Part of pipeline color blend state
     * 
     * @param ctx Command context for recording this command
     * @param srcRgb Source RGB blend factor
     * @param dstRgb Destination RGB blend factor
     * @param srcAlpha Source alpha blend factor
     * @param dstAlpha Destination alpha blend factor
     */
    void setBlendFunction(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
    
    /**
     * Sets the blend equation for both RGB and alpha components.
     * 
     * In OpenGL: Maps to glBlendEquation()
     * In Vulkan: Part of pipeline blend state
     * 
     * Controls how source and destination colors are combined after applying blend factors.
     * Common modes include GL_FUNC_ADD, GL_FUNC_SUBTRACT, GL_FUNC_REVERSE_SUBTRACT, GL_MIN, GL_MAX.
     * 
     * @param ctx Command context for recording this command
     * @param mode The blend equation mode
     */
    void setBlendEquation(CommandContext ctx, int mode);
    
    /**
     * Sets the depth comparison function.
     * 
     * In OpenGL: Maps to glDepthFunc()
     * In Vulkan: Part of pipeline depth/stencil state
     * 
     * Determines how incoming fragment depth values are compared against stored depth buffer values.
     * Common functions include GL_LESS, GL_LEQUAL, GL_GREATER, GL_EQUAL, GL_ALWAYS, GL_NEVER.
     * 
     * @param ctx Command context for recording this command
     * @param func The depth comparison function
     */
    void setDepthFunc(CommandContext ctx, int func);
    
    /**
     * Specifies which color buffer to read from for read operations.
     * 
     * In OpenGL: Maps to glReadBuffer()
     * In Vulkan: Maps to framebuffer attachment selection for read operations
     * 
     * Controls which color buffer is used as the source for operations like glReadPixels() and glCopyTexImage2D().
     * Common values include GL_FRONT, GL_BACK, GL_COLOR_ATTACHMENT0, etc.
     * 
     * @param ctx Command context for recording this command
     * @param buffer The color buffer to read from
     */
    void setReadBuffer(CommandContext ctx, int buffer);
    
    /**
     * Attaches a texture to a framebuffer attachment point.
     * 
     * In OpenGL: Maps to glFramebufferTexture2D()
     * In Vulkan: Maps to framebuffer attachment configuration
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (e.g., GL_FRAMEBUFFER)
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0)
     * @param textarget The texture target (e.g., GL_TEXTURE_2D)
     * @param texture The texture object ID
     * @param level The mipmap level
     */
    void framebufferTexture(CommandContext ctx, int target, int attachment, int textarget, int texture, int level);
    
    /**
     * Attaches a 2D texture image to a framebuffer attachment point.
     * 
     * In OpenGL: Maps to glFramebufferTexture2D()
     * In Vulkan: Maps to framebuffer attachment configuration
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (e.g., GL_FRAMEBUFFER)
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0)
     * @param textarget The texture target (e.g., GL_TEXTURE_2D)
     * @param texture The texture object ID
     * @param level The mipmap level
     */
    void framebufferTexture2D(CommandContext ctx, int target, int attachment, int textarget, int texture, int level);
    
    /**
     * Checks the completeness status of a framebuffer.
     * 
     * In OpenGL: Maps to glCheckFramebufferStatus()
     * In Vulkan: Framebuffers must be complete at creation time
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (e.g., GL_FRAMEBUFFER)
     * @return Status code indicating framebuffer completeness
     */
    int checkFramebufferStatus(CommandContext ctx, int target);
    
    /**
     * Specifies a list of color buffers to be drawn into.
     * 
     * In OpenGL: Maps to glDrawBuffers()
     * In Vulkan: Part of render pass subpass configuration
     * 
     * @param ctx Command context for recording this command
     * @param buffers Array of buffers to draw into (e.g., GL_COLOR_ATTACHMENT0)
     */
    void drawBuffers(CommandContext ctx, int[] buffers);
    
    /**
     * Sets the blend function for source and destination blend factors.
     * 
     * In OpenGL: Maps to glBlendFunc()
     * In Vulkan: Part of pipeline color blend state
     * 
     * @param ctx Command context for recording this command
     * @param sfactor Source blend factor
     * @param dfactor Destination blend factor
     */
    void blendFunc(CommandContext ctx, int sfactor, int dfactor);
    
    /**
     * Sets the blend function for a specific draw buffer.
     * 
     * In OpenGL: Maps to glBlendFuncSeparatei()
     * In Vulkan: Part of pipeline color blend state per attachment
     * 
     * @param ctx Command context for recording this command
     * @param buffer The draw buffer index
     * @param srcRGB Source RGB blend factor
     * @param dstRGB Destination RGB blend factor
     * @param srcAlpha Source alpha blend factor
     * @param dstAlpha Destination alpha blend factor
     */
    void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);
    
    /**
     * Queries an integer state variable.
     * 
     * In OpenGL: Maps to glGetInteger()
     * In Vulkan: Queries device state or cached pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param pname The parameter name to query
     * @return The queried integer value
     */
    int getInteger(CommandContext ctx, int pname);
    
    /**
     * Sets uniform values for a vec3 shader variable.
     * 
     * In OpenGL: Maps to glUniform3f()
     * In Vulkan: Updates push constants or uniform buffer data
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param v0 The first component value
     * @param v1 The second component value
     * @param v2 The third component value
     */
    void setUniform3f(CommandContext ctx, int location, float v0, float v1, float v2);
    
    /**
     * Sets the clear color value.
     * 
     * In OpenGL: Maps to glClearColor()
     * In Vulkan: Part of render pass clear values
     * 
     * @param ctx Command context for recording this command
     * @param r Red component
     * @param g Green component
     * @param b Blue component
     * @param a Alpha component
     */
    void setClearColor(CommandContext ctx, float r, float g, float b, float a);
    
    /**
     * Sets the depth clear value used by subsequent clear operations.
     * 
     * In OpenGL: Maps to glClearDepth()
     * In Vulkan: Part of VkRenderPassBeginInfo / vkCmdClearDepthStencilImage
     * 
     * @param ctx Command context for recording this command
     * @param depth The depth clear value (0.0 to 1.0)
     */
    void setClearDepth(CommandContext ctx, double depth);
    
    /**
     * Sets the viewport transformation.
     * 
     * In OpenGL: Maps to glViewport()
     * In Vulkan: Part of dynamic state or pipeline viewport state
     * 
     * @param ctx Command context for recording this command
     * @param x The lower left corner x coordinate
     * @param y The lower left corner y coordinate
     * @param width The viewport width
     * @param height The viewport height
     */
    void setViewport(CommandContext ctx, int x, int y, int width, int height);
    
    /**
     * Sets the polygon rasterization mode.
     * 
     * In OpenGL: Maps to glPolygonMode()
     * In Vulkan: Part of pipeline rasterization state
     * 
     * @param ctx Command context for recording this command
     * @param face Which polygons the mode applies to (e.g., GL_FRONT_AND_BACK)
     * @param mode The rasterization mode (e.g., GL_FILL, GL_LINE, GL_POINT)
     */
    void setPolygonMode(CommandContext ctx, int face, int mode);
    
    /**
     * Sets the polygon offset parameters for depth offset calculation.
     * 
     * In OpenGL: Maps to glPolygonOffset()
     * In Vulkan: Part of pipeline rasterization state (depthBiasSlopeFactor, depthBiasConstantFactor)
     * 
     * @param ctx Command context for recording this command
     * @param factor Scale factor for variable depth offset
     * @param units Scale factor for constant depth offset
     */
    void setPolygonOffset(CommandContext ctx, float factor, float units);
    
    /**
     * Sets the logical operation for color blending.
     * 
     * In OpenGL: Maps to glLogicOp()
     * In Vulkan: Part of pipeline color blend state
     * 
     * @param ctx Command context for recording this command
     * @param opcode The logical operation (e.g., GL_COPY, GL_AND, GL_XOR)
     */
    void setLogicOp(CommandContext ctx, int opcode);
    
    /**
     * Creates a new framebuffer object.
     * 
     * In OpenGL: Maps to glCreateFramebuffers() (DSA) or glGenFramebuffers()
     * In Vulkan: Maps to vkCreateFramebuffer()
     * 
     * @param ctx Command context for recording this command
     * @return The framebuffer object ID
     */
    int createFramebuffer(CommandContext ctx);
    
    // Error checking
    
    /**
     * Gets the current OpenGL error code.
     * 
     * In OpenGL: Maps to glGetError()
     * In Vulkan: Returns 0 (Vulkan uses validation layers for error detection)
     * 
     * @param ctx Command context for recording this command
     * @return The error code, or 0 (GL_NO_ERROR) if no error has occurred
     */
    int getError(CommandContext ctx);
    
    
    // Texture pixel data transfer
    
    /**
     * Uploads pixel data to a 2D texture.
     * 
     * In OpenGL: Maps to glTexImage2D()
     * In Vulkan: Maps to vkCmdCopyBufferToImage() after staging buffer setup
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param level Mipmap level
     * @param internalFormat Internal format of the texture
     * @param width Width of the texture
     * @param height Height of the texture
     * @param border Border width (must be 0)
     * @param format Format of the pixel data
     * @param type Data type of the pixel data
     * @param pixels Pixel data buffer (can be null)
     */
    void uploadTexture2D(CommandContext ctx, int target, int level, int internalFormat, int width, int height, 
                         int border, int format, int type, java.nio.ByteBuffer pixels);
    
    /**
     * Uploads pixel data to a subregion of a 2D texture.
     * 
     * In OpenGL: Maps to glTexSubImage2D()
     * In Vulkan: Maps to vkCmdCopyBufferToImage() with offset
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param level Mipmap level
     * @param xOffset X offset in the texture
     * @param yOffset Y offset in the texture
     * @param width Width of the subregion
     * @param height Height of the subregion
     * @param format Format of the pixel data
     * @param type Data type of the pixel data
     * @param pixels Pixel data (can be pointer or ByteBuffer)
     */
    void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                  int width, int height, int format, int type, long pixels);
    
    /**
     * Uploads pixel data to a subregion of a 2D texture from a ByteBuffer.
     * 
     * In OpenGL: Maps to glTexSubImage2D()
     * In Vulkan: Maps to vkCmdCopyBufferToImage() with offset
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param level Mipmap level
     * @param xOffset X offset in the texture
     * @param yOffset Y offset in the texture
     * @param width Width of the subregion
     * @param height Height of the subregion
     * @param format Format of the pixel data
     * @param type Data type of the pixel data
     * @param pixels Pixel data buffer
     */
    void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                  int width, int height, int format, int type, java.nio.ByteBuffer pixels);
    
    
    // GPU buffer lifecycle
    
    /**
     * Creates a new buffer object.
     * 
     * In OpenGL: Maps to glGenBuffers()
     * In Vulkan: Maps to vkCreateBuffer()
     * 
     * @param ctx Command context for recording this command
     * @return The buffer object ID
     */
    int createBuffer(CommandContext ctx);
    
    /**
     * Creates multiple buffer objects.
     * 
     * In OpenGL: Maps to glGenBuffers()
     * In Vulkan: Maps to multiple vkCreateBuffer() calls
     * 
     * @param ctx Command context for recording this command
     * @param buffers Array to receive the buffer object IDs
     */
    void createBuffers(CommandContext ctx, int[] buffers);
    
    /**
     * Deletes a buffer object.
     * 
     * In OpenGL: Maps to glDeleteBuffers()
     * In Vulkan: Maps to vkDestroyBuffer()
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer object ID to delete
     */
    void deleteBuffer(CommandContext ctx, int buffer);
    
    /**
     * Uploads data to a buffer object.
     * 
     * In OpenGL: Maps to glBufferData()
     * In Vulkan: Maps to vkCmdCopyBuffer() or buffer memory mapping
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     * @param data The data to upload
     * @param usage Usage hint (e.g., GL_STATIC_DRAW)
     */
    void bufferData(CommandContext ctx, int target, java.nio.ByteBuffer data, int usage);
    
    /**
     * Allocates buffer storage with specified size.
     * 
     * In OpenGL: Maps to glBufferData() with NULL data
     * In Vulkan: Maps to vkCreateBuffer() with size allocation
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     * @param size Size in bytes to allocate
     * @param usage Usage hint (e.g., GL_STATIC_DRAW)
     */
    void bufferData(CommandContext ctx, int target, long size, int usage);
    
    /**
     * Allocates and initializes buffer object data with float array.
     * 
     * @param ctx Command context for recording this command
     * @param target Buffer binding target
     * @param data Float array data to copy
     * @param usage Usage hint (e.g., GL_STATIC_DRAW)
     */
    void bufferData(CommandContext ctx, int target, float[] data, int usage);
    
    /**
     * Allocates and initializes buffer object data with int array.
     * 
     * @param ctx Command context for recording this command
     * @param target Buffer binding target
     * @param data Int array data to copy
     * @param usage Usage hint (e.g., GL_STATIC_DRAW)
     */
    void bufferData(CommandContext ctx, int target, int[] data, int usage);
    
    
    /**
     * Updates a subset of a buffer object's data.
     * 
     * In OpenGL: Maps to glBufferSubData()
     * In Vulkan: Maps to vkCmdUpdateBuffer() or memory mapping
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     * @param offset Offset into the buffer where data starts
     * @param data The data to copy into the buffer
     */
    void bufferSubData(CommandContext ctx, int target, long offset, java.nio.ByteBuffer data);
    
    /**
     * Creates immutable buffer storage.
     * 
     * In OpenGL: Maps to glBufferStorage() (OpenGL 4.4+)
     * In Vulkan: Maps to vkCreateBuffer() with appropriate usage flags
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     * @param size Size of the buffer in bytes
     * @param flags Storage flags (e.g., GL_DYNAMIC_STORAGE_BIT, GL_MAP_READ_BIT)
     */
    void bufferStorage(CommandContext ctx, int target, long size, int flags);
    
    /**
     * Creates immutable buffer storage with initial data.
     * 
     * In OpenGL: Maps to glBufferStorage() (OpenGL 4.4+)
     * In Vulkan: Maps to vkCreateBuffer() with appropriate usage flags
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     * @param data Initial data for the buffer
     * @param flags Storage flags (e.g., GL_DYNAMIC_STORAGE_BIT, GL_MAP_READ_BIT)
     */
    void bufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags);
    
    /**
     * Copies data between buffer objects.
     * 
     * In OpenGL: Maps to glCopyBufferSubData()
     * In Vulkan: Maps to vkCmdCopyBuffer()
     * 
     * @param ctx Command context for recording this command
     * @param readTarget The source buffer target (e.g., GL_ARRAY_BUFFER)
     * @param writeTarget The destination buffer target (e.g., GL_ARRAY_BUFFER)
     * @param readOffset Offset in source buffer
     * @param writeOffset Offset in destination buffer
     * @param size Number of bytes to copy
     */
    void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size);
    
    /**
     * Flushes modifications to a mapped buffer range.
     * 
     * In OpenGL: Maps to glFlushMappedBufferRange()
     * In Vulkan: Maps to vkFlushMappedMemoryRanges()
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     * @param offset Starting offset of the modified range
     * @param length Length of the modified range in bytes
     */
    void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length);
    
    
    // Vertex array objects
    
    /**
     * Creates a new vertex array object (VAO).
     * 
     * In OpenGL: Maps to glGenVertexArrays()
     * In Vulkan: No direct equivalent (vertex input state is part of pipeline)
     * 
     * @param ctx Command context for recording this command
     * @return The vertex array object ID
     */
    int createVertexArray(CommandContext ctx);
    
    /**
     * Binds a vertex array object for subsequent vertex attribute operations.
     * 
     * In OpenGL: Maps to glBindVertexArray()
     * In Vulkan: No direct equivalent (vertex binding is part of vkCmdBindVertexBuffers)
     * 
     * @param ctx Command context for recording this command
     * @param vao The vertex array object ID to bind (0 for default)
     */
    void bindVertexArray(CommandContext ctx, int vao);
    
    
    // Buffer memory mapping
    
    /**
     * Maps a range of buffer object's data store into client memory.
     * 
     * In OpenGL: Maps to glMapBufferRange()
     * In Vulkan: Maps to vkMapMemory()
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     * @param offset Starting offset within the buffer
     * @param length Number of bytes to map
     * @param access Access flags (e.g., GL_MAP_READ_BIT, GL_MAP_WRITE_BIT)
     * @return A ByteBuffer representing the mapped memory region
     */
    java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, long offset, long length, int access);
    
    /**
     * Unmaps a previously mapped buffer object.
     * 
     * In OpenGL: Maps to glUnmapBuffer()
     * In Vulkan: Maps to vkUnmapMemory()
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (e.g., GL_ARRAY_BUFFER)
     */
    void unmapBuffer(CommandContext ctx, int target);
    
    // Framebuffer lifecycle
    
    /**
     * Deletes a framebuffer object.
     * 
     * In OpenGL: Maps to glDeleteFramebuffers()
     * In Vulkan: Maps to vkDestroyFramebuffer()
     * 
     * @param ctx Command context for recording this command
     * @param fbo The framebuffer object ID to delete
     */
    void deleteFramebuffer(CommandContext ctx, int fbo);
    
    /**
     * Copies a block of pixels from one framebuffer to another (blit operation).
     * 
     * In OpenGL: Maps to glBlitFramebuffer()
     * In Vulkan: Maps to vkCmdBlitImage()
     * 
     * @param ctx Command context for recording this command
     * @param srcX0 Source region start X
     * @param srcY0 Source region start Y
     * @param srcX1 Source region end X
     * @param srcY1 Source region end Y
     * @param dstX0 Destination region start X
     * @param dstY0 Destination region start Y
     * @param dstX1 Destination region end X
     * @param dstY1 Destination region end Y
     * @param mask Buffer bit mask (GL_COLOR_BUFFER_BIT, GL_DEPTH_BUFFER_BIT, GL_STENCIL_BUFFER_BIT)
     * @param filter Interpolation filter (GL_NEAREST, GL_LINEAR)
     */
    void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, 
                         int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    
    // Shader pipeline
    
    /**
     * Creates a new shader object.
     * 
     * In OpenGL: Maps to glCreateShader()
     * In Vulkan: Maps to vkCreateShaderModule() with SPIR-V bytecode
     * 
     * @param ctx Command context for recording this command
     * @param shaderType Type of shader (e.g., GL_VERTEX_SHADER, GL_FRAGMENT_SHADER)
     * @return The shader object ID
     */
    int createShader(CommandContext ctx, int shaderType);
    
    /**
     * Compiles a shader object.
     * 
     * In OpenGL: Maps to glCompileShader()
     * In Vulkan: Shader compilation is done offline to SPIR-V
     * 
     * @param ctx Command context for recording this command
     * @param shader The shader object ID to compile
     */
    void compileShader(CommandContext ctx, int shader);
    
    /**
     * Creates a new shader program object.
     * 
     * In OpenGL: Maps to glCreateProgram()
     * In Vulkan: Maps to pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @return The shader program object ID
     */
    int createShaderProgram(CommandContext ctx);
    
    /**
     * Attaches a shader to a program object.
     * 
     * In OpenGL: Maps to glAttachShader()
     * In Vulkan: Shader modules are specified during pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param shader The shader object ID to attach
     */
    void attachShader(CommandContext ctx, int program, int shader);
    
    /**
     * Detaches a shader from a program object.
     * 
     * In OpenGL: Maps to glDetachShader()
     * In Vulkan: Not directly applicable (shaders are part of pipeline creation)
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param shader The shader object ID to detach
     */
    void detachShader(CommandContext ctx, int program, int shader);
    
    /**
     * Links a shader program.
     * 
     * In OpenGL: Maps to glLinkProgram()
     * In Vulkan: Part of pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID to link
     */
    void linkProgram(CommandContext ctx, int program);
    
    /**
     * Queries a program parameter.
     * 
     * In OpenGL: Maps to glGetProgramiv()
     * In Vulkan: Maps to pipeline state queries
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param pname The parameter name to query
     * @return The parameter value
     */
    int getProgramParameter(CommandContext ctx, int program, int pname);
    
    /**
     * Queries multiple program parameters into an array.
     * 
     * In OpenGL: Maps to glGetProgramiv()
     * In Vulkan: Maps to pipeline state queries
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param pname The parameter name to query
     * @param params Array to receive the parameter values
     */
    void getProgramiv(CommandContext ctx, int program, int pname, int[] params);
    
    /**
     * Queries a shader parameter.
     * 
     * In OpenGL: Maps to glGetShaderiv()
     * In Vulkan: Maps to shader module queries
     * 
     * @param ctx Command context for recording this command
     * @param shader The shader object ID
     * @param pname The parameter name to query
     * @return The parameter value
     */
    int getShaderParameter(CommandContext ctx, int shader, int pname);
    
    /**
     * Retrieves the information log for a program.
     * 
     * In OpenGL: Maps to glGetProgramInfoLog()
     * In Vulkan: Maps to pipeline creation messages
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @return The information log string
     */
    String getProgramInfoLog(CommandContext ctx, int program);
    
    /**
     * Retrieves the information log for a shader.
     * 
     * In OpenGL: Maps to glGetShaderInfoLog()
     * In Vulkan: Maps to shader module creation messages
     * 
     * @param ctx Command context for recording this command
     * @param shader The shader object ID
     * @return The information log string
     */
    String getShaderInfoLog(CommandContext ctx, int shader);
    
    /**
     * Retrieves information about an active uniform variable.
     * 
     * In OpenGL: Maps to glGetActiveUniform()
     * In Vulkan: Maps to descriptor set layout queries
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param index The index of the uniform variable
     * @param size Buffer to receive the size of the uniform
     * @param type Buffer to receive the data type of the uniform
     * @param name Buffer to receive the name of the uniform
     * @return The name of the uniform variable
     */
    String getActiveUniform(CommandContext ctx, int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name);
    
    /**
     * Locates a uniform variable in a program.
     * 
     * In OpenGL: Maps to glGetUniformLocation()
     * In Vulkan: Maps to descriptor binding lookup
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param name The name of the uniform variable
     * @return The location of the uniform variable
     */
    int getUniformLocation(CommandContext ctx, int program, CharSequence name);
    
    /**
     * Locates an attribute variable in a program.
     * 
     * In OpenGL: Maps to glGetAttribLocation()
     * In Vulkan: Maps to vertex input attribute binding lookup
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param name The name of the attribute variable
     * @return The location of the attribute variable, or -1 if not found
     */
    int getAttributeLocation(CommandContext ctx, int program, CharSequence name);
    
    /**
     * Sets an integer uniform value.
     * 
     * In OpenGL: Maps to glUniform1i()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param value The integer value to set
     */
    void setUniform1i(CommandContext ctx, int location, int value);
    
    /**
     * Sets a float uniform value.
     * 
     * In OpenGL: Maps to glUniform1f()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param value The float value to set
     */
    void setUniform1f(CommandContext ctx, int location, float value);
    
    /**
     * Sets a 2-component float vector uniform value.
     * 
     * In OpenGL: Maps to glUniform2f()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param v0 The first component value
     * @param v1 The second component value
     */
    void setUniform2f(CommandContext ctx, int location, float v0, float v1);
    
    /**
     * Sets a 2-component integer vector uniform value.
     * 
     * In OpenGL: Maps to glUniform2i()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param v0 The first component value
     * @param v1 The second component value
     */
    void setUniform2i(CommandContext ctx, int location, int v0, int v1);
    
    /**
     * Sets a 3-component integer vector uniform value.
     * 
     * In OpenGL: Maps to glUniform3i()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param v0 The first component value
     * @param v1 The second component value
     * @param v2 The third component value
     */
    void setUniform3i(CommandContext ctx, int location, int v0, int v1, int v2);
    
    /**
     * Sets a 4-component float vector uniform value.
     * 
     * In OpenGL: Maps to glUniform4f()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param v0 The first component value
     * @param v1 The second component value
     * @param v2 The third component value
     * @param v3 The fourth component value
     */
    void setUniform4f(CommandContext ctx, int location, float v0, float v1, float v2, float v3);
    
    /**
     * Sets a 4-component integer vector uniform value.
     * 
     * In OpenGL: Maps to glUniform4i()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param v0 The first component value
     * @param v1 The second component value
     * @param v2 The third component value
     * @param v3 The fourth component value
     */
    void setUniform4i(CommandContext ctx, int location, int v0, int v1, int v2, int v3);
    
    /**
     * Sets a 3x3 matrix uniform value.
     * 
     * In OpenGL: Maps to glUniformMatrix3fv()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param transpose Whether to transpose the matrix
     * @param matrix The matrix data
     */
    void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix);
    
    /**
     * Sets a 3x3 matrix uniform value from a float array.
     * 
     * In OpenGL: Maps to glUniformMatrix3fv()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param transpose Whether to transpose the matrix
     * @param matrix The matrix data array
     */
    void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, float[] matrix);
    
    /**
     * Sets a 4x4 matrix uniform value.
     * 
     * In OpenGL: Maps to glUniformMatrix4fv()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param transpose Whether to transpose the matrix
     * @param matrix The matrix data
     */
    void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix);
    
    /**
     * Sets a 4x4 matrix uniform value from a float array.
     * 
     * In OpenGL: Maps to glUniformMatrix4fv()
     * In Vulkan: Maps to push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param transpose Whether to transpose the matrix
     * @param matrix The matrix data array
     */
    void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, float[] matrix);
    
    /**
     * Configures a vertex attribute pointer.
     * 
     * In OpenGL: Maps to glVertexAttribPointer()
     * In Vulkan: Part of vertex input state in pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @param index The attribute index
     * @param size Number of components per attribute
     * @param type Data type of each component
     * @param normalized Whether values should be normalized
     * @param stride Byte offset between consecutive attributes
     * @param pointer Offset of the first component
     */
    void setVertexAttribPointer(CommandContext ctx, int index, int size, int type, boolean normalized, int stride, long pointer);
    
    /**
     * Enables a vertex attribute array.
     * 
     * In OpenGL: Maps to glEnableVertexAttribArray()
     * In Vulkan: Part of vertex input state in pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @param index The attribute index to enable
     */
    void enableVertexAttribArray(CommandContext ctx, int index);
    
    /**
     * Binds a buffer to a vertex buffer binding point.
     * 
     * In OpenGL: Maps to glBindVertexBuffer() (OpenGL 4.3+)
     * In Vulkan: Part of vkCmdBindVertexBuffers() command
     * 
     * @param ctx Command context for recording this command
     * @param bindingindex The vertex buffer binding point
     * @param buffer The buffer object to bind
     * @param offset The offset into the buffer
     * @param stride The stride between consecutive elements
     */
    void bindVertexBuffer(CommandContext ctx, int bindingindex, int buffer, long offset, int stride);
    
    /**
     * Configures a vertex attribute pointer for integer data.
     * 
     * In OpenGL: Maps to glVertexAttribIPointer()
     * In Vulkan: Part of vertex input state in pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @param index The attribute index
     * @param size Number of components per attribute
     * @param type Data type of each component
     * @param stride Byte offset between consecutive attributes
     * @param pointer Offset of the first component
     */
    void setVertexAttribIPointer(CommandContext ctx, int index, int size, int type, int stride, long pointer);
    
    /**
     * Disables a vertex attribute array.
     * 
     * In OpenGL: Maps to glDisableVertexAttribArray()
     * In Vulkan: Part of vertex input state in pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @param index The attribute index to disable
     */
    void disableVertexAttribArray(CommandContext ctx, int index);
    
    /**
     * Sets the vertex attribute divisor for instanced rendering.
     * 
     * In OpenGL: Maps to glVertexAttribDivisor()
     * In Vulkan: Part of vertex input state in pipeline creation
     * 
     * @param ctx Command context for recording this command
     * @param index The attribute index
     * @param divisor The divisor value (0 = per-vertex, N = per-instance every N instances)
     */
    void setVertexAttribDivisor(CommandContext ctx, int index, int divisor);
    
    /**
     * Sets a 4-component float vertex attribute value.
     * 
     * In OpenGL: Maps to glVertexAttrib4f()
     * In Vulkan: Sets a vertex input attribute constant value
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute
     * @param v0 The first component value
     * @param v1 The second component value
     * @param v2 The third component value
     * @param v3 The fourth component value
     */
    void setVertexAttrib4f(CommandContext ctx, int index, float v0, float v1, float v2, float v3);
    
    /**
     * Deletes a shader program.
     * 
     * In OpenGL: Maps to glDeleteProgram()
     * In Vulkan: Maps to pipeline destruction
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID to delete
     */
    void deleteProgram(CommandContext ctx, int program);
    
    /**
     * Deletes a shader object.
     * 
     * In OpenGL: Maps to glDeleteShader()
     * In Vulkan: Maps to shader module destruction
     * 
     * @param ctx Command context for recording this command
     * @param shader The shader object ID to delete
     */
    void deleteShader(CommandContext ctx, int shader);
    
    /**
     * Binds an attribute location in a shader program.
     * 
     * In OpenGL: Maps to glBindAttribLocation()
     * In Vulkan: Attributes are specified via layout qualifiers in shaders
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param index The attribute index
     * @param name The attribute name in the shader
     */
    void setAttributeLocation(CommandContext ctx, int program, int index, CharSequence name);
    
    
    // Vertex attributes
    
    /**
     * Issues a memory barrier to ensure memory operations are visible.
     * 
     * In OpenGL: Maps to glMemoryBarrier()
     * In Vulkan: Maps to vkCmdPipelineBarrier() with memory barriers
     * 
     * @param ctx Command context for recording this command
     * @param barriers Bitfield of memory barrier flags
     */
    void memoryBarrier(CommandContext ctx, int barriers);
    
    // Synchronization
    /**
     * Creates a fence sync object for GPU-CPU synchronization.
     * 
     * In OpenGL: Maps to glFenceSync()
     * In Vulkan: Maps to vkCreateFence()
     * 
     * @param ctx Command context for recording this command
     * @param condition Must be GL_SYNC_GPU_COMMANDS_COMPLETE
     * @param flags Must be 0 (reserved for future use)
     * @return Sync object handle
     */
    long createFenceSync(CommandContext ctx, int condition, int flags);
    
    /**
     * Deletes a fence sync object.
     * 
     * In OpenGL: Maps to glDeleteSync()
     * In Vulkan: Maps to vkDestroyFence()
     * 
     * @param ctx Command context for recording this command
     * @param sync The sync object to delete
     */
    void destroySync(CommandContext ctx, long sync);
    
    /**
     * Waits on a fence sync object, blocking until it signals or the timeout expires.
     * 
     * In OpenGL: Maps to glClientWaitSync()
     * In Vulkan: Maps to vkWaitForFences()
     * 
     * @param ctx Command context
     * @param sync The sync object to wait on
     * @param flags Bitfield controlling flush behaviour (e.g. GL_SYNC_FLUSH_COMMANDS_BIT)
     * @param timeout Timeout in nanoseconds; Long.MAX_VALUE means wait indefinitely
     * @return Result status (e.g. GL_ALREADY_SIGNALED, GL_CONDITION_SATISFIED, GL_TIMEOUT_EXPIRED)
     */
    int waitForSync(CommandContext ctx, long sync, int flags, long timeout);
    
    // Texture queries
    /**
     * Checks if a name corresponds to a texture object.
     * 
     * In OpenGL: Maps to glIsTexture()
     * In Vulkan: Would query internal texture registry
     * 
     * @param ctx Command context for recording this command
     * @param texture The name to check
     * @return True if texture is a texture object, false otherwise
     */
    boolean isTexture(CommandContext ctx, int texture);
    
    /**
     * Returns the value of a texture-level parameter.
     * 
     * In OpenGL: Maps to glGetTexLevelParameteriv()
     * In Vulkan: Would query VkImage properties via vkGetImageSubresourceLayout
     * 
     * @param ctx Command context
     * @param target Texture target (e.g. GL_TEXTURE_2D)
     * @param level Mipmap level
     * @param pname Parameter name (e.g. GL_TEXTURE_WIDTH)
     * @return The integer parameter value
     */
    int getTextureLevelParameter(CommandContext ctx, int target, int level, int pname);
    
    // Shader source (native)
    /**
     * Uploads GLSL source code to a shader object using native pointer addresses.
     * 
     * In OpenGL: Maps to nglShaderSource()
     * In Vulkan: Not used – Vulkan uses SPIR-V bytecode via vkCreateShaderModule
     * 
     * @param ctx Command context
     * @param shader The shader object ID
     * @param pointerBufferAddress Native address of the string pointer array
     * @param stringCount Number of strings
     * @param lengthsPointer Native address of the lengths array (0 means null-terminated)
     */
    void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer);
    
    /**
     * Binds a uniform block to a uniform block binding point.
     * 
     * In OpenGL: Maps to glUniformBlockBinding()
     * In Vulkan: Maps to descriptor set binding
     * 
     * @param ctx Command context for recording this command
     * @param program The shader program object ID
     * @param uniformBlockIndex The index of the uniform block
     * @param uniformBlockBinding The binding point to bind to
     */
    void uniformBlockBinding(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding);
    
    /**
     * Returns the name string of an active uniform block in a shader program.
     * 
     * In OpenGL: Maps to glGetActiveUniformBlockName()
     * In Vulkan: Would query descriptor set layout reflection data
     * 
     * @param ctx Command context
     * @param program The shader program object ID
     * @param uniformBlockIndex The index of the uniform block
     * @return The name of the uniform block
     */
    String retrieveActiveUniformBlockName(CommandContext ctx, int program, int uniformBlockIndex);
    
    // Timer query operations
    /**
     * Creates a new query object.
     * 
     * In OpenGL: Maps to glGenQueries()
     * In Vulkan: Maps to vkCreateQueryPool()
     * 
     * @param ctx Command context
     * @return The new query object name/ID
     */
    int generateQueryObject(CommandContext ctx);
    
    /**
     * Begins a query block.
     * 
     * In OpenGL: Maps to glBeginQuery()
     * In Vulkan: Maps to vkCmdBeginQuery()
     * 
     * @param ctx Command context
     * @param target The query target (e.g. GL_TIME_ELAPSED)
     * @param id The query object name/ID
     */
    void initiateQuery(CommandContext ctx, int target, int id);
    
    /**
     * Ends a query block.
     * 
     * In OpenGL: Maps to glEndQuery()
     * In Vulkan: Maps to vkCmdEndQuery()
     * 
     * @param ctx Command context
     * @param target The query target
     */
    void concludeQuery(CommandContext ctx, int target);
    
    /**
     * Deletes a query object.
     * 
     * In OpenGL: Maps to glDeleteQueries()
     * In Vulkan: Maps to vkDestroyQueryPool()
     * 
     * @param ctx Command context
     * @param id The query object name/ID to delete
     */
    void disposeQueryObject(CommandContext ctx, int id);
    
    /**
     * Returns the integer value of a query object parameter.
     * 
     * In OpenGL: Maps to glGetQueryObjectiv()
     * In Vulkan: Maps to vkGetQueryPoolResults()
     * 
     * @param ctx Command context
     * @param id The query object name/ID
     * @param pname The parameter to query (e.g. GL_QUERY_RESULT_AVAILABLE)
     * @return The integer result
     */
    int retrieveQueryObjectInt(CommandContext ctx, int id, int pname);
    
    /**
     * Returns the 64-bit integer value of a query object parameter.
     * 
     * In OpenGL: Maps to glGetQueryObjecti64v() (ARB_timer_query)
     * In Vulkan: Maps to vkGetQueryPoolResults() with 64-bit flag
     * 
     * @param ctx Command context
     * @param id The query object name/ID
     * @param pname The parameter to query (e.g. GL_QUERY_RESULT)
     * @return The 64-bit integer result
     */
    long retrieveQueryObjectInt64(CommandContext ctx, int id, int pname);
    
    // Debug label operations (KHR_debug)
    /**
     * Labels a debug object for easier identification in debugging tools.
     * 
     * In OpenGL: Maps to glObjectLabel()
     * In Vulkan: Maps to vkSetDebugUtilsObjectNameEXT()
     * 
     * @param ctx Command context for recording this command
     * @param identifier The type of object being labeled (GL_BUFFER, GL_TEXTURE, etc.)
     * @param name The name/ID of the object
     * @param label The debug label string
     */
    void labelDebugObject(CommandContext ctx, int identifier, int name, String label);
    
    /**
     * Pushes a debug group for hierarchical organization of debug messages.
     * 
     * In OpenGL: Maps to glPushDebugGroup()
     * In Vulkan: Maps to vkCmdBeginDebugUtilsLabelEXT()
     * 
     * @param ctx Command context for recording this command
     * @param source The source of the debug group (typically GL_DEBUG_SOURCE_APPLICATION)
     * @param id User-provided ID for the debug group
     * @param message The debug group message
     */
    void enterDebugGroup(CommandContext ctx, int source, int id, CharSequence message);
    
    /**
     * Pops the current debug group from the stack.
     * 
     * In OpenGL: Maps to glPopDebugGroup()
     * In Vulkan: Maps to vkCmdEndDebugUtilsLabelEXT()
     * 
     * @param ctx Command context for recording this command
     */
    void exitDebugGroup(CommandContext ctx);
    
    /**
     * Controls debug message filtering (GL43/KHR_debug).
     * 
     * In OpenGL: Maps to glDebugMessageControl()
     * In Vulkan: Controls validation layer message filtering via VK_EXT_debug_utils
     * 
     * @param ctx Command context for recording this command
     * @param source Message source filter
     * @param type Message type filter
     * @param severity Message severity filter
     * @param ids Array of message IDs to filter (null for all)
     * @param enabled True to enable messages, false to disable
     */
    void debugMessageControl(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled);
    
    /**
     * Controls debug message filtering (KHR_debug extension).
     * 
     * In OpenGL: Maps to glDebugMessageControl() from KHR_debug extension
     * In Vulkan: Controls validation layer message filtering via VK_EXT_debug_utils
     * 
     * @param ctx Command context for recording this command
     * @param source Message source filter
     * @param type Message type filter
     * @param severity Message severity filter
     * @param ids Array of message IDs to filter (null for all)
     * @param enabled True to enable messages, false to disable
     */
    void debugMessageControlKHR(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled);
    
    /**
     * Controls debug message filtering (ARB_debug_output extension).
     * 
     * In OpenGL: Maps to glDebugMessageControlARB() from ARB_debug_output extension
     * In Vulkan: Controls validation layer message filtering via VK_EXT_debug_utils
     * 
     * @param ctx Command context for recording this command
     * @param source Message source filter
     * @param type Message type filter
     * @param severity Message severity filter
     * @param ids Array of message IDs to filter (null for all)
     * @param enabled True to enable messages, false to disable
     */
    void debugMessageControlARB(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled);
    
    /**
     * Controls debug message filtering (AMD_debug_output extension).
     * 
     * In OpenGL: Maps to glDebugMessageEnableAMD() from AMD_debug_output extension
     * In Vulkan: Controls validation layer message filtering via VK_EXT_debug_utils
     * 
     * @param ctx Command context for recording this command
     * @param category Message category filter
     * @param severity Message severity filter
     * @param ids Array of message IDs to filter (null for all)
     * @param enabled True to enable messages, false to disable
     */
    void debugMessageEnableAMD(CommandContext ctx, int category, int severity, int[] ids, boolean enabled);
    
    // Debug label operations (EXT_debug_label)
    /**
     * Labels an object using the EXT_debug_label extension.
     * 
     * In OpenGL: Maps to glLabelObjectEXT()
     * In Vulkan: Maps to vkSetDebugUtilsObjectNameEXT()
     * 
     * @param ctx Command context for recording this command
     * @param type The type identifier for the object (e.g., GL_BUFFER_OBJECT_EXT)
     * @param object The object name/handle
     * @param label The debug label string
     */
    void labelObjectExt(CommandContext ctx, int type, int object, String label);
    
    // Debug system initialization (wraps entire debug setup)
    boolean supportsKhrDebug();
    boolean supportsArbDebugOutput();
    void setupKhrDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler);
    void setupArbDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler);
    
    // Extension capability checking
    boolean hasBufferStorageExtension();
    boolean hasVertexAttribBindingExtension();
    
    // ARB vertex attrib binding operations (all callers migrated to bindVertexBuffer/setVertexAttrib* ctx methods)
    
    // Clear operations (setClearDepth and setClearColor already declared earlier in this interface)
    
    /**
     * Specifies the color buffer to draw into.
     * In OpenGL: Maps to glDrawBuffer()
     * In Vulkan: Part of render pass color attachments
     * @param ctx Command context for recording this command
     * @param mode The draw buffer target
     */
    void setDrawBuffer(CommandContext ctx, int mode);
    
    // Advanced drawing operations
    /**
     * Renders indexed primitives with instancing and a base vertex.
     * In OpenGL: Maps to glDrawElementsInstancedBaseVertex()
     * In Vulkan: Maps to vkCmdDrawIndexed() with firstVertex parameter
     * @param ctx Command context for recording this command
     */
    void drawIndexedInstancedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount, int baseVertex);
    
    /**
     * Renders indexed primitives with a base vertex offset.
     * In OpenGL: Maps to glDrawElementsBaseVertex()
     * In Vulkan: Maps to vkCmdDrawIndexed() with vertexOffset parameter
     * @param ctx Command context for recording this command
     */
    void drawIndexedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int baseVertex);
    
    /**
     * Renders indexed primitives with instancing.
     * In OpenGL: Maps to glDrawElementsInstanced()
     * In Vulkan: Maps to vkCmdDrawIndexed() with instanceCount parameter
     * @param ctx Command context for recording this command
     */
    void drawIndexedInstanced(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount);
    
    /**
     * Renders primitives using array data with instancing.
     * In OpenGL: Maps to glDrawArraysInstanced()
     * In Vulkan: Maps to vkCmdDraw() with instanceCount parameter
     * @param ctx Command context for recording this command
     */
    void drawArraysInstanced(CommandContext ctx, int mode, int first, int count, int instanceCount);
    
    // Uniform buffer operations
    /**
     * Binds a range of a buffer object to a uniform buffer binding point.
     * In OpenGL: Maps to glBindBufferRange() with GL_UNIFORM_BUFFER target
     * In Vulkan: Part of VkDescriptorBufferInfo / vkUpdateDescriptorSets
     * @param ctx Command context for recording this command
     */
    void bindUniformBufferRange(CommandContext ctx, int target, int index, int buffer, long offset, long size);
    
    // Texture buffer operations
    /**
     * Attaches a buffer object to a texture buffer object.
     * In OpenGL: Maps to glTexBuffer()
     * In Vulkan: Implemented via VK_EXT_texel_buffer
     * @param ctx Command context for recording this command
     */
    void texBuffer(CommandContext ctx, int target, int internalFormat, int buffer);
    
    // Uniform operations (additional, all with CommandContext)
    void setUniform2fv(CommandContext ctx, int location, float[] value);
    void setUniform3fv(CommandContext ctx, int location, float[] value);
    void setUniform4fv(CommandContext ctx, int location, float[] value);
    void bindUniformBufferBase(CommandContext ctx, int bindingPoint, int bufferId);
    
    // Program fragment data binding
    void bindFragDataLocation(CommandContext ctx, int program, int colorNumber, CharSequence name);
    
    // Sync query operations
    int getSynci(CommandContext ctx, long sync, int pname, java.nio.IntBuffer length);
    
    // Graphics Capabilities
    GraphicsCapabilities obtainGraphicsCapabilities();
    GraphicsCapabilities initializeGraphicsCapabilities();
    boolean checkFunctionAvailable(String functionName);
    
    // Additional buffer operations for Sodium
    void deleteVertexArrays(CommandContext ctx, int vertexArray);
    
    // Multi-draw operations
    void multiDrawElementsBaseVertex(CommandContext ctx, int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex);
    
    // Texture operations
    
    // Clear texture image (ARB_clear_texture)
    /**
     * Clears a texture image to a specified value.
     * 
     * In OpenGL: Maps to glClearTexImage()
     * In Vulkan: Maps to vkCmdClearColorImage() or vkCmdClearDepthStencilImage()
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture to clear
     * @param level The mipmap level to clear
     * @param format The format of the clear data
     * @param type The type of the clear data
     * @param data The clear value data (can be null for zero-fill)
     */
    void clearTexImage(CommandContext ctx, int texture, int level, int format, int type, int[] data);
    
    
    // Parallel shader compile (KHR/ARB)
    void setMaxShaderCompilerThreads(int count);
    
    // Capabilities
    GraphicsCapabilities getGraphicsCapabilities();
    
    // Debug object labeling
    
    // Debug group push/pop
    
    // Additional methods for IrisRenderSystem
    
    /**
     * Returns multiple integer values from the OpenGL state.
     * 
     * In OpenGL: Maps to glGetIntegerv()
     * In Vulkan: May query device limits or current state
     * 
     * @param ctx Command context for recording this command
     * @param pname The state parameter to query (e.g., GL_MAX_TEXTURE_SIZE)
     * @param params Array to receive the values
     */
    void getIntegerv(CommandContext ctx, int pname, int[] params);
    
    /**
     * Returns multiple float values from the OpenGL state.
     * 
     * In OpenGL: Maps to glGetFloatv()
     * In Vulkan: May query device limits or current state
     * 
     * @param ctx Command context for recording this command
     * @param pname The state parameter to query (e.g., GL_MAX_VIEWPORT_DIMS)
     * @param params Array to receive the values
     */
    void getFloatv(CommandContext ctx, int pname, float[] params);
    
    
    /**
     * Uploads pixel data to a 1D texture.
     * 
     * In OpenGL: Maps to glTexImage1D()
     * In Vulkan: Maps to vkCmdCopyBufferToImage() after staging buffer setup
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_1D)
     * @param level Mipmap level
     * @param internalformat Internal format of the texture
     * @param width Width of the texture
     * @param border Border width (must be 0)
     * @param format Format of the pixel data
     * @param type Data type of the pixel data
     * @param pixels Pixel data buffer (can be null)
     */
    void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels);
    
    /**
     * Uploads pixel data to a 3D texture.
     * 
     * In OpenGL: Maps to glTexImage3D()
     * In Vulkan: Maps to vkCmdCopyBufferToImage() after staging buffer setup
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_3D)
     * @param level Mipmap level
     * @param internalformat Internal format of the texture
     * @param width Width of the texture
     * @param height Height of the texture
     * @param depth Depth of the texture
     * @param border Border width (must be 0)
     * @param format Format of the pixel data
     * @param type Data type of the pixel data
     * @param pixels Pixel data buffer (can be null)
     */
    void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels);
    
    /**
     * Copies pixels from framebuffer to a 2D texture.
     * 
     * In OpenGL: Maps to glCopyTexImage2D()
     * In Vulkan: Maps to vkCmdCopyImage() or vkCmdBlitImage()
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param level Mipmap level
     * @param internalFormat Internal format of the texture
     * @param x X coordinate of the lower-left corner of the framebuffer region
     * @param y Y coordinate of the lower-left corner of the framebuffer region
     * @param width Width of the texture and framebuffer region
     * @param height Height of the texture and framebuffer region
     * @param border Border width (must be 0)
     */
    void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat, int x, int y, int width, int height, int border);
    
    /**
     * Copies a region of pixels from one image to another.
     * 
     * In OpenGL: Maps to glCopyImageSubData()
     * In Vulkan: Maps to vkCmdCopyImage()
     * 
     * @param ctx Command context for recording this command
     * @param srcName Source image name
     * @param srcTarget Source image target
     * @param srcLevel Source mipmap level
     * @param srcX Source X coordinate
     * @param srcY Source Y coordinate
     * @param srcZ Source Z coordinate
     * @param dstName Destination image name
     * @param dstTarget Destination image target
     * @param dstLevel Destination mipmap level
     * @param dstX Destination X coordinate
     * @param dstY Destination Y coordinate
     * @param dstZ Destination Z coordinate
     * @param width Width of the region to copy
     * @param height Height of the region to copy
     * @param depth Depth of the region to copy
     */
    void copyImageSubData(CommandContext ctx, int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, 
                         int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, 
                         int width, int height, int depth);
    
    
    /**
     * Reads pixels from the framebuffer into a float array.
     * 
     * In OpenGL: Maps to glReadPixels()
     * In Vulkan: Maps to vkCmdCopyImageToBuffer() followed by buffer read
     * 
     * @param ctx Command context for recording this command
     * @param x The x coordinate of the lower-left corner of the rectangular region
     * @param y The y coordinate of the lower-left corner of the rectangular region
     * @param width Width of the pixel rectangle
     * @param height Height of the pixel rectangle
     * @param format Format of the pixel data (e.g., GL_RGBA)
     * @param type Data type of the pixel data (e.g., GL_FLOAT)
     * @param pixels Float array to receive the pixel data
     */
    void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels);
    
    /**
     * Reads a block of pixels from the framebuffer into a native memory address.
     * 
     * In OpenGL: Maps to glReadPixels() with a native pointer offset (for PBO reads)
     * In Vulkan: Maps to vkCmdCopyImageToBuffer with a dst buffer offset
     * 
     * @param ctx Command context for recording this command
     * @param x The x coordinate of the lower-left corner of the rectangular region
     * @param y The y coordinate of the lower-left corner of the rectangular region
     * @param width Width of the pixel rectangle
     * @param height Height of the pixel rectangle
     * @param format Format of the pixel data (e.g., GL_RGBA)
     * @param type Data type of the pixel data (e.g., GL_UNSIGNED_BYTE)
     * @param pixels Native memory address to receive the pixel data
     */
    void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, long pixels);
    
    /**
     * Returns a string value from the OpenGL implementation.
     * 
     * In OpenGL: Maps to glGetStringi()
     * In Vulkan: May query device properties or extension names
     * 
     * @param ctx Command context for recording this command
     * @param name The symbolic constant identifying the string (e.g., GL_EXTENSIONS)
     * @param index The index of the string to return
     * @return The requested string
     */
    String getString(CommandContext ctx, int name, int index);
    
    /**
     * Returns a string describing the current GL connection.
     * 
     * In OpenGL: Maps to glGetString()
     * In Vulkan: May query device properties
     * 
     * @param ctx Command context for recording this command
     * @param name The symbolic constant identifying the string (e.g., GL_VENDOR, GL_VERSION)
     * @return The requested string
     */
    String getString(CommandContext ctx, int name);
    
    
    // DSA methods
    
    /**
     * Copies a portion of a read framebuffer to a texture subregion using Direct State Access.
     * 
     * In OpenGL: Maps to glCopyTextureSubImage2D() from ARB_direct_state_access
     * In Vulkan: Maps to vkCmdCopyImageToBuffer() + vkCmdCopyBufferToImage() sequence
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture object to copy into
     * @param level The mipmap level to copy into
     * @param xoffset The x offset within the texture
     * @param yoffset The y offset within the texture
     * @param x The x coordinate of the source framebuffer region
     * @param y The y coordinate of the source framebuffer region
     * @param width The width of the region to copy
     * @param height The height of the region to copy
     */
    void copyTextureSubImage2D(CommandContext ctx, int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    
    /**
     * Binds a texture to a specified texture unit using Direct State Access.
     * 
     * In OpenGL: Maps to glBindTextureUnit() from ARB_direct_state_access
     * In Vulkan: Maps to descriptor set binding (vkCmdBindDescriptorSets)
     * 
     * @param ctx Command context for recording this command
     * @param unit The texture unit to bind to (0-based)
     * @param texture The texture object to bind
     */
    void bindTextureUnit(CommandContext ctx, int unit, int texture);
    
    /**
     * Creates a new buffer object using Direct State Access.
     * 
     * In OpenGL: Maps to glCreateBuffers() from ARB_direct_state_access
     * In Vulkan: Maps to vkCreateBuffer()
     * 
     * @param ctx Command context for recording this command
     * @return The buffer object ID
     */
    int createBuffers(CommandContext ctx);
    
    /**
     * Uploads float array data to a named buffer using Direct State Access.
     * 
     * In OpenGL: Maps to glNamedBufferData() from ARB_direct_state_access
     * In Vulkan: Maps to vkCmdUpdateBuffer() or buffer memory mapping
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer object to update
     * @param data The float array data to upload
     * @param usage Usage hint for the buffer (e.g., GL_STATIC_DRAW)
     */
    void namedBufferData(CommandContext ctx, int buffer, float[] data, int usage);
    
    /**
     * Copies a rectangular region between two named framebuffers using Direct State Access.
     * 
     * In OpenGL: Maps to glBlitNamedFramebuffer() from ARB_direct_state_access
     * In Vulkan: Maps to vkCmdBlitImage()
     * 
     * @param ctx Command context for recording this command
     * @param readFramebuffer The source framebuffer
     * @param drawFramebuffer The destination framebuffer
     * @param srcX0 Source region left coordinate
     * @param srcY0 Source region bottom coordinate
     * @param srcX1 Source region right coordinate
     * @param srcY1 Source region top coordinate
     * @param dstX0 Destination region left coordinate
     * @param dstY0 Destination region bottom coordinate
     * @param dstX1 Destination region right coordinate
     * @param dstY1 Destination region top coordinate
     * @param mask Bitfield of buffers to copy (e.g., GL_COLOR_BUFFER_BIT)
     * @param filter Interpolation filter (GL_NEAREST or GL_LINEAR)
     */
    void blitNamedFramebuffer(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    /**
     * Attaches a texture to a framebuffer attachment point using Direct State Access.
     * 
     * In OpenGL: Maps to glNamedFramebufferTexture() from ARB_direct_state_access
     * In Vulkan: Part of framebuffer/render pass setup (VkFramebufferCreateInfo)
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0)
     * @param texture The texture object to attach
     * @param level The mipmap level of the texture to attach
     */
    void namedFramebufferTexture(CommandContext ctx, int framebuffer, int attachment, int texture, int level);
    
    /**
     * Creates a new framebuffer object using Direct State Access.
     * 
     * In OpenGL: Maps to glCreateFramebuffers() from ARB_direct_state_access
     * In Vulkan: Maps to vkCreateFramebuffer()
     * 
     * @param ctx Command context for recording this command
     * @return The framebuffer object ID
     */
    int createFramebuffers(CommandContext ctx);
    
    /**
     * Creates a new texture object for a specific target using Direct State Access.
     * 
     * In OpenGL: Maps to glCreateTextures() from ARB_direct_state_access
     * In Vulkan: Maps to vkCreateImage()
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D, GL_TEXTURE_CUBE_MAP)
     * @return The texture object ID
     */
    int createTextures(CommandContext ctx, int target);
    
    /**
     * Generates mipmaps for a texture bound to the specified target.
     * 
     * In OpenGL: Maps to glGenerateMipmap()
     * In Vulkan: Maps to vkCmdBlitImage() with mipmap level generation
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     */
    void generateMipmap(CommandContext ctx, int target);
    
    /**
     * Sets a floating-point texture parameter for a texture bound to the specified target.
     * 
     * In OpenGL: Maps to glTexParameterf()
     * In Vulkan: Part of sampler state (VkSamplerCreateInfo)
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param pname The parameter name (e.g., GL_TEXTURE_MIN_FILTER)
     * @param param The parameter value
     */
    void texParameterf(CommandContext ctx, int target, int pname, float param);
    
    /**
     * Sets a single integer texture parameter.
     * 
     * In OpenGL: Maps to glTexParameteri()
     * In Vulkan: Maps to sampler or image view creation parameters
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param pname The parameter name (e.g., GL_TEXTURE_MIN_FILTER, GL_TEXTURE_BASE_LEVEL)
     * @param param The integer parameter value
     */
    void texParameteri(CommandContext ctx, int target, int pname, int param);
    
    
    // Additional rendering operations
    
    // Debug callback methods (low-level control methods only)
    
    // High-level debug callback wrapper methods
    void setupDebugMessageCallback(VulkanicAPI.DebugMessageCallback callback);
    void setupDebugMessageCallbackKHR(VulkanicAPI.DebugMessageCallback callback);
    void setupDebugMessageCallbackARB(VulkanicAPI.DebugMessageCallbackARB callback);
    void setupDebugMessageCallbackAMD(VulkanicAPI.DebugMessageCallbackAMD callback);
    void clearDebugMessageCallback();
    void clearDebugMessageCallbackKHR();
    void clearDebugMessageCallbackARB();
    void clearDebugMessageCallbackAMD();
    
    // GL43+ vertex attribute methods
    
    /**
     * Specifies the layout of a vertex attribute (float) using the GL43+ vertex format API.
     * 
     * In OpenGL: Maps to glVertexAttribFormat()
     * In Vulkan: Part of VkPipelineVertexInputStateCreateInfo vertex attribute description
     * 
     * @param ctx Command context for recording this command
     * @param attribindex The attribute index
     * @param size Number of components (1, 2, 3 or 4)
     * @param type Data type of each component
     * @param normalized Whether to normalize fixed-point data
     * @param relativeoffset Offset of the first element within the attribute buffer
     */
    void setVertexAttribFormat(CommandContext ctx, int attribindex, int size, int type, boolean normalized, int relativeoffset);
    
    /**
     * Specifies the layout of an integer vertex attribute using the GL43+ vertex format API.
     * 
     * In OpenGL: Maps to glVertexAttribIFormat()
     * In Vulkan: Part of VkPipelineVertexInputStateCreateInfo vertex attribute description
     * 
     * @param ctx Command context for recording this command
     * @param attribindex The attribute index
     * @param size Number of components (1, 2, 3 or 4)
     * @param type Integer data type of each component
     * @param relativeoffset Offset of the first element within the attribute buffer
     */
    void setVertexAttribIFormat(CommandContext ctx, int attribindex, int size, int type, int relativeoffset);
    
    /**
     * Associates a vertex attribute index with a vertex buffer binding point.
     * 
     * In OpenGL: Maps to glVertexAttribBinding()
     * In Vulkan: Part of VkVertexInputAttributeDescription binding field
     * 
     * @param ctx Command context for recording this command
     * @param attribindex The attribute index
     * @param bindingindex The vertex buffer binding point
     */
    void setVertexAttribBinding(CommandContext ctx, int attribindex, int bindingindex);
    
    // VAO methods
    
    // GL context capabilities
    Object getGLCapabilities();
    void setupDebugMessageCallback(java.io.PrintStream stream);
    
    // Capability checking methods (to avoid casting GLCapabilities outside backends/opengl)
    boolean checkOpenGL32Support();
    boolean checkOpenGL33Support();
    boolean checkARBInstancedArraysSupport();
    long getNamedBufferDataPointer();
    long getBufferStoragePointer();
    long getBindVertexBufferPointer();
    
    // Additional GL query and state methods
    
    /**
     * Checks if a name corresponds to a framebuffer object.
     * 
     * In OpenGL: Maps to glIsFramebuffer()
     * In Vulkan: Would query internal framebuffer registry
     * 
     * @param ctx Command context
     * @param framebuffer The name to check
     * @return True if the name is a framebuffer object
     */
    boolean isFramebuffer(CommandContext ctx, int framebuffer);
    
    /**
     * Checks if a name corresponds to a vertex array object.
     * 
     * In OpenGL: Maps to glIsVertexArray()
     * In Vulkan: Would query internal vertex array registry
     * 
     * @param ctx Command context
     * @param array The name to check
     * @return True if the name is a vertex array object
     */
    boolean isVertexArray(CommandContext ctx, int array);
    
    /**
     * Checks if a name corresponds to a program object.
     * 
     * In OpenGL: Maps to glIsProgram()
     * In Vulkan: Would query internal pipeline registry
     * 
     * @param ctx Command context
     * @param program The name to check
     * @return True if the name is a program object
     */
    boolean isProgram(CommandContext ctx, int program);
    
    // Additional GL state methods
    
    /**
     * Sets the RGB and alpha blend equations separately.
     * 
     * In OpenGL: Maps to glBlendEquationSeparate()
     * In Vulkan: Configured in VkPipelineColorBlendStateCreateInfo
     * 
     * @param ctx Command context
     * @param modeRGB Blend equation for RGB components
     * @param modeAlpha Blend equation for the alpha component
     */
    void setBlendEquationSeparate(CommandContext ctx, int modeRGB, int modeAlpha);
    
    /**
     * Sets the stencil test function and reference value.
     * 
     * In OpenGL: Maps to glStencilFunc()
     * In Vulkan: Maps to vkCmdSetStencilCompareMask() / vkCmdSetStencilReference()
     * 
     * @param ctx Command context
     * @param func Comparison function (e.g. GL_ALWAYS, GL_EQUAL)
     * @param ref Reference value for the stencil test
     * @param mask Mask ANDed with both the reference and the stored stencil value
     */
    void setStencilFunc(CommandContext ctx, int func, int ref, int mask);
    
    // Additional texture methods
    
    /**
     * Dispatches compute shader work groups.
     * 
     * In OpenGL: Maps to glDispatchCompute()
     * In Vulkan: Maps to vkCmdDispatch()
     * 
     * @param ctx Command context for recording this command
     * @param workX Number of work groups in X dimension
     * @param workY Number of work groups in Y dimension  
     * @param workZ Number of work groups in Z dimension
     */
    void dispatchCompute(CommandContext ctx, int workX, int workY, int workZ);
    
    /**
     * Dispatches compute work groups with parameters from a buffer.
     * 
     * In OpenGL: Maps to glDispatchComputeIndirect()
     * In Vulkan: Maps to vkCmdDispatchIndirect()
     * 
     * @param ctx Command context for recording this command
     * @param offset Offset in the buffer binding where dispatch parameters are stored
     */
    void dispatchComputeIndirect(CommandContext ctx, long offset);
    
    /**
     * Checks if a name corresponds to a buffer object.
     * 
     * In OpenGL: Maps to glIsBuffer()
     * In Vulkan: Would check buffer handle validity
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer name to check
     * @return true if buffer is a valid buffer object name
     */
    boolean isBuffer(CommandContext ctx, int buffer);
    
    /**
     * Tests whether a specific capability is enabled.
     * 
     * In OpenGL: Maps to glIsEnabled()
     * In Vulkan: Would map to pipeline state queries or current render pass state
     * 
     * @param ctx Command context for recording this command
     * @param cap The capability to test (e.g., GL_BLEND, GL_DEPTH_TEST)
     * @return true if the capability is enabled
     */
    boolean isEnabled(CommandContext ctx, int cap);
    
    /**
     * Sets texture parameters using an array of integers.
     * 
     * In OpenGL: Maps to glTexParameteriv()
     * In Vulkan: Maps to sampler or image view creation parameters
     * 
     * @param ctx Command context for recording this command
     * @param target Texture target (GL_TEXTURE_2D, etc.)
     * @param pname Parameter name (GL_TEXTURE_BORDER_COLOR, etc.)
     * @param params Array of parameter values
     */
    void texParameteriv(CommandContext ctx, int target, int pname, int[] params);
    
    /**
     * Retrieves the index of a uniform block in a shader program.
     * 
     * In OpenGL: Maps to glGetUniformBlockIndex()
     * In Vulkan: Maps to descriptor set layout binding queries
     * 
     * @param ctx Command context for recording this command
     * @param program The shader program
     * @param uniformBlockName Name of the uniform block
     * @return The index of the uniform block, or GL_INVALID_INDEX if not found
     */
    int getUniformBlockIndex(CommandContext ctx, int program, String uniformBlockName);
    
    /**
     * Gets the maximum number of image units supported by the implementation.
     * 
     * In OpenGL: Maps to GL_MAX_IMAGE_UNITS query
     * In Vulkan: Maps to VkPhysicalDeviceLimits.maxPerStageDescriptorStorageImages
     * 
     * @param ctx Command context for recording this command
     * @return The maximum number of image units
     */
    int getMaxImageUnits(CommandContext ctx);
    
    /**
     * Clears a sub-region of a buffer object's data store with a constant value.
     * 
     * In OpenGL: Maps to glClearBufferSubData()
     * In Vulkan: Maps to vkCmdFillBuffer() or vkCmdUpdateBuffer()
     * 
     * @param ctx Command context for recording this command
     * @param target Buffer target (e.g., GL_ARRAY_BUFFER)
     * @param internalformat Internal format to use for clearing
     * @param offset Offset in bytes into the buffer
     * @param size Size in bytes of the region to clear
     * @param format Format of the data
     * @param type Type of the data
     * @param data Data to use for clearing
     */
    void clearBufferSubData(CommandContext ctx, int target, int internalformat, long offset, long size, int format, int type, int[] data);
    
    /**
     * Clears a floating-point buffer.
     * 
     * In OpenGL: Maps to glClearBufferfv()
     * In Vulkan: Maps to vkCmdClearColorImage() or render pass clear attachment
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer to clear (GL_COLOR, GL_DEPTH, etc.)
     * @param drawbuffer Draw buffer index
     * @param values Float values to clear with
     */
    void clearBufferfv(CommandContext ctx, int buffer, int drawbuffer, float[] values);
    
    /**
     * Clears an integer buffer.
     * 
     * In OpenGL: Maps to glClearBufferiv()
     * In Vulkan: Maps to vkCmdClearColorImage() or render pass clear attachment
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer to clear (GL_COLOR, GL_DEPTH, etc.)
     * @param drawbuffer Draw buffer index
     * @param values Integer values to clear with
     */
    void clearBufferiv(CommandContext ctx, int buffer, int drawbuffer, int[] values);
    
    /**
     * Clears an unsigned integer buffer.
     * 
     * In OpenGL: Maps to glClearBufferuiv()
     * In Vulkan: Maps to vkCmdClearColorImage() or render pass clear attachment
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer to clear (GL_COLOR, GL_DEPTH, etc.)
     * @param drawbuffer Draw buffer index
     * @param values Unsigned integer values to clear with
     */
    void clearBufferuiv(CommandContext ctx, int buffer, int drawbuffer, int[] values);

    // =========================================================================
    // Phase 3a: Buffer Lifecycle
    // =========================================================================

    /**
     * Creates a new GPU buffer with the given usage flags and size.
     *
     * <p>In OpenGL: Calls glGenBuffers + glBufferData (or glBufferStorage if available).
     * In Vulkan: Calls vkCreateBuffer + vkAllocateMemory + vkBindBufferMemory.
     *
     * @param label debug label for the buffer (may be null)
     * @param usage usage flags (VulkanicBuffer.USAGE_* constants)
     * @param size  size in bytes (must be > 0)
     * @return a new VulkanicBuffer ready for use
     */
    VulkanicBuffer createManagedBuffer(Supplier<String> label, int usage, int size);

    /**
     * Creates a new GPU buffer initialized with the given data.
     *
     * @param label       debug label for the buffer (may be null)
     * @param usage       usage flags (VulkanicBuffer.USAGE_* constants)
     * @param initialData ByteBuffer containing the initial data (must have remaining bytes)
     * @return a new VulkanicBuffer containing the uploaded data
     */
    VulkanicBuffer createManagedBuffer(Supplier<String> label, int usage, java.nio.ByteBuffer initialData);

    /**
     * Maps a managed buffer for CPU access.
     *
     * <p>The returned MappedView must be closed when done to unmap the buffer.
     *
     * @param buffer    the buffer to map
     * @param read      true if the mapping will be used for reading
     * @param write     true if the mapping will be used for writing
     * @return a MappedView providing CPU access to the buffer's memory
     */
    VulkanicBuffer.MappedView mapManagedBuffer(VulkanicBuffer buffer, boolean read, boolean write);

    // =========================================================================
    // Phase 3b: Texture Lifecycle
    // =========================================================================

    /**
     * Creates a new GPU texture with the given parameters.
     *
     * <p>In OpenGL: Calls glGenTextures + glTexImage2D (or glTexStorage2D).
     * In Vulkan: Calls vkCreateImage + vkAllocateMemory + vkBindImageMemory.
     *
     * @param label         debug label (may be null)
     * @param usage         usage flags (VulkanicTexture.USAGE_* constants)
     * @param format        texture format
     * @param width         width in pixels
     * @param height        height in pixels
     * @param depthOrLayers depth for 3D textures, layer count for arrays (usually 1)
     * @param mipLevels     number of mip levels (at least 1)
     * @return a new VulkanicTexture ready for use
     */
    VulkanicTexture createManagedTexture(String label, int usage, VulkanicTextureFormat format,
                                          int width, int height, int depthOrLayers, int mipLevels);

    /**
     * Creates a view of the given texture covering all its mip levels.
     *
     * @param texture the parent texture
     * @return a texture view covering all mip levels
     */
    VulkanicTextureView createManagedTextureView(VulkanicTexture texture);

    /**
     * Creates a view of the given texture covering a specific mip range.
     *
     * @param texture       the parent texture
     * @param baseMipLevel  first mip level to expose
     * @param mipLevelCount number of mip levels to expose
     * @return a texture view covering the specified mip range
     */
    VulkanicTextureView createManagedTextureView(VulkanicTexture texture, int baseMipLevel, int mipLevelCount);

    // =========================================================================
    // Phase 3c: Pipeline Objects
    // =========================================================================

    /**
     * Creates (or retrieves a cached) compiled render pipeline from the given descriptor.
     *
     * <p>In OpenGL: Compiles and links vertex + fragment shaders into a GL program.
     * In Vulkan: Compiles SPIR-V shaders and creates a VkPipeline object.
     *
     * <p>Use {@link PipelineDescriptor#fromRenderPipeline} to create a descriptor from
     * an existing Blaze3D {@code RenderPipeline}.
     *
     * @param descriptor pipeline descriptor
     * @return a PipelineHandle for the compiled pipeline
     */
    PipelineHandle createPipeline(PipelineDescriptor descriptor);

    // =========================================================================
    // Phase 3d: Command Buffer Lifecycle
    // =========================================================================

    /**
     * Begins a new command buffer for recording rendering commands.
     *
     * <p>In OpenGL: Returns the singleton immediate-mode context (no recording needed).
     * In Vulkan: Calls vkBeginCommandBuffer and returns a CommandContext wrapping the handle.
     *
     * @return a CommandContext for recording commands
     */
    CommandContext beginCommandBuffer();

    /**
     * Submits a completed command buffer for GPU execution.
     *
     * <p>In OpenGL: No-op — commands execute immediately.
     * In Vulkan: Calls vkQueueSubmit to submit the recorded commands.
     *
     * @param ctx the command context returned by {@link #beginCommandBuffer()}
     */
    void submitCommandBuffer(CommandContext ctx);

    // =========================================================================
    // Phase 3b: Render Pass
    // =========================================================================

    /**
     * Begins a new render pass targeting the given color attachment.
     *
     * <p>In OpenGL: creates and binds a framebuffer object with {@code colorTarget}
     * attached, optionally clears it, and sets the viewport to the texture dimensions.
     * In Vulkan: records {@code vkCmdBeginRenderPass} into the command buffer
     * identified by {@code ctx}.
     *
     * <p>The returned {@link VulkanicRenderPass} must be closed (e.g. via
     * try-with-resources) to end the render pass and release GPU resources.
     *
     * @param ctx         command context (immediate-mode for OpenGL; VkCommandBuffer for Vulkan)
     * @param label       debug label for profiling tools (may be null)
     * @param colorTarget texture view to use as the color attachment
     * @param clearColor  if present, the ARGB color to clear the attachment before rendering
     * @return an active render pass for recording draw commands
     */
    VulkanicRenderPass beginRenderPass(CommandContext ctx, Supplier<String> label,
                                        VulkanicTextureView colorTarget, OptionalInt clearColor);

    /**
     * Begins a new render pass targeting a color and optional depth attachment.
     *
     * <p>In OpenGL: creates and binds an FBO with color and depth attachments,
     * optionally clears them, and sets the viewport.
     * In Vulkan: records {@code vkCmdBeginRenderPass}.
     *
     * @param ctx         command context
     * @param label       debug label (may be null)
     * @param colorTarget texture view for the color attachment
     * @param clearColor  if present, ARGB clear color
     * @param depthTarget texture view for the depth attachment (may be null)
     * @param clearDepth  if present, depth value to clear the depth attachment with
     * @return an active render pass for recording draw commands
     */
    VulkanicRenderPass beginRenderPass(CommandContext ctx, Supplier<String> label,
                                        VulkanicTextureView colorTarget, OptionalInt clearColor,
                                        @org.jetbrains.annotations.Nullable VulkanicTextureView depthTarget,
                                        OptionalDouble clearDepth);
}
