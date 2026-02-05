package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.vulkanic.GraphicsBackend;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * OpenGL implementation of the Vulkanic Graphics Backend.
 * Provides 1:1 mappings to OpenGL functions with proper state tracking.
 */
public class OpenGLBackend implements GraphicsBackend {
    
    @Override
    public void bindTexture(int textureId) {
        // Use state tracking from GlStateManager to avoid redundant binds
        // This is critical for proper texture unit management
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
}
