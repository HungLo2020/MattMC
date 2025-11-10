package mattmc.client.renderer.ui;

import mattmc.client.gui.components.TextRenderer;
import mattmc.client.renderer.ItemRenderer;
import mattmc.client.renderer.texture.Texture;
import mattmc.world.item.Inventory;
import mattmc.world.item.ItemStack;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders the hotbar at the bottom of the screen.
 */
public class HotbarRenderer {
    private static final float HOTBAR_SCALE = 3.5f;
    private static final float ITEM_SIZE = 19.2f;
    
    private Texture hotbarTexture;
    private Texture selectionTexture;
    
    public HotbarRenderer() {
        this.hotbarTexture = Texture.load("/assets/textures/gui/widgets.png");
        this.selectionTexture = Texture.load("/assets/textures/gui/widgets.png");
    }
    
    /**
     * Render the hotbar with items.
     */
    public void render(Inventory inventory, int selectedSlot, int screenWidth, int screenHeight) {
        if (hotbarTexture == null || inventory == null) return;
        
        // Calculate hotbar position (centered at bottom)
        float hotbarWidth = 182 * HOTBAR_SCALE;
        float hotbarHeight = 22 * HOTBAR_SCALE;
        float x = (screenWidth - hotbarWidth) / 2;
        float y = screenHeight - hotbarHeight - 10;
        
        // Render hotbar background
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        
        hotbarTexture.bind();
        
        // Hotbar texture coordinates in widgets.png
        float u1 = 0f;
        float v1 = 0f;
        float u2 = 182f / 256f;
        float v2 = 22f / 256f;
        
        glBegin(GL_QUADS);
        glTexCoord2f(u1, v2); glVertex2f(x, y);
        glTexCoord2f(u2, v2); glVertex2f(x + hotbarWidth, y);
        glTexCoord2f(u2, v1); glVertex2f(x + hotbarWidth, y + hotbarHeight);
        glTexCoord2f(u1, v1); glVertex2f(x, y + hotbarHeight);
        glEnd();
        
        // Render selection highlight
        if (selectedSlot >= 0 && selectedSlot < 9) {
            float selectionWidth = 24 * HOTBAR_SCALE;
            float selectionHeight = 24 * HOTBAR_SCALE;
            float selectionX = x - 1 * HOTBAR_SCALE + selectedSlot * 20 * HOTBAR_SCALE;
            float selectionY = y - 1 * HOTBAR_SCALE;
            
            // Selection texture coordinates
            float su1 = 0f;
            float sv1 = 22f / 256f;
            float su2 = 24f / 256f;
            float sv2 = 46f / 256f;
            
            glBegin(GL_QUADS);
            glTexCoord2f(su1, sv2); glVertex2f(selectionX, selectionY);
            glTexCoord2f(su2, sv2); glVertex2f(selectionX + selectionWidth, selectionY);
            glTexCoord2f(su2, sv1); glVertex2f(selectionX + selectionWidth, selectionY + selectionHeight);
            glTexCoord2f(su1, sv1); glVertex2f(selectionX, selectionY + selectionHeight);
            glEnd();
        }
        
        glDisable(GL_TEXTURE_2D);
        
        // Render items in hotbar slots
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack != null && stack.getItem() != null) {
                float itemX = x + 3 * HOTBAR_SCALE + i * 20 * HOTBAR_SCALE + 8 * HOTBAR_SCALE;
                float itemY = y + 3 * HOTBAR_SCALE + 14 * HOTBAR_SCALE;
                
                ItemRenderer.renderItem(stack, itemX, itemY, ITEM_SIZE);
                
                // Render item count
                if (stack.getCount() > 1) {
                    renderItemCount(stack.getCount(), itemX, itemY);
                }
            }
        }
        
        glDisable(GL_BLEND);
    }
    
    private void renderItemCount(int count, float itemX, float itemY) {
        String countText = String.valueOf(count);
        float textX = itemX + 10;
        float textY = itemY - 15;
        float textScale = 1.0f;
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Shadow
        glColor4f(0.25f, 0.25f, 0.25f, 1.0f);
        TextRenderer.drawText(countText, textX + 1, textY + 1, textScale);
        
        // Text
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        TextRenderer.drawText(countText, textX, textY, textScale);
        
        glDisable(GL_BLEND);
    }
    
    public void close() {
        if (hotbarTexture != null) {
            hotbarTexture.close();
            hotbarTexture = null;
        }
        if (selectionTexture != null) {
            selectionTexture.close();
            selectionTexture = null;
        }
    }
}
