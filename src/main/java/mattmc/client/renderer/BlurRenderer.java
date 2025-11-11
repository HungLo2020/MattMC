package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Helper class for rendering blurred backgrounds in overlay screens.
 */
public final class BlurRenderer {
    
    // Private constructor to prevent instantiation
    private BlurRenderer() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Captures the current screen, applies blur effect, and renders it as the background.
     * @param blurEffect The blur effect to use (should be initialized by caller)
     * @param width Screen width
     * @param height Screen height
     */
    public static void renderBlurredBackground(BlurEffect blurEffect, int width, int height) {
        // Save current GL state
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        
        int captureTexture = glGenTextures();
        try {
            // Capture screen to texture
            glBindTexture(GL_TEXTURE_2D, captureTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            
            // Apply blur
            Framebuffer blurredResult = blurEffect.applyBlur(captureTexture, width, height);
            
            // Render blurred result as background
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, 1, 1, 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, blurredResult.getTextureId());
            glColor4f(1f, 1f, 1f, 1f);
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(0, 0);
            glTexCoord2f(1, 1); glVertex2f(1, 0);
            glTexCoord2f(1, 0); glVertex2f(1, 1);
            glTexCoord2f(0, 0); glVertex2f(0, 1);
            glEnd();
            
            // Unbind texture before disabling GL_TEXTURE_2D
            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
            
            // Restore viewport
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        } finally {
            // Ensure texture is cleaned up even if an exception occurs
            glDeleteTextures(captureTexture);
        }
    }
}
