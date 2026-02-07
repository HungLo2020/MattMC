package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.vulkanic.GraphicsBackend;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * OpenGL implementation of the Vulkanic Graphics Backend.
 * This is the ONLY place where direct OpenGL calls should be made.
 */
public class OpenGLBackend implements GraphicsBackend {
    
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
    
    @Override
    public void bindTexture(int textureId) {
        int activeTexUnit = GlStateManager.activeTexture;
        if (textureId != GlStateManager.TEXTURES[activeTexUnit].binding) {
            GlStateManager.TEXTURES[activeTexUnit].binding = textureId;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }
    }
    
    @Override
    public void bindTexture(int target, int textureId) {
        GL11.glBindTexture(target, textureId);
    }
    
    @Override
    public void generateMipmap(int target) {
        GL30.glGenerateMipmap(target);
    }
    
    @Override
    public void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }
    
    @Override
    public void clear(int mask) {
        GL11.glClear(mask);
    }
    
    @Override
    public void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }
    
    @Override
    public void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    @Override
    public void useProgram(int programId) {
        GL20.glUseProgram(programId);
    }
    
    @Override
    public void enable(int cap) {
        GL11.glEnable(cap);
    }
    
    @Override
    public void disable(int cap) {
        GL11.glDisable(cap);
    }
    
    @Override
    public void setDepthTestFunction(int func) {
        GL11.glDepthFunc(func);
    }
    
    @Override
    public void setDepthWriteEnabled(boolean enabled) {
        GL11.glDepthMask(enabled);
    }
    
    @Override
    public void setColorWriteMask(boolean r, boolean g, boolean b, boolean a) {
        GL11.glColorMask(r, g, b, a);
    }
    
    @Override
    public void setScissorBox(int x, int y, int w, int h) {
        GL20.glScissor(x, y, w, h);
    }
    
    @Override
    public void setPixelStoreMode(int pname, int value) {
        GL11.glPixelStorei(pname, value);
    }
    
    @Override
    public void attachFramebuffer(int target, int fbo) {
        GL30.glBindFramebuffer(target, fbo);
    }
    
    @Override
    public void attachTextureToFramebuffer(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public void attachBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }
    
    // Direct State Access buffer operations
    @Override
    public int createBufferDSA() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers();
    }
    
    @Override
    public void namedBufferDataDSA(int buffer, long size, int usage) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData(buffer, size, usage);
    }
    
    @Override
    public void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData(buffer, data, usage);
    }
    
    @Override
    public void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferSubData(buffer, offset, data);
    }
    
    @Override
    public void namedBufferStorageDSA(int buffer, long size, int flags) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage(buffer, size, flags);
    }
    
    @Override
    public void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage(buffer, data, flags);
    }
    
    @Override
    public java.nio.ByteBuffer mapNamedBufferRangeDSA(int buffer, long offset, long length, int access) {
        return org.lwjgl.opengl.ARBDirectStateAccess.glMapNamedBufferRange(buffer, offset, length, access);
    }
    
    @Override
    public void unmapNamedBufferDSA(int buffer) {
        org.lwjgl.opengl.ARBDirectStateAccess.glUnmapNamedBuffer(buffer);
    }
    
    @Override
    public void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length) {
        org.lwjgl.opengl.ARBDirectStateAccess.glFlushMappedNamedBufferRange(buffer, offset, length);
    }
    
    @Override
    public void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // Direct State Access framebuffer operations
    @Override
    public int createFramebufferDSA() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    @Override
    public void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Override
    public void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                                                      dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Override
    public void activateTextureUnit(int unit) {
        org.lwjgl.opengl.GL13.glActiveTexture(unit);
    }
    
    @Override
    public void configureTextureParameter(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }
    
    @Override
    public int createTexture() {
        return GL11.glGenTextures();
    }
    
    @Override
    public void removeTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }
    
    @Override
    public void configurePolygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }
    
    @Override
    public void configurePolygonOffset(float factor, float units) {
        GL11.glPolygonOffset(factor, units);
    }
    
    @Override
    public void configureLogicOp(int opcode) {
        GL11.glLogicOp(opcode);
    }
    
    @Override
    public void drawPrimitiveArrays(int mode, int first, int count) {
        GL11.glDrawArrays(mode, first, count);
    }

    @Override
    public void drawIndexedElements(int mode, int count, int type, long indices) {
        GL11.glDrawElements(mode, count, type, indices);
    }
    
    @Override
    public void configureBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    @Override
    public int checkForErrors() {
        return GL11.glGetError();
    }
    
    @Override
    public void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        GL11.glTexImage2D(tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    @Override
    public void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix) {
        GL11.glTexSubImage2D(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Override
    public void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        GL11.glTexSubImage2D(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Override
    public int allocateBufferObject() {
        return GL15.glGenBuffers();
    }
    
    @Override
    public void releaseBufferObject(int buf) {
        GL15.glDeleteBuffers(buf);
    }
    
    @Override
    public void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg) {
        GL15.glBufferData(tgt, dat, usg);
    }
    
    @Override
    public void fillBufferWithSize(int tgt, long sz, int usg) {
        GL15.glBufferData(tgt, sz, usg);
    }
    
    @Override
    public void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat) {
        GL15.glBufferSubData(tgt, off, dat);
    }
    
    @Override
    public int createVertexArrayObject() {
        return GL30.glGenVertexArrays();
    }
    
    @Override
    public void selectVertexArray(int vao) {
        GL30.glBindVertexArray(vao);
    }
    
    @Override
    public java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc) {
        return GL30.glMapBufferRange(tgt, off, len, acc);
    }
    
    @Override
    public void unmapBufferData(int tgt) {
        GL15.glUnmapBuffer(tgt);
    }
    
    @Override
    public int generateFramebufferObject() {
        return GL30.glGenFramebuffers();
    }
    
    @Override
    public void destroyFramebufferObject(int fbo) {
        GL30.glDeleteFramebuffers(fbo);
    }
    
    @Override
    public void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
    }
    
    @Override
    public int constructShaderObject(int shaderType) {
        return GL20.glCreateShader(shaderType);
    }
    
    @Override
    public void disposeShaderObject(int shader) {
        GL20.glDeleteShader(shader);
    }
    
    @Override
    public void compileShaderSource(int shader) {
        GL20.glCompileShader(shader);
    }
    
    @Override
    public int constructProgramObject() {
        return GL20.glCreateProgram();
    }
    
    @Override
    public void disposeProgramObject(int program) {
        GL20.glDeleteProgram(program);
    }
    
    @Override
    public void linkProgramBinary(int program) {
        GL20.glLinkProgram(program);
    }
    
    @Override
    public void attachShaderToProgram(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }
    
    @Override
    public int queryProgramParameter(int program, int pname) {
        return GL20.glGetProgrami(program, pname);
    }
    
    @Override
    public int queryShaderParameter(int shader, int pname) {
        return GL20.glGetShaderi(shader, pname);
    }
    
    @Override
    public void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Override
    public void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer) {
        org.lwjgl.opengl.GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Override
    public void activateVertexAttribute(int index) {
        GL20.glEnableVertexAttribArray(index);
    }
    
    @Override
    public void deactivateVertexAttribute(int index) {
        GL20.glDisableVertexAttribArray(index);
    }
    
    @Override
    public void setVertexAttribDivisor(int index, int divisor) {
        org.lwjgl.opengl.GL33.glVertexAttribDivisor(index, divisor);
    }
    
    @Override
    public String retrieveProgramInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program);
    }
    
    @Override
    public String retrieveShaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader);
    }
    
    @Override
    public int locateUniformVariable(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }
    
    @Override
    public void assignUniformInteger(int location, int value) {
        GL20.glUniform1i(location, value);
    }
    
    @Override
    public void bindAttributeLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
    }
    
    @Override
    public long createFenceSync(int condition, int flags) {
        return org.lwjgl.opengl.GL32.glFenceSync(condition, flags);
    }
    
    @Override
    public int waitForSync(long sync, int flags, long timeout) {
        return org.lwjgl.opengl.GL32.glClientWaitSync(sync, flags, timeout);
    }
    
    @Override
    public void destroySync(long sync) {
        org.lwjgl.opengl.GL32.glDeleteSync(sync);
    }
    
    @Override
    public int queryIntegerState(int pname) {
        return GL11.glGetInteger(pname);
    }
    
    @Override
    public String queryStringInfo(int name) {
        return GL11.glGetString(name);
    }
    
    @Override
    public int pollErrorCode() {
        return GL11.glGetError();
    }
    
    @Override
    public void readFramebufferPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Override
    public int queryTextureLevelParameter(int target, int level, int pname) {
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }
    
    @Override
    public void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, stringCount, pointerBufferAddress, lengthsPointer);
    }
    
    @Override
    public int locateUniformBlock(int program, String uniformBlockName) {
        return org.lwjgl.opengl.GL31.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Override
    public void bindUniformBlock(int program, int uniformBlockIndex, int uniformBlockBinding) {
        org.lwjgl.opengl.GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Override
    public String retrieveActiveUniformBlockName(int program, int uniformBlockIndex) {
        return org.lwjgl.opengl.GL31.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    @Override
    public int generateQueryObject() {
        return org.lwjgl.opengl.GL32C.glGenQueries();
    }
    
    @Override
    public void initiateQuery(int target, int id) {
        org.lwjgl.opengl.GL32C.glBeginQuery(target, id);
    }
    
    @Override
    public void concludeQuery(int target) {
        org.lwjgl.opengl.GL32C.glEndQuery(target);
    }
    
    @Override
    public void disposeQueryObject(int id) {
        org.lwjgl.opengl.GL32C.glDeleteQueries(id);
    }
    
    @Override
    public int retrieveQueryObjectInt(int id, int pname) {
        return org.lwjgl.opengl.GL32C.glGetQueryObjecti(id, pname);
    }
    
    @Override
    public long retrieveQueryObjectInt64(int id, int pname) {
        return org.lwjgl.opengl.ARBTimerQuery.glGetQueryObjecti64(id, pname);
    }
    
    @Override
    public void labelDebugObject(int identifier, int name, String label) {
        org.lwjgl.opengl.KHRDebug.glObjectLabel(identifier, name, label);
    }
    
    @Override
    public void enterDebugGroup(int source, int id, CharSequence message) {
        org.lwjgl.opengl.KHRDebug.glPushDebugGroup(source, id, message);
    }
    
    @Override
    public void exitDebugGroup() {
        org.lwjgl.opengl.KHRDebug.glPopDebugGroup();
    }
    
    @Override
    public void labelObjectExt(int type, int object, String label) {
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
    public void attachVertexBuffer(int bindingIndex, int buffer, long offset, int stride) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glBindVertexBuffer(bindingIndex, buffer, offset, stride);
    }
    
    @Override
    public void specifyVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribFormat(attribIndex, size, type, normalized, relativeOffset);
    }
    
    @Override
    public void specifyVertexAttribIFormat(int attribIndex, int size, int type, int relativeOffset) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribIFormat(attribIndex, size, type, relativeOffset);
    }
    
    @Override
    public void associateVertexAttrib(int attribIndex, int bindingIndex) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribBinding(attribIndex, bindingIndex);
    }
    
    @Override
    public void setClearDepthValue(double depth) {
        org.lwjgl.opengl.GL11.glClearDepth(depth);
    }
    
    @Override
    public void setClearColorValue(float red, float green, float blue, float alpha) {
        org.lwjgl.opengl.GL11.glClearColor(red, green, blue, alpha);
    }
    
    @Override
    public void selectDrawBuffer(int mode) {
        org.lwjgl.opengl.GL11.glDrawBuffer(mode);
    }
    
    @Override
    public void renderIndexedInstancedWithBase(int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    @Override
    public void renderIndexedWithBase(int mode, int count, int type, long indices, int baseVertex) {
        org.lwjgl.opengl.GL32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }
    
    @Override
    public void renderIndexedInstanced(int mode, int count, int type, long indices, int instanceCount) {
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }
    
    @Override
    public void renderArraysInstanced(int mode, int first, int count, int instanceCount) {
        org.lwjgl.opengl.GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
    
    @Override
    public void attachUniformBufferRange(int target, int index, int buffer, long offset, long size) {
        org.lwjgl.opengl.GL32.glBindBufferRange(target, index, buffer, offset, size);
    }
    
    @Override
    public void attachBufferToTexture(int target, int internalFormat, int buffer) {
        org.lwjgl.opengl.GL31.glTexBuffer(target, internalFormat, buffer);
    }
    
    @Override
    public void assignUniformFloat(int location, float value) {
        org.lwjgl.opengl.GL30C.glUniform1f(location, value);
    }
    
    @Override
    public void assignUniformFloat2(int location, float x, float y) {
        org.lwjgl.opengl.GL30C.glUniform2f(location, x, y);
    }
    
    @Override
    public void assignUniformFloat2v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform2fv(location, value);
    }
    
    @Override
    public void assignUniformFloat3(int location, float x, float y, float z) {
        org.lwjgl.opengl.GL30C.glUniform3f(location, x, y, z);
    }
    
    @Override
    public void assignUniformFloat3v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform3fv(location, value);
    }
    
    @Override
    public void assignUniformFloat4(int location, float x, float y, float z, float w) {
        org.lwjgl.opengl.GL30C.glUniform4f(location, x, y, z, w);
    }
    
    @Override
    public void assignUniformFloat4v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform4fv(location, value);
    }
    
    @Override
    public void assignUniformMatrix4f(int location, java.nio.FloatBuffer matrix) {
        org.lwjgl.opengl.GL30C.glUniformMatrix4fv(location, false, matrix);
    }
    
    @Override
    public void bindUniformBufferBase(int bindingPoint, int bufferId) {
        org.lwjgl.opengl.GL32C.glBindBufferBase(org.lwjgl.opengl.GL32C.GL_UNIFORM_BUFFER, bindingPoint, bufferId);
    }
    
    @Override
    public void bindFragmentDataLocation(int program, int colorNumber, CharSequence name) {
        org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, colorNumber, name);
    }
    
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
    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        org.lwjgl.opengl.GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Override
    public void deleteVertexArray(int vertexArray) {
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(vertexArray);
    }
    
    @Override
    public void flushMappedBufferRange(int target, long offset, long length) {
        org.lwjgl.opengl.GL30.glFlushMappedBufferRange(target, offset, length);
    }
    
    @Override
    public void createBufferStorage(int target, long size, int flags) {
        org.lwjgl.opengl.GLCapabilities capabilities = org.lwjgl.opengl.GL.getCapabilities();
        
        if (capabilities.OpenGL44) {
            org.lwjgl.opengl.GL44C.glBufferStorage(target, size, flags);
        } else if (capabilities.GL_ARB_buffer_storage) {
            org.lwjgl.opengl.ARBBufferStorage.glBufferStorage(target, size, flags);
        } else {
            throw new UnsupportedOperationException("Buffer storage is not supported");
        }
    }
    
    @Override
    public void createBufferStorage(int target, ByteBuffer data, int flags) {
        org.lwjgl.opengl.GLCapabilities capabilities = org.lwjgl.opengl.GL.getCapabilities();
        
        if (capabilities.OpenGL44) {
            org.lwjgl.opengl.GL44C.glBufferStorage(target, data, flags);
        } else if (capabilities.GL_ARB_buffer_storage) {
            org.lwjgl.opengl.ARBBufferStorage.glBufferStorage(target, data, flags);
        } else {
            throw new UnsupportedOperationException("Buffer storage is not supported");
        }
    }
    
    @Override
    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    @Override
    public void assignUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        org.lwjgl.opengl.GL20C.glUniformMatrix4fv(location, transpose, value);
    }
    
    @Override
    public String queryString(int name) {
        return org.lwjgl.opengl.GL11C.glGetString(name);
    }
    
    @Override
    public String queryStringIndexed(int name, int index) {
        return org.lwjgl.opengl.GL30C.glGetStringi(name, index);
    }
    
    @Override
    public void uploadShaderSourceNative(int shader, int count, long strings, long length) {
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, count, strings, length);
    }
    
    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        org.lwjgl.opengl.GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Override
    public void clearTexImage(int texture, int level, int format, int type, int[] data) {
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
    
    @Override
    public void labelObject(int identifier, int name, String label) {
        org.lwjgl.opengl.KHRDebug.glObjectLabel(identifier, name, label);
    }
    
    @Override
    public void pushDebugGroup(int source, int id, String message) {
        org.lwjgl.opengl.KHRDebug.glPushDebugGroup(source, id, message);
    }
    
    @Override
    public void popDebugGroup() {
        org.lwjgl.opengl.KHRDebug.glPopDebugGroup();
    }
    
    // Additional methods for IrisRenderSystem
    
    @Override
    public void glGetIntegerv(int pname, int[] params) {
        org.lwjgl.opengl.GL32C.glGetIntegerv(pname, params);
    }
    
    @Override
    public void glGetFloatv(int pname, float[] params) {
        org.lwjgl.opengl.GL32C.glGetFloatv(pname, params);
    }
    
    @Override
    public void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL32C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
    
    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix) {
        org.lwjgl.opengl.GL32C.glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, float[] matrix) {
        org.lwjgl.opengl.GL32C.glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Override
    public void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        org.lwjgl.opengl.GL32C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    @Override
    public void glUniform1f(int location, float v0) {
        org.lwjgl.opengl.GL32C.glUniform1f(location, v0);
    }
    
    @Override
    public void glUniform2f(int location, float v0, float v1) {
        org.lwjgl.opengl.GL32C.glUniform2f(location, v0, v1);
    }
    
    @Override
    public void glUniform2i(int location, int v0, int v1) {
        org.lwjgl.opengl.GL32C.glUniform2i(location, v0, v1);
    }
    
    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        org.lwjgl.opengl.GL32C.glUniform3f(location, v0, v1, v2);
    }
    
    @Override
    public void glUniform3i(int location, int v0, int v1, int v2) {
        org.lwjgl.opengl.GL32C.glUniform3i(location, v0, v1, v2);
    }
    
    @Override
    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        org.lwjgl.opengl.GL32C.glUniform4f(location, v0, v1, v2, v3);
    }
    
    @Override
    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        org.lwjgl.opengl.GL32C.glUniform4i(location, v0, v1, v2, v3);
    }
    
    @Override
    public void glTexParameteriv(int target, int pname, int[] params) {
        org.lwjgl.opengl.GL32C.glTexParameteriv(target, pname, params);
    }
    
    @Override
    public void glTexParameteri(int target, int pname, int param) {
        org.lwjgl.opengl.GL32C.glTexParameteri(target, pname, param);
    }
    
    @Override
    public void glTexParameterf(int target, int pname, float param) {
        org.lwjgl.opengl.GL32C.glTexParameterf(target, pname, param);
    }
    
    @Override
    public String glGetProgramInfoLog(int program) {
        return org.lwjgl.opengl.GL32C.glGetProgramInfoLog(program);
    }
    
    @Override
    public String glGetShaderInfoLog(int shader) {
        return org.lwjgl.opengl.GL32C.glGetShaderInfoLog(shader);
    }
    
    @Override
    public void glDrawBuffers(int[] buffers) {
        org.lwjgl.opengl.GL32C.glDrawBuffers(buffers);
    }
    
    @Override
    public void glReadBuffer(int buffer) {
        org.lwjgl.opengl.GL32C.glReadBuffer(buffer);
    }
    
    @Override
    public void glClearBufferfv(int buffer, int drawbuffer, float[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferfv(buffer, drawbuffer, values);
    }
    
    @Override
    public void glClearBufferiv(int buffer, int drawbuffer, int[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferiv(buffer, drawbuffer, values);
    }
    
    @Override
    public void glClearBufferuiv(int buffer, int drawbuffer, int[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferuiv(buffer, drawbuffer, values);
    }
    
    @Override
    public String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return org.lwjgl.opengl.GL32C.glGetActiveUniform(program, index, size, type, name);
    }
    
    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
        org.lwjgl.opengl.GL32C.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Override
    public void glBufferData(int target, float[] data, int usage) {
        org.lwjgl.opengl.GL32C.glBufferData(target, data, usage);
    }
    
    @Override
    public void glBufferStorage(int target, long size, int flags) {
        org.lwjgl.opengl.GL45C.glBufferStorage(target, size, flags);
    }
    
    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        org.lwjgl.opengl.GL43C.glBindBufferBase(target, index, buffer);
    }
    
    @Override
    public void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        org.lwjgl.opengl.GL32C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    @Override
    public void glDetachShader(int program, int shader) {
        org.lwjgl.opengl.GL32C.glDetachShader(program, shader);
    }
    
    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        org.lwjgl.opengl.GL32C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Override
    public int glGetTexParameteri(int target, int pname) {
        return org.lwjgl.opengl.GL32C.glGetTexParameteri(target, pname);
    }
    
    @Override
    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.OpenGL42 || caps.GL_ARB_shader_image_load_store) {
            org.lwjgl.opengl.GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
        } else {
            org.lwjgl.opengl.EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
        }
    }
    
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
    public void glGenBuffers(int[] buffers) {
        org.lwjgl.opengl.GL43C.glGenBuffers(buffers);
    }
    
    @Override
    public void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        org.lwjgl.opengl.GL43C.glClearBufferSubData(target, internalformat, offset, size, format, type, data);
    }
    
    @Override
    public void glGetProgramiv(int program, int pname, int[] params) {
        org.lwjgl.opengl.GL32C.glGetProgramiv(program, pname, params);
    }
    
    @Override
    public void glDispatchCompute(int workX, int workY, int workZ) {
        org.lwjgl.opengl.GL45C.glDispatchCompute(workX, workY, workZ);
    }
    
    @Override
    public void glMemoryBarrier(int barriers) {
        org.lwjgl.opengl.GL45C.glMemoryBarrier(barriers);
    }
    
    @Override
    public void glDisablei(int target, int index) {
        org.lwjgl.opengl.GL32C.glDisablei(target, index);
    }
    
    @Override
    public void glEnablei(int target, int index) {
        org.lwjgl.opengl.GL32C.glEnablei(target, index);
    }
    
    @Override
    public void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    @Override
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return org.lwjgl.opengl.GL32C.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Override
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        org.lwjgl.opengl.GL32C.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Override
    public int glGenSamplers() {
        return org.lwjgl.opengl.GL33C.glGenSamplers();
    }
    
    @Override
    public void glDeleteSamplers(int sampler) {
        org.lwjgl.opengl.GL33C.glDeleteSamplers(sampler);
    }
    
    @Override
    public void glBindSampler(int unit, int sampler) {
        org.lwjgl.opengl.GL33C.glBindSampler(unit, sampler);
    }
    
    @Override
    public void glBindSamplers(int first, int[] samplers) {
        org.lwjgl.opengl.GL45C.glBindSamplers(first, samplers);
    }
    
    @Override
    public void glSamplerParameteri(int sampler, int pname, int param) {
        org.lwjgl.opengl.GL33C.glSamplerParameteri(sampler, pname, param);
    }
    
    @Override
    public void glSamplerParameterf(int sampler, int pname, float param) {
        org.lwjgl.opengl.GL33C.glSamplerParameterf(sampler, pname, param);
    }
    
    @Override
    public void glSamplerParameteriv(int sampler, int pname, int[] params) {
        org.lwjgl.opengl.GL33C.glSamplerParameteriv(sampler, pname, params);
    }
    
    @Override
    public int glGetInteger(int pname) {
        return org.lwjgl.opengl.GL32C.glGetInteger(pname);
    }
    
    @Override
    public void glDeleteBuffers(int buffer) {
        org.lwjgl.opengl.GL43C.glDeleteBuffers(buffer);
    }
    
    @Override
    public void glPolygonMode(int face, int mode) {
        org.lwjgl.opengl.GL43C.glPolygonMode(face, mode);
    }
    
    @Override
    public void glDispatchComputeIndirect(long offset) {
        org.lwjgl.opengl.GL43C.glDispatchComputeIndirect(offset);
    }
    
    @Override
    public void glBindBuffer(int target, int buffer) {
        org.lwjgl.opengl.GL46C.glBindBuffer(target, buffer);
    }
    
    @Override
    public String glGetStringi(int name, int index) {
        return org.lwjgl.opengl.GL46C.glGetStringi(name, index);
    }
    
    @Override
    public void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth) {
        org.lwjgl.opengl.GL46C.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel, dstX, dstY, dstZ, width, height, depth);
    }
    
    @Override
    public int glCheckFramebufferStatus(int target) {
        return org.lwjgl.opengl.GL46C.glCheckFramebufferStatus(target);
    }
    
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        org.lwjgl.opengl.GL46C.glUniformMatrix3fv(location, transpose, value);
    }
    
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
        org.lwjgl.opengl.GL46C.glUniformMatrix3fv(location, transpose, value);
    }
    
    @Override
    public void glClearColor(float r, float g, float b, float a) {
        org.lwjgl.opengl.GL46C.glClearColor(r, g, b, a);
    }
    
    @Override
    public int glGetAttribLocation(int program, String name) {
        return org.lwjgl.opengl.GL46C.glGetAttribLocation(program, name);
    }
    
    @Override
    public void glGenerateMipmap(int target) {
        org.lwjgl.opengl.GL32C.glGenerateMipmap(target);
    }
    
    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        org.lwjgl.opengl.GL32C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    // DSA methods
    
    @Override
    public void glGenerateTextureMipmap(int texture) {
        org.lwjgl.opengl.ARBDirectStateAccess.glGenerateTextureMipmap(texture);
    }
    
    @Override
    public void glTextureParameteri(int texture, int pname, int param) {
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }
    
    @Override
    public void glTextureParameterf(int texture, int pname, float param) {
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }
    
    @Override
    public void glTextureParameteriv(int texture, int pname, int[] params) {
        org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteriv(texture, pname, params);
    }
    
    @Override
    public void glNamedFramebufferReadBuffer(int framebuffer, int mode) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, mode);
    }
    
    @Override
    public void glNamedFramebufferDrawBuffers(int framebuffer, int[] bufs) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }
    
    @Override
    public void glClearNamedFramebufferfv(int framebuffer, int buffer, int drawbuffer, float[] value) {
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Override
    public void glClearNamedFramebufferiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Override
    public void glClearNamedFramebufferuiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Override
    public int glGetTextureParameteri(int texture, int pname) {
        return org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
    }
    
    @Override
    public void glCopyTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        org.lwjgl.opengl.ARBDirectStateAccess.glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Override
    public void glBindTextureUnit(int unit, int texture) {
        org.lwjgl.opengl.ARBDirectStateAccess.glBindTextureUnit(unit, texture);
    }
    
    @Override
    public int glCreateBuffers() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers();
    }
    
    @Override
    public void glNamedBufferData(int buffer, float[] data, int usage) {
        org.lwjgl.opengl.GL45C.glNamedBufferData(buffer, data, usage);
    }
    
    @Override
    public void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Override
    public void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Override
    public int glCreateFramebuffers() {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
    }
    
    @Override
    public int glCreateTextures(int target) {
        return org.lwjgl.opengl.ARBDirectStateAccess.glCreateTextures(target);
    }
    
    // Additional rendering operations
    @Override
    public void glDrawElements(int mode, int count, int type, long indices) {
        org.lwjgl.opengl.GL32.glDrawElements(mode, count, type, indices);
    }
    
    @Override
    public void glBlendEquation(int mode) {
        org.lwjgl.opengl.GL32.glBlendEquation(mode);
    }
    
    @Override
    public void glClearDepth(double depth) {
        org.lwjgl.opengl.GL32.glClearDepth(depth);
    }
    
    @Override
    public int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return org.lwjgl.opengl.GL32.glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }
    
    @Override
    public void glDebugMessageControl(int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.GL43C.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Override
    public void glDebugMessageControlKHR(int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.KHRDebug.glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Override
    public void glDebugMessageControlARB(int source, int type, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.ARBDebugOutput.glDebugMessageControlARB(source, type, severity, ids, enabled);
    }
    
    @Override
    public void glDebugMessageEnableAMD(int category, int severity, int[] ids, boolean enabled) {
        org.lwjgl.opengl.AMDDebugOutput.glDebugMessageEnableAMD(category, severity, ids, enabled);
    }
    
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
}
