package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.CommandBuffer;

import mattmc.client.renderer.UIRenderLogic;

import mattmc.client.renderer.backend.DrawCommand;

import mattmc.client.renderer.backend.RenderBackend;

import mattmc.client.renderer.backend.opengl.Texture;

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
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Enable blending for texture rendering
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Synchronize selected slot
        if (player != null && player.getInventory() != null) {
            selectedHotbarSlot = player.getInventory().getSelectedSlot();
        }
        
        // Use backend if available (Stage 4)
        if (backend != null) {
            // Build and submit commands via backend
            UIRenderLogic logic = new UIRenderLogic();
            CommandBuffer buffer = new CommandBuffer();
            
            // Clear text registry for this frame
            UIRenderLogic.clearTextRegistry();
            
            // Build hotbar commands
            logic.buildHotbarCommands(screenWidth, screenHeight, selectedHotbarSlot, buffer);
            
            // Submit to backend
            for (DrawCommand cmd : buffer.getCommands()) {
                backend.submit(cmd);
            }
        } else {
            // Legacy rendering path
            renderLegacy(screenWidth, screenHeight);
        }
        
        // Draw items in hotbar slots (common to both paths)
        renderHotbarItems(screenWidth, screenHeight, player);
        
        glDisable(GL_BLEND);
        UIRenderHelper.restore2DProjection();
    }
    
    /**
     * Legacy rendering path (before backend).
     */
    private void renderLegacy(int screenWidth, int screenHeight) {
        // Load hotbar texture if not already loaded
        if (hotbarTexture == null) {
            hotbarTexture = Texture.load("/assets/textures/gui/sprites/hud/hotbar.png");
        }
        
        // Load hotbar selection texture if not already loaded
        if (hotbarSelectionTexture == null) {
            hotbarSelectionTexture = Texture.load("/assets/textures/gui/sprites/hud/hotbar_selection.png");
        }
        
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
            float slotWidth = (hotbarTexture.width * HOTBAR_SCALE) / 9f;
            float selectionWidth = hotbarSelectionTexture.width * HOTBAR_SCALE;
            float selectionHeight = hotbarSelectionTexture.height * HOTBAR_SCALE;
            
            // Center the selection overlay on the slot
            float centerOffset = (selectionWidth - slotWidth) / 2f;
            float selectionX = hotbarX + (selectedHotbarSlot * slotWidth) - centerOffset;
            float selectionY = hotbarY - (1 * HOTBAR_SCALE);
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(selectionX, selectionY);
            glTexCoord2f(1, 1); glVertex2f(selectionX + selectionWidth, selectionY);
            glTexCoord2f(1, 0); glVertex2f(selectionX + selectionWidth, selectionY + selectionHeight);
            glTexCoord2f(0, 0); glVertex2f(selectionX, selectionY + selectionHeight);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
        }
    }
    
    /**
     * Render items in hotbar slots (common to both backend and legacy paths).
     */
    private void renderHotbarItems(int screenWidth, int screenHeight, mattmc.world.entity.player.LocalPlayer player) {
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        
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
                
                // Use backend if available, otherwise legacy rendering
                if (backend != null) {
                    ItemRenderer.render(stack, itemX, itemY, itemSize, backend);
                } else {
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
                    float countX = slotX + slotSpacing - 20;
                    float countY = slotStartY + 18f * HOTBAR_SCALE - 15;
                    UIRenderHelper.drawText(countText, countX, countY, 1.0f, 0xFFFFFF);
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
