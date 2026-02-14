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
    
    @Deprecated
    void clear(int mask);
    @Deprecated
    void useProgram(int programId);
    @Deprecated
    void enable(int cap);
    @Deprecated
    void disable(int cap);
    
    // Depth operations
    @Deprecated
    void setDepthTestFunction(int func);
    @Deprecated
    void setDepthWriteEnabled(boolean enabled);
    
    // Color operations
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
    void drawPrimitiveArrays(int mode, int first, int count);
    @Deprecated
    void drawIndexedElements(int mode, int count, int type, long indices);
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
    int constructShaderObject(int shaderType);
    @Deprecated
    void disposeShaderObject(int shader);
    @Deprecated
    void compileShaderSource(int shader);
    @Deprecated
    int constructProgramObject();
    @Deprecated
    void disposeProgramObject(int program);
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
    
    // ========================================================================
    // NEW VULKAN-COMPATIBLE API (Phase 1)
    // These methods are NOT deprecated - they form the new Vulkan-compatible API
    // ========================================================================
    
    // Pipeline State Objects
    /**
     * Creates an immutable pipeline state object.
     * 
     * In Vulkan: Creates VkPipeline
     * In OpenGL: Creates a state configuration object
     * 
     * @param desc Pipeline state descriptor
     * @return The created pipeline
     */
    Pipeline createPipeline(PipelineStateDesc desc);
    
    /**
     * Binds a pipeline for subsequent draw calls.
     * 
     * In Vulkan: vkCmdBindPipeline
     * In OpenGL: Tracks current pipeline, applies state on draw
     * 
     * @param cmd Command buffer/context
     * @param pipeline Pipeline to bind
     */
    void bindPipeline(CommandBuffer cmd, Pipeline pipeline);
    
    /**
     * Destroys a pipeline and frees its resources.
     * 
     * @param pipeline Pipeline to destroy
     */
    void destroyPipeline(Pipeline pipeline);
    
    // Descriptor Sets
    /**
     * Creates a descriptor set layout.
     * 
     * In Vulkan: Creates VkDescriptorSetLayout
     * In OpenGL: Creates a binding configuration
     * 
     * @param builder Builder containing binding specifications
     * @return The created layout
     */
    DescriptorSetLayout createDescriptorSetLayout(DescriptorSetLayoutBuilder builder);
    
    /**
     * Allocates a descriptor set from a layout.
     * 
     * In Vulkan: Allocates from descriptor pool
     * In OpenGL: Creates a binding set
     * 
     * @param layout Descriptor set layout
     * @return Allocated descriptor set
     */
    DescriptorSet allocateDescriptorSet(DescriptorSetLayout layout);
    
    /**
     * Updates a descriptor set with a texture resource.
     * 
     * @param set Descriptor set to update
     * @param binding Binding index
     * @param texture Texture to bind
     */
    void updateDescriptorSetTexture(DescriptorSet set, int binding, Texture texture);
    
    /**
     * Updates a descriptor set with a buffer resource.
     * 
     * @param set Descriptor set to update
     * @param binding Binding index
     * @param buffer Buffer to bind
     */
    void updateDescriptorSetBuffer(DescriptorSet set, int binding, Buffer buffer);
    
    /**
     * Binds a descriptor set for subsequent draw calls.
     * 
     * In Vulkan: vkCmdBindDescriptorSets
     * In OpenGL: Tracks current descriptor set, applies bindings on draw
     * 
     * @param cmd Command buffer/context
     * @param set Descriptor set to bind
     * @param setIndex Set index (for multiple sets)
     */
    void bindDescriptorSet(CommandBuffer cmd, DescriptorSet set, int setIndex);
    
    // Render Passes
    /**
     * Creates a render pass.
     * 
     * In Vulkan: Creates VkRenderPass
     * In OpenGL: Creates framebuffer configuration
     * 
     * @param desc Render pass descriptor
     * @return The created render pass
     */
    RenderPass createRenderPass(RenderPassDesc desc);
    
    /**
     * Begins a render pass.
     * 
     * In Vulkan: vkCmdBeginRenderPass
     * In OpenGL: Binds framebuffer and clears attachments
     * 
     * @param cmd Command buffer/context
     * @param renderPass Render pass to begin
     * @param framebuffer Framebuffer handle (backend-specific)
     */
    void beginRenderPass(CommandBuffer cmd, RenderPass renderPass, long framebuffer);
    
    /**
     * Ends the current render pass.
     * 
     * In Vulkan: vkCmdEndRenderPass
     * In OpenGL: Unbinds framebuffer
     * 
     * @param cmd Command buffer/context
     */
    void endRenderPass(CommandBuffer cmd);
    
    // Command Buffers
    /**
     * Allocates a command buffer.
     * 
     * In Vulkan: Allocates VkCommandBuffer from pool
     * In OpenGL: Creates immediate-mode context
     * 
     * @return Allocated command buffer
     */
    CommandBuffer allocateCommandBuffer();
    
    /**
     * Begins recording commands into a command buffer.
     * 
     * In Vulkan: vkBeginCommandBuffer
     * In OpenGL: No-op (immediate mode)
     * 
     * @param cmd Command buffer to begin
     */
    void beginCommandBuffer(CommandBuffer cmd);
    
    /**
     * Ends recording commands into a command buffer.
     * 
     * In Vulkan: vkEndCommandBuffer
     * In OpenGL: No-op (immediate mode)
     * 
     * @param cmd Command buffer to end
     */
    void endCommandBuffer(CommandBuffer cmd);
    
    /**
     * Submits a command buffer for execution.
     * 
     * In Vulkan: Submits to queue
     * In OpenGL: No-op (already executed)
     * 
     * @param cmd Command buffer to submit
     */
    void submitCommandBuffer(CommandBuffer cmd);
    
    // Resources
    /**
     * Creates a buffer resource.
     * 
     * @param size Size in bytes
     * @param usage Buffer usage flags
     * @return Created buffer
     */
    Buffer createBuffer(long size, BufferUsage usage);
    
    /**
     * Destroys a buffer and frees its resources.
     * 
     * @param buffer Buffer to destroy
     */
    void destroyBuffer(Buffer buffer);
    
    /**
     * Creates a texture resource.
     * 
     * @param width Width in pixels
     * @param height Height in pixels
     * @param format Texture format
     * @return Created texture
     */
    Texture createTexture(int width, int height, Format format);
    
    /**
     * Destroys a texture and frees its resources.
     * 
     * @param texture Texture to destroy
     */
    void destroyTexture(Texture texture);
}
