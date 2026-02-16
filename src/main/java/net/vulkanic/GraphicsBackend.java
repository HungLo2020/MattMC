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
     * Creates a new buffer object using Direct State Access.
     * 
     * In OpenGL: Maps to glCreateBuffers() (DSA)
     * In Vulkan: Maps to vkCreateBuffer() with appropriate usage flags
     * 
     * @param ctx Command context for recording this command
     * @return The buffer object ID
     */
    int createBuffer(CommandContext ctx);
    
    /**
     * Allocates and initializes buffer storage with a given size.
     * 
     * In OpenGL: Maps to glNamedBufferData() (DSA)
     * In Vulkan: Maps to vkCreateBuffer() + vkAllocateMemory() + vkBindBufferMemory()
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer object ID
     * @param size Size in bytes
     * @param usage Usage hint (e.g., GL_STATIC_DRAW, GL_DYNAMIC_DRAW)
     */
    void bufferData(CommandContext ctx, int buffer, long size, int usage);
    
    /**
     * Allocates and initializes buffer storage with data.
     * 
     * In OpenGL: Maps to glNamedBufferData() (DSA)
     * In Vulkan: Maps to vkCreateBuffer() + memory allocation + data upload
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer object ID
     * @param data Data to upload
     * @param usage Usage hint
     */
    void bufferData(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage);
    
    /**
     * Update a subset of buffer data.
     * @param ctx Command context
     * @param buffer Buffer object
     * @param offset Offset in bytes
     * @param data Data to upload
     */
    void bufferSubData(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data);
    
    /**
     * Create immutable buffer storage (size).
     * @param ctx Command context
     * @param buffer Buffer object
     * @param size Size in bytes
     * @param flags Storage flags
     */
    void bufferStorage(CommandContext ctx, int buffer, long size, int flags);
    
    /**
     * Create immutable buffer storage (data).
     * @param ctx Command context
     * @param buffer Buffer object
     * @param data Initial data
     * @param flags Storage flags
     */
    void bufferStorage(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags);
    
    /**
     * Map a buffer range for client access.
     * @param ctx Command context
     * @param buffer Buffer object
     * @param offset Offset in bytes
     * @param length Length in bytes
     * @param access Access flags
     * @return Mapped buffer or null
     */
    java.nio.ByteBuffer mapBufferRange(CommandContext ctx, int buffer, long offset, long length, int access);
    
    /**
     * Unmap a previously mapped buffer.
     * @param ctx Command context
     * @param buffer Buffer object
     */
    void unmapBuffer(CommandContext ctx, int buffer);
    
    @Deprecated
    void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length);
    @Deprecated
    void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size);
    
    // Direct State Access framebuffer operations
    @Deprecated
    void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level);
    void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, 
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
    
    /**
     * Checks for OpenGL errors and returns the error code.
     * 
     * In OpenGL: Maps to glGetError()
     * In Vulkan: Returns VK_SUCCESS or appropriate error code from last operation
     * 
     * @param ctx Command context for recording this command
     * @return Error code (GL_NO_ERROR/VK_SUCCESS if no error)
     */
    int getError(CommandContext ctx);
    
    /**
     * Creates a new buffer object.
     * 
     * In OpenGL: Maps to glGenBuffers()
     * In Vulkan: Maps to vkCreateBuffer()
     * 
     * @param ctx Command context for recording this command
     * @return The buffer object ID
     */
    int createBufferObject(CommandContext ctx);
    
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
    
    // Error checking (deprecated)
    @Deprecated
    int checkForErrors();
    
    // Texture pixel data transfer
    @Deprecated
    void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix);
    @Deprecated
    void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix);
    @Deprecated
    void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix);
    
    // GPU buffer lifecycle (deprecated)
    @Deprecated
    void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg);
    @Deprecated
    void fillBufferWithSize(int tgt, long sz, int usg);
    @Deprecated
    void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat);
    
    // Vertex array objects
    int createVertexArray(CommandContext ctx);
    void bindVertexArray(CommandContext ctx, int vao);
    
    // Buffer memory mapping
    java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, int offset, int length, int access);
    void unmapBufferTarget(CommandContext ctx, int target);
    
    // Framebuffer lifecycle
    int createFramebufferObject(CommandContext ctx);
    void deleteFramebuffer(CommandContext ctx, int fbo);
    void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    // Shader pipeline
    int createShader(CommandContext ctx, int shaderType);
    void deleteShader(CommandContext ctx, int shader);
    void compileShader(CommandContext ctx, int shader);
    
    // Program management
    int createProgram(CommandContext ctx);
    void deleteProgram(CommandContext ctx, int program);
    void linkProgram(CommandContext ctx, int program);
    int getProgramParameter(CommandContext ctx, int program, int pname);
    String getProgramInfoLog(CommandContext ctx, int program);
    
    @Deprecated
    void attachShaderToProgram(int program, int shader);
    @Deprecated
    int queryShaderParameter(int shader, int pname);
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
}
