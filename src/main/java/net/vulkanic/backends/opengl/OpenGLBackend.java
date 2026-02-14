package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.vulkanic.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * OpenGL implementation of the Vulkanic Graphics Backend.
 * This is the ONLY place where direct OpenGL calls should be made.
 */
public class OpenGLBackend implements GraphicsBackend {
    
    // Pipeline manager for bridging stateful API to pipeline-based API
    private final PipelineManager pipelineManager = new PipelineManager();
    
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
    public void clear(int mask) {
        GL11.glClear(mask);
    }
    
    @Deprecated
    @Override
    public void useProgram(int programId) {
        // Update pipeline manager state (Vulkan-compatible path)
        pipelineManager.setShader(ShaderStage.VERTEX, programId);
        pipelineManager.setShader(ShaderStage.FRAGMENT, programId);
        
        // Also apply directly for immediate effect (OpenGL path)
        GL20.glUseProgram(programId);
    }
    
    @Deprecated
    @Override
    public void enable(int cap) {
        // Update pipeline manager state (Vulkan-compatible path)
        updatePipelineManagerForCapability(cap, true);
        
        // Also apply directly for immediate effect (OpenGL path)
        GL11.glEnable(cap);
    }
    
    @Deprecated
    @Override
    public void disable(int cap) {
        // Update pipeline manager state (Vulkan-compatible path)
        updatePipelineManagerForCapability(cap, false);
        
        // Also apply directly for immediate effect (OpenGL path)
        GL11.glDisable(cap);
    }
    
    /**
     * Updates PipelineManager state based on GL capability enable/disable
     */
    private void updatePipelineManagerForCapability(int cap, boolean enabled) {
        switch (cap) {
            case GL11.GL_BLEND:
                pipelineManager.setBlendMode(enabled ? BlendMode.ALPHA_BLEND : BlendMode.NONE);
                break;
            case GL11.GL_DEPTH_TEST:
                pipelineManager.setDepthTest(enabled, CompareOp.LESS);
                break;
            case GL11.GL_CULL_FACE:
                pipelineManager.setCullMode(enabled ? CullMode.BACK : CullMode.NONE);
                break;
            // Other capabilities don't map to pipeline state
        }
    }
    
    @Deprecated
    @Override
    public void setDepthTestFunction(int func) {
        // Update pipeline manager state (Vulkan-compatible path)
        CompareOp compareOp = glCompareOpToEnum(func);
        pipelineManager.setDepthTest(true, compareOp);
        
        // Also apply directly for immediate effect (OpenGL path)
        GL11.glDepthFunc(func);
    }
    
    /**
     * Converts GL depth function constant to CompareOp enum
     */
    private CompareOp glCompareOpToEnum(int glFunc) {
        return switch (glFunc) {
            case GL11.GL_NEVER -> CompareOp.NEVER;
            case GL11.GL_LESS -> CompareOp.LESS;
            case GL11.GL_EQUAL -> CompareOp.EQUAL;
            case GL11.GL_LEQUAL -> CompareOp.LESS_EQUAL;
            case GL11.GL_GREATER -> CompareOp.GREATER;
            case GL11.GL_NOTEQUAL -> CompareOp.NOT_EQUAL;
            case GL11.GL_GEQUAL -> CompareOp.GREATER_EQUAL;
            case GL11.GL_ALWAYS -> CompareOp.ALWAYS;
            default -> CompareOp.LESS; // Safe default
        };
    }
    
    @Deprecated
    @Override
    public void setDepthWriteEnabled(boolean enabled) {
        // Update pipeline manager state (Vulkan-compatible path)
        pipelineManager.setDepthWrite(enabled);
        
        // Also apply directly for immediate effect (OpenGL path)
        GL11.glDepthMask(enabled);
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
    
    @Deprecated
    @Override
    public void setPixelStoreMode(int pname, int value) {
        GL11.glPixelStorei(pname, value);
    }
    
    @Deprecated
    @Override
    public void attachFramebuffer(int target, int fbo) {
        GL30.glBindFramebuffer(target, fbo);
    }
    
    @Deprecated
    @Override
    public void attachTextureToFramebuffer(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Deprecated
    @Override
    public void attachBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }
    
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
    public void activateTextureUnit(int unit) {
        org.lwjgl.opengl.GL13.glActiveTexture(unit);
    }
    
    @Deprecated
    @Override
    public void configureTextureParameter(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }
    
    @Deprecated
    @Override
    public int createTexture() {
        return GL11.glGenTextures();
    }
    
    @Deprecated
    @Override
    public void removeTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }
    
