package mattmc.client.gui.screens.inventory;

import mattmc.client.Window;
import mattmc.client.renderer.BlurEffect;
import mattmc.client.renderer.BlurRenderer;
import mattmc.client.renderer.TooltipRenderer;
import mattmc.client.renderer.texture.Texture;
import mattmc.world.item.Inventory;
import mattmc.world.item.Item;
import mattmc.world.item.ItemStack;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.List;

import static mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Renders inventory GUI elements.
 */
public class InventoryRenderer {
    private static final float GUI_SCALE = 3.0f;
    private static final float CONTENT_OFFSET_X = 40f;
    private static final float CONTENT_OFFSET_Y = 45f;
    private static final int CREATIVE_COLS = 9;
    private static final int CREATIVE_ROWS = 15;
    
    private final Window window;
    private final InventorySlotManager slotManager;
    private Texture inventoryTexture;
    private Texture creativeInventoryTexture;
    private BlurEffect blurEffect;
    private TooltipRenderer tooltipRenderer;
    
    public InventoryRenderer(Window window, InventorySlotManager slotManager) {
        this.window = window;
        this.slotManager = slotManager;
        this.inventoryTexture = Texture.load("/assets/textures/gui/container/inventory.png");
        this.creativeInventoryTexture = Texture.load("/assets/textures/gui/container/creativeinv.png");
        this.tooltipRenderer = new TooltipRenderer();
    }
    
