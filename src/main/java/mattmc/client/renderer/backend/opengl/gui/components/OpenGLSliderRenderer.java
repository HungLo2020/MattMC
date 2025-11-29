package mattmc.client.renderer.backend.opengl.gui.components;

import mattmc.client.gui.components.SliderButton;
import mattmc.client.renderer.backend.opengl.Texture;

import static org.lwjgl.opengl.GL11.*;

/**
 * OpenGL-specific slider rendering implementation.
 * 
 * <p><b>INTERNAL USE ONLY:</b> This class is part of the OpenGL backend implementation
 * and should NOT be used directly by code outside the backend/opengl package.
 * Use the {@link mattmc.client.renderer.backend.RenderBackend} interface methods instead.
 * 
 * <p>This class renders sliders using the same buttonswide.png texture as buttons,
 * but adds a handle indicator for the current value position.
 */
public final class OpenGLSliderRenderer {
    
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
     * Renders a slider widget.
     * 
     * @param slider The slider to render
     */
    public static void drawSlider(SliderButton slider) {
        ensureTextureLoaded();
        
        int x = slider.getX();
        int y = slider.getY();
        int w = slider.getWidth();
        int h = slider.getHeight();
        float value = slider.getValue();
        boolean hover = slider.isHover();
        boolean dragging = slider.isDragging();
        
        // Determine texture Y offset for the track
        // Middle = regular, Bottom = hover/dragging
        float texYOffset = (hover || dragging) ? 2.0f / 3.0f : 1.0f / 3.0f;
        
        // Enable texturing and blending
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        buttonTexture.bind();
        
        // Draw the slider track (slightly dimmer to show it's a slider)
        glColor4f(0.7f, 0.7f, 0.7f, 1f);
        
        glBegin(GL_QUADS);
        glTexCoord2f(0, texYOffset); 
        glVertex2f(x, y);
        
        glTexCoord2f(1, texYOffset); 
        glVertex2f(x + w, y);
        
        glTexCoord2f(1, texYOffset + 1.0f / 3.0f); 
        glVertex2f(x + w, y + h);
        
        glTexCoord2f(0, texYOffset + 1.0f / 3.0f); 
        glVertex2f(x, y + h);
        glEnd();
        
        // Draw the handle (a small section at the current value position)
        int handleWidth = 8;
        int handleX = (int) (x + (w - handleWidth) * value);
        
        // Handle uses highlighted texture
        float handleTexYOffset = (hover || dragging) ? 0.0f : 2.0f / 3.0f;
        glColor4f(1f, 1f, 1f, 1f);
        
        glBegin(GL_QUADS);
        glTexCoord2f(0.48f, handleTexYOffset); 
        glVertex2f(handleX, y);
        
        glTexCoord2f(0.52f, handleTexYOffset); 
        glVertex2f(handleX + handleWidth, y);
        
        glTexCoord2f(0.52f, handleTexYOffset + 1.0f / 3.0f); 
        glVertex2f(handleX + handleWidth, y + h);
        
        glTexCoord2f(0.48f, handleTexYOffset + 1.0f / 3.0f); 
        glVertex2f(handleX, y + h);
        glEnd();
        
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
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
