package mattmc.client.renderer.gui.components;

import mattmc.client.renderer.backend.RenderBackend;

/**
 * API-agnostic text rendering utility.
 * 
 * <p>This class provides text rendering capabilities without any direct OpenGL calls.
 * All rendering is delegated to the {@link RenderBackend} interface, making this class
 * compatible with any rendering backend (OpenGL, Vulkan, debug/test backends, etc.).
 * 
 * <p>This class can be used by any code in the application, including code outside
 * the renderer/ directory, without violating the API-agnostic rendering paradigm.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * TextRenderer textRenderer = new TextRenderer(backend);
 * textRenderer.drawText("Hello World", 100, 200, 1.5f);
 * </pre>
 * 
 * @since Rendering refactor - Stage 4 (GUI Components)
 */
public final class TextRenderer {
    
    private final RenderBackend backend;
    
    /**
     * Create a new text renderer.
     * 
     * @param backend the render backend to use for rendering
     * @throws IllegalArgumentException if backend is null
     */
    public TextRenderer(RenderBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("RenderBackend cannot be null");
        }
        this.backend = backend;
    }
    
    /**
     * Render text at a specific position with scale.
     * 
     * @param text Text to render
     * @param x X position (left)
     * @param y Y position (top)
     * @param scale Text scale
     */
    public void drawText(String text, float x, float y, float scale) {
        backend.drawText(text, x, y, scale);
    }
    
    /**
     * Render text centered horizontally at a position.
     * 
     * @param text Text to render
     * @param centerX Center X position
     * @param y Y position
     * @param scale Text scale
     */
    public void drawCenteredText(String text, float centerX, float y, float scale) {
        backend.drawCenteredText(text, centerX, y, scale);
    }
    
    /**
     * Get the width of text at a given scale.
     * 
     * @param text Text to measure
     * @param scale Text scale
     * @return Width in pixels
     */
    public float getTextWidth(String text, float scale) {
        return backend.getTextWidth(text, scale);
    }
    
    /**
     * Get the height of text at a given scale.
     * 
     * @param text Text to measure
     * @param scale Text scale
     * @return Height in pixels
     */
    public float getTextHeight(String text, float scale) {
        return backend.getTextHeight(text, scale);
    }
    
    /**
     * Render text with a specific color.
     * Sets the color, draws the text, then resets the color to white.
     * 
     * @param text Text to render
     * @param x X position (left)
     * @param y Y position (top)
     * @param scale Text scale
     * @param rgb Color in 0xRRGGBB format
     */
    public void drawText(String text, float x, float y, float scale, int rgb) {
        backend.setColor(rgb, 1.0f);
        backend.drawText(text, x, y, scale);
        backend.resetColor();
    }
    
    /**
     * Render text centered horizontally with a specific color.
     * 
     * @param text Text to render
     * @param centerX Center X position
     * @param y Y position
     * @param scale Text scale
     * @param rgb Color in 0xRRGGBB format
     */
    public void drawCenteredText(String text, float centerX, float y, float scale, int rgb) {
        backend.setColor(rgb, 1.0f);
        backend.drawCenteredText(text, centerX, y, scale);
        backend.resetColor();
    }
    
    /**
     * Render text right-aligned at a specific position.
     * 
     * @param text Text to render
     * @param rightX Right X position (where the text ends)
     * @param y Y position
     * @param scale Text scale
     */
    public void drawTextRightAligned(String text, float rightX, float y, float scale) {
        float textWidth = backend.getTextWidth(text, scale);
        backend.drawText(text, rightX - textWidth, y, scale);
    }
    
    /**
     * Render text right-aligned with a specific color.
     * 
     * @param text Text to render
     * @param rightX Right X position (where the text ends)
     * @param y Y position
     * @param scale Text scale
     * @param rgb Color in 0xRRGGBB format
     */
    public void drawTextRightAligned(String text, float rightX, float y, float scale, int rgb) {
        float textWidth = backend.getTextWidth(text, scale);
        backend.setColor(rgb, 1.0f);
        backend.drawText(text, rightX - textWidth, y, scale);
        backend.resetColor();
    }
}
