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
}
