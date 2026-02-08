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
    long getGraphicsContext();
    
    void bindTexture(int textureId);
    void bindTexture(int target, int textureId);  // For explicit target binding
    void generateMipmap(int target);
    void viewport(int x, int y, int width, int height);
    void clear(int mask);
    void enableBlend();
    void disableBlend();
    void useProgram(int programId);
    void enable(int cap);
    void disable(int cap);
    
    // Depth operations
    void setDepthTestFunction(int func);
    void setDepthWriteEnabled(boolean enabled);
    
    // Color operations
    void setColorWriteMask(boolean r, boolean g, boolean b, boolean a);
    
    // Scissor operations
    void setScissorBox(int x, int y, int w, int h);
    
    // Pixel operations
    void setPixelStoreMode(int pname, int value);
    
    // Framebuffer operations
    void attachFramebuffer(int target, int fbo);
    void attachTextureToFramebuffer(int target, int attachment, int textarget, int texture, int level);
    
    // Buffer operations  
    void attachBuffer(int target, int buffer);
    
    // Direct State Access buffer operations
    int createBufferDSA();
    void namedBufferDataDSA(int buffer, long size, int usage);
    void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage);
    void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data);
    void namedBufferStorageDSA(int buffer, long size, int flags);
    void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags);
    java.nio.ByteBuffer mapNamedBufferRangeDSA(int buffer, long offset, long length, int access);
    void unmapNamedBufferDSA(int buffer);
    void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length);
    void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size);
    
    // Direct State Access framebuffer operations
    int createFramebufferDSA();
    void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level);
    void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, 
                                  int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    // Texture unit and parameter operations
    void activateTextureUnit(int unit);
    void configureTextureParameter(int target, int pname, int param);
    int createTexture();
    void removeTexture(int texture);
    
    // Polygon rendering operations
    void configurePolygonMode(int face, int mode);
    void configurePolygonOffset(float factor, float units);
    void configureLogicOp(int opcode);
    
    // Drawing operations
    void drawPrimitiveArrays(int mode, int first, int count);
    void drawIndexedElements(int mode, int count, int type, long indices);
    void configureBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
    
    // Error checking
    int checkForErrors();
    
    // Texture pixel data transfer
    void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix);
    void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix);
    void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix);
    
    // GPU buffer lifecycle
    int allocateBufferObject();
    void releaseBufferObject(int buf);
    void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg);
    void fillBufferWithSize(int tgt, long sz, int usg);
    void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat);
    
    // Vertex array objects
    int createVertexArrayObject();
    void selectVertexArray(int vao);
    
    // Buffer memory mapping
    java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc);
    void unmapBufferData(int tgt);
    
    // Framebuffer lifecycle
    int generateFramebufferObject();
    void destroyFramebufferObject(int fbo);
    void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt);
    
    // Shader pipeline
    int constructShaderObject(int shaderType);
    void disposeShaderObject(int shader);
    void compileShaderSource(int shader);
    int constructProgramObject();
    void disposeProgramObject(int program);
    void linkProgramBinary(int program);
    void attachShaderToProgram(int program, int shader);
    int queryProgramParameter(int program, int pname);
    int queryShaderParameter(int shader, int pname);
    String retrieveProgramInfoLog(int program);
    String retrieveShaderInfoLog(int shader);
    int locateUniformVariable(int program, CharSequence name);
    void assignUniformInteger(int location, int value);
    void bindAttributeLocation(int program, int index, CharSequence name);
    
    // Vertex attributes
    void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer);
    void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer);
    void activateVertexAttribute(int index);
    void deactivateVertexAttribute(int index);
    void setVertexAttribDivisor(int index, int divisor);
    
    // Synchronization
    long createFenceSync(int condition, int flags);
    int waitForSync(long sync, int flags, long timeout);
    void destroySync(long sync);
    
    // Query operations
    int queryIntegerState(int pname);
    String queryStringInfo(int name);
    int pollErrorCode();
    
    // Pixel readback
    void readFramebufferPixels(int x, int y, int width, int height, int format, int type, long pixels);
    
    // Texture queries
    int queryTextureLevelParameter(int target, int level, int pname);
    
    // Shader source (native)
    void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer);
    
    // Uniform block operations
    int locateUniformBlock(int program, String uniformBlockName);
    void bindUniformBlock(int program, int uniformBlockIndex, int uniformBlockBinding);
    String retrieveActiveUniformBlockName(int program, int uniformBlockIndex);
    
    // Timer query operations
    int generateQueryObject();
    void initiateQuery(int target, int id);
    void concludeQuery(int target);
    void disposeQueryObject(int id);
    int retrieveQueryObjectInt(int id, int pname);
    long retrieveQueryObjectInt64(int id, int pname);
    
    // Debug label operations (KHR_debug)
    void labelDebugObject(int identifier, int name, String label);
    void enterDebugGroup(int source, int id, CharSequence message);
    void exitDebugGroup();
    
    // Debug label operations (EXT_debug_label)
    void labelObjectExt(int type, int object, String label);
    
    // Debug system initialization (wraps entire debug setup)
    boolean supportsKhrDebug();
    boolean supportsArbDebugOutput();
    void setupKhrDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler);
    void setupArbDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler);
    
    // Extension capability checking
    boolean hasBufferStorageExtension();
    boolean hasVertexAttribBindingExtension();
    
    // ARB vertex attrib binding operations
    void attachVertexBuffer(int bindingIndex, int buffer, long offset, int stride);
    void specifyVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset);
    void specifyVertexAttribIFormat(int attribIndex, int size, int type, int relativeOffset);
    void associateVertexAttrib(int attribIndex, int bindingIndex);
    
    // Clear operations
    void setClearDepthValue(double depth);
    void setClearColorValue(float red, float green, float blue, float alpha);
    void selectDrawBuffer(int mode);
    
    // Advanced drawing operations
    void renderIndexedInstancedWithBase(int mode, int count, int type, long indices, int instanceCount, int baseVertex);
    void renderIndexedWithBase(int mode, int count, int type, long indices, int baseVertex);
    void renderIndexedInstanced(int mode, int count, int type, long indices, int instanceCount);
    void renderArraysInstanced(int mode, int first, int count, int instanceCount);
    
    // Uniform buffer operations
    void attachUniformBufferRange(int target, int index, int buffer, long offset, long size);
    
    // Texture buffer operations
    void attachBufferToTexture(int target, int internalFormat, int buffer);
    
    // Uniform operations (additional)
    void assignUniformFloat(int location, float value);
    void assignUniformFloat2(int location, float x, float y);
    void assignUniformFloat2v(int location, float[] value);
    void assignUniformFloat3(int location, float x, float y, float z);
    void assignUniformFloat3v(int location, float[] value);
    void assignUniformFloat4(int location, float x, float y, float z, float w);
    void assignUniformFloat4v(int location, float[] value);
    void assignUniformMatrix4f(int location, java.nio.FloatBuffer matrix);
    void bindUniformBufferBase(int bindingPoint, int bufferId);
    
    // Program fragment data binding
    void bindFragmentDataLocation(int program, int colorNumber, CharSequence name);
    
    // Sync query operations
    int querySyncStatus(long sync, int pname, java.nio.IntBuffer length);
    
    // Graphics Capabilities
    GraphicsCapabilities obtainGraphicsCapabilities();
    GraphicsCapabilities initializeGraphicsCapabilities();
    boolean checkFunctionAvailable(String functionName);
    
    // Additional buffer operations for Sodium
    void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size);
    void deleteVertexArray(int vertexArray);
    void flushMappedBufferRange(int target, long offset, long length);
    
    // Buffer storage operations
    void createBufferStorage(int target, long size, int flags);
    void createBufferStorage(int target, ByteBuffer data, int flags);
    
    // Multi-draw operations
    void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex);
    
    // Uniform matrix operations
    void assignUniformMatrix4fv(int location, boolean transpose, FloatBuffer value);
    
    // String queries
    String queryString(int name);
    String queryStringIndexed(int name, int index);
    
    // Native shader source upload
    void uploadShaderSourceNative(int shader, int count, long strings, long length);
    
    // Texture operations
    void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    
    // Clear texture image (ARB_clear_texture)
    void clearTexImage(int texture, int level, int format, int type, int[] data);
    
    // Parallel shader compile (KHR/ARB)
    void setMaxShaderCompilerThreads(int count);
    
    // Capabilities
    GraphicsCapabilities getGraphicsCapabilities();
    
    // Debug object labeling
    void labelObject(int identifier, int name, String label);
    
    // Debug group push/pop
    void pushDebugGroup(int source, int id, String message);
    void popDebugGroup();
    
    // Additional methods for IrisRenderSystem
    void glGetIntegerv(int pname, int[] params);
    void glGetFloatv(int pname, float[] params);
    void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels);
    void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels);
    void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels);
    void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix);
    void glUniformMatrix4fv(int location, boolean transpose, float[] matrix);
    void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border);
    void glUniform1f(int location, float v0);
    void glUniform2f(int location, float v0, float v1);
    void glUniform2i(int location, int v0, int v1);
    void glUniform3f(int location, float v0, float v1, float v2);
    void glUniform3i(int location, int v0, int v1, int v2);
    void glUniform4f(int location, float v0, float v1, float v2, float v3);
    void glUniform4i(int location, int v0, int v1, int v2, int v3);
    void glTexParameteriv(int target, int pname, int[] params);
    void glTexParameteri(int target, int pname, int param);
    void glTexParameterf(int target, int pname, float param);
    String glGetProgramInfoLog(int program);
    String glGetShaderInfoLog(int shader);
    void glDrawBuffers(int[] buffers);
    void glReadBuffer(int buffer);
    void glClearBufferfv(int buffer, int drawbuffer, float[] values);
    void glClearBufferiv(int buffer, int drawbuffer, int[] values);
    void glClearBufferuiv(int buffer, int drawbuffer, int[] values);
    String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name);
    void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels);
    void glBufferData(int target, float[] data, int usage);
    void glBufferData(int target, int[] data, int usage);
    void glBufferData(int target, java.nio.ByteBuffer data, int usage);
    void glBufferData(int target, long size, int usage);
    void glBufferSubData(int target, long offset, java.nio.ByteBuffer data);
    void glBufferStorage(int target, long size, int flags);
    void glBufferStorage(int target, java.nio.ByteBuffer data, int flags);
    java.nio.ByteBuffer glMapBufferRange(int target, long offset, long length, int access);
    boolean glUnmapBuffer(int target);
    boolean glIsBuffer(int buffer);
    void glBindBufferBase(int target, int index, int buffer);
    void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3);
    void glDetachShader(int program, int shader);
    void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
    void glFramebufferTexture(int target, int attachment, int texture, int level);
    int glGetTexParameteri(int target, int pname);
    void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format);
    int glGetMaxImageUnits();
    void glGenBuffers(int[] buffers);
    void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data);
    void glGetProgramiv(int program, int pname, int[] params);
    void glDispatchCompute(int workX, int workY, int workZ);
    void glMemoryBarrier(int barriers);
    void glDisablei(int target, int index);
    void glEnablei(int target, int index);
    void glBlendFunc(int sfactor, int dfactor);
    void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);
    int glGetUniformBlockIndex(int program, String uniformBlockName);
    void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding);
    int glGenSamplers();
    void glDeleteSamplers(int sampler);
    void glBindSampler(int unit, int sampler);
    void glBindSamplers(int first, int[] samplers);
    void glSamplerParameteri(int sampler, int pname, int param);
    void glSamplerParameterf(int sampler, int pname, float param);
    void glSamplerParameteriv(int sampler, int pname, int[] params);
    int glGetInteger(int pname);
    void glDeleteBuffers(int buffer);
    void glPolygonMode(int face, int mode);
    void glViewport(int x, int y, int width, int height);
    void glDispatchComputeIndirect(long offset);
    void glBindBuffer(int target, int buffer);
    String glGetStringi(int name, int index);
    void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth);
    int glCheckFramebufferStatus(int target);
    void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value);
    void glUniformMatrix3fv(int location, boolean transpose, float[] value);
    void glClearColor(float r, float g, float b, float a);
    int glGetAttribLocation(int program, CharSequence name);
    void glGenerateMipmap(int target);
    void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    
    // DSA methods
    void glGenerateTextureMipmap(int texture);
    void glTextureParameteri(int texture, int pname, int param);
    void glTextureParameterf(int texture, int pname, float param);
    void glTextureParameteriv(int texture, int pname, int[] params);
    void glNamedFramebufferReadBuffer(int framebuffer, int mode);
    void glNamedFramebufferDrawBuffers(int framebuffer, int[] bufs);
    void glClearNamedFramebufferfv(int framebuffer, int buffer, int drawbuffer, float[] value);
    void glClearNamedFramebufferiv(int framebuffer, int buffer, int drawbuffer, int[] value);
    void glClearNamedFramebufferuiv(int framebuffer, int buffer, int drawbuffer, int[] value);
    int glGetTextureParameteri(int texture, int pname);
    void glCopyTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    void glBindTextureUnit(int unit, int texture);
    int glCreateBuffers();
    void glNamedBufferData(int buffer, float[] data, int usage);
    void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level);
    int glCreateFramebuffers();
    int glCreateTextures(int target);
    
    // Additional rendering operations
    void glDrawElements(int mode, int count, int type, long indices);
    void glBlendEquation(int mode);
    void glClearDepth(double depth);
    int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname);
    
    // Debug callback methods (low-level control methods only)
    void glDebugMessageControl(int source, int type, int severity, int[] ids, boolean enabled);
    void glDebugMessageControlKHR(int source, int type, int severity, int[] ids, boolean enabled);
    void glDebugMessageControlARB(int source, int type, int severity, int[] ids, boolean enabled);
    void glDebugMessageEnableAMD(int category, int severity, int[] ids, boolean enabled);
    
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
    void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride);
    void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset);
    void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset);
    void vertexAttribBinding(int attribindex, int bindingindex);
    
    // VAO methods
    int genVertexArrays();
    void bindVertexArray(int array);
    void deleteVertexArrays(int array);
    
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
    long getVertexAttribBindingPointer();
    
    // Additional GL query and state methods
    boolean glIsEnabled(int cap);
    boolean glIsFramebuffer(int framebuffer);
    boolean glIsTexture(int texture);
    boolean glIsVertexArray(int array);
    boolean glIsProgram(int program);
    
    // Additional GL state methods
    void glBlendEquationSeparate(int modeRGB, int modeAlpha);
    void glStencilFunc(int func, int ref, int mask);
    void glCullFace(int mode);
    
    // Additional texture methods
    int glGenTextures();
}
