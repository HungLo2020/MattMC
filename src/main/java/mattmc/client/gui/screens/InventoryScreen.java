package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.renderer.BlurEffect;
import mattmc.client.renderer.BlurRenderer;
import mattmc.client.renderer.texture.Texture;
import mattmc.world.entity.player.PlayerInput;
import mattmc.world.item.ItemStack;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Inventory screen overlay - displays the inventory.png centered on screen.
 * Does not pause the game. Press E (or configured inventory key) to close.
 */
public final class InventoryScreen implements Screen {
    // GUI rendering constants
    private static final float GUI_SCALE = 3.0f;
    private static final float CONTENT_OFFSET_X = 40f;
    private static final float CONTENT_OFFSET_Y = 45f;
    private static final float SLOT_SIZE = 16f;
    
    private final Minecraft game;
    private final Window window;
    private final DevplayScreen gameScreen;
    private Texture inventoryTexture;
    
    // Blur effect for background
    private BlurEffect blurEffect;
    
    // Mouse tracking for slot highlighting
    private double mouseXWin, mouseYWin;
    private final List<InventorySlot> slots = new ArrayList<>();
    
    // Held item state (for drag-and-drop)
    private mattmc.world.item.ItemStack heldItem = null;
    private int heldItemSourceSlot = -1;
    
    // Helper class to represent an inventory slot
    private static class InventorySlot {
        final float x, y, width, height; // Relative to 176x166 GUI coordinate system
        final int inventoryIndex; // Index in player inventory (0-35, or -1 for non-inventory slots)
        
        InventorySlot(float x, float y, float width, float height, int inventoryIndex) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.inventoryIndex = inventoryIndex;
        }
        
        boolean contains(float px, float py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public InventoryScreen(Minecraft game, DevplayScreen gameScreen) {
        this.game = game;
        this.window = game.window();
        this.gameScreen = gameScreen;
        
        // Sync player position to prevent flickering during interpolation
        gameScreen.syncPlayerPosition();

        // Initialize inventory slots
        initializeSlots();

        // Load inventory texture
        inventoryTexture = Texture.load("/assets/textures/gui/container/inventory.png");

        // Release mouse cursor
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);

        // Track mouse position for slot highlighting
        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { 
            mouseXWin = x; 
            mouseYWin = y; 
        });

