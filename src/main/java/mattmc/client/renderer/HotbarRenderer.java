package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.opengl.UIRenderHelper;
import mattmc.world.entity.player.LocalPlayer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Hotbar renderer coordinator.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (the hotbar)
 * and coordinating with the backend to actually render it.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Handles 2D projection setup (OpenGL infrastructure code)</li>
 * </ul>
 * 
 * <p><b>Note:</b> While this class lives outside backend/opengl/, it does use
 * {@link UIRenderHelper} for 2D projection setup, which is OpenGL-specific infrastructure.
 * This is necessary infrastructure code for UI rendering that would need to be abstracted
 * further for true multi-backend support.
 */
public class HotbarRenderer {
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend;
    
    // Selected hotbar slot (0-8 for slots 1-9)
    private int selectedHotbarSlot = 0;
    
    /**
     * Create a new hotbar renderer.
     */
    public HotbarRenderer() {
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
     * Render the hotbar at the bottom center of the screen.
     * 
     * <p>This method coordinates the rendering process by setting up the 2D projection,
     * building draw commands, and submitting them to the backend.
     * 
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param player The player whose inventory to display
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight, LocalPlayer player) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Setup 2D projection for UI rendering
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Enable blending for texture rendering
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Synchronize selected slot from player inventory
        if (player != null && player.getInventory() != null) {
            selectedHotbarSlot = player.getInventory().getSelectedSlot();
        }
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        UIRenderLogic.clearTextRegistry(); // Clear text registry for this frame
        logic.buildHotbarCommands(screenWidth, screenHeight, selectedHotbarSlot, buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection
        glDisable(GL_BLEND);
        UIRenderHelper.restore2DProjection();
        
        // TODO: Item rendering in hotbar slots needs to be refactored to use backend
        // For now, this is commented out to maintain backend-agnostic nature
        // The item rendering logic should also be moved to UIRenderLogic in the future
    }
    
    /**
     * Get the selected hotbar slot (0-8 for slots 1-9).
     */
    public int getSelectedHotbarSlot() {
        return selectedHotbarSlot;
    }
    
    /**
     * Set the selected hotbar slot (0-8 for slots 1-9).
     */
    public void setSelectedHotbarSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            selectedHotbarSlot = slot;
        }
    }
    
}
