package mattmc.client.gui.screens.inventory;

import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.util.CoordinateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages slot positions and detects slot clicks for the inventory screen.
 */
public class InventorySlotManager {
    private static final float SLOT_SIZE = 16f;
    private final List<InventorySlot> slots = new ArrayList<>();
    
    public InventorySlotManager() {
        initializeSlots();
    }
    
    /**
     * Initialize all inventory slots with their positions.
     */
    private void initializeSlots() {
        slots.clear();
        
        // Armor slots (4 slots, vertical on left side) - Not linked to inventory yet
        float armorX = 8f;
        float armorY = 8f;
        for (int i = 0; i < 4; i++) {
            slots.add(new InventorySlot(armorX, armorY + i * 18f, SLOT_SIZE, SLOT_SIZE, -1));
        }
        
        // Crafting grid (2x2 slots) - Not linked to inventory yet
        float craftX = 98f;
        float craftY = 18f;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                slots.add(new InventorySlot(craftX + col * 18f, craftY + row * 18f, SLOT_SIZE, SLOT_SIZE, -1));
            }
        }
        
        // Crafting output slot - Not linked to inventory yet
        slots.add(new InventorySlot(154f, 28f, SLOT_SIZE, SLOT_SIZE, -1));
        
        // Main inventory (3 rows x 9 columns) - slots 9-35
        float invX = 8f;
        float invY = 84f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int inventoryIndex = 9 + (row * 9 + col);
                slots.add(new InventorySlot(invX + col * 18f, invY + row * 18f, SLOT_SIZE, SLOT_SIZE, inventoryIndex));
            }
        }
        
        // Hotbar (1 row x 9 columns) - slots 0-8
        float hotbarX = 8f;
        float hotbarY = 142f;
        for (int col = 0; col < 9; col++) {
            slots.add(new InventorySlot(hotbarX + col * 18f, hotbarY, SLOT_SIZE, SLOT_SIZE, col));
        }
    }
    
    /**
     * Find which slot was clicked based on mouse position.
     * @param mouseXWin Mouse X in window coordinates
     * @param mouseYWin Mouse Y in window coordinates
     * @param guiX GUI X position in framebuffer coordinates
     * @param guiY GUI Y position in framebuffer coordinates
     * @param guiScale GUI scale factor
     * @param window Window reference for coordinate conversion
     * @return Inventory index of clicked slot, or -1 if no valid slot clicked
     */
    public int findClickedSlot(double mouseXWin, double mouseYWin, float guiX, float guiY, float guiScale, WindowHandle window) {
        // Convert window mouse coordinates to framebuffer coordinates
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        
        // Convert mouse position to GUI-relative coordinates
        float mouseGuiX = (fbCoords.x - guiX) / guiScale;
        float mouseGuiY = (fbCoords.y - guiY) / guiScale;
        
        // Find clicked slot
        for (InventorySlot slot : slots) {
            if (slot.contains(mouseGuiX, mouseGuiY)) {
                // Only return inventory slots (not armor/crafting slots)
                if (slot.inventoryIndex >= 0) {
                    return slot.inventoryIndex;
                }
            }
        }
        
        return -1; // No valid slot clicked
    }
    
    public List<InventorySlot> getSlots() {
        return slots;
    }
    
    public boolean isHotbarSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < 9;
    }
    
    public boolean isMainInventorySlot(int slotIndex) {
        return slotIndex >= 9 && slotIndex < 36;
    }
}
