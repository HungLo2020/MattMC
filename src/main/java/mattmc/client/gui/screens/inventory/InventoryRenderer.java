package mattmc.client.gui.screens.inventory;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.TooltipRenderer;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.util.CoordinateUtils;
import mattmc.world.item.Inventory;
import mattmc.world.item.Item;
import mattmc.world.item.ItemStack;

import java.util.List;

import static mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled;

/**
 * Renders inventory GUI elements using the RenderBackend abstraction.
 */
public class InventoryRenderer {
    private static final float GUI_SCALE = 3.0f;
    private static final float CONTENT_OFFSET_X = 40f;
    private static final float CONTENT_OFFSET_Y = 45f;
    private static final int CREATIVE_COLS = 9;
    private static final int CREATIVE_ROWS = 15;
    
    private final WindowHandle window;
    private final InventorySlotManager slotManager;
    private RenderBackend backend;
    private int inventoryTextureId = -1;
    private int creativeInventoryTextureId = -1;
    private int inventoryWidth, inventoryHeight;
    private int creativeWidth, creativeHeight;
    private TooltipRenderer tooltipRenderer;
    
    public InventoryRenderer(WindowHandle window, InventorySlotManager slotManager) {
        this.window = window;
        this.slotManager = slotManager;
        this.tooltipRenderer = new TooltipRenderer();
    }
    
