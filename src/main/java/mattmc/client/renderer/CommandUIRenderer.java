package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;

/**
 * Backend-agnostic command UI renderer.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (command overlay and feedback)
 * and coordinating with the backend to actually render it. It contains NO OpenGL-specific
 * code and works purely through the {@link RenderBackend} abstraction.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Delegates projection setup to the backend (backend-agnostic)</li>
 * </ul>
 * 
 * <p><b>Abstraction Layer:</b> This class lives outside the backend/ directory
 * and can be safely used by any code in the application. It depends only on the
 * backend interface, not on any specific implementation.
 */
public class CommandUIRenderer {
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend;
    
    /**
     * Create a new command UI renderer.
     */
    public CommandUIRenderer() {
        this.logic = new UIRenderLogic();
        this.buffer = new CommandBuffer();
    }
    
    /**
     * Set the render backend to use for rendering.
     * 
     * @param backend the backend to use (must not be null)
     * @throws IllegalArgumentException if backend is null
     */
    public void setBackend(RenderBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend cannot be null");
        }
        this.backend = backend;
    }
    
    /**
     * Render command overlay at bottom of screen (like Minecraft).
     * Shows command input box with cursor.
     * 
     * <p>This method is completely backend-agnostic. It delegates projection setup,
     * builds draw commands, and submits them to the backend for rendering.
     * 
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param commandText The command text to display
     * @throws IllegalStateException if backend has not been set
     */
    public void renderCommandOverlay(int screenWidth, int screenHeight, String commandText) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Setup 2D projection for UI rendering (delegated to backend)
        backend.setup2DProjection(screenWidth, screenHeight);
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        UIRenderLogic.clearTextRegistry(); // Clear text registry for this frame
        logic.buildCommandOverlayCommands(screenWidth, screenHeight, commandText, buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection (delegated to backend)
        backend.restore2DProjection();
    }
    
    /**
     * Render command feedback message above the hotbar area.
     * This message appears independently of the command input overlay and fades after a few seconds.
     * Similar to Minecraft's action bar messages.
     * 
     * <p>This method is completely backend-agnostic. It delegates projection setup,
     * builds draw commands, and submits them to the backend for rendering.
     * 
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param message The feedback message to display
     * @throws IllegalStateException if backend has not been set
     */
    public void renderCommandFeedback(int screenWidth, int screenHeight, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Setup 2D projection for UI rendering (delegated to backend)
        backend.setup2DProjection(screenWidth, screenHeight);
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        UIRenderLogic.clearTextRegistry(); // Clear text registry for this frame
        logic.buildCommandFeedbackCommands(screenWidth, screenHeight, message, buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection (delegated to backend)
        backend.restore2DProjection();
    }
}
