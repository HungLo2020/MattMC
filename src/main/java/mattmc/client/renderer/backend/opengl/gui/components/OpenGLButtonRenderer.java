package mattmc.client.renderer.backend.opengl.gui.components;

import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.opengl.Texture;

import static org.lwjgl.opengl.GL11.*;

/**
 * OpenGL-specific button rendering implementation.
 * 
 * <p><b>INTERNAL USE ONLY:</b> This class is part of the OpenGL backend implementation
 * and should NOT be used directly by code outside the backend/opengl package.
 * Use {@link mattmc.client.renderer.gui.components.ButtonRenderer} instead for
 * API-agnostic button rendering, or use the {@link mattmc.client.renderer.backend.RenderBackend}
 * interface methods directly.
 * 
 * <p>This class renders buttons using the buttonswide.png texture which contains
 * 3 vertically stacked button states of equal height:
 * <ul>
 *   <li>Top third: clicked state</li>
 *   <li>Middle third: regular state</li>
 *   <li>Bottom third: highlighted/hover state</li>
 * </ul>
 * 
 * @since OpenGL Backend - Internal Implementation
 * @see mattmc.client.renderer.gui.components.ButtonRenderer API-agnostic wrapper
 */
public final class OpenGLButtonRenderer {
    private static Texture buttonTexture;
    private static final String TEXTURE_PATH = "/assets/textures/gui/sprites/buttonswide.png";
    
    /**
     * Loads the button texture if not already loaded (thread-safe).
     */
    public static synchronized void ensureTextureLoaded() {
        if (buttonTexture == null) {
            buttonTexture = Texture.load(TEXTURE_PATH);
        }
    }
    
    /**
     * Renders a button with the appropriate texture state based on hover.
     * 
     * @param b The button to render
     * @param clicked Whether the button is currently clicked
     */
    public static void drawButton(Button b, boolean clicked) {
        ensureTextureLoaded();
        
        // Determine which third of the texture to use
        // Top = clicked, Middle = regular, Bottom = hover
        float texYOffset;
        if (clicked) {
            texYOffset = 0.0f; // Top third
        } else if (b.hover()) {
            texYOffset = 2.0f / 3.0f; // Bottom third
        } else {
            texYOffset = 1.0f / 3.0f; // Middle third
        }
        
        // Enable texturing and blending
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        buttonTexture.bind();
        glColor4f(1f, 1f, 1f, 1f);
        
        // Draw the button using the appropriate third of the texture
        glBegin(GL_QUADS);
        glTexCoord2f(0, texYOffset); 
        glVertex2f(b.x, b.y);
        
        glTexCoord2f(1, texYOffset); 
        glVertex2f(b.x + b.w, b.y);
        
        glTexCoord2f(1, texYOffset + 1.0f / 3.0f); 
        glVertex2f(b.x + b.w, b.y + b.h);
        
        glTexCoord2f(0, texYOffset + 1.0f / 3.0f); 
        glVertex2f(b.x, b.y + b.h);
        glEnd();
        
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
    }
    
    /**
     * Renders a button with the appropriate texture state (no clicked state).
     * 
     * @param b The button to render
     */
    public static void drawButton(Button b) {
        drawButton(b, false);
    }
    
    /**
     * Cleans up resources when done (thread-safe).
     */
    public static synchronized void cleanup() {
        if (buttonTexture != null) {
            buttonTexture.close();
            buttonTexture = null;
        }
    }
}
