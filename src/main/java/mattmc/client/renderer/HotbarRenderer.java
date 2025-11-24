package mattmc.client.renderer;

import mattmc.client.renderer.texture.Texture;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders the hotbar at the bottom center of the screen.
 */
public class HotbarRenderer {
    
    private static final float HOTBAR_SCALE = 3.0f; // Scale factor for hotbar rendering
    
    // Hotbar texture
    private Texture hotbarTexture;
    private Texture hotbarSelectionTexture;
    
    // Selected hotbar slot (0-8 for slots 1-9)
    private int selectedHotbarSlot = 0;
    
    // Backend support (Stage 4)
    private RenderBackend backend = null;
    
    /**
     * Draw hotbar at the bottom center of the screen.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param player The player whose inventory to display
     */
    public void render(int screenWidth, int screenHeight, mattmc.world.entity.player.LocalPlayer player) {
        // Load hotbar texture if not already loaded
        if (hotbarTexture == null) {
            hotbarTexture = Texture.load("/assets/textures/gui/sprites/hud/hotbar.png");
        }
        
        // Load hotbar selection texture if not already loaded
        if (hotbarSelectionTexture == null) {
            hotbarSelectionTexture = Texture.load("/assets/textures/gui/sprites/hud/hotbar_selection.png");
        }
        
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Enable blending for texture rendering
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw hotbar texture centered at bottom of screen
        float hotbarX = 0f;
        float hotbarY = 0f;
        
        if (hotbarTexture != null) {
            glEnable(GL_TEXTURE_2D);
            hotbarTexture.bind();
            glColor4f(1f, 1f, 1f, 1f);
            
            // Scale hotbar to 3x size for better visibility (like Minecraft GUI scale)
            float texWidth = hotbarTexture.width * HOTBAR_SCALE;
            float texHeight = hotbarTexture.height * HOTBAR_SCALE;
            hotbarX = (screenWidth - texWidth) / 2f;
            hotbarY = screenHeight - texHeight - 10; // 10 pixels from bottom
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(hotbarX, hotbarY);
            glTexCoord2f(1, 1); glVertex2f(hotbarX + texWidth, hotbarY);
            glTexCoord2f(1, 0); glVertex2f(hotbarX + texWidth, hotbarY + texHeight);
            glTexCoord2f(0, 0); glVertex2f(hotbarX, hotbarY + texHeight);
            glEnd();
        }
        
        // Draw selection overlay on the selected hotbar slot
        if (hotbarSelectionTexture != null && hotbarTexture != null) {
            hotbarSelectionTexture.bind();
            glColor4f(1f, 1f, 1f, 1f);
            
            // Calculate position for selection overlay
            // The hotbar has 9 slots, so each slot width = (hotbar texture width / 9)
            // The selection overlay is larger than a slot, so center it on the slot
            float slotWidth = (hotbarTexture.width * HOTBAR_SCALE) / 9f;
            float selectionWidth = hotbarSelectionTexture.width * HOTBAR_SCALE;
            float selectionHeight = hotbarSelectionTexture.height * HOTBAR_SCALE;
            
            // Center the selection overlay on the slot by offsetting by half the difference
            float centerOffset = (selectionWidth - slotWidth) / 2f;
            float selectionX = hotbarX + (selectedHotbarSlot * slotWidth) - centerOffset;
            float selectionY = hotbarY - (1 * HOTBAR_SCALE); // Move up by 1 PNG pixel (3 screen pixels due to 3x scale)
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(selectionX, selectionY);
            glTexCoord2f(1, 1); glVertex2f(selectionX + selectionWidth, selectionY);
            glTexCoord2f(1, 0); glVertex2f(selectionX + selectionWidth, selectionY + selectionHeight);
            glTexCoord2f(0, 0); glVertex2f(selectionX, selectionY + selectionHeight);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
        }
        
        // Draw items in hotbar slots
        if (player != null && player.getInventory() != null) {
            mattmc.world.item.Inventory inventory = player.getInventory();
            
            // Synchronize selected slot between HotbarRenderer and player inventory
            selectedHotbarSlot = inventory.getSelectedSlot();
            
            // Item size: 16 pixels * HOTBAR_SCALE = 48 pixels, half = 24 pixels
            float itemSize = 24f;
            
            // Draw each item in the hotbar (slots 0-8)
            for (int i = 0; i < 9; i++) {
                mattmc.world.item.ItemStack stack = inventory.getStack(i);
                if (stack != null && stack.getItem() != null) {
                    // Calculate position for this slot
                    // Hotbar texture is 182 pixels wide in PNG, divided into 9 slots
                    // Each slot is approximately 20.22 pixels wide in PNG
                    // Slot spacing is 20 pixels per slot
                    float slotSpacing = 20f * HOTBAR_SCALE;
                    
                    // Position at slot top-left
                    // First slot starts at x=3 in the hotbar texture (3 pixel border)
                    float slotStartX = hotbarX + 3f * HOTBAR_SCALE;
                    float slotStartY = hotbarY + 3f * HOTBAR_SCALE; // 3 pixel border at top too
                    
                    float slotX = slotStartX + i * slotSpacing;
                    // Slots in hotbar are 18x18 pixels, items are 16x16, move left 1 pixel from center
                    float itemX = slotX + 8f * HOTBAR_SCALE;
                    float itemY = slotStartY + 9f * HOTBAR_SCALE;
                    
                    // Use backend if available (Stage 4), otherwise legacy rendering
                    if (backend != null) {
                        ItemRenderer.render(stack, itemX, itemY, itemSize, backend);
                    } else {
                        // Use data-driven rendering with GUI context (legacy)
                        ItemRenderer.renderItemWithTransform(
                            stack, 
                            mattmc.client.renderer.item.ItemDisplayContext.GUI, 
                            itemX, 
                            itemY, 
                            itemSize
                        );
                    }
                    
                    // Draw item count in bottom-right of slot if > 1
                    if (stack.getCount() > 1) {
                        String countText = String.valueOf(stack.getCount());
                        float countX = slotX + slotSpacing - 20; // Bottom-right corner
                        float countY = slotStartY + 18f * HOTBAR_SCALE - 15;
                        UIRenderHelper.drawText(countText, countX, countY, 1.0f, 0xFFFFFF);
                    }
                }
            }
        }
        
        glDisable(GL_BLEND);
        
        UIRenderHelper.restore2DProjection();
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
    
    /**
     * Set the render backend to use for rendering (Stage 4).
     * When set, items will be rendered via the backend architecture.
     * 
     * @param backend the backend to use, or null to use legacy rendering
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
    }
}
