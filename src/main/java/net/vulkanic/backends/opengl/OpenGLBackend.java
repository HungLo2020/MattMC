package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.vulkanic.GraphicsBackend;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * OpenGL implementation of the Vulkanic Graphics Backend.
 * This is the ONLY place where direct OpenGL calls should be made.
 */
public class OpenGLBackend implements GraphicsBackend {
    
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
    public int querySyncStatus(long sync, int pname, java.nio.IntBuffer values) {
        return org.lwjgl.opengl.GL32C.glGetSynci(sync, pname, values);
    }
    
    @Override
    public org.lwjgl.opengl.GLCapabilities obtainGraphicsCapabilities() {
        return org.lwjgl.opengl.GL.getCapabilities();
    }
}