    public void renderBackground(int screenWidth, int screenHeight) {
        // Apply blur if enabled
        if (isMenuScreenBlurEnabled()) {
            if (blurEffect == null) {
                blurEffect = new BlurEffect();
            }
            BlurRenderer.renderBlurredBackground(blurEffect, screenWidth, screenHeight);
        }
        
        // Draw dark overlay
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Semi-transparent black background
        setColor(0x000000, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(screenWidth, 0);
        glVertex2f(screenWidth, screenHeight);
        glVertex2f(0, screenHeight);
        glEnd();
    }
    
    public void renderInventoryBackground() {
        if (inventoryTexture == null) return;
        
        int w = window.width(), h = window.height();
        
        glEnable(GL_TEXTURE_2D);
        inventoryTexture.bind();
        glColor4f(1f, 1f, 1f, 1f);
        
        float texWidth = inventoryTexture.width * GUI_SCALE;
        float texHeight = inventoryTexture.height * GUI_SCALE;
        float x = (w - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
        float y = (h - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
        
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x, y);
        glTexCoord2f(1, 1); glVertex2f(x + texWidth, y);
        glTexCoord2f(1, 0); glVertex2f(x + texWidth, y + texHeight);
        glTexCoord2f(0, 0); glVertex2f(x, y + texHeight);
        glEnd();
        
        glDisable(GL_TEXTURE_2D);
    }
    
    public void renderSlotHighlight(double mouseXWin, double mouseYWin) {
        if (inventoryTexture == null) return;
        
        int w = window.width(), h = window.height();
        float texWidth = inventoryTexture.width * GUI_SCALE;
        float texHeight = inventoryTexture.height * GUI_SCALE;
        float guiX = (w - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
        float guiY = (h - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
        
        // Convert window mouse coordinates to framebuffer coordinates
        float mouseFBX, mouseFBY;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1), fbH  = stack.mallocInt(1);
            glfwGetWindowSize(window.handle(), winW, winH);
            glfwGetFramebufferSize(window.handle(), fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mouseFBX = (float) mouseXWin * sx;
            mouseFBY = (float) mouseYWin * sy;
        }
        
        // Convert mouse position to GUI-relative coordinates
        float mouseGuiX = (mouseFBX - guiX) / GUI_SCALE;
        float mouseGuiY = (mouseFBY - guiY) / GUI_SCALE;
        
        // Check which slot the mouse is over
        for (InventorySlot slot : slotManager.getSlots()) {
            if (slot.contains(mouseGuiX, mouseGuiY)) {
                // Draw transparent white highlight over this slot
                float slotScreenX = guiX + slot.x * GUI_SCALE;
                float slotScreenY = guiY + slot.y * GUI_SCALE;
                float slotScreenW = slot.width * GUI_SCALE;
                float slotScreenH = slot.height * GUI_SCALE;
                
                glColor4f(1f, 1f, 1f, 0.3f);
                glBegin(GL_QUADS);
                glVertex2f(slotScreenX, slotScreenY);
                glVertex2f(slotScreenX + slotScreenW, slotScreenY);
                glVertex2f(slotScreenX + slotScreenW, slotScreenY + slotScreenH);
                glVertex2f(slotScreenX, slotScreenY + slotScreenH);
                glEnd();
                
                break;
            }
        }
    }
    
    public void renderInventoryItems(Inventory inventory) {
        if (inventoryTexture == null || inventory == null) return;
        
        int w = window.width(), h = window.height();
        float texWidth = inventoryTexture.width * GUI_SCALE;
        float texHeight = inventoryTexture.height * GUI_SCALE;
        float guiX = (w - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
        float guiY = (h - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
        
        // Item size calculation:
        // - Slot texture is 18x18 pixels in the PNG
        // - Items are 16x16 pixels at texture scale
        // - At GUI_SCALE=3.0: 16*3 = 48 pixels on screen, half = 24 pixels
        float itemSize = 24f;
        
        // Draw items in hotbar (slots 0-8)
        float hotbarX = 8f;
        float hotbarY = 142f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack != null && stack.getItem() != null) {
                // Position item at slot top-left corner
                // Slot spacing is 18 pixels, items are 16x16, so 1 pixel offset on each side
                float slotX = guiX + (hotbarX + i * 18f) * GUI_SCALE;
                float slotY = guiY + hotbarY * GUI_SCALE;
                // Items are rendered center-based, offset by 1 pixel to center 16px item in 18px slot
                float itemCenterX = slotX + 9f * GUI_SCALE;
                float itemCenterY = slotY + 9f * GUI_SCALE;
                
                // Use data-driven rendering with GUI context
                mattmc.client.renderer.ItemRenderer.renderItemWithTransform(
                    stack, 
                    mattmc.client.renderer.item.ItemDisplayContext.GUI, 
                    itemCenterX, 
                    itemCenterY, 
                    itemSize
                );
                
                if (stack.getCount() > 1) {
                    renderItemCount(stack.getCount(), itemCenterX, itemCenterY, GUI_SCALE, itemSize);
                }
            }
        }
        
        // Draw items in main inventory (slots 9-35)
        float invX = 8f;
        float invY = 84f;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack != null && stack.getItem() != null) {
                int invIndex = slot - 9;
                int row = invIndex / 9;
                int col = invIndex % 9;
                
                // Position item at slot top-left corner
                float slotX = guiX + (invX + col * 18f) * GUI_SCALE;
                float slotY = guiY + (invY + row * 18f) * GUI_SCALE;
                // Items are rendered center-based, offset by 1 pixel to center 16px item in 18px slot
                float itemCenterX = slotX + 9f * GUI_SCALE;
                float itemCenterY = slotY + 9f * GUI_SCALE;
                
                // Use data-driven rendering with GUI context
                mattmc.client.renderer.ItemRenderer.renderItemWithTransform(
                    stack, 
                    mattmc.client.renderer.item.ItemDisplayContext.GUI, 
                    itemCenterX, 
                    itemCenterY, 
                    itemSize
                );
                
                if (stack.getCount() > 1) {
                    renderItemCount(stack.getCount(), itemCenterX, itemCenterY, GUI_SCALE, itemSize);
                }
            }
        }
    }
    
    public void renderItemCount(int count, float itemCenterX, float itemCenterY, float guiScale, float itemSize) {
        String countText = String.valueOf(count);
        
        float slotSize = 16f * guiScale;
        float halfSlot = slotSize / 2f;
        
        float textScale = 1.0f;
        float textX = itemCenterX + halfSlot - 12f;
        float textY = itemCenterY + halfSlot - 30f;
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw shadow
        glColor4f(0.25f, 0.25f, 0.25f, 1.0f);
        mattmc.client.gui.components.TextRenderer.drawText(countText, textX + 1, textY + 1, textScale);
        
        // Draw main text
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mattmc.client.gui.components.TextRenderer.drawText(countText, textX, textY, textScale);
        
        glDisable(GL_BLEND);
    }
    
    public void renderHeldItem(ItemStack heldItem, double mouseXWin, double mouseYWin) {
        if (heldItem == null) return;
        
        // Convert window mouse coordinates to framebuffer coordinates
        float mouseFBX, mouseFBY;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1), fbH  = stack.mallocInt(1);
            glfwGetWindowSize(window.handle(), winW, winH);
            glfwGetFramebufferSize(window.handle(), fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mouseFBX = (float) mouseXWin * sx;
            mouseFBY = (float) mouseYWin * sy;
        }
        
        float itemSize = 19.2f;
        // Use data-driven rendering for held items (using GUI context as they're in the GUI)
        mattmc.client.renderer.ItemRenderer.renderItemWithTransform(
            heldItem, 
            mattmc.client.renderer.item.ItemDisplayContext.GUI, 
            mouseFBX, 
            mouseFBY, 
            itemSize
        );
        
        if (heldItem.getCount() > 1) {
            renderItemCount(heldItem.getCount(), mouseFBX, mouseFBY, 1.0f, itemSize);
        }
    }
    