        // Handle mouse button clicks for inventory interaction
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    handleLeftClick(mods);
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    handleRightClick(mods);
                }
            }
        });

        // Set up key callback for inventory key (respects user configuration) or ESC to close
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ESCAPE) {
                    closeInventory();
                } else {
                    // Check if this is the inventory key
                    Integer inventoryKey = PlayerInput.getInstance().getKeybind(PlayerInput.INVENTORY);
                    if (inventoryKey != null && key == inventoryKey) {
                        closeInventory();
                    }
                }
            }
        });

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
        });
    }

    private void closeInventory() {
        // Return held item to inventory if any
        if (heldItem != null) {
            mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
            if (player != null && player.getInventory() != null) {
                mattmc.world.item.Inventory inventory = player.getInventory();
                
                // Try to return item to source slot first (if valid and compatible)
                if (heldItemSourceSlot >= 0 && heldItemSourceSlot < inventory.getSize()) {
                    mattmc.world.item.ItemStack slotItem = inventory.getStack(heldItemSourceSlot);
                    if (slotItem == null) {
                        // Source slot is empty - return item there
                        inventory.setStack(heldItemSourceSlot, heldItem);
                        heldItem = null;
                    } else if (slotItem.canMergeWith(heldItem)) {
                        // Source slot has compatible item - try to merge
                        int spaceLeft = slotItem.getItem().getMaxStackSize() - slotItem.getCount();
                        int toAdd = Math.min(spaceLeft, heldItem.getCount());
                        if (toAdd > 0) {
                            slotItem.grow(toAdd);
                            int remaining = heldItem.getCount() - toAdd;
                            if (remaining > 0) {
                                heldItem.setCount(remaining);
                            } else {
                                heldItem = null;
                            }
                        }
                    }
                }
                
                // If item still held, try to add to any compatible slot or first empty slot
                if (heldItem != null) {
                    boolean added = inventory.addItem(heldItem);
                    if (added) {
                        heldItem = null;
                    }
                    // Note: If inventory is full and item can't be added, it will be lost.
                    // In a production system, you might want to drop it to the world instead.
                    // For now, we accept this limitation as inventory space management is
                    // the player's responsibility.
                }
            }
            
            // Clear held item state
            heldItem = null;
            heldItemSourceSlot = -1;
        }
        
        // Recapture mouse for FPS controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        game.setScreen(gameScreen);
    }
    
    /**
     * Find the inventory slot under the current mouse position.
     * @return The slot's inventory index, or -1 if no valid slot was clicked
     */
    private int findClickedSlot() {
        // Get GUI coordinates
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
    
    /**
     * Handle left-click for inventory item interaction.
     */
    private void handleLeftClick(int mods) {
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        int slotIndex = findClickedSlot();
        
        if (slotIndex >= 0) {
            boolean isShiftClick = (mods & GLFW_MOD_SHIFT) != 0;
            
            if (isShiftClick) {
                handleShiftClick(inventory, slotIndex);
            } else {
                handleNormalClick(inventory, slotIndex);
            }
        }
    }
    
    /**
     * Handle right-click for inventory item interaction.
     * Right-click on stack with empty cursor: pick up half
     * Right-click on empty slot with cursor: deposit one item
     */
    private void handleRightClick(int mods) {
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        int slotIndex = findClickedSlot();
        
        if (slotIndex >= 0) {
            handleRightClickSlot(inventory, slotIndex);
        }
    }
    
    /**
     * Handle normal (non-shift) click on an inventory slot.
     */
    private void handleNormalClick(mattmc.world.item.Inventory inventory, int slotIndex) {
        mattmc.world.item.ItemStack slotItem = inventory.getStack(slotIndex);
        
        if (heldItem == null) {
            // Pick up item from slot
            if (slotItem != null) {
                heldItem = slotItem;
                heldItemSourceSlot = slotIndex;
                inventory.setStack(slotIndex, null);
            }
        } else {
            // Placing held item in slot
            if (slotItem == null) {
                // Empty slot - place held item
                inventory.setStack(slotIndex, heldItem);
                heldItem = null;
                heldItemSourceSlot = -1;
            } else if (slotItem.getItem() == heldItem.getItem()) {
                // Same item type - try to merge
                int spaceLeft = slotItem.getItem().getMaxStackSize() - slotItem.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, heldItem.getCount());
                    slotItem.grow(toAdd);
                    
                    int remainingHeld = heldItem.getCount() - toAdd;
                    if (remainingHeld > 0) {
                        heldItem.setCount(remainingHeld);
                    } else {
                        heldItem = null;
                        heldItemSourceSlot = -1;
                    }
                } else {
                    // Stack is full - swap items
                    mattmc.world.item.ItemStack temp = slotItem;
                    inventory.setStack(slotIndex, heldItem);
                    heldItem = temp;
                    heldItemSourceSlot = -1; // Source slot no longer valid after swap
                }
            } else {
                // Different item type - swap
                mattmc.world.item.ItemStack temp = slotItem;
                inventory.setStack(slotIndex, heldItem);
                heldItem = temp;
                heldItemSourceSlot = -1; // Source slot no longer valid after swap
            }
        }
    }
    
    /**
     * Handle right-click on an inventory slot.
     * If cursor is empty and slot has items: pick up half (rounded up)
     * If cursor has items and slot is empty: place one item
     */
    private void handleRightClickSlot(mattmc.world.item.Inventory inventory, int slotIndex) {
        mattmc.world.item.ItemStack slotItem = inventory.getStack(slotIndex);
        
        if (heldItem == null) {
            // Empty cursor - pick up half of the stack
            if (slotItem != null) {
                int totalCount = slotItem.getCount();
                int pickupCount = (totalCount + 1) / 2; // Round up
                int remainingCount = totalCount - pickupCount;
                
                // Create held item with pickup count
                heldItem = new mattmc.world.item.ItemStack(slotItem.getItem(), pickupCount);
                heldItemSourceSlot = slotIndex;
                
                // Update slot with remaining count, or clear if none left
                if (remainingCount > 0) {
                    slotItem.setCount(remainingCount);
                } else {
                    inventory.setStack(slotIndex, null);
                }
            }
        } else {
            // Cursor has items - place one item
            if (slotItem == null) {
                // Empty slot - place one item
                mattmc.world.item.ItemStack newStack = new mattmc.world.item.ItemStack(heldItem.getItem(), 1);
                inventory.setStack(slotIndex, newStack);
                
                // Reduce held item count
                int newHeldCount = heldItem.getCount() - 1;
                if (newHeldCount > 0) {
                    heldItem.setCount(newHeldCount);
                } else {
                    heldItem = null;
                    heldItemSourceSlot = -1;
                }
            } else if (slotItem.getItem() == heldItem.getItem() && !slotItem.isFull()) {
                // Same item type and not full - add one item
                slotItem.grow(1);
                
                // Reduce held item count
                int newHeldCount = heldItem.getCount() - 1;
                if (newHeldCount > 0) {
                    heldItem.setCount(newHeldCount);
                } else {
                    heldItem = null;
                    heldItemSourceSlot = -1;
                }
            }
        }
    }
    
    /**
     * Handle shift-click on an inventory slot.
     * Moves item from hotbar to inventory or vice versa, with stack merging support.
     */
    private void handleShiftClick(mattmc.world.item.Inventory inventory, int slotIndex) {
        mattmc.world.item.ItemStack slotItem = inventory.getStack(slotIndex);
        
        if (slotItem == null) {
            return; // Nothing to move
        }
        
        // Create a copy to track remaining items to move
        ItemStack itemsToMove = slotItem.copy();
        
        if (mattmc.world.item.Inventory.isHotbarSlot(slotIndex)) {
            // Move from hotbar to main inventory (slots 9-35)
            itemsToMove = moveItemsToRange(inventory, itemsToMove, 9, 36);
        } else if (mattmc.world.item.Inventory.isMainInventorySlot(slotIndex)) {
            // Move from main inventory to hotbar (slots 0-8)
            itemsToMove = moveItemsToRange(inventory, itemsToMove, 0, 9);
        }
        
        // Update source slot
        if (itemsToMove == null || itemsToMove.getCount() == 0) {
            // All items moved
            inventory.setStack(slotIndex, null);
        } else {
            // Some items couldn't be moved
            inventory.setStack(slotIndex, itemsToMove);
        }
    }
    
    /**
     * Helper method to move items to a range of slots with merging support.
     * First tries to merge with existing stacks, then places in empty slots.
     * 
     * @param inventory The inventory
     * @param itemsToMove The items to move
     * @param startSlot Start of the slot range (inclusive)
     * @param endSlot End of the slot range (exclusive)
     * @return Remaining items that couldn't be moved, or null if all moved
     */
    private ItemStack moveItemsToRange(mattmc.world.item.Inventory inventory, ItemStack itemsToMove, int startSlot, int endSlot) {
        if (itemsToMove == null || itemsToMove.getCount() == 0) {
            return null;
        }
        
        // First pass: try to merge with existing stacks of the same type
        for (int i = startSlot; i < endSlot; i++) {
            ItemStack targetStack = inventory.getStack(i);
            if (targetStack != null && targetStack.canMergeWith(itemsToMove)) {
                int spaceLeft = targetStack.getItem().getMaxStackSize() - targetStack.getCount();
                if (spaceLeft > 0) {
                    int toAdd = Math.min(spaceLeft, itemsToMove.getCount());
                    targetStack.grow(toAdd);
                    int remaining = itemsToMove.getCount() - toAdd;
                    
                    if (remaining <= 0) {
                        return null; // All items merged
                    }
                    itemsToMove.setCount(remaining);
                }
            }
        }
        
        // Second pass: place remaining items in empty slots
        for (int i = startSlot; i < endSlot; i++) {
            if (inventory.getStack(i) == null) {
                inventory.setStack(i, itemsToMove.copy());
                return null; // All items placed
            }
        }
        
        // If we get here, there wasn't enough space for all items
        return itemsToMove;
    }
    
    /**
     * Initialize all inventory slot positions based on standard Minecraft inventory layout.
     * Coordinates are relative to the 176x166 GUI coordinate system.
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

    @Override
    public void tick() {
        // Don't update the game while inventory is open - player should not move
        // and game is effectively paused (similar to pause menu behavior)
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();
        
        // First render the game screen behind this overlay
        gameScreen.render(alpha);
        
        // Apply blur if enabled
        if (isMenuScreenBlurEnabled()) {
            if (blurEffect == null) {
                blurEffect = new BlurEffect();
            }
            BlurRenderer.renderBlurredBackground(blurEffect, w, h);
        }
        
        // Draw dark overlay
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Semi-transparent black background
        setColor(0x000000, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(w, 0);
        glVertex2f(w, h);
        glVertex2f(0, h);
        glEnd();
        
        // Draw inventory texture centered
        if (inventoryTexture != null) {
            glEnable(GL_TEXTURE_2D);
            inventoryTexture.bind();
            glColor4f(1f, 1f, 1f, 1f);
            
            // Scale inventory to 3x size for better visibility (like Minecraft GUI scale)
            // The inventory texture is 256x256 but the actual content is ~176x166 centered
            // We need to offset by the empty space around the content
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
            
            // Draw slot highlight if mouse is over a slot
            drawSlotHighlight(x, y, GUI_SCALE);
            
            // Draw items in inventory slots
            drawInventoryItems(x, y, GUI_SCALE);
            
            // Draw held item under mouse cursor
            if (heldItem != null) {
                drawHeldItem();
            }
        }
        
        glDisable(GL_BLEND);
    }
    
    /**
     * Draws a transparent white highlight over the hovered inventory slot.
     */
    private void drawSlotHighlight(float guiX, float guiY, float scale) {
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
        float mouseGuiX = (mouseFBX - guiX) / scale;
        float mouseGuiY = (mouseFBY - guiY) / scale;
        
        // Check which slot the mouse is over
        for (InventorySlot slot : slots) {
            if (slot.contains(mouseGuiX, mouseGuiY)) {
                // Draw transparent white highlight over this slot
                float slotScreenX = guiX + slot.x * scale;
                float slotScreenY = guiY + slot.y * scale;
                float slotScreenW = slot.width * scale;
                float slotScreenH = slot.height * scale;
                
                // Use a fairly transparent white color for the highlight
                glColor4f(1f, 1f, 1f, 0.3f);
                glBegin(GL_QUADS);
                glVertex2f(slotScreenX, slotScreenY);
                glVertex2f(slotScreenX + slotScreenW, slotScreenY);
                glVertex2f(slotScreenX + slotScreenW, slotScreenY + slotScreenH);
                glVertex2f(slotScreenX, slotScreenY + slotScreenH);
                glEnd();
                
                break; // Only highlight one slot at a time
            }
        }
    }
    
    /**
     * Draws items in all inventory slots.
     * This includes the hotbar (slots 0-8) and main inventory (slots 9-35).
     */
    private void drawInventoryItems(float guiX, float guiY, float scale) {
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        
        // Standard Minecraft slot size in GUI, increased by 20% for better visibility
        float itemSize = 19.2f;
        
        // Draw items in hotbar (slots 0-8, at the bottom of the inventory GUI)
        float hotbarX = 8f;
        float hotbarY = 142f;
        for (int i = 0; i < 9; i++) {
            mattmc.world.item.ItemStack stack = inventory.getStack(i);
            if (stack != null && stack.getItem() != null) {
                // Calculate screen position for this slot
                float slotCenterX = guiX + (hotbarX + i * 18f + 8f) * scale;
                float slotCenterY = guiY + (hotbarY + 14f) * scale;
                
                // Render the item
                mattmc.client.renderer.ItemRenderer.renderItem(stack, slotCenterX, slotCenterY, itemSize);
                
                // Render item count if more than 1
                if (stack.getCount() > 1) {
                    drawItemCount(stack.getCount(), slotCenterX, slotCenterY, scale, itemSize);
                }
            }
        }
        
        // Draw items in main inventory (slots 9-35, 3 rows x 9 columns above hotbar)
        float invX = 8f;
        float invY = 84f;
        for (int slot = 9; slot < 36; slot++) {
            mattmc.world.item.ItemStack stack = inventory.getStack(slot);
            if (stack != null && stack.getItem() != null) {
                // Calculate row and column in main inventory
                int invIndex = slot - 9; // 0-26
                int row = invIndex / 9;
                int col = invIndex % 9;
                
                // Calculate screen position for this slot
                float slotCenterX = guiX + (invX + col * 18f + 8f) * scale;
                float slotCenterY = guiY + (invY + row * 18f + 14f) * scale;
                
                // Render the item
                mattmc.client.renderer.ItemRenderer.renderItem(stack, slotCenterX, slotCenterY, itemSize);
                
                // Render item count if more than 1
                if (stack.getCount() > 1) {
                    drawItemCount(stack.getCount(), slotCenterX, slotCenterY, scale, itemSize);
                }
            }
        }
    }
    
    /**
     * Draw item count text in the bottom-right corner of an item slot.
     */
    private void drawItemCount(int count, float itemCenterX, float itemCenterY, float guiScale, float itemSize) {
        String countText = String.valueOf(count);
        
        // Slots are 16x16 in GUI coordinates, scaled by guiScale
        // The item is centered in the slot
        // We want the text at the bottom-right of the slot
        float slotSize = 16f * guiScale;
        float halfSlot = slotSize / 2f;
        
        // Position text in bottom-right corner of the slot
        float textScale = 1.0f;
        // Offset from item center to bottom-right of slot
        // X: Move right to edge, then back a bit for padding
        float textX = itemCenterX + halfSlot - 12f;
        // Y: Move down to bottom edge, then up a bit for padding
        // Since text origin is at top-left of text, we need to position it higher
        float textY = itemCenterY + halfSlot - 30f; // Moved up from -10f to -18f
        
        // Draw text with shadow for better visibility
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw shadow (offset slightly)
        glColor4f(0.25f, 0.25f, 0.25f, 1.0f);
        mattmc.client.gui.components.TextRenderer.drawText(countText, textX + 1, textY + 1, textScale);
        
        // Draw main text
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mattmc.client.gui.components.TextRenderer.drawText(countText, textX, textY, textScale);
        
        glDisable(GL_BLEND);
    }
    
    /**
     * Draw the item being held by the mouse cursor.
     */
    private void drawHeldItem() {
        if (heldItem == null) {
            return;
        }
        
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
        
        // Render the held item centered on the mouse cursor
        float itemSize = 19.2f;
        mattmc.client.renderer.ItemRenderer.renderItem(heldItem, mouseFBX, mouseFBY, itemSize);
        
        // Render item count if more than 1
        if (heldItem.getCount() > 1) {
            drawItemCount(heldItem.getCount(), mouseFBX, mouseFBY, 1.0f, itemSize);
        }
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }

    @Override
    public void onOpen() {}
    
    @Override
    public void onClose() {
        // Clear GLFW callbacks to prevent memory leaks
        glfwSetCursorPosCallback(window.handle(), null);
        glfwSetMouseButtonCallback(window.handle(), null);
        glfwSetKeyCallback(window.handle(), null);
        glfwSetFramebufferSizeCallback(window.handle(), null);
        
        if (inventoryTexture != null) {
            inventoryTexture.close();
            inventoryTexture = null;
        }
        if (blurEffect != null) {
            blurEffect.close();
            blurEffect = null;
        }
    }
}
