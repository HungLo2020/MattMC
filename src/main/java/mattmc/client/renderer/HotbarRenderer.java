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
            
            // Draw each item in the hotbar (slots 0-8)
            for (int i = 0; i < 9; i++) {
                mattmc.world.item.ItemStack stack = inventory.getStack(i);
                if (stack != null && stack.getItem() != null) {
                    // Calculate position for this slot
                    float slotWidth = (hotbarTexture.width * HOTBAR_SCALE) / 9f;
                    float itemX = hotbarX + (i * slotWidth) + (slotWidth / 2f); // Center item in slot horizontally
                    
                    // Calculate vertical center of the actual slot (not just hotbar texture)
                    // Hotbar slots have padding/border, so the slot center is below the texture center
                    float hotbarCenterY = hotbarY + (hotbarTexture.height * HOTBAR_SCALE / 2f);
                    float slotCenterOffset = 19f; // Offset to center in the actual slot area
                    float itemY = hotbarCenterY + slotCenterOffset;
                    
                    // Render the item using ItemRenderer (increased size: 16 * 1.2 * 1.1 = 21.12)
                    // Use data-driven rendering with GUI context
                    float itemSize = 21.12f;
                    ItemRenderer.renderItemWithTransform(
                        stack, 
                        mattmc.client.renderer.item.ItemDisplayContext.GUI, 
                        itemX, 
                        itemY, 
                        itemSize
                    );
                    
                    // Draw item count in bottom-right of slot if > 1
                    if (stack.getCount() > 1) {
                        String countText = String.valueOf(stack.getCount());
                        float countX = hotbarX + ((i + 1) * slotWidth) - 20; // Bottom-right corner
                        float countY = hotbarY + (hotbarTexture.height * HOTBAR_SCALE) - 15;
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
}
