package mattmc.client.renderer.gui.components;

import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.RenderBackend;

/**
 * API-agnostic button rendering utility.
 * 
 * <p>This class provides button rendering capabilities without any direct OpenGL calls.
 * All rendering is delegated to the {@link RenderBackend} interface, making this class
 * compatible with any rendering backend (OpenGL, Vulkan, debug/test backends, etc.).
 * 
 * <p>This class can be used by any code in the application, including code outside
 * the renderer/ directory, without violating the API-agnostic rendering paradigm.
 * 
 * <p>The button appearance changes based on its hover and selected/clicked state.
 * The visual representation is determined by the backend implementation.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * ButtonRenderer buttonRenderer = new ButtonRenderer(backend);
 * buttonRenderer.drawButton(button);
 * buttonRenderer.drawButton(button, true);  // selected/clicked
 * </pre>
 * 
 * @since Rendering refactor - Stage 4 (GUI Components)
 */
public final class ButtonRenderer {
    
    private final RenderBackend backend;
    
    /**
     * Create a new button renderer.
     * 
     * @param backend the render backend to use for rendering
     * @throws IllegalArgumentException if backend is null
     */
    public ButtonRenderer(RenderBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("RenderBackend cannot be null");
        }
        this.backend = backend;
    }
    
    /**
     * Renders a button with the appropriate texture state based on hover.
     * 
     * @param button The button to render
     */
    public void drawButton(Button button) {
        backend.drawButton(button);
    }
    
    /**
     * Renders a button with the appropriate texture state based on hover and selection.
     * 
     * @param button The button to render
     * @param selected Whether the button is currently clicked/selected
     */
    public void drawButton(Button button, boolean selected) {
        backend.drawButton(button, selected);
    }
    
    /**
     * Renders a button and its label text.
     * 
     * <p>This is a convenience method that renders both the button background
     * and the centered text label in a single call.
     * 
     * @param button The button to render
     * @param label The text label to display centered on the button
     * @param textScale Scale for the text (e.g., 1.5f)
     */
    public void drawButtonWithLabel(Button button, String label, float textScale) {
        // Draw button background
        backend.drawButton(button);
        
        // Draw centered text on button
        float centerX = button.x + button.w / 2f;
        float textHeight = backend.getTextHeight(label, textScale);
        float centerY = button.y + (button.h - textHeight) / 2f;
        
        backend.drawCenteredText(label, centerX, centerY, textScale);
    }
    
    /**
     * Renders a button and its label text with specified selection state.
     * 
     * @param button The button to render
     * @param selected Whether the button is currently clicked/selected
     * @param label The text label to display centered on the button
     * @param textScale Scale for the text (e.g., 1.5f)
     */
    public void drawButtonWithLabel(Button button, boolean selected, String label, float textScale) {
        // Draw button background with selection state
        backend.drawButton(button, selected);
        
        // Draw centered text on button
        float centerX = button.x + button.w / 2f;
        float textHeight = backend.getTextHeight(label, textScale);
        float centerY = button.y + (button.h - textHeight) / 2f;
        
        backend.drawCenteredText(label, centerX, centerY, textScale);
    }
}
