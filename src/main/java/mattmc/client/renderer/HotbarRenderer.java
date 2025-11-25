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
 *   <li>Delegates projection setup to the backend (backend-agnostic)</li>
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
     * <p>This method is completely backend-agnostic. It delegates projection setup,
     * builds draw commands, and submits them to the backend for rendering.
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
        
        // Setup 2D projection for UI rendering (delegated to backend)
        backend.setup2DProjection(screenWidth, screenHeight);
        
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
        
        // Render items in hotbar slots (within the frame)
        renderHotbarItems(screenWidth, screenHeight, player);
        
        backend.endFrame();
        
        // Restore projection (delegated to backend)
        backend.restore2DProjection();
    }
    
    /**
     * Render items in hotbar slots.
     * This is a temporary solution using ItemRenderer from backend/opengl.
     * TODO: Refactor to use backend-agnostic approach with UIRenderLogic
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param player The player whose inventory to display
     */
    private void renderHotbarItems(int screenWidth, int screenHeight, LocalPlayer player) {
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        
        // Constants from original implementation
        final float HOTBAR_SCALE = 3.0f;
        
        // Calculate hotbar position
        float texWidth = 182 * HOTBAR_SCALE;
        float texHeight = 22 * HOTBAR_SCALE;
        float hotbarX = (screenWidth - texWidth) / 2f;
        float hotbarY = screenHeight - texHeight - 10;
        
        // Item size
        float itemSize = 24f;
        
        // Draw each item in the hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            mattmc.world.item.ItemStack stack = inventory.getStack(i);
            if (stack != null && stack.getItem() != null) {
                // Calculate position for this slot
                float slotSpacing = 20f * HOTBAR_SCALE;
                float slotStartX = hotbarX + 3f * HOTBAR_SCALE;
                float slotStartY = hotbarY + 3f * HOTBAR_SCALE;
                
                float slotX = slotStartX + i * slotSpacing;
                float itemX = slotX + 8f * HOTBAR_SCALE;
                float itemY = slotStartY + 9f * HOTBAR_SCALE;
                
                // Render item using backend
                mattmc.client.renderer.backend.opengl.ItemRenderer.render(stack, itemX, itemY, itemSize, backend);
                
                // Draw item count in bottom-right of slot if > 1
                if (stack.getCount() > 1) {
                    String countText = String.valueOf(stack.getCount());
                    float countX = slotX + slotSpacing - 20;
                    float countY = slotStartY + 18f * HOTBAR_SCALE - 15;
                    // Use backend for text rendering (API-agnostic)
                    backend.setColor(0xFFFFFF, 1.0f);
                    backend.drawText(countText, countX, countY, 1.0f);
                    backend.resetColor();
                }
            }
        }
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
