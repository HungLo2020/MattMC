package net.vulkanic;

/**
 * Interface for graphics backend implementations.
 * This interface defines the contract that all backends (OpenGL, Vulkan) must implement.
 */
public interface GraphicsBackend {
    
    void bindTexture(int textureId);
    void bindTexture(int target, int textureId);  // For explicit target binding
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
    
    // GL Capabilities
    org.lwjgl.opengl.GLCapabilities obtainGraphicsCapabilities();
}