    @Deprecated
    @Override
    public void configurePolygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }
    
    @Deprecated
    @Override
    public void configurePolygonOffset(float factor, float units) {
        GL11.glPolygonOffset(factor, units);
    }
    
    @Deprecated
    @Override
    public void configureLogicOp(int opcode) {
        GL11.glLogicOp(opcode);
    }
    
    @Deprecated
    @Override
    public void drawPrimitiveArrays(int mode, int first, int count) {
        // Apply current pipeline state and descriptor bindings before drawing
        applyPipelineState();
        applyDescriptorSetBindings();
        
        GL11.glDrawArrays(mode, first, count);
    }

    @Deprecated
    @Override
    public void drawIndexedElements(int mode, int count, int type, long indices) {
        // Apply current pipeline state and descriptor bindings before drawing
        applyPipelineState();
        applyDescriptorSetBindings();
        
        GL11.glDrawElements(mode, count, type, indices);
    }
    
    @Deprecated
    @Override
    public void configureBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    @Override
    public int checkForErrors() {
        return GL11.glGetError();
    }
    
    @Deprecated
    @Override
    public void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        GL11.glTexImage2D(tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    @Deprecated
    @Override
    public void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix) {
        GL11.glTexSubImage2D(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Deprecated
    @Override
    public void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        GL11.glTexSubImage2D(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Deprecated
    @Override
    public int allocateBufferObject() {
        return GL15.glGenBuffers();
    }
    
    @Deprecated
    @Override
    public void releaseBufferObject(int buf) {
        GL15.glDeleteBuffers(buf);
    }
    
    @Deprecated
    @Override
    public void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg) {
        GL15.glBufferData(tgt, dat, usg);
    }
    
    @Deprecated
    @Override
    public void fillBufferWithSize(int tgt, long sz, int usg) {
        GL15.glBufferData(tgt, sz, usg);
    }
    
    @Deprecated
    @Override
    public void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat) {
        GL15.glBufferSubData(tgt, off, dat);
    }
    
    @Deprecated
    @Override
    public int createVertexArrayObject() {
        return GL30.glGenVertexArrays();
    }
    
    @Deprecated
    @Override
    public void selectVertexArray(int vao) {
        GL30.glBindVertexArray(vao);
    }
    
    @Deprecated
    @Override
    public java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc) {
        return GL30.glMapBufferRange(tgt, off, len, acc);
    }
    
    @Deprecated
    @Override
    public void unmapBufferData(int tgt) {
        GL15.glUnmapBuffer(tgt);
    }
    
    @Deprecated
    @Override
    public int generateFramebufferObject() {
        return GL30.glGenFramebuffers();
    }
    
    @Deprecated
    @Override
    public void destroyFramebufferObject(int fbo) {
        GL30.glDeleteFramebuffers(fbo);
    }
    
    @Deprecated
    @Override
    public void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
    }
    
    @Deprecated
    @Override
    public int constructShaderObject(int shaderType) {
        return GL20.glCreateShader(shaderType);
    }
    
    @Deprecated
    @Override
    public void disposeShaderObject(int shader) {
        GL20.glDeleteShader(shader);
    }
    
    @Deprecated
    @Override
    public void compileShaderSource(int shader) {
        GL20.glCompileShader(shader);
    }
    
    @Deprecated
    @Override
    public int constructProgramObject() {
        return GL20.glCreateProgram();
    }
    
    @Deprecated
    @Override
    public void disposeProgramObject(int program) {
        GL20.glDeleteProgram(program);
    }
    
    @Deprecated
    @Override
    public void linkProgramBinary(int program) {
        GL20.glLinkProgram(program);
    }
    
    @Deprecated
    @Override
    public void attachShaderToProgram(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }
    
    @Deprecated
    @Override
    public int queryProgramParameter(int program, int pname) {
        return GL20.glGetProgrami(program, pname);
    }
    
    @Deprecated
    @Override
    public int queryShaderParameter(int shader, int pname) {
        return GL20.glGetShaderi(shader, pname);
    }
    
    @Deprecated
    @Override
    public void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    @Deprecated
    @Override
    public void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer) {
        org.lwjgl.opengl.GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    @Deprecated
    @Override
    public void activateVertexAttribute(int index) {
        GL20.glEnableVertexAttribArray(index);
    }
    
    @Deprecated
    @Override
    public void deactivateVertexAttribute(int index) {
        GL20.glDisableVertexAttribArray(index);
    }
    
    @Deprecated
    @Override
    public void setVertexAttribDivisor(int index, int divisor) {
        org.lwjgl.opengl.GL33.glVertexAttribDivisor(index, divisor);
    }
    
    @Deprecated
    @Override
    public String retrieveProgramInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program);
    }
    
    @Deprecated
    @Override
    public String retrieveShaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader);
    }
    
    @Deprecated
    @Override
    public int locateUniformVariable(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }
    
    @Deprecated
    @Override
    public void assignUniformInteger(int location, int value) {
        GL20.glUniform1i(location, value);
    }
    
    @Deprecated
    @Override
    public void bindAttributeLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
    }
    
    @Deprecated
    @Override
    public long createFenceSync(int condition, int flags) {
        return org.lwjgl.opengl.GL32.glFenceSync(condition, flags);
    }
    
    @Deprecated
    @Override
    public int waitForSync(long sync, int flags, long timeout) {
        return org.lwjgl.opengl.GL32.glClientWaitSync(sync, flags, timeout);
    }
    
    @Deprecated
    @Override
    public void destroySync(long sync) {
        org.lwjgl.opengl.GL32.glDeleteSync(sync);
    }
    
    @Deprecated
    @Override
    public int queryIntegerState(int pname) {
        return GL11.glGetInteger(pname);
    }
    
    @Deprecated
    @Override
    public String queryStringInfo(int name) {
        return GL11.glGetString(name);
    }
    
    @Deprecated
    @Override
    public int pollErrorCode() {
        return GL11.glGetError();
    }
    
    @Deprecated
    @Override
    public void readFramebufferPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Deprecated
    @Override
    public int queryTextureLevelParameter(int target, int level, int pname) {
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }
    
    @Deprecated
    @Override
    public void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        org.lwjgl.opengl.GL20C.nglShaderSource(shader, stringCount, pointerBufferAddress, lengthsPointer);
    }
    
    @Deprecated
    @Override
    public int locateUniformBlock(int program, String uniformBlockName) {
        return org.lwjgl.opengl.GL31.glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Deprecated
    @Override
    public void bindUniformBlock(int program, int uniformBlockIndex, int uniformBlockBinding) {
        org.lwjgl.opengl.GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Deprecated
    @Override
    public String retrieveActiveUniformBlockName(int program, int uniformBlockIndex) {
        return org.lwjgl.opengl.GL31.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    @Deprecated
    @Override
    public int generateQueryObject() {
        return org.lwjgl.opengl.GL32C.glGenQueries();
    }
    
    @Deprecated
    @Override
    public void initiateQuery(int target, int id) {
        org.lwjgl.opengl.GL32C.glBeginQuery(target, id);
    }
    
    @Deprecated
    @Override
    public void concludeQuery(int target) {
        org.lwjgl.opengl.GL32C.glEndQuery(target);
    }
    
    @Deprecated
    @Override
    public void disposeQueryObject(int id) {
        org.lwjgl.opengl.GL32C.glDeleteQueries(id);
    }
    
    @Deprecated
    @Override
    public int retrieveQueryObjectInt(int id, int pname) {
        return org.lwjgl.opengl.GL32C.glGetQueryObjecti(id, pname);
    }
    
    @Deprecated
    @Override
    public long retrieveQueryObjectInt64(int id, int pname) {
        return org.lwjgl.opengl.ARBTimerQuery.glGetQueryObjecti64(id, pname);
    }
    
    @Deprecated
    @Override
    public void labelDebugObject(int identifier, int name, String label) {
        org.lwjgl.opengl.KHRDebug.glObjectLabel(identifier, name, label);
    }
    
    @Deprecated
    @Override
    public void enterDebugGroup(int source, int id, CharSequence message) {
        org.lwjgl.opengl.KHRDebug.glPushDebugGroup(source, id, message);
    }
    
    @Deprecated
    @Override
    public void exitDebugGroup() {
        org.lwjgl.opengl.KHRDebug.glPopDebugGroup();
    }
    
    @Deprecated
    @Override
    public void labelObjectExt(int type, int object, String label) {
        org.lwjgl.opengl.EXTDebugLabel.glLabelObjectEXT(type, object, label);
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
    public void attachVertexBuffer(int bindingIndex, int buffer, long offset, int stride) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glBindVertexBuffer(bindingIndex, buffer, offset, stride);
    }
    
    @Deprecated
    @Override
    public void specifyVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribFormat(attribIndex, size, type, normalized, relativeOffset);
    }
    
    @Deprecated
    @Override
    public void specifyVertexAttribIFormat(int attribIndex, int size, int type, int relativeOffset) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribIFormat(attribIndex, size, type, relativeOffset);
    }
    
    @Deprecated
    @Override
    public void associateVertexAttrib(int attribIndex, int bindingIndex) {
        org.lwjgl.opengl.ARBVertexAttribBinding.glVertexAttribBinding(attribIndex, bindingIndex);
    }
    
    @Deprecated
    @Override
    public void setClearDepthValue(double depth) {
        org.lwjgl.opengl.GL11.glClearDepth(depth);
    }
    
    @Deprecated
    @Override
    public void setClearColorValue(float red, float green, float blue, float alpha) {
        org.lwjgl.opengl.GL11.glClearColor(red, green, blue, alpha);
    }
    
    @Deprecated
    @Override
    public void selectDrawBuffer(int mode) {
        org.lwjgl.opengl.GL11.glDrawBuffer(mode);
    }
    
    @Deprecated
    @Override
    public void renderIndexedInstancedWithBase(int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        applyPipelineState();
        applyDescriptorSetBindings();
        org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    @Deprecated
    @Override
    public void renderIndexedWithBase(int mode, int count, int type, long indices, int baseVertex) {
        applyPipelineState();
        applyDescriptorSetBindings();
        org.lwjgl.opengl.GL32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }
    
    @Deprecated
    @Override
    public void renderIndexedInstanced(int mode, int count, int type, long indices, int instanceCount) {
        applyPipelineState();
        applyDescriptorSetBindings();
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }
    
    @Deprecated
    @Override
    public void renderArraysInstanced(int mode, int first, int count, int instanceCount) {
        applyPipelineState();
        applyDescriptorSetBindings();
        org.lwjgl.opengl.GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
    
    @Deprecated
    @Override
    public void attachUniformBufferRange(int target, int index, int buffer, long offset, long size) {
        org.lwjgl.opengl.GL32.glBindBufferRange(target, index, buffer, offset, size);
    }
    
    @Deprecated
    @Override
    public void attachBufferToTexture(int target, int internalFormat, int buffer) {
        org.lwjgl.opengl.GL31.glTexBuffer(target, internalFormat, buffer);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat(int location, float value) {
        org.lwjgl.opengl.GL30C.glUniform1f(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat2(int location, float x, float y) {
        org.lwjgl.opengl.GL30C.glUniform2f(location, x, y);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat2v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform2fv(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat3(int location, float x, float y, float z) {
        org.lwjgl.opengl.GL30C.glUniform3f(location, x, y, z);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat3v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform3fv(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat4(int location, float x, float y, float z, float w) {
        org.lwjgl.opengl.GL30C.glUniform4f(location, x, y, z, w);
    }
    
    @Deprecated
    @Override
    public void assignUniformFloat4v(int location, float[] value) {
        org.lwjgl.opengl.GL30C.glUniform4fv(location, value);
    }
    
    @Deprecated
    @Override
    public void assignUniformMatrix4f(int location, java.nio.FloatBuffer matrix) {
        org.lwjgl.opengl.GL30C.glUniformMatrix4fv(location, false, matrix);
    }
    
    @Deprecated
    @Override
    public void bindUniformBufferBase(int bindingPoint, int bufferId) {
        org.lwjgl.opengl.GL32C.glBindBufferBase(org.lwjgl.opengl.GL32C.GL_UNIFORM_BUFFER, bindingPoint, bufferId);
    }
    
    @Deprecated
    @Override
    public void bindFragmentDataLocation(int program, int colorNumber, CharSequence name) {
        org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, colorNumber, name);
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
    
    @Deprecated
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
    
    @Deprecated
    @Override
    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    @Deprecated
    @Override
    public void assignUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        org.lwjgl.opengl.GL20C.glUniformMatrix4fv(location, transpose, value);
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
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        org.lwjgl.opengl.GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
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
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) {
        org.lwjgl.opengl.GL32C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
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
    public void glTexParameteri(int target, int pname, int param) {
        org.lwjgl.opengl.GL32C.glTexParameteri(target, pname, param);
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
    public void glClearBufferfv(int buffer, int drawbuffer, float[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferfv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    @Override
    public void glClearBufferiv(int buffer, int drawbuffer, int[] values) {
        org.lwjgl.opengl.GL32C.glClearBufferiv(buffer, drawbuffer, values);
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
    public void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
        org.lwjgl.opengl.GL32C.glReadPixels(x, y, width, height, format, type, pixels);
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
    public boolean glUnmapBuffer(int target) {
        return org.lwjgl.opengl.GL32C.glUnmapBuffer(target);
    }
    
    @Deprecated
    @Override
    public boolean glIsBuffer(int buffer) {
        return org.lwjgl.opengl.GL32C.glIsBuffer(buffer);
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
    public void glFramebufferTexture(int target, int attachment, int texture, int level) {
        org.lwjgl.opengl.GL32C.glFramebufferTexture(target, attachment, texture, level);
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
    public void glMemoryBarrier(int barriers) {
        org.lwjgl.opengl.GL45C.glMemoryBarrier(barriers);
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
        applyPipelineState();
        applyDescriptorSetBindings();
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
    public Object getGLCapabilities() {
        return org.lwjgl.opengl.GL.getCapabilities();
    }
    
    @Deprecated
    @Override
    public void setupDebugMessageCallback(java.io.PrintStream stream) {
        org.lwjgl.opengl.GLUtil.setupDebugMessageCallback(stream);
    }
    
    // Capability checking methods
    
    @Deprecated
    @Override
    public boolean checkOpenGL32Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL32;
    }
    
    @Deprecated
    @Override
    public boolean checkOpenGL33Support() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.OpenGL33;
    }
    
    @Deprecated
    @Override
    public boolean checkARBInstancedArraysSupport() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.GL_ARB_instanced_arrays;
    }
    
    @Deprecated
    @Override
    public long getNamedBufferDataPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glNamedBufferData;
    }
    
    @Deprecated
    @Override
    public long getBufferStoragePointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBufferStorage;
    }
    
    @Deprecated
    @Override
    public long getBindVertexBufferPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glBindVertexBuffer;
    }
    
    @Deprecated
    @Override
    public long getVertexAttribBindingPointer() {
        org.lwjgl.opengl.GLCapabilities caps = (org.lwjgl.opengl.GLCapabilities) getGLCapabilities();
        return caps.glVertexAttribBinding;
    }
    
    // Additional GL query and state methods
    
    @Deprecated
    @Override
    public boolean glIsEnabled(int cap) {
        return org.lwjgl.opengl.GL11.glIsEnabled(cap);
    }
    
    @Deprecated
    @Override
    public boolean glIsFramebuffer(int framebuffer) {
        return org.lwjgl.opengl.GL30.glIsFramebuffer(framebuffer);
    }
    
    @Deprecated
    @Override
    public boolean glIsTexture(int texture) {
        return org.lwjgl.opengl.GL11.glIsTexture(texture);
    }
    
    @Deprecated
    @Override
    public boolean glIsVertexArray(int array) {
        return org.lwjgl.opengl.GL30.glIsVertexArray(array);
    }
    
    @Deprecated
    @Override
    public boolean glIsProgram(int program) {
        return org.lwjgl.opengl.GL20.glIsProgram(program);
    }
    
    // Additional GL state methods
    
    @Deprecated
    @Override
    public void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        org.lwjgl.opengl.GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }
    
    @Deprecated
    @Override
    public void glStencilFunc(int func, int ref, int mask) {
        org.lwjgl.opengl.GL11.glStencilFunc(func, ref, mask);
    }
    
    @Deprecated
    @Override
    public void glCullFace(int mode) {
        org.lwjgl.opengl.GL11.glCullFace(mode);
    }
    
    // Additional texture methods
    
    @Deprecated
    @Override
    public int glGenTextures() {
        return org.lwjgl.opengl.GL11.glGenTextures();
    }
    
    // ========================================================================
    // NEW VULKAN-COMPATIBLE API IMPLEMENTATION (Phase 1)
    // ========================================================================
    
    // State tracking for new API
    private long nextHandle = 1;  // Simple handle counter
    private GLPipeline currentPipeline;
    private GLDescriptorSet currentDescriptorSet;
    private GLRenderPass currentRenderPass;
    private long currentFramebuffer;
    
    // Pipeline State Objects
    
    @Override
    public Pipeline createPipeline(PipelineStateDesc desc) {
        long handle = nextHandle++;
        return new GLPipeline(handle, desc);
    }
    
    @Override
    public void bindPipeline(CommandBuffer cmd, Pipeline pipeline) {
        // Track current pipeline - state will be applied on draw
        this.currentPipeline = (GLPipeline) pipeline;
    }
    
    @Override
    public void destroyPipeline(Pipeline pipeline) {
        // In OpenGL, nothing to destroy (no compiled pipeline object)
        // Just remove reference if it's current
        if (currentPipeline == pipeline) {
            currentPipeline = null;
        }
    }
    
    // Descriptor Sets
    
    @Override
    public DescriptorSetLayout createDescriptorSetLayout(DescriptorSetLayoutBuilder builder) {
        long handle = nextHandle++;
        return new GLDescriptorSetLayout(handle, builder.getBindings());
    }
    
    @Override
    public DescriptorSet allocateDescriptorSet(DescriptorSetLayout layout) {
        long handle = nextHandle++;
        return new GLDescriptorSet(handle, layout);
    }
    
    @Override
    public void updateDescriptorSetTexture(DescriptorSet set, int binding, Texture texture) {
        GLDescriptorSet glSet = (GLDescriptorSet) set;
        glSet.setBinding(binding, GLDescriptorSet.Binding.Type.TEXTURE, texture.getHandle());
    }
    
    @Override
    public void updateDescriptorSetBuffer(DescriptorSet set, int binding, Buffer buffer) {
        GLDescriptorSet glSet = (GLDescriptorSet) set;
        glSet.setBinding(binding, GLDescriptorSet.Binding.Type.UNIFORM_BUFFER, buffer.getHandle());
    }
    
    @Override
    public void bindDescriptorSet(CommandBuffer cmd, DescriptorSet set, int setIndex) {
        // Track current descriptor set - bindings will be applied on draw
        this.currentDescriptorSet = (GLDescriptorSet) set;
    }
    
    // Render Passes
    
    @Override
    public RenderPass createRenderPass(RenderPassDesc desc) {
        long handle = nextHandle++;
        return new GLRenderPass(handle, desc);
    }
    
    @Override
    public void beginRenderPass(CommandBuffer cmd, RenderPass renderPass, long framebuffer) {
        GLRenderPass glRenderPass = (GLRenderPass) renderPass;
        RenderPassDesc desc = glRenderPass.getDesc();
        
        this.currentRenderPass = glRenderPass;
        this.currentFramebuffer = framebuffer;
        
        // Bind framebuffer
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, (int) framebuffer);
        
        // Apply clear operations
        int clearMask = 0;
        
        if (desc.hasClearColor()) {
            GL11.glClearColor(desc.getClearR(), desc.getClearG(), desc.getClearB(), desc.getClearA());
            clearMask |= GL11.GL_COLOR_BUFFER_BIT;
        }
        
        if (desc.hasClearDepth()) {
            GL11.glClearDepth(desc.getClearDepth());
            clearMask |= GL11.GL_DEPTH_BUFFER_BIT;
        }
        
        if (clearMask != 0) {
            GL11.glClear(clearMask);
        }
    }
    
    @Override
    public void endRenderPass(CommandBuffer cmd) {
        // In OpenGL, just track that we've ended
        // Could unbind framebuffer here, but typically leave bound
        this.currentRenderPass = null;
    }
    
    // Command Buffers
    
    @Override
    public CommandBuffer allocateCommandBuffer() {
        long handle = nextHandle++;
        return new GLCommandBuffer(handle);
    }
    
    @Override
    public void beginCommandBuffer(CommandBuffer cmd) {
        // In OpenGL immediate mode, this is a no-op
        // Just mark as recording for tracking purposes
        if (cmd instanceof GLCommandBuffer) {
            ((GLCommandBuffer) cmd).setRecording(true);
        }
    }
    
    @Override
    public void endCommandBuffer(CommandBuffer cmd) {
        // In OpenGL immediate mode, this is a no-op
        // Just mark as not recording
        if (cmd instanceof GLCommandBuffer) {
            ((GLCommandBuffer) cmd).setRecording(false);
        }
    }
    
    @Override
    public void submitCommandBuffer(CommandBuffer cmd) {
        // In OpenGL immediate mode, this is a no-op
        // Commands have already executed
    }
    
    // Resources
    
    @Override
    public Buffer createBuffer(long size, BufferUsage usage) {
        int glBuffer = GL15.glGenBuffers();
        return new GLBuffer(glBuffer, size, usage);
    }
    
    @Override
    public void destroyBuffer(Buffer buffer) {
        GL15.glDeleteBuffers((int) buffer.getHandle());
    }
    
    @Override
    public Texture createTexture(int width, int height, Format format) {
        int glTexture = GL11.glGenTextures();
        return new GLTexture(glTexture, width, height, format);
    }
    
    @Override
    public void destroyTexture(Texture texture) {
        GL11.glDeleteTextures((int) texture.getHandle());
    }
    
    // Helper method: Apply pipeline state before drawing
    // This will be called from draw methods in future phases
    private void applyPipelineState() {
        if (currentPipeline == null) {
            return;
        }
        
        // Apply blend state
        BlendMode blendMode = currentPipeline.getBlendMode();
        switch (blendMode) {
            case NONE:
                GL11.glDisable(GL11.GL_BLEND);
                break;
            case ALPHA_BLEND:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                break;
            case ADDITIVE:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                break;
            case MULTIPLY:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);
                break;
            case PREMULTIPLIED_ALPHA:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                break;
        }
        
        // Apply depth state
        if (currentPipeline.isDepthTestEnabled()) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(compareOpToGL(currentPipeline.getDepthCompareOp()));
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthMask(currentPipeline.isDepthWriteEnabled());
        
        // Apply cull state
        CullMode cullMode = currentPipeline.getCullMode();
        if (cullMode == CullMode.NONE) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        } else {
            GL11.glEnable(GL11.GL_CULL_FACE);
            switch (cullMode) {
                case FRONT:
                    GL11.glCullFace(GL11.GL_FRONT);
                    break;
                case BACK:
                    GL11.glCullFace(GL11.GL_BACK);
                    break;
                case FRONT_AND_BACK:
                    GL11.glCullFace(GL11.GL_FRONT_AND_BACK);
                    break;
            }
        }
        
        // Apply front face
        GL11.glFrontFace(currentPipeline.isFrontFaceCounterClockwise() ? 
            GL11.GL_CCW : GL11.GL_CW);
        
        // Apply shaders (if any)
        Map<ShaderStage, Long> shaders = currentPipeline.getShaders();
        if (shaders.containsKey(ShaderStage.VERTEX) || shaders.containsKey(ShaderStage.FRAGMENT)) {
            // For now, just track that we have shaders
            // Actual shader binding will be handled in Phase 2
        }
    }
    
    // Helper method: Apply descriptor set bindings before drawing
    private void applyDescriptorSetBindings() {
        if (currentDescriptorSet == null) {
            return;
        }
        
        Map<Integer, GLDescriptorSet.Binding> bindings = currentDescriptorSet.getBindings();
        for (Map.Entry<Integer, GLDescriptorSet.Binding> entry : bindings.entrySet()) {
            int binding = entry.getKey();
            GLDescriptorSet.Binding resource = entry.getValue();
            
            switch (resource.type) {
                case TEXTURE:
                    GL20.glActiveTexture(GL20.GL_TEXTURE0 + binding);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, (int) resource.resourceHandle);
                    break;
                case UNIFORM_BUFFER:
                    // GL_UNIFORM_BUFFER = 0x8A11
                    org.lwjgl.opengl.GL31.glBindBufferBase(0x8A11, binding, (int) resource.resourceHandle);
                    break;
                case STORAGE_BUFFER:
                    // GL_SHADER_STORAGE_BUFFER = 0x90D2
                    org.lwjgl.opengl.GL43.glBindBufferBase(0x90D2, binding, (int) resource.resourceHandle);
                    break;
            }
        }
    }
    
    // Helper: Convert CompareOp to GL constant
    private int compareOpToGL(CompareOp op) {
        switch (op) {
            case NEVER: return GL11.GL_NEVER;
            case LESS: return GL11.GL_LESS;
            case EQUAL: return GL11.GL_EQUAL;
            case LESS_EQUAL: return GL11.GL_LEQUAL;
            case GREATER: return GL11.GL_GREATER;
            case NOT_EQUAL: return GL11.GL_NOTEQUAL;
            case GREATER_EQUAL: return GL11.GL_GEQUAL;
            case ALWAYS: return GL11.GL_ALWAYS;
            default: return GL11.GL_LESS;
        }
    }
}
