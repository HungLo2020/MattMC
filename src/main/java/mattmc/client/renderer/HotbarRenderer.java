package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.world.entity.player.LocalPlayer;

/**
 * Backend-agnostic hotbar renderer.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (the hotbar)
 * and coordinating with the backend to actually render it. It contains NO OpenGL-specific
 * code and works purely through the {@link RenderBackend} abstraction.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Contains no graphics API-specific code (OpenGL, Vulkan, etc.)</li>
 * </ul>
 * 
 * <p><b>Abstraction Layer:</b> This class lives outside the backend/ directory
 * and can be safely used by any code in the application. It depends only on the
 * backend interface, not on any specific implementation.
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
     * <p>This method is completely backend-agnostic. It builds draw commands
     * describing what to draw and submits them to the backend for rendering.
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
