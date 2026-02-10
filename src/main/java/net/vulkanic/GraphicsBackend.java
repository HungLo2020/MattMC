package net.vulkanic;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

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
    @Deprecated
    long getGraphicsContext();
    
    @Deprecated
    void bindTexture(int textureId);
    @Deprecated
    void bindTexture(int target, int textureId);  // For explicit target binding
    @Deprecated
    void generateMipmap(int target);
    
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
    
    // Depth operations
    @Deprecated
    void setColorWriteMask(boolean r, boolean g, boolean b, boolean a);
    
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
     * Clears the specified buffers to their clear values.
     * 
     * In OpenGL: Maps to glClear()
     * In Vulkan: Maps to vkCmdClearAttachments() or part of vkCmdBeginRenderPass()
     * 
     * The mask parameter specifies which buffers should be cleared (color, depth, stencil).
     * This operation affects the current framebuffer/render target.
     * 
     * @param ctx Command context for recording this command
     * @param mask Bitwise OR of buffer masks (e.g., GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
     */
    void clear(CommandContext ctx, int mask);
    
    /**
     * Draws primitives using vertex array data.
     * 
     * In OpenGL: Maps to glDrawArrays()
     * In Vulkan: Maps to vkCmdDraw()
     * 
     * This command renders primitives from array data without using an index buffer.
     * The vertex data is read sequentially from the currently bound vertex buffer(s).
     * 
     * @param ctx Command context for recording this command
     * @param mode Primitive topology (e.g., GL_TRIANGLES, GL_LINES)
     * @param first Starting vertex index in the vertex buffer
     * @param count Number of vertices to draw
     */
    void drawArrays(CommandContext ctx, int mode, int first, int count);
    
    /**
     * Draws indexed primitives using vertex array data and an index buffer.
     * 
     * In OpenGL: Maps to glDrawElements()
     * In Vulkan: Maps to vkCmdDrawIndexed()
     * 
     * This command renders primitives using indices from an index buffer to reference
     * vertices in the vertex buffer(s). More efficient than drawArrays when vertices
     * are shared between primitives.
     * 
     * @param ctx Command context for recording this command
     * @param mode Primitive topology (e.g., GL_TRIANGLES, GL_LINES)
     * @param count Number of indices to draw
     * @param type Data type of indices (e.g., GL_UNSIGNED_INT, GL_UNSIGNED_SHORT)
     * @param indices Offset in bytes from the start of the index buffer, or pointer to index data
     */
    void drawElements(CommandContext ctx, int mode, int count, int type, long indices);
    
    /**
     * Binds a shader program for subsequent rendering operations.
     * 
     * In OpenGL: Maps to glUseProgram()
     * In Vulkan: Shader programs are bound as part of pipeline state, not individually
     * 
     * This command makes a shader program active for subsequent draw calls. In Vulkan,
     * this will be handled by binding a pipeline that was created with the shader modules.
     * 
     * @param ctx Command context for recording this command
     * @param programId The shader program ID to bind
     */
    void bindShaderProgram(CommandContext ctx, int programId);
    
    /**
     * Sets the depth write mask (whether depth values are written to the depth buffer).
     * 
     * In OpenGL: Maps to glDepthMask()
     * In Vulkan: Part of pipeline state (depthWriteEnable in VkPipelineDepthStencilStateCreateInfo)
     * 
     * Controls whether fragments can update the depth buffer. This is typically disabled
     * when rendering transparent objects or when doing depth-only rendering passes.
     * 
     * @param ctx Command context for recording this command
     * @param enabled true to enable depth writes, false to disable
     */
    void setDepthWriteMask(CommandContext ctx, boolean enabled);
    
    /**
     * Sets the color write mask (which color channels can be written to the framebuffer).
     * 
     * In OpenGL: Maps to glColorMask()
     * In Vulkan: Part of pipeline state (colorWriteMask in VkPipelineColorBlendAttachmentState)
     * 
     * Controls which color channels (red, green, blue, alpha) can be written by fragment shaders.
     * Useful for effects like rendering to specific channels or masking certain outputs.
     * 
     * @param ctx Command context for recording this command
     * @param r true to enable red channel writes
     * @param g true to enable green channel writes
     * @param b true to enable blue channel writes
     * @param a true to enable alpha channel writes
     */
    void setColorWriteMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a);
    
    /**
     * Sets the depth comparison function.
     * 
     * In OpenGL: Maps to glDepthFunc()
     * In Vulkan: Part of pipeline state (depthCompareOp in VkPipelineDepthStencilStateCreateInfo)
     * 
     * The depth function determines how incoming fragment depth values are compared
     * against the depth buffer to determine if the fragment should be discarded.
     * Common values: LESS, LEQUAL, GREATER, GEQUAL, EQUAL, NOTEQUAL, ALWAYS, NEVER.
     * 
     * @param ctx Command context for recording this command
     * @param func The depth comparison function (e.g., GL_LESS, GL_LEQUAL)
     */
    void setDepthFunc(CommandContext ctx, int func);
    
    /**
     * Sets the blend function for color blending.
     * 
     * In OpenGL: Maps to glBlendFuncSeparate()
     * In Vulkan: Part of pipeline state (VkPipelineColorBlendAttachmentState)
     * 
     * Controls how source and destination colors are combined during blending.
     * Allows separate blend functions for RGB and alpha channels.
     * 
     * @param ctx Command context for recording this command
     * @param srcRgb Source RGB blend factor (e.g., GL_SRC_ALPHA)
     * @param dstRgb Destination RGB blend factor (e.g., GL_ONE_MINUS_SRC_ALPHA)
     * @param srcAlpha Source alpha blend factor
     * @param dstAlpha Destination alpha blend factor
     */
    void setBlendFunc(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
    
    /**
     * Binds a buffer object to a target binding point.
     * 
     * In OpenGL: Maps to glBindBuffer()
     * In Vulkan: Buffers are bound via vkCmdBindVertexBuffers() or descriptor sets
     * 
     * Makes a buffer object active for the specified target (e.g., ARRAY_BUFFER, ELEMENT_ARRAY_BUFFER).
     * Subsequent buffer operations will affect the bound buffer.
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param buffer The buffer object ID to bind
     */
    void bindBuffer(CommandContext ctx, int target, int buffer);
    
    /**
     * Enables blending for rendering operations.
     * 
     * In OpenGL: Maps to glEnable(GL_BLEND)
     * In Vulkan: Part of pipeline state (blendEnable in VkPipelineColorBlendAttachmentState)
     * 
     * Controls whether fragment colors are blended with the framebuffer. When enabled,
     * source and destination colors are combined based on the blend function.
     * 
     * @param ctx Command context for recording this command
     */
    void enableBlend(CommandContext ctx);
    
    /**
     * Disables blending for rendering operations.
     * 
     * In OpenGL: Maps to glDisable(GL_BLEND)
     * In Vulkan: Part of pipeline state (blendEnable in VkPipelineColorBlendAttachmentState)
     * 
     * When disabled, fragment colors directly replace framebuffer colors without blending.
     * 
     * @param ctx Command context for recording this command
     */
    void disableBlend(CommandContext ctx);
    
    /**
     * Enables a generic OpenGL capability.
     * 
     * In OpenGL: Maps to glEnable(cap)
     * In Vulkan: Most capabilities map to pipeline state or dynamic state
     * 
     * Enables various rendering capabilities like depth testing, culling, scissor test, etc.
     * Common capabilities: GL_DEPTH_TEST, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST.
     * 
     * @param ctx Command context for recording this command
     * @param cap The capability to enable (e.g., GL_DEPTH_TEST)
     */
    void enable(CommandContext ctx, int cap);
    
    /**
     * Disables a generic OpenGL capability.
     * 
     * In OpenGL: Maps to glDisable(cap)
     * In Vulkan: Most capabilities map to pipeline state or dynamic state
     * 
     * Disables various rendering capabilities like depth testing, culling, scissor test, etc.
     * 
     * @param ctx Command context for recording this command
     * @param cap The capability to disable (e.g., GL_DEPTH_TEST)
     */
    void disable(CommandContext ctx, int cap);
    
    /**
     * Sets the active texture unit for subsequent texture operations.
     * 
     * In OpenGL: Maps to glActiveTexture(unit)
     * In Vulkan: Texture units are abstracted through descriptor sets
     * 
     * Selects which texture unit subsequent texture binding operations will affect.
     * Texture units are numbered GL_TEXTURE0, GL_TEXTURE1, etc.
     * 
     * @param ctx Command context for recording this command
     * @param unit The texture unit to activate (e.g., GL_TEXTURE0)
     */
    void activateTextureUnit(CommandContext ctx, int unit);
    
    /**
     * Generates mipmaps for a texture target.
     * 
     * In OpenGL: Maps to glGenerateMipmap(target)
     * In Vulkan: Handled through image layout transitions and vkCmdBlitImage
     * 
     * Automatically generates a complete set of mipmaps for a texture, creating
     * successively smaller filtered versions of the base level image.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     */
    void generateMipmap(CommandContext ctx, int target);
    
    /**
     * Binds a texture to the currently active texture unit.
     * 
     * In OpenGL: Maps to glBindTexture(GL_TEXTURE_2D, textureId)
     * In Vulkan: Textures are bound through descriptor sets
     * 
     * Makes a texture object active for subsequent texture operations on the
     * currently active texture unit.
     * 
     * @param ctx Command context for recording this command
     * @param textureId The texture ID to bind
     */
    void bindTexture(CommandContext ctx, int textureId);
    
    /**
     * Binds a texture to a specific target on the currently active texture unit.
     * 
     * In OpenGL: Maps to glBindTexture(target, textureId)
     * In Vulkan: Textures are bound through descriptor sets
     * 
     * Makes a texture object active for the specified target (e.g., GL_TEXTURE_2D,
     * GL_TEXTURE_CUBE_MAP) on the currently active texture unit.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param textureId The texture ID to bind
     */
    void bindTexture(CommandContext ctx, int target, int textureId);
    
    /**
     * Sets pixel storage modes for texture upload operations.
     * 
     * In OpenGL: Maps to glPixelStorei(pname, value)
     * In Vulkan: Handled through buffer copy parameters
     * 
     * Controls how pixel data is read from client memory during texture upload.
     * Common parameters: GL_UNPACK_ALIGNMENT, GL_PACK_ALIGNMENT.
     * 
     * @param ctx Command context for recording this command
     * @param pname The pixel storage parameter name
     * @param value The value to set
     */
    void setPixelStoreMode(CommandContext ctx, int pname, int value);
    
    /**
     * Binds a framebuffer object to a framebuffer target.
     * 
     * In OpenGL: Maps to glBindFramebuffer(target, fbo)
     * In Vulkan: Framebuffers are bound through render pass begin
     * 
     * Makes a framebuffer object active for subsequent rendering operations.
     * Target can be GL_FRAMEBUFFER, GL_READ_FRAMEBUFFER, or GL_DRAW_FRAMEBUFFER.
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target
     * @param fbo The framebuffer object ID to bind (0 for default framebuffer)
     */
    void attachFramebuffer(CommandContext ctx, int target, int fbo);
    
    /**
     * Attaches a texture to a framebuffer attachment point.
     * 
     * In OpenGL: Maps to glFramebufferTexture2D(target, attachment, textarget, texture, level)
     * In Vulkan: Textures are attached during framebuffer creation
     * 
     * Attaches a texture image to a framebuffer attachment point. This is used for 
     * render-to-texture operations and off-screen rendering.
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (e.g., GL_FRAMEBUFFER)
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0)
     * @param textarget The texture target (e.g., GL_TEXTURE_2D)
     * @param texture The texture ID to attach
     * @param level The mipmap level to attach
     */
    void attachTextureToFramebuffer(CommandContext ctx, int target, int attachment, int textarget, int texture, int level);
    
    /**
     * Sets a texture parameter.
     * 
     * In OpenGL: Maps to glTexParameteri(target, pname, param)
     * In Vulkan: Texture parameters are set through sampler objects
     * 
     * Controls texture sampling behavior such as filtering, wrapping, etc.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param pname The parameter name (e.g., GL_TEXTURE_MIN_FILTER)
     * @param param The parameter value
     */
    void configureTextureParameter(CommandContext ctx, int target, int pname, int param);
    
    /**
     * Deletes a texture object.
     * 
     * In OpenGL: Maps to glDeleteTextures()
     * In Vulkan: Maps to vkDestroyImage/vkDestroyImageView
     * 
     * Frees the texture resource and makes its ID available for reuse.
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture ID to delete
     */
    void removeTexture(CommandContext ctx, int texture);
    
    /**
     * Sets the polygon rasterization mode.
     * 
     * In OpenGL: Maps to glPolygonMode(face, mode)
     * In Vulkan: Part of pipeline state (polygonMode in VkPipelineRasterizationStateCreateInfo)
     * 
     * Controls how polygons are rasterized (filled, lines, or points).
     * 
     * @param ctx Command context for recording this command
     * @param face Which faces to apply to (e.g., GL_FRONT_AND_BACK)
     * @param mode The rasterization mode (e.g., GL_FILL, GL_LINE, GL_POINT)
     */
    void configurePolygonMode(CommandContext ctx, int face, int mode);
    
    /**
     * Creates a new texture object.
     * 
     * In OpenGL: Maps to glGenTextures()
     * In Vulkan: Maps to vkCreateImage() and vkCreateImageView()
     * 
     * Allocates a new texture ID for subsequent texture operations.
     * 
     * @param ctx Command context for recording this command
     * @return The newly created texture ID
     */
    int createTexture(CommandContext ctx);
    
    /**
     * Sets the polygon offset for depth value calculations.
     * 
     * In OpenGL: Maps to glPolygonOffset(factor, units)
     * In Vulkan: Part of pipeline state (depthBias* in VkPipelineRasterizationStateCreateInfo)
     * 
     * Controls depth offset to prevent Z-fighting artifacts.
     * 
     * @param ctx Command context for recording this command
     * @param factor Scale factor for depth slope
     * @param units Constant depth offset value
     */
    void configurePolygonOffset(CommandContext ctx, float factor, float units);
    
    /**
     * Sets the logical operation for framebuffer blending.
     * 
     * In OpenGL: Maps to glLogicOp(opcode)
     * In Vulkan: Part of pipeline state (logicOp in VkPipelineColorBlendStateCreateInfo)
     * 
     * Defines logical operation for combining fragment and framebuffer values.
     * 
     * @param ctx Command context for recording this command
     * @param opcode The logical operation code (e.g., GL_COPY, GL_XOR)
     */
    void configureLogicOp(CommandContext ctx, int opcode);
    
    /**
     * Sets the clear value for the depth buffer.
     * 
     * In OpenGL: Maps to glClearDepth(depth)
     * In Vulkan: Clear values are specified in vkCmdBeginRenderPass()
     * 
     * Specifies the depth value used when clearing the depth buffer.
     * 
     * @param ctx Command context for recording this command
     * @param depth The depth clear value (typically 1.0 for far plane)
     */
    void setClearDepthValue(CommandContext ctx, double depth);
    
    /**
     * Sets the clear value for the color buffer.
     * 
     * In OpenGL: Maps to glClearColor(r, g, b, a)
     * In Vulkan: Clear values are specified in vkCmdBeginRenderPass()
     * 
     * Specifies the RGBA values used when clearing the color buffer.
     * 
     * @param ctx Command context for recording this command
     * @param red Red component (0.0 to 1.0)
     * @param green Green component (0.0 to 1.0)
     * @param blue Blue component (0.0 to 1.0)
     * @param alpha Alpha component (0.0 to 1.0)
     */
    void setClearColorValue(CommandContext ctx, float red, float green, float blue, float alpha);
    
    /**
     * Selects which color buffer to draw to.
     * 
     * In OpenGL: Maps to glDrawBuffer(mode)
     * In Vulkan: Specified in render pass creation (VkAttachmentDescription)
     * 
     * Specifies which color buffer (or buffers) to render into. Commonly used
     * to select between front/back buffers or different color attachments.
     * 
     * @param ctx Command context for recording this command
     * @param mode The draw buffer mode (e.g., GL_BACK, GL_FRONT, GL_COLOR_ATTACHMENT0)
     */
    void selectDrawBuffer(CommandContext ctx, int mode);
    
    /**
     * Allocates a new buffer object.
     * 
     * In OpenGL: Maps to glGenBuffers()
     * In Vulkan: Maps to vkCreateBuffer()
     * 
     * Creates a new GPU buffer object for storing vertex data, index data,
     * uniform data, or other GPU-accessible data.
     * 
     * @param ctx Command context for recording this command
     * @return The newly created buffer object ID
     */
    int allocateBufferObject(CommandContext ctx);
    
    /**
     * Releases a buffer object.
     * 
     * In OpenGL: Maps to glDeleteBuffers()
     * In Vulkan: Maps to vkDestroyBuffer()
     * 
     * Frees the GPU memory associated with the buffer object and makes
     * its ID available for reuse.
     * 
     * @param ctx Command context for recording this command
     * @param buf The buffer object ID to release
     */
    void releaseBufferObject(CommandContext ctx, int buf);
    
    /**
     * Creates a new vertex array object (VAO).
     * 
     * In OpenGL: Maps to glGenVertexArrays()
     * In Vulkan: No direct equivalent (state is part of pipeline)
     * 
     * Creates a VAO which stores vertex attribute configuration state.
     * In Vulkan, this state will be baked into the pipeline.
     * 
     * @param ctx Command context for recording this command
     * @return The newly created vertex array object ID
     */
    int createVertexArrayObject(CommandContext ctx);
    
    /**
     * Generates a new framebuffer object.
     * 
     * In OpenGL: Maps to glGenFramebuffers()
     * In Vulkan: Maps to vkCreateFramebuffer()
     * 
     * Creates a framebuffer object for off-screen rendering or
     * render-to-texture operations.
     * 
     * @param ctx Command context for recording this command
     * @return The newly created framebuffer object ID
     */
    int generateFramebufferObject(CommandContext ctx);
    
    /**
     * Destroys a framebuffer object.
     * 
     * In OpenGL: Maps to glDeleteFramebuffers()
     * In Vulkan: Maps to vkDestroyFramebuffer()
     * 
     * Frees the GPU memory associated with the framebuffer object and makes
     * its ID available for reuse.
     * 
     * @param ctx Command context for recording this command
     * @param fbo The framebuffer object ID to destroy
     */
    void destroyFramebufferObject(CommandContext ctx, int fbo);
    
    /**
     * Binds a vertex array object (VAO).
     * 
     * In OpenGL: Maps to glBindVertexArray()
     * In Vulkan: No direct equivalent (state is part of pipeline)
     * 
     * Selects which VAO is active for subsequent vertex attribute configuration
     * and draw calls. In Vulkan, this state is baked into the pipeline.
     * 
     * @param ctx Command context for recording this command
     * @param vao The vertex array object ID to bind
     */
    void selectVertexArray(CommandContext ctx, int vao);
    
    /**
     * Fills a buffer with data.
     * 
     * In OpenGL: Maps to glBufferData()
     * In Vulkan: Maps to vkCmdUpdateBuffer() or memory mapping
     * 
     * Uploads data from CPU memory to GPU buffer memory. The buffer must
     * be bound to a target before calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param dat The data to upload
     * @param usg Usage hint for the buffer (e.g., GL_STATIC_DRAW)
     */
    void fillBufferWithData(CommandContext ctx, int tgt, java.nio.ByteBuffer dat, int usg);
    
    /**
     * Allocates buffer storage with a specified size.
     * 
     * In OpenGL: Maps to glBufferData() with null data
     * In Vulkan: Maps to vkCreateBuffer() with appropriate size
     * 
     * Allocates GPU buffer memory of the specified size without initializing
     * the data. The buffer must be bound to a target before calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param sz The size in bytes to allocate
     * @param usg Usage hint for the buffer (e.g., GL_DYNAMIC_DRAW)
     */
    void fillBufferWithSize(CommandContext ctx, int tgt, long sz, int usg);
    
    /**
     * Checks for graphics API errors.
     * 
     * In OpenGL: Maps to glGetError()
     * In Vulkan: Maps to validation layer queries
     * 
     * Returns the error code of the last operation, or NO_ERROR if no error occurred.
     * This is primarily used for debugging and should be avoided in production code
     * for performance reasons.
     * 
     * @param ctx Command context for recording this command
     * @return The error code, or NO_ERROR (0) if no error occurred
     */
    int checkForErrors(CommandContext ctx);
    
    /**
     * Updates a subset of buffer data.
     * 
     * In OpenGL: Maps to glBufferSubData()
     * In Vulkan: Maps to vkCmdUpdateBuffer() or staging buffer copy
     * 
     * Updates a region of an already allocated buffer with new data. The buffer
     * must be bound to a target before calling this method. This is more efficient
     * than reallocating the entire buffer when only a portion needs updating.
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param off Offset in bytes from the start of the buffer
     * @param dat The data to upload
     */
    void fillBufferSubregion(CommandContext ctx, int tgt, long off, java.nio.ByteBuffer dat);
    
    /**
     * Maps a region of buffer memory for CPU access.
     * 
     * In OpenGL: Maps to glMapBufferRange()
     * In Vulkan: Maps to vkMapMemory()
     * 
     * Returns a ByteBuffer that provides direct CPU access to GPU buffer memory.
     * The buffer must be unmapped before it can be used in rendering operations.
     * This is useful for streaming data or reading back GPU-computed results.
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param off Offset in bytes from the start of the buffer
     * @param len Length in bytes of the region to map
     * @param acc Access flags (e.g., GL_MAP_READ_BIT, GL_MAP_WRITE_BIT)
     * @return A ByteBuffer providing access to the mapped memory region
     */
    java.nio.ByteBuffer mapBufferRegion(CommandContext ctx, int tgt, int off, int len, int acc);
    
    /**
     * Unmaps previously mapped buffer memory.
     * 
     * In OpenGL: Maps to glUnmapBuffer()
     * In Vulkan: Maps to vkUnmapMemory()
     * 
     * Releases the CPU mapping of buffer memory, allowing the buffer to be used
     * in rendering operations again. Must be called after mapBufferRegion() when
     * done accessing the mapped memory.
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     */
    void unmapBufferData(CommandContext ctx, int tgt);
    
    /**
     * Copies a rectangular region from one framebuffer to another (blit operation).
     * 
     * In OpenGL: Maps to glBlitFramebuffer()
     * In Vulkan: Maps to vkCmdBlitImage()
     * 
     * Performs a copy (and potentially scaling/filtering) operation from the read
     * framebuffer to the draw framebuffer. This is commonly used for post-processing
     * effects, MSAA resolves, and copying render results.
     * 
     * @param ctx Command context for recording this command
     * @param srcX0 Source rectangle minimum X coordinate
     * @param srcY0 Source rectangle minimum Y coordinate
     * @param srcX1 Source rectangle maximum X coordinate
     * @param srcY1 Source rectangle maximum Y coordinate
     * @param dstX0 Destination rectangle minimum X coordinate
     * @param dstY0 Destination rectangle minimum Y coordinate
     * @param dstX1 Destination rectangle maximum X coordinate
     * @param dstY1 Destination rectangle maximum Y coordinate
     * @param msk Bit mask indicating which buffers to copy (GL_COLOR_BUFFER_BIT, etc.)
     * @param flt Filter mode for scaling (GL_NEAREST or GL_LINEAR)
     */
    void copyFramebufferRegion(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, 
                               int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt);
    
    /**
     * Uploads 2D texture image data.
     * 
     * In OpenGL: Maps to glTexImage2D()
     * In Vulkan: Maps to vkCmdCopyBufferToImage() with staging buffer
     * 
     * Specifies a two-dimensional texture image. This allocates GPU memory for
     * the texture and uploads the initial pixel data. If the texture has already
     * been allocated, this reallocates it with the new dimensions/format.
     * 
     * @param ctx Command context for recording this command
     * @param tgt Texture target (e.g., GL_TEXTURE_2D)
     * @param lvl Mipmap level (0 for base level)
     * @param intfmt Internal format (e.g., GL_RGBA8)
     * @param w Width in pixels
     * @param h Height in pixels
     * @param bdr Border width (must be 0 in modern OpenGL)
     * @param fmt Pixel data format (e.g., GL_RGBA)
     * @param typ Pixel data type (e.g., GL_UNSIGNED_BYTE)
     * @param pix Buffer containing pixel data, or null to allocate without initializing
     */
    void transferTexture2DImage(CommandContext ctx, int tgt, int lvl, int intfmt, int w, int h, 
                                int bdr, int fmt, int typ, java.nio.ByteBuffer pix);
    
    /**
     * Creates a shader object of the specified type.
     * 
     * In OpenGL: Maps to glCreateShader()
     * In Vulkan: Will create VkShaderModule from SPIR-V bytecode
     * 
     * Shaders are compiled code that runs on the GPU during different stages of the rendering pipeline.
     * This method creates a shader object that can be compiled and attached to a program/pipeline.
     * 
     * @param ctx Command context (for future Vulkan resource tracking)
     * @param shaderType Type of shader (e.g., GL_VERTEX_SHADER, GL_FRAGMENT_SHADER)
     * @return Shader object ID/handle
     */
    int constructShaderObject(CommandContext ctx, int shaderType);
    
    /**
     * Deletes a shader object and frees its resources.
     * 
     * In OpenGL: Maps to glDeleteShader()
     * In Vulkan: Maps to vkDestroyShaderModule()
     * 
     * Shader objects can be deleted after they've been attached to a program and the program has been linked.
     * OpenGL reference counts shader objects, so they won't be actually deleted until they're detached.
     * 
     * @param ctx Command context (for future Vulkan resource tracking)
     * @param shader Shader object ID to delete
     */
    void disposeShaderObject(CommandContext ctx, int shader);
    
    /**
     * Compiles the shader source code associated with a shader object.
     * 
     * In OpenGL: Maps to glCompileShader() - compiles GLSL source at runtime
     * In Vulkan: No direct equivalent - shaders are pre-compiled to SPIR-V
     * 
     * For Vulkan backend, this method will validate SPIR-V bytecode or perform offline compilation.
     * The CommandContext allows the backend to handle these different compilation models.
     * 
     * @param ctx Command context (for future Vulkan compilation pipeline)
     * @param shader Shader object ID to compile
     */
    void compileShaderSource(CommandContext ctx, int shader);
    
    /**
     * Creates a program object for linking shaders together.
     * 
     * In OpenGL: Maps to glCreateProgram()
     * In Vulkan: Will create VkPipeline (much more complex, includes all state)
     * 
     * Programs (OpenGL) or Pipelines (Vulkan) represent the complete shader execution environment.
     * In Vulkan, pipelines are monolithic and include render state, while in OpenGL they're separate.
     * 
     * @param ctx Command context (for future Vulkan pipeline creation)
     * @return Program/Pipeline object ID/handle
     */
    int constructProgramObject(CommandContext ctx);
    
    /**
     * Deletes a program object and frees its resources.
     * 
     * In OpenGL: Maps to glDeleteProgram()
     * In Vulkan: Maps to vkDestroyPipeline()
     * 
     * Deleting a program/pipeline releases all associated resources including linked shader objects.
     * 
     * @param ctx Command context (for future Vulkan resource tracking)
     * @param program Program/Pipeline object ID to delete
     */
    void disposeProgramObject(CommandContext ctx, int program);
    
    /**
     * Uploads GLSL shader source code to a shader object.
     * 
     * In OpenGL: Maps to glShaderSource() - uploads source as string
     * In Vulkan: Will load pre-compiled SPIR-V binary instead
     * 
     * This method uses native memory pointers for efficient source upload.
     * The shader must be created before source can be uploaded.
     * 
     * @param ctx Command context (for future Vulkan resource management)
     * @param shader Shader object ID
     * @param pointerBufferAddress Native pointer to array of source string pointers
     * @param stringCount Number of source strings
     * @param lengthsPointer Native pointer to array of string lengths (or 0 for null-terminated)
     */
    void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer);
    
    /**
     * Uploads GLSL shader source code to a shader object (native version).
     * 
     * In OpenGL: Maps to nglShaderSource() - native OpenGL call
     * In Vulkan: Will load pre-compiled SPIR-V binary instead
     * 
     * This is a low-level native version for direct memory access.
     * 
     * @param ctx Command context (for future Vulkan resource management)
     * @param shader Shader object ID
     * @param count Number of source strings
     * @param strings Native pointer to array of source string pointers
     * @param length Native pointer to array of string lengths (or 0 for null-terminated)
     */
    void uploadShaderSourceNative(CommandContext ctx, int shader, int count, long strings, long length);
    
    /**
     * Attaches a compiled shader object to a program object.
     * 
     * In OpenGL: Maps to glAttachShader() - attaches shader for linking
     * In Vulkan: Will be part of pipeline creation (shaders specified during pipeline creation)
     * 
     * Multiple shaders (vertex, fragment, etc.) can be attached to a single program.
     * The program must be linked after attaching shaders.
     * 
     * @param ctx Command context (for future Vulkan pipeline management)
     * @param program Program object ID
     * @param shader Compiled shader object ID to attach
     */
    void attachShaderToProgram(CommandContext ctx, int program, int shader);
    
    /**
     * Links all attached shaders into an executable program.
     * 
     * In OpenGL: Maps to glLinkProgram() - links attached shaders into executable
     * In Vulkan: Will create graphics/compute pipeline (monolithic operation)
     * 
     * Linking combines all attached shader stages into a single executable program.
     * After linking, the program can be used for rendering. Link status should be
     * checked to ensure success.
     * 
     * @param ctx Command context (for future Vulkan pipeline creation)
     * @param program Program object ID to link
     */
    void linkProgramBinary(CommandContext ctx, int program);
    
    /**
     * Detaches a shader object from a program object.
     * 
     * In OpenGL: Maps to glDetachShader() - removes shader from program
     * In Vulkan: N/A (shaders specified during immutable pipeline creation)
     * 
     * Detaching shaders is typically done after linking to free shader objects.
     * The program retains the linked code even after shaders are detached.
     * 
     * @param ctx Command context (for future Vulkan resource management)
     * @param program Program object ID
     * @param shader Shader object ID to detach
     */
    void glDetachShader(CommandContext ctx, int program, int shader);
    
    /**
     * Binds a vertex attribute variable name to a specific attribute index.
     * 
     * In OpenGL: Maps to glBindAttribLocation()
     * In Vulkan: Attribute locations are specified in SPIR-V shader code via layout(location=X)
     * 
     * This must be called before linking the program. In OpenGL, this allows control over
     * which attribute index corresponds to which shader variable. In Vulkan, this is handled
     * at compile-time in the SPIR-V shader via layout qualifiers.
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param index The attribute index to bind to (0-15 typically)
     * @param name The name of the vertex attribute variable in the shader
     */
    void bindAttributeLocation(CommandContext ctx, int program, int index, CharSequence name);
    
    /**
     * Queries the location of a vertex attribute variable in a linked program.
     * 
     * In OpenGL: Maps to glGetAttribLocation()
     * In Vulkan: Attribute locations are defined in SPIR-V, reflection needed
     * 
     * Returns the attribute index that was assigned to the named variable during linking.
     * Must be called after the program has been successfully linked.
     * 
     * @param ctx Command context for recording this command
     * @param program The linked program object ID
     * @param name The name of the vertex attribute variable to query
     * @return The attribute location/index, or -1 if not found
     */
    int getAttributeLocation(CommandContext ctx, int program, CharSequence name);
    
    /**
     * Queries the location of a uniform variable in a linked program.
     * 
     * In OpenGL: Maps to glGetUniformLocation()
     * In Vulkan: Uniforms are in descriptor sets, requires reflection or pre-defined bindings
     * 
     * Returns the uniform location that can be used to update the uniform's value.
     * Must be called after the program has been successfully linked.
     * 
     * @param ctx Command context for recording this command
     * @param program The linked program object ID
     * @param name The name of the uniform variable to query
     * @return The uniform location, or -1 if not found or not active
     */
    int locateUniformVariable(CommandContext ctx, int program, CharSequence name);
    
    /**
     * Sets the value of a single integer uniform variable.
     * 
     * In OpenGL: Maps to glUniform1i()
     * In Vulkan: Maps to vkCmdPushConstants() for push constants or descriptor set updates
     * 
     * Updates the value of a uniform variable at the specified location. The program
     * containing this uniform must be bound/active when calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param value The integer value to assign to the uniform
     */
    void assignUniformInteger(CommandContext ctx, int location, int value);
    
    /**
     * Sets the value of a single float uniform variable.
     * 
     * In OpenGL: Maps to glUniform1f()
     * In Vulkan: Maps to vkCmdPushConstants() for push constants or descriptor set updates
     * 
     * Updates the value of a uniform variable at the specified location. The program
     * containing this uniform must be bound/active when calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param value The float value to assign to the uniform
     */
    void assignUniformFloat(CommandContext ctx, int location, float value);
    
    /**
     * Sets the value of a 3-component float vector uniform variable.
     * 
     * In OpenGL: Maps to glUniform3f()
     * In Vulkan: Maps to vkCmdPushConstants() for push constants or descriptor set updates
     * 
     * Updates the value of a vec3 uniform variable at the specified location. The program
     * containing this uniform must be bound/active when calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param x The x component value
     * @param y The y component value
     * @param z The z component value
     */
    void assignUniformFloat3(CommandContext ctx, int location, float x, float y, float z);
    
    /**
     * Sets the value of a 3-component integer vector uniform variable.
     * 
     * In OpenGL: Maps to glUniform3i()
     * In Vulkan: Maps to vkCmdPushConstants() for push constants or descriptor set updates
     * 
     * Updates the value of an ivec3 uniform variable at the specified location. The program
     * containing this uniform must be bound/active when calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param x The x component value
     * @param y The y component value
     * @param z The z component value
     */
    void assignUniformInteger3(CommandContext ctx, int location, int x, int y, int z);
    
    /**
     * Sets the value of a 4-component float vector uniform variable.
     * 
     * In OpenGL: Maps to glUniform4f()
     * In Vulkan: Maps to vkCmdPushConstants() for push constants or descriptor set updates
     * 
     * Updates the value of a vec4 uniform variable at the specified location. The program
     * containing this uniform must be bound/active when calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param x The x component value
     * @param y The y component value
     * @param z The z component value
     * @param w The w component value
     */
    void assignUniformFloat4(CommandContext ctx, int location, float x, float y, float z, float w);
    
    /**
     * Sets the value of a 4x4 matrix uniform variable.
     * 
     * In OpenGL: Maps to glUniformMatrix4fv()
     * In Vulkan: Maps to vkCmdPushConstants() for push constants or descriptor set updates
     * 
     * Updates the value of a mat4 uniform variable at the specified location. The program
     * containing this uniform must be bound/active when calling this method.
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param transpose Whether to transpose the matrix (typically false for column-major)
     * @param value Buffer containing the 16 float values of the matrix
     */
    void assignUniformMatrix4(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer value);
    
    /**
     * Enables a vertex attribute array.
     * 
     * In OpenGL: Maps to glEnableVertexAttribArray()
     * In Vulkan: Vertex attributes are enabled as part of pipeline state
     * 
     * Enables the generic vertex attribute array specified by index. When enabled,
     * the values in the vertex attribute array will be accessed and used for rendering
     * when vertex attribute index is referenced by vertex shader.
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the generic vertex attribute to enable
     */
    void activateVertexAttributeArray(CommandContext ctx, int index);
    
    /**
     * Sets a 2-component float vector uniform (vec2).
     * 
     * In OpenGL: Maps to glUniform2f()
     * In Vulkan: Maps to vkCmdPushConstants() or descriptor set updates
     * 
     * Used for setting 2D vectors such as UV coordinates, screen positions, 2D directions.
     * The uniform variable must be of type vec2 in the shader.
     * 
     * @param ctx Command context for recording this command
     * @param location The location of the uniform variable
     * @param x The first component (x coordinate)
     * @param y The second component (y coordinate)
     */
    void assignUniformFloat2(CommandContext ctx, int location, float x, float y);
    
    /**
     * Sets a 2-component integer vector uniform (ivec2).
     * 
     * In OpenGL: Maps to glUniform2i()
     * In Vulkan: Maps to vkCmdPushConstants() or descriptor set updates
     * 
     * Used for setting 2D integer vectors such as grid coordinates, texture indices, 2D discrete values.
     * The uniform variable must be of type ivec2 in the shader.
     * 
     * @param ctx Command context for recording this command
     * @param location The location of the uniform variable
     * @param x The first component
     * @param y The second component
     */
    void assignUniformInteger2(CommandContext ctx, int location, int x, int y);
    
    /**
     * Copies a rectangular region from the framebuffer to a texture.
     * 
     * In OpenGL: Maps to glCopyTexSubImage2D()
     * In Vulkan: Maps to vkCmdCopyImage() or render-to-texture approach
     * 
     * Copies pixels from the current framebuffer (as specified by glReadBuffer) to a texture image.
     * This is commonly used for post-processing effects, creating mipmap levels, or updating textures
     * with rendered content.
     * 
     * @param ctx Command context for recording this command
     * @param target Texture target (e.g., GL_TEXTURE_2D)
     * @param level Mipmap level of the texture
     * @param xoffset X offset into the texture image
     * @param yoffset Y offset into the texture image
     * @param x X position in the framebuffer to start reading
     * @param y Y position in the framebuffer to start reading
     * @param width Width of the region to copy
     * @param height Height of the region to copy
     */
    void copyTexture2DSubImage(CommandContext ctx, int target, int level, int xoffset, int yoffset, 
                               int x, int y, int width, int height);
    
    /**
     * Reads pixel data from the framebuffer into CPU memory.
     * 
     * In OpenGL: Maps to glReadPixels()
     * In Vulkan: Maps to vkCmdCopyImageToBuffer() followed by staging buffer readback
     * 
     * Reads a rectangular region of pixels from the current framebuffer and stores them
     * in the provided array. This is a CPU synchronization point and should be used sparingly.
     * Common uses include screenshots, pixel picking, and debugging.
     * 
     * @param ctx Command context for recording this command
     * @param x X position of the first pixel to read
     * @param y Y position of the first pixel to read
     * @param width Width of the pixel rectangle
     * @param height Height of the pixel rectangle
     * @param format Pixel format (e.g., GL_RGBA, GL_RGB)
     * @param type Data type of pixels (e.g., GL_FLOAT, GL_UNSIGNED_BYTE)
     * @param pixels Array to store the pixel data
     */
    void readPixelsFromFramebuffer(CommandContext ctx, int x, int y, int width, int height, 
                                   int format, int type, float[] pixels);
    
    /**
     * Sets the viewport for rendering (static/non-dynamic version).
     * 
     * In OpenGL: Maps to glViewport()
     * In Vulkan: Maps to VkViewport in pipeline state or vkCmdSetViewport (dynamic)
     * 
     * Defines the viewport transformation from normalized device coordinates to window coordinates.
     * This version is intended for pipelines without VK_DYNAMIC_STATE_VIEWPORT. For dynamic viewport
     * updates during rendering, use setDynamicViewport() instead.
     * 
     * @param ctx Command context for recording this command
     * @param x X coordinate of the lower-left corner of the viewport
     * @param y Y coordinate of the lower-left corner of the viewport
     * @param width Width of the viewport
     * @param height Height of the viewport
     */
    void setStaticViewport(CommandContext ctx, int x, int y, int width, int height);
    
    /**
     * Configures the data format and location for a vertex attribute.
     * 
     * In OpenGL: Maps to glVertexAttribPointer()
     * In Vulkan: Maps to VkVertexInputAttributeDescription in pipeline state
     * 
     * Specifies how vertex shader attributes read data from the currently bound vertex buffer.
     * This defines the size, type, stride, and offset for a specific vertex attribute.
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute to configure
     * @param size Number of components per vertex (1, 2, 3, or 4)
     * @param type Data type of each component (e.g., GL_FLOAT, GL_INT)
     * @param normalized Whether fixed-point data should be normalized
     * @param stride Byte offset between consecutive vertex attributes
     * @param pointer Offset of the first component in the buffer
     */
    void configureVertexAttributePointer(CommandContext ctx, int index, int size, int type, 
                                        boolean normalized, int stride, long pointer);
    
    /**
     * Disables a vertex attribute array.
     * 
     * In OpenGL: Maps to glDisableVertexAttribArray()
     * In Vulkan: Vertex attributes are part of immutable pipeline state
     * 
     * Disables the specified vertex attribute array. When disabled, the attribute
     * will use a constant value instead of reading from a buffer.
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute to disable
     */
    void deactivateVertexAttributeArray(CommandContext ctx, int index);
    
    /**
     * Sets a 3x3 matrix uniform value (mat3).
     * 
     * In OpenGL: Maps to glUniformMatrix3fv()
     * In Vulkan: Maps to vkCmdPushConstants() or descriptor set updates
     * 
     * Sets a 3x3 matrix uniform variable. Commonly used for normal transformation matrices.
     * The matrix data must be in column-major order (OpenGL/GLSL standard).
     * 
     * @param ctx Command context for recording this command
     * @param location Location of the uniform variable
     * @param transpose Whether to transpose the matrix
     * @param value FloatBuffer containing the matrix data (9 floats)
     */
    void assignUniformMatrix3(CommandContext ctx, int location, boolean transpose, FloatBuffer value);
    
    /**
     * Sets a 3x3 matrix uniform value from an array (mat3).
     * 
     * In OpenGL: Maps to glUniformMatrix3fv()
     * In Vulkan: Maps to vkCmdPushConstants() or descriptor set updates
     * 
     * Sets a 3x3 matrix uniform variable from a float array. Array variant for convenience.
     * The matrix data must be in column-major order (OpenGL/GLSL standard).
     * 
     * @param ctx Command context for recording this command
     * @param location Location of the uniform variable
     * @param transpose Whether to transpose the matrix
     * @param value Float array containing the matrix data (9 floats)
     */
    void assignUniformMatrix3Array(CommandContext ctx, int location, boolean transpose, float[] value);
    
    /**
     * Sets the blend equation for color blending.
     * 
     * In OpenGL: Maps to glBlendEquation()
     * In Vulkan: Maps to VkPipelineColorBlendAttachmentState.blendOp
     * 
     * Controls how source and destination colors are combined during blending.
     * Common modes include GL_FUNC_ADD, GL_FUNC_SUBTRACT, GL_MIN, GL_MAX.
     * 
     * @param ctx Command context for recording this command
     * @param mode The blend equation mode (e.g., GL_FUNC_ADD)
     */
    void setBlendEquation(CommandContext ctx, int mode);
    
    // ================================================================================
    // DEPRECATED METHODS - To be replaced with CommandContext-aware versions
    // ================================================================================
    
    // Pixel operations
    @Deprecated
    void setPixelStoreMode(int pname, int value);
    
    // Framebuffer operations
    @Deprecated
    void attachFramebuffer(int target, int fbo);
    @Deprecated
    void attachTextureToFramebuffer(int target, int attachment, int textarget, int texture, int level);
    
    // Buffer operations  
    @Deprecated
    void attachBuffer(int target, int buffer);
    
    // Direct State Access buffer operations
    @Deprecated
    int createBufferDSA();
    @Deprecated
    void namedBufferDataDSA(int buffer, long size, int usage);
    @Deprecated
    void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage);
    @Deprecated
    void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data);
    @Deprecated
    void namedBufferStorageDSA(int buffer, long size, int flags);
    @Deprecated
    void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags);
    @Deprecated
    java.nio.ByteBuffer mapNamedBufferRangeDSA(int buffer, long offset, long length, int access);
    @Deprecated
    void unmapNamedBufferDSA(int buffer);
    @Deprecated
    void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length);
    @Deprecated
    void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size);
    
    // Direct State Access framebuffer operations
    @Deprecated
    int createFramebufferDSA();
    @Deprecated
    void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level);
    void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, 
                                  int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    // Texture unit and parameter operations
    @Deprecated
    void activateTextureUnit(int unit);
    @Deprecated
    void configureTextureParameter(int target, int pname, int param);
    @Deprecated
    int createTexture();
    @Deprecated
    void removeTexture(int texture);
    
    // Polygon rendering operations
    @Deprecated
    void configurePolygonMode(int face, int mode);
    @Deprecated
    void configurePolygonOffset(float factor, float units);
    @Deprecated
    void configureLogicOp(int opcode);
    
    // Drawing operations
    @Deprecated
    void configureBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
    
    // Error checking
    @Deprecated
    int checkForErrors();
    
    // Texture pixel data transfer
    @Deprecated
    void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix);
    @Deprecated
    void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix);
    @Deprecated
    void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix);
    
    // GPU buffer lifecycle
    @Deprecated
    int allocateBufferObject();
    @Deprecated
    void releaseBufferObject(int buf);
    @Deprecated
    void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg);
    @Deprecated
    void fillBufferWithSize(int tgt, long sz, int usg);
    @Deprecated
    void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat);
    
    // Vertex array objects
    @Deprecated
    int createVertexArrayObject();
    @Deprecated
    void selectVertexArray(int vao);
    
    // Buffer memory mapping
    @Deprecated
    java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc);
    @Deprecated
    void unmapBufferData(int tgt);
    
    // Framebuffer lifecycle
    @Deprecated
    int generateFramebufferObject();
    @Deprecated
    void destroyFramebufferObject(int fbo);
    @Deprecated
    void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt);
    
    // Shader pipeline
    @Deprecated
    void linkProgramBinary(int program);
    @Deprecated
    void attachShaderToProgram(int program, int shader);
    @Deprecated
    int queryProgramParameter(int program, int pname);
    @Deprecated
    int queryShaderParameter(int shader, int pname);
    @Deprecated
    String retrieveProgramInfoLog(int program);
    @Deprecated
    String retrieveShaderInfoLog(int shader);
    @Deprecated
    int locateUniformVariable(int program, CharSequence name);
    @Deprecated
    void assignUniformInteger(int location, int value);
    @Deprecated
    void bindAttributeLocation(int program, int index, CharSequence name);
    
    // Vertex attributes
    @Deprecated
    void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer);
    @Deprecated
    void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer);
    @Deprecated
    void activateVertexAttribute(int index);
    @Deprecated
    void deactivateVertexAttribute(int index);
    @Deprecated
    void setVertexAttribDivisor(int index, int divisor);
    
    // Synchronization
    @Deprecated
    long createFenceSync(int condition, int flags);
    @Deprecated
    int waitForSync(long sync, int flags, long timeout);
    @Deprecated
    void destroySync(long sync);
    
    // Query operations
    @Deprecated
    int queryIntegerState(int pname);
    @Deprecated
    String queryStringInfo(int name);
    @Deprecated
    int pollErrorCode();
    
    // Pixel readback
    @Deprecated
    void readFramebufferPixels(int x, int y, int width, int height, int format, int type, long pixels);
    
    // Texture queries
    @Deprecated
    int queryTextureLevelParameter(int target, int level, int pname);
    
    // Shader source (native)
    @Deprecated
    void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer);
    
    // Uniform block operations
    @Deprecated
    int locateUniformBlock(int program, String uniformBlockName);
    @Deprecated
    void bindUniformBlock(int program, int uniformBlockIndex, int uniformBlockBinding);
    @Deprecated
    String retrieveActiveUniformBlockName(int program, int uniformBlockIndex);
    
    // Timer query operations
    @Deprecated
    int generateQueryObject();
    @Deprecated
    void initiateQuery(int target, int id);
    @Deprecated
    void concludeQuery(int target);
    @Deprecated
    void disposeQueryObject(int id);
    @Deprecated
    int retrieveQueryObjectInt(int id, int pname);
    @Deprecated
    long retrieveQueryObjectInt64(int id, int pname);
    
    // Debug label operations (KHR_debug)
    @Deprecated
    void labelDebugObject(int identifier, int name, String label);
    @Deprecated
    void enterDebugGroup(int source, int id, CharSequence message);
    @Deprecated
    void exitDebugGroup();
    
    // Debug label operations (EXT_debug_label)
    @Deprecated
    void labelObjectExt(int type, int object, String label);
    
    // Debug system initialization (wraps entire debug setup)
    @Deprecated
    boolean supportsKhrDebug();
    @Deprecated
    boolean supportsArbDebugOutput();
    @Deprecated
    void setupKhrDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler);
    @Deprecated
    void setupArbDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler);
    
    // Extension capability checking
    @Deprecated
    boolean hasBufferStorageExtension();
    @Deprecated
    boolean hasVertexAttribBindingExtension();
    
    // ARB vertex attrib binding operations
    @Deprecated
    void attachVertexBuffer(int bindingIndex, int buffer, long offset, int stride);
    @Deprecated
    void specifyVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset);
    @Deprecated
    void specifyVertexAttribIFormat(int attribIndex, int size, int type, int relativeOffset);
    @Deprecated
    void associateVertexAttrib(int attribIndex, int bindingIndex);
    
    // Clear operations
    @Deprecated
    void setClearDepthValue(double depth);
    @Deprecated
    void setClearColorValue(float red, float green, float blue, float alpha);
    @Deprecated
    void selectDrawBuffer(int mode);
    
    // Advanced drawing operations
    @Deprecated
    void renderIndexedInstancedWithBase(int mode, int count, int type, long indices, int instanceCount, int baseVertex);
    @Deprecated
    void renderIndexedWithBase(int mode, int count, int type, long indices, int baseVertex);
    @Deprecated
    void renderIndexedInstanced(int mode, int count, int type, long indices, int instanceCount);
    @Deprecated
    void renderArraysInstanced(int mode, int first, int count, int instanceCount);
    
    // Uniform buffer operations
    @Deprecated
    void attachUniformBufferRange(int target, int index, int buffer, long offset, long size);
    
    // Texture buffer operations
    @Deprecated
    void attachBufferToTexture(int target, int internalFormat, int buffer);
    
    // Uniform operations (additional)
    @Deprecated
    void assignUniformFloat(int location, float value);
    @Deprecated
    void assignUniformFloat2(int location, float x, float y);
    @Deprecated
    void assignUniformFloat2v(int location, float[] value);
    @Deprecated
    void assignUniformFloat3(int location, float x, float y, float z);
    @Deprecated
    void assignUniformFloat3v(int location, float[] value);
    @Deprecated
    void assignUniformFloat4(int location, float x, float y, float z, float w);
    @Deprecated
    void assignUniformFloat4v(int location, float[] value);
    @Deprecated
    void assignUniformMatrix4f(int location, java.nio.FloatBuffer matrix);
    @Deprecated
    void bindUniformBufferBase(int bindingPoint, int bufferId);
    
    // Program fragment data binding
    @Deprecated
    void bindFragmentDataLocation(int program, int colorNumber, CharSequence name);
    
    // Sync query operations
    @Deprecated
    int querySyncStatus(long sync, int pname, java.nio.IntBuffer length);
    
    // Graphics Capabilities
    @Deprecated
    GraphicsCapabilities obtainGraphicsCapabilities();
    @Deprecated
    GraphicsCapabilities initializeGraphicsCapabilities();
    @Deprecated
    boolean checkFunctionAvailable(String functionName);
    
    // Additional buffer operations for Sodium
    @Deprecated
    void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size);
    @Deprecated
    void deleteVertexArray(int vertexArray);
    @Deprecated
    void flushMappedBufferRange(int target, long offset, long length);
    
    // Buffer storage operations
    @Deprecated
    void createBufferStorage(int target, long size, int flags);
    @Deprecated
    void createBufferStorage(int target, ByteBuffer data, int flags);
    
    // Multi-draw operations
    @Deprecated
    void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex);
    
    // Uniform matrix operations
    @Deprecated
    void assignUniformMatrix4fv(int location, boolean transpose, FloatBuffer value);
    
    // String queries
    @Deprecated
    String queryString(int name);
    @Deprecated
    String queryStringIndexed(int name, int index);
    
    // Native shader source upload
    @Deprecated
    void uploadShaderSourceNative(int shader, int count, long strings, long length);
    
    // Texture operations
    @Deprecated
    void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    
    // Clear texture image (ARB_clear_texture)
    @Deprecated
    void clearTexImage(int texture, int level, int format, int type, int[] data);
    
    // Parallel shader compile (KHR/ARB)
    @Deprecated
    void setMaxShaderCompilerThreads(int count);
    
    // Capabilities
    @Deprecated
    GraphicsCapabilities getGraphicsCapabilities();
    
    // Debug object labeling
    @Deprecated
    void labelObject(int identifier, int name, String label);
    
    // Debug group push/pop
    @Deprecated
    void pushDebugGroup(int source, int id, String message);
    @Deprecated
    void popDebugGroup();
    
    // Additional methods for IrisRenderSystem
    @Deprecated
    void glGetIntegerv(int pname, int[] params);
    @Deprecated
    void glGetFloatv(int pname, float[] params);
    @Deprecated
    void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels);
    @Deprecated
    void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels);
    @Deprecated
    void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels);
    @Deprecated
    void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix);
    @Deprecated
    void glUniformMatrix4fv(int location, boolean transpose, float[] matrix);
    @Deprecated
    void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border);
    @Deprecated
    void glUniform1f(int location, float v0);
    @Deprecated
    void glUniform2f(int location, float v0, float v1);
    @Deprecated
    void glUniform2i(int location, int v0, int v1);
    @Deprecated
    void glUniform3f(int location, float v0, float v1, float v2);
    @Deprecated
    void glUniform3i(int location, int v0, int v1, int v2);
    @Deprecated
    void glUniform4f(int location, float v0, float v1, float v2, float v3);
    @Deprecated
    void glUniform4i(int location, int v0, int v1, int v2, int v3);
    @Deprecated
    void glTexParameteriv(int target, int pname, int[] params);
    @Deprecated
    void glTexParameteri(int target, int pname, int param);
    @Deprecated
    void glTexParameterf(int target, int pname, float param);
    @Deprecated
    String glGetProgramInfoLog(int program);
    @Deprecated
    String glGetShaderInfoLog(int shader);
    @Deprecated
    void glDrawBuffers(int[] buffers);
    @Deprecated
    void glReadBuffer(int buffer);
    @Deprecated
    void glClearBufferfv(int buffer, int drawbuffer, float[] values);
    @Deprecated
    void glClearBufferiv(int buffer, int drawbuffer, int[] values);
    @Deprecated
    void glClearBufferuiv(int buffer, int drawbuffer, int[] values);
    @Deprecated
    String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name);
    @Deprecated
    void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels);
    @Deprecated
    void glBufferData(int target, float[] data, int usage);
    @Deprecated
    void glBufferData(int target, int[] data, int usage);
    @Deprecated
    void glBufferData(int target, java.nio.ByteBuffer data, int usage);
    @Deprecated
    void glBufferData(int target, long size, int usage);
    @Deprecated
    void glBufferSubData(int target, long offset, java.nio.ByteBuffer data);
    @Deprecated
    void glBufferStorage(int target, long size, int flags);
    @Deprecated
    void glBufferStorage(int target, java.nio.ByteBuffer data, int flags);
    @Deprecated
    java.nio.ByteBuffer glMapBufferRange(int target, long offset, long length, int access);
    @Deprecated
    boolean glUnmapBuffer(int target);
    @Deprecated
    boolean glIsBuffer(int buffer);
    @Deprecated
    void glBindBufferBase(int target, int index, int buffer);
    @Deprecated
    void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3);
    @Deprecated
    void glDetachShader(int program, int shader);
    @Deprecated
    void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
    @Deprecated
    void glFramebufferTexture(int target, int attachment, int texture, int level);
    @Deprecated
    int glGetTexParameteri(int target, int pname);
    @Deprecated
    void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format);
    @Deprecated
    int glGetMaxImageUnits();
    @Deprecated
    void glGenBuffers(int[] buffers);
    @Deprecated
    void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data);
    @Deprecated
    void glGetProgramiv(int program, int pname, int[] params);
    @Deprecated
    void glDispatchCompute(int workX, int workY, int workZ);
    @Deprecated
    void glMemoryBarrier(int barriers);
    @Deprecated
    void glDisablei(int target, int index);
    @Deprecated
    void glEnablei(int target, int index);
    @Deprecated
    void glBlendFunc(int sfactor, int dfactor);
    @Deprecated
    void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);
    @Deprecated
    int glGetUniformBlockIndex(int program, String uniformBlockName);
    @Deprecated
    void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding);
    @Deprecated
    int glGenSamplers();
    @Deprecated
    void glDeleteSamplers(int sampler);
    @Deprecated
    void glBindSampler(int unit, int sampler);
    @Deprecated
    void glBindSamplers(int first, int[] samplers);
    @Deprecated
    void glSamplerParameteri(int sampler, int pname, int param);
    @Deprecated
    void glSamplerParameterf(int sampler, int pname, float param);
    @Deprecated
    void glSamplerParameteriv(int sampler, int pname, int[] params);
    @Deprecated
    int glGetInteger(int pname);
    @Deprecated
    void glDeleteBuffers(int buffer);
    @Deprecated
    void glPolygonMode(int face, int mode);
    @Deprecated
    void glViewport(int x, int y, int width, int height);
    @Deprecated
    void glDispatchComputeIndirect(long offset);
    @Deprecated
    void glBindBuffer(int target, int buffer);
    @Deprecated
    String glGetStringi(int name, int index);
    @Deprecated
    void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth);
    @Deprecated
    int glCheckFramebufferStatus(int target);
    @Deprecated
    void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value);
    @Deprecated
    void glUniformMatrix3fv(int location, boolean transpose, float[] value);
    @Deprecated
    void glClearColor(float r, float g, float b, float a);
    @Deprecated
    int glGetAttribLocation(int program, CharSequence name);
    @Deprecated
    void glGenerateMipmap(int target);
    @Deprecated
    void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    // DSA methods
    @Deprecated
    void glGenerateTextureMipmap(int texture);
    @Deprecated
    void glTextureParameteri(int texture, int pname, int param);
    @Deprecated
    void glTextureParameterf(int texture, int pname, float param);
    @Deprecated
    void glTextureParameteriv(int texture, int pname, int[] params);
    @Deprecated
    void glNamedFramebufferReadBuffer(int framebuffer, int mode);
    @Deprecated
    void glNamedFramebufferDrawBuffers(int framebuffer, int[] bufs);
    @Deprecated
    void glClearNamedFramebufferfv(int framebuffer, int buffer, int drawbuffer, float[] value);
    @Deprecated
    void glClearNamedFramebufferiv(int framebuffer, int buffer, int drawbuffer, int[] value);
    @Deprecated
    void glClearNamedFramebufferuiv(int framebuffer, int buffer, int drawbuffer, int[] value);
    @Deprecated
    int glGetTextureParameteri(int texture, int pname);
    @Deprecated
    void glCopyTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    @Deprecated
    void glBindTextureUnit(int unit, int texture);
    @Deprecated
    int glCreateBuffers();
    @Deprecated
    void glNamedBufferData(int buffer, float[] data, int usage);
    @Deprecated
    void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    @Deprecated
    void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level);
    @Deprecated
    int glCreateFramebuffers();
    @Deprecated
    int glCreateTextures(int target);
    
    // Additional rendering operations
    @Deprecated
    void glDrawElements(int mode, int count, int type, long indices);
    @Deprecated
    void glBlendEquation(int mode);
    @Deprecated
    void glClearDepth(double depth);
    @Deprecated
    int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname);
    
    // Debug callback methods (low-level control methods only)
    @Deprecated
    void glDebugMessageControl(int source, int type, int severity, int[] ids, boolean enabled);
    @Deprecated
    void glDebugMessageControlKHR(int source, int type, int severity, int[] ids, boolean enabled);
    @Deprecated
    void glDebugMessageControlARB(int source, int type, int severity, int[] ids, boolean enabled);
    @Deprecated
    void glDebugMessageEnableAMD(int category, int severity, int[] ids, boolean enabled);
    
    // High-level debug callback wrapper methods
    @Deprecated
    void setupDebugMessageCallback(VulkanicAPI.DebugMessageCallback callback);
    @Deprecated
    void setupDebugMessageCallbackKHR(VulkanicAPI.DebugMessageCallback callback);
    @Deprecated
    void setupDebugMessageCallbackARB(VulkanicAPI.DebugMessageCallbackARB callback);
    @Deprecated
    void setupDebugMessageCallbackAMD(VulkanicAPI.DebugMessageCallbackAMD callback);
    @Deprecated
    void clearDebugMessageCallback();
    @Deprecated
    void clearDebugMessageCallbackKHR();
    @Deprecated
    void clearDebugMessageCallbackARB();
    @Deprecated
    void clearDebugMessageCallbackAMD();
    
    // GL43+ vertex attribute methods
    @Deprecated
    void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride);
    @Deprecated
    void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset);
    @Deprecated
    void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset);
    @Deprecated
    void vertexAttribBinding(int attribindex, int bindingindex);
    
    // VAO methods
    @Deprecated
    int genVertexArrays();
    @Deprecated
    void bindVertexArray(int array);
    @Deprecated
    void deleteVertexArrays(int array);
    
    // GL context capabilities
    @Deprecated
    Object getGLCapabilities();
    @Deprecated
    void setupDebugMessageCallback(java.io.PrintStream stream);
    
    // Capability checking methods (to avoid casting GLCapabilities outside backends/opengl)
    @Deprecated
    boolean checkOpenGL32Support();
    @Deprecated
    boolean checkOpenGL33Support();
    @Deprecated
    boolean checkARBInstancedArraysSupport();
    @Deprecated
    long getNamedBufferDataPointer();
    @Deprecated
    long getBufferStoragePointer();
    @Deprecated
    long getBindVertexBufferPointer();
    @Deprecated
    long getVertexAttribBindingPointer();
    
    // Additional GL query and state methods
    @Deprecated
    boolean glIsEnabled(int cap);
    @Deprecated
    boolean glIsFramebuffer(int framebuffer);
    @Deprecated
    boolean glIsTexture(int texture);
    @Deprecated
    boolean glIsVertexArray(int array);
    @Deprecated
    boolean glIsProgram(int program);
    
    // Additional GL state methods
    @Deprecated
    void glBlendEquationSeparate(int modeRGB, int modeAlpha);
    @Deprecated
    void glStencilFunc(int func, int ref, int mask);
    @Deprecated
    void glCullFace(int mode);
    
    // Additional texture methods
    @Deprecated
    int glGenTextures();
    
    // ===========================
    // Phase 12: Shader Query & State Retrieval Methods (CommandContext-aware)
    // ===========================
    
    /**
     * Queries shader program parameter.
     * 
     * In OpenGL: Maps to glGetProgramiv()
     * In Vulkan: Maps to pipeline reflection or VkPipeline properties
     * 
     * Common parameters:
     * - GL_LINK_STATUS: Check if program linked successfully
     * - GL_DELETE_STATUS: Check if program is flagged for deletion
     * - GL_VALIDATE_STATUS: Check if program validation succeeded
     * - GL_INFO_LOG_LENGTH: Length of info log
     * - GL_ATTACHED_SHADERS: Number of attached shaders
     * - GL_ACTIVE_ATTRIBUTES: Number of active vertex attributes
     * - GL_ACTIVE_UNIFORMS: Number of active uniform variables
     * 
     * @param ctx Command recording context
     * @param program The shader program ID
     * @param pname The parameter name to query
     * @return The queried parameter value
     * 
     * Example usage:
     * <pre>{@code
     * int linkStatus = backend.queryProgramParameter(CTX, programId, GL_LINK_STATUS);
     * if (linkStatus == GL_FALSE) {
     *     String log = backend.retrieveProgramInfoLog(CTX, programId);
     *     throw new RuntimeException("Link failed: " + log);
     * }
     * }</pre>
     */
    int queryProgramParameter(CommandContext ctx, int program, int pname);
    
    /**
     * Retrieves the information log for a shader program.
     * 
     * In OpenGL: Maps to glGetProgramInfoLog()
     * In Vulkan: Maps to VkPipeline creation validation messages
     * 
     * The info log contains errors and warnings from the linking process, or
     * validation messages if glValidateProgram was called.
     * 
     * @param ctx Command recording context
     * @param program The shader program ID
     * @return The program info log as a string
     * 
     * Example usage:
     * <pre>{@code
     * String log = backend.retrieveProgramInfoLog(CTX, programId);
     * if (!log.isEmpty()) {
     *     System.err.println("Program log: " + log);
     * }
     * }</pre>
     */
    String retrieveProgramInfoLog(CommandContext ctx, int program);
    
    /**
     * Queries an integer state value from the graphics API.
     * 
     * In OpenGL: Maps to glGetIntegerv() - queries global state
     * In Vulkan: Queries specific objects (pipeline, descriptor sets, etc.)
     * 
     * This is a general-purpose query method for retrieving various state values.
     * In Vulkan, this will need to query from appropriate objects rather than global state.
     * 
     * Common parameters:
     * - GL_CURRENT_PROGRAM: Currently bound shader program
     * - GL_VERTEX_ARRAY_BINDING: Currently bound VAO
     * - GL_ARRAY_BUFFER_BINDING: Currently bound VBO
     * - GL_ELEMENT_ARRAY_BUFFER_BINDING: Currently bound EBO
     * - GL_FRAMEBUFFER_BINDING: Currently bound FBO
     * - GL_TEXTURE_BINDING_2D: Currently bound 2D texture
     * - GL_ACTIVE_TEXTURE: Currently active texture unit
     * 
     * @param ctx Command recording context
     * @param pname The parameter name to query
     * @return The queried integer value
     * 
     * Example usage:
     * <pre>{@code
     * int currentProgram = backend.queryIntegerState(CTX, GL_CURRENT_PROGRAM);
     * int boundVAO = backend.queryIntegerState(CTX, GL_VERTEX_ARRAY_BINDING);
     * }</pre>
     */
    int queryIntegerState(CommandContext ctx, int pname);
    
    /**
     * Activates a shader program for use in rendering operations.
     * 
     * In OpenGL: Maps to glUseProgram()
     * In Vulkan: Maps to vkCmdBindPipeline()
     * 
     * This makes the shader program active for subsequent draw calls. In Vulkan,
     * this operation records a pipeline bind command into the command buffer.
     * 
     * Note: This is a convenience wrapper around bindShaderProgram(ctx, program).
     * The bindShaderProgram method should be preferred in new code.
     * 
     * @param ctx Command recording context
     * @param program The shader program ID to activate (0 to unbind)
     * 
     * Example usage:
     * <pre>{@code
     * backend.activateShaderProgram(CTX, myProgramId);
     * // Perform draw calls
     * backend.activateShaderProgram(CTX, 0); // Unbind
     * }</pre>
     */
    void activateShaderProgram(CommandContext ctx, int program);
    
    /**
     * Deletes a shader program and releases its resources.
     * 
     * In OpenGL: Maps to glDeleteProgram()
     * In Vulkan: Maps to vkDestroyPipeline()
     * 
     * This marks the program for deletion. If the program is currently in use,
     * it will be deleted once it's no longer in use. All resources associated
     * with the program, including linked shader objects, are released.
     * 
     * Note: This is a convenience wrapper around disposeProgramObject(ctx, program).
     * The disposeProgramObject method should be preferred in new code.
     * 
     * @param ctx Command recording context
     * @param program The shader program ID to delete
     * 
     * Example usage:
     * <pre>{@code
     * backend.destroyShaderProgram(CTX, oldProgramId);
     * }</pre>
     */
    void destroyShaderProgram(CommandContext ctx, int program);
}