    /**
     * Set the render backend for rendering operations.
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
        if (tooltipRenderer != null) {
            tooltipRenderer.setBackend(backend);
        }
        // Load textures if not already loaded
        if (inventoryTextureId < 0) {
            inventoryTextureId = backend.loadTexture("/assets/textures/gui/container/inventory.png");
            if (inventoryTextureId >= 0) {
                inventoryWidth = backend.getTextureWidth(inventoryTextureId);
                inventoryHeight = backend.getTextureHeight(inventoryTextureId);
            }
        }
        if (creativeInventoryTextureId < 0) {
            creativeInventoryTextureId = backend.loadTexture("/assets/textures/gui/container/creativeinv.png");
            if (creativeInventoryTextureId >= 0) {
                creativeWidth = backend.getTextureWidth(creativeInventoryTextureId);
                creativeHeight = backend.getTextureHeight(creativeInventoryTextureId);
            }
        }
    }
    
    public void renderBackground(int screenWidth, int screenHeight) {
        if (backend == null) return;
        
        // Apply blur if enabled
        if (isMenuScreenBlurEnabled()) {
            backend.applyRegionalBlur(0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }
        
        // Set up 2D projection
        backend.setup2DProjection(screenWidth, screenHeight);
        
        // Enable blending for transparent overlay
        backend.enableBlend();
        
        // Semi-transparent black background
        backend.setColor(0x000000, 0.5f);
        backend.fillRect(0, 0, screenWidth, screenHeight);
    }
    
    public void renderInventoryBackground() {
        if (backend == null || inventoryTextureId < 0) return;
        
        int w = window.width(), h = window.height();
        
        float texWidth = inventoryWidth * GUI_SCALE;
        float texHeight = inventoryHeight * GUI_SCALE;
        float x = (w - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
        float y = (h - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
        
        backend.drawTexture(inventoryTextureId, x, y, texWidth, texHeight);
    }
    
    public void renderSlotHighlight(double mouseXWin, double mouseYWin) {
        if (backend == null || inventoryTextureId < 0) return;
        
        int w = window.width(), h = window.height();
        float texWidth = inventoryWidth * GUI_SCALE;
        float texHeight = inventoryHeight * GUI_SCALE;
        float guiX = (w - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
        float guiY = (h - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
        
        // Convert window mouse coordinates to framebuffer coordinates
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        
        // Convert mouse position to GUI-relative coordinates
        float mouseGuiX = (fbCoords.x - guiX) / GUI_SCALE;
        float mouseGuiY = (fbCoords.y - guiY) / GUI_SCALE;
        
        // Check which slot the mouse is over
        for (InventorySlot slot : slotManager.getSlots()) {
            if (slot.contains(mouseGuiX, mouseGuiY)) {
                // Draw transparent white highlight over this slot
                float slotScreenX = guiX + slot.x * GUI_SCALE;
                float slotScreenY = guiY + slot.y * GUI_SCALE;
                float slotScreenW = slot.width * GUI_SCALE;
                float slotScreenH = slot.height * GUI_SCALE;
                
                backend.setColor(0xFFFFFF, 0.3f);
                backend.fillRect(slotScreenX, slotScreenY, slotScreenW, slotScreenH);
                
                break;
            }
        }
    }
    
    public void renderInventoryItems(Inventory inventory) {
        if (backend == null || inventoryTextureId < 0 || inventory == null) return;
        
        int w = window.width(), h = window.height();
        float texWidth = inventoryWidth * GUI_SCALE;
        float texHeight = inventoryHeight * GUI_SCALE;
        float guiX = (w - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
        float guiY = (h - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
        
        // Item size calculation:
        // - Slot texture is 18x18 pixels in the PNG
        // - Items are 16x16 pixels at texture scale
        // - At GUI_SCALE=3.0: 16*3 = 48 pixels on screen, half = 24 pixels
        float itemSize = 24f;
        
        // Begin frame for item rendering (ItemRenderer.render() submits commands to backend)
        backend.beginFrame();
        
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
                // Items are rendered center-based, move left 1 pixel from center
                float itemCenterX = slotX + 8f * GUI_SCALE;
                float itemCenterY = slotY + 9f * GUI_SCALE;
                
                // Use data-driven rendering with GUI context
                mattmc.client.renderer.backend.opengl.ItemRenderer.render(
                    stack, 
                    itemCenterX, 
                    itemCenterY, 
                    itemSize,
                    backend
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
                // Items are rendered center-based, move left 1 pixel from center
                float itemCenterX = slotX + 8f * GUI_SCALE;
                float itemCenterY = slotY + 9f * GUI_SCALE;
                
                // Use data-driven rendering with GUI context
                mattmc.client.renderer.backend.opengl.ItemRenderer.render(
                    stack, 
                    itemCenterX, 
                    itemCenterY, 
                    itemSize,
                    backend
                );
                
                if (stack.getCount() > 1) {
                    renderItemCount(stack.getCount(), itemCenterX, itemCenterY, GUI_SCALE, itemSize);
                }
            }
        }
        
        // End frame after all inventory items are rendered
        backend.endFrame();
    }
    
    public void renderItemCount(int count, float itemCenterX, float itemCenterY, float guiScale, float itemSize) {
        if (backend == null) return;
        
        String countText = String.valueOf(count);
        
        float slotSize = 16f * guiScale;
        float halfSlot = slotSize / 2f;
        
        float textScale = 1.0f;
        float textX = itemCenterX + halfSlot - 12f;
        float textY = itemCenterY + halfSlot - 30f;
        
        backend.enableBlend();
        
        // Draw shadow
        backend.setColor(0x404040, 1.0f);
        backend.drawText(countText, textX + 1, textY + 1, textScale);
        
        // Draw main text
        backend.setColor(0xFFFFFF, 1.0f);
        backend.drawText(countText, textX, textY, textScale);
        
        backend.disableBlend();
    }
    
    public void renderHeldItem(ItemStack heldItem, double mouseXWin, double mouseYWin) {
        if (backend == null || heldItem == null) return;
        
        // Convert window mouse coordinates to framebuffer coordinates
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        
        float itemSize = 19.2f;
        
        // Begin frame for item rendering (ItemRenderer.render() submits commands to backend)
        backend.beginFrame();
        
        // Use data-driven rendering for held items (using GUI context as they're in the GUI)
        mattmc.client.renderer.backend.opengl.ItemRenderer.render(
            heldItem, 
            fbCoords.x, 
            fbCoords.y, 
            itemSize,
            backend
        );
        
        // End frame after held item is rendered
        backend.endFrame();
        
        if (heldItem.getCount() > 1) {
            renderItemCount(heldItem.getCount(), fbCoords.x, fbCoords.y, 1.0f, itemSize);
        }
    }
    
    public void renderCreativeInventory(List<Item> allItems, int scrollRow) {
        if (backend == null || creativeInventoryTextureId < 0) return;
        
        int w = window.width(), h = window.height();
        // Use actual texture dimensions for rendering the full texture
        float contentWidth = creativeWidth * GUI_SCALE;
        float contentHeight = creativeHeight * GUI_SCALE;
        // Position: right edge of screen, adjusted for visual alignment
        float x = w - contentWidth + 190f;
        float y = (h - contentHeight) / 2f + 80f;
        
        // Draw texture (partial texture coordinates handled by backend implementation)
        backend.drawTexture(creativeInventoryTextureId, x, y, contentWidth, contentHeight);
        
        renderCreativeItems(allItems, scrollRow, x, y);
    }
    
    public void renderCreativeHoverHighlight(double mouseXWin, double mouseYWin, List<Item> allItems, int scrollRow) {
        if (backend == null || creativeInventoryTextureId < 0) return;
        
        int w = window.width(), h = window.height();
        // Use actual texture dimensions for position calculation (must match renderCreativeInventory)
        float contentWidth = creativeWidth * GUI_SCALE;
        float contentHeight = creativeHeight * GUI_SCALE;
        // Position: right edge of screen, adjusted for visual alignment
        float guiX = w - contentWidth + 190f;
        float guiY = (h - contentHeight) / 2f + 80f;
        
        // Get mouse position in framebuffer coordinates
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        
        float startX = guiX + 8f * GUI_SCALE;
        float startY = guiY + 18f * GUI_SCALE;
        float slotSpacing = 18f * GUI_SCALE;
        
        for (int row = 0; row < CREATIVE_ROWS; row++) {
            for (int col = 0; col < CREATIVE_COLS; col++) {
                float slotX = startX + col * slotSpacing;
                float slotY = startY + row * slotSpacing;
                
                if (fbCoords.x >= slotX && fbCoords.x < slotX + slotSpacing &&
                    fbCoords.y >= slotY && fbCoords.y < slotY + slotSpacing) {
                    
                    int itemIndex = (scrollRow + row) * CREATIVE_COLS + col;
                    
                    if (itemIndex >= 0 && itemIndex < allItems.size()) {
                        float highlightSize = 16f * GUI_SCALE;
                        float highlightX = slotX + (slotSpacing - highlightSize) / 2f - 3f;
                        float highlightY = slotY + (slotSpacing - highlightSize) / 2f - 3f;
                        
                        backend.enableBlend();
                        backend.setColor(0xFFFFFF, 0.3f);
                        backend.fillRect(highlightX, highlightY, highlightSize, highlightSize);
                        backend.disableBlend();
                        return;
                    }
                }
            }
        }
    }
    
    private void renderCreativeItems(List<Item> allItems, int scrollRow, float guiX, float guiY) {
        if (backend == null) return;
        
        // Item size: 16 pixels * GUI_SCALE = 48 pixels, half = 24 pixels
        float itemSize = 24f;
        float startX = 8f * GUI_SCALE;
        float startY = 18f * GUI_SCALE;
        float slotSpacing = 18f * GUI_SCALE;
        
        // Begin frame for item rendering (ItemRenderer.render() submits commands to backend)
        backend.beginFrame();
        
        for (int row = 0; row < CREATIVE_ROWS; row++) {
            for (int col = 0; col < CREATIVE_COLS; col++) {
                int itemIndex = (scrollRow + row) * CREATIVE_COLS + col;
                
                if (itemIndex >= 0 && itemIndex < allItems.size()) {
                    Item item = allItems.get(itemIndex);
                    ItemStack stack = new ItemStack(item, 1);
                    
                    // Position at slot center, move left 1 pixel
                    float slotX = guiX + startX + col * slotSpacing;
                    float slotY = guiY + startY + row * slotSpacing;
                    float itemX = slotX + 8f * GUI_SCALE;
                    float itemY = slotY + 9f * GUI_SCALE;
                    
                    // Use data-driven rendering for creative inventory
                    mattmc.client.renderer.backend.opengl.ItemRenderer.render(
                        stack, 
                        itemX, 
                        itemY, 
                        itemSize,
                        backend
                    );
                }
            }
        }
        
        // End frame after all creative items are rendered
        backend.endFrame();
    }
    
    public void renderTooltip(Item hoveredItem, double mouseXWin, double mouseYWin) {
        if (hoveredItem == null || hoveredItem.getIdentifier() == null || tooltipRenderer == null || backend == null) return;
        
        String identifier = hoveredItem.getIdentifier();
        String itemName = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
        itemName = itemName.substring(0, 1).toUpperCase() + itemName.substring(1).replace('_', ' ');
        
        // Convert window coordinates to framebuffer coordinates
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        
        tooltipRenderer.renderTooltip(itemName, fbCoords.x, fbCoords.y, window.width(), window.height());
    }
    
    public void close() {
        if (backend != null) {
            if (inventoryTextureId >= 0) {
                backend.releaseTexture(inventoryTextureId);
                inventoryTextureId = -1;
            }
            if (creativeInventoryTextureId >= 0) {
                backend.releaseTexture(creativeInventoryTextureId);
                creativeInventoryTextureId = -1;
            }
        }
        tooltipRenderer = null;
    }
    
    public int getInventoryWidth() {
        return inventoryWidth;
    }
    
    public int getInventoryHeight() {
        return inventoryHeight;
    }
    
    public boolean hasInventoryTexture() {
        return inventoryTextureId >= 0;
    }
}