    public void renderCreativeInventory(List<Item> allItems, int scrollRow) {
        if (creativeInventoryTexture == null) return;
        
        int w = window.width(), h = window.height();
        float contentWidth = 176f * GUI_SCALE;
        float contentHeight = 296f * GUI_SCALE;
        float x = w - contentWidth - 20f;
        float y = (h - contentHeight) / 2f;
        
        // Render texture
        glEnable(GL_TEXTURE_2D);
        creativeInventoryTexture.bind();
        glColor4f(1f, 1f, 1f, 1f);
        
        float texU = 176f / 256f;
        float texV_bottom = 1.0f - (296f / 384f);
        
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x, y);
        glTexCoord2f(texU, 1); glVertex2f(x + contentWidth, y);
        glTexCoord2f(texU, texV_bottom); glVertex2f(x + contentWidth, y + contentHeight);
        glTexCoord2f(0, texV_bottom); glVertex2f(x, y + contentHeight);
        glEnd();
        
        glDisable(GL_TEXTURE_2D);
        
        renderCreativeItems(allItems, scrollRow, x, y);
    }
    
    public void renderCreativeHoverHighlight(double mouseXWin, double mouseYWin, List<Item> allItems, int scrollRow) {
        if (creativeInventoryTexture == null) return;
        
        int w = window.width(), h = window.height();
        float contentWidth = 176f * GUI_SCALE;
        float contentHeight = 296f * GUI_SCALE;
        float guiX = w - contentWidth - 20f;
        float guiY = (h - contentHeight) / 2f;
        
        // Get mouse position in framebuffer coordinates
        float mouseFBX, mouseFBY;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1), fbH  = stack.mallocInt(1);
            glfwGetWindowSize(window.handle(), winW, winH);
            glfwGetFramebufferSize(window.handle(), fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mouseFBX = (float) mouseXWin * sx;
            mouseFBY = (float) mouseYWin * sy;
        }
        
        float startX = guiX + 8f * GUI_SCALE;
        float startY = guiY + 18f * GUI_SCALE;
        float slotSpacing = 18f * GUI_SCALE;
        
        for (int row = 0; row < CREATIVE_ROWS; row++) {
            for (int col = 0; col < CREATIVE_COLS; col++) {
                float slotX = startX + col * slotSpacing;
                float slotY = startY + row * slotSpacing;
                
                if (mouseFBX >= slotX && mouseFBX < slotX + slotSpacing &&
                    mouseFBY >= slotY && mouseFBY < slotY + slotSpacing) {
                    
                    int itemIndex = (scrollRow + row) * CREATIVE_COLS + col;
                    
                    if (itemIndex >= 0 && itemIndex < allItems.size()) {
                        float highlightSize = 16f * GUI_SCALE;
                        float highlightX = slotX + (slotSpacing - highlightSize) / 2f - 3f;
                        float highlightY = slotY + (slotSpacing - highlightSize) / 2f - 3f;
                        
                        glEnable(GL_BLEND);
                        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                        glColor4f(1f, 1f, 1f, 0.3f);
                        
                        glBegin(GL_QUADS);
                        glVertex2f(highlightX, highlightY);
                        glVertex2f(highlightX + highlightSize, highlightY);
                        glVertex2f(highlightX + highlightSize, highlightY + highlightSize);
                        glVertex2f(highlightX, highlightY + highlightSize);
                        glEnd();
                        
                        glDisable(GL_BLEND);
                        return;
                    }
                }
            }
        }
    }
    
    private void renderCreativeItems(List<Item> allItems, int scrollRow, float guiX, float guiY) {
        // Item size: 16 pixels * GUI_SCALE = 48 pixels, half = 24 pixels
        float itemSize = 24f;
        float startX = 8f * GUI_SCALE;
        float startY = 18f * GUI_SCALE;
        float slotSpacing = 18f * GUI_SCALE;
        
        for (int row = 0; row < CREATIVE_ROWS; row++) {
            for (int col = 0; col < CREATIVE_COLS; col++) {
                int itemIndex = (scrollRow + row) * CREATIVE_COLS + col;
                
                if (itemIndex >= 0 && itemIndex < allItems.size()) {
                    Item item = allItems.get(itemIndex);
                    ItemStack stack = new ItemStack(item, 1);
                    
                    // Position at slot center (16px items centered in 18px slots)
                    float slotX = guiX + startX + col * slotSpacing;
                    float slotY = guiY + startY + row * slotSpacing;
                    float itemX = slotX + 9f * GUI_SCALE;
                    float itemY = slotY + 9f * GUI_SCALE;
                    
                    // Use data-driven rendering for creative inventory
                    mattmc.client.renderer.ItemRenderer.renderItemWithTransform(
                        stack, 
                        mattmc.client.renderer.item.ItemDisplayContext.GUI, 
                        itemX, 
                        itemY, 
                        itemSize
                    );
                }
            }
        }
    }
    
    public void renderTooltip(Item hoveredItem, double mouseXWin, double mouseYWin) {
        if (hoveredItem == null || hoveredItem.getIdentifier() == null || tooltipRenderer == null) return;
        
        String identifier = hoveredItem.getIdentifier();
        String itemName = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
        itemName = itemName.substring(0, 1).toUpperCase() + itemName.substring(1).replace('_', ' ');
        
        tooltipRenderer.renderTooltip(itemName, mouseXWin, mouseYWin, window.handle(), window.width(), window.height());
    }
    
    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }
    
    public void close() {
        if (inventoryTexture != null) {
            inventoryTexture.close();
            inventoryTexture = null;
        }
        if (creativeInventoryTexture != null) {
            creativeInventoryTexture.close();
            creativeInventoryTexture = null;
        }
        if (blurEffect != null) {
            blurEffect.close();
            blurEffect = null;
        }
        if (tooltipRenderer != null) {
            tooltipRenderer.close();
            tooltipRenderer = null;
        }
    }
    
    public Texture getInventoryTexture() {
        return inventoryTexture;
    }
}
