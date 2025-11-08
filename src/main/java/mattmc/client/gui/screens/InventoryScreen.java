package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.renderer.BlurEffect;
import mattmc.client.renderer.BlurRenderer;
import mattmc.client.renderer.texture.Texture;
import mattmc.world.entity.player.PlayerInput;
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
    private final Minecraft game;
    private final Window window;
    private final DevplayScreen gameScreen;
    private Texture inventoryTexture;
    
    // Blur effect for background
    private BlurEffect blurEffect;
    
    // Mouse tracking for slot highlighting
    private double mouseXWin, mouseYWin;
    private final List<InventorySlot> slots = new ArrayList<>();
    
    // Helper class to represent an inventory slot
    private static class InventorySlot {
        final float x, y, width, height; // Relative to 176x166 GUI coordinate system
        
        InventorySlot(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
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

        // Disable mouse button callback to prevent block interaction
        glfwSetMouseButtonCallback(window.handle(), null);

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
        // Recapture mouse for FPS controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        game.setScreen(gameScreen);
    }
    
    /**
     * Initialize all inventory slot positions based on standard Minecraft inventory layout.
     * Coordinates are relative to the 176x166 GUI coordinate system.
     */
    private void initializeSlots() {
        slots.clear();
        
        // Slot dimensions (standard Minecraft slot size)
        float slotSize = 16f;
        
        // Armor slots (4 slots, vertical on left side)
        float armorX = 8f;
        float armorY = 8f;
        for (int i = 0; i < 4; i++) {
            slots.add(new InventorySlot(armorX, armorY + i * 18f, slotSize, slotSize));
        }
        
        // Crafting grid (2x2 slots)
        float craftX = 98f;
        float craftY = 18f;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                slots.add(new InventorySlot(craftX + col * 18f, craftY + row * 18f, slotSize, slotSize));
            }
        }
        
        // Crafting output slot
        slots.add(new InventorySlot(154f, 28f, slotSize, slotSize));
        
        // Main inventory (3 rows x 9 columns)
        float invX = 8f;
        float invY = 84f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slots.add(new InventorySlot(invX + col * 18f, invY + row * 18f, slotSize, slotSize));
            }
        }
        
        // Hotbar (1 row x 9 columns)
        float hotbarX = 8f;
        float hotbarY = 142f;
        for (int col = 0; col < 9; col++) {
            slots.add(new InventorySlot(hotbarX + col * 18f, hotbarY, slotSize, slotSize));
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
            float scale = 3.0f;
            // The inventory texture is 256x256 but the actual content is ~176x166 centered
            // We need to offset by the empty space around the content
            float contentOffsetX = 40f; // Offset from left edge of texture to content
            float contentOffsetY = 45f; // Offset from top edge of texture to content
            float texWidth = inventoryTexture.width * scale;
            float texHeight = inventoryTexture.height * scale;
            float x = (w - texWidth) / 2f + (contentOffsetX * scale);
            float y = (h - texHeight) / 2f + (contentOffsetY * scale);
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(x, y);
            glTexCoord2f(1, 1); glVertex2f(x + texWidth, y);
            glTexCoord2f(1, 0); glVertex2f(x + texWidth, y + texHeight);
            glTexCoord2f(0, 0); glVertex2f(x, y + texHeight);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
            
            // Draw slot highlight if mouse is over a slot
            drawSlotHighlight(x, y, scale);
            
            // Draw items in inventory slots
            drawInventoryItems(x, y, scale);
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
        
        // Standard Minecraft slot size in GUI
        float itemSize = 16f;
        
        // Draw items in hotbar (slots 0-8, at the bottom of the inventory GUI)
        float hotbarX = 8f;
        float hotbarY = 142f;
        for (int i = 0; i < 9; i++) {
            mattmc.world.item.ItemStack stack = inventory.getStack(i);
            if (stack != null && stack.getItem() != null) {
                // Calculate screen position for this slot
                float slotCenterX = guiX + (hotbarX + i * 18f + 8f) * scale;
                float slotCenterY = guiY + (hotbarY + 12f) * scale;
                
                // Render the item
                mattmc.client.renderer.ItemRenderer.renderItem(stack, slotCenterX, slotCenterY, itemSize);
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
                float slotCenterY = guiY + (invY + row * 18f + 12f) * scale;
                
                // Render the item
                mattmc.client.renderer.ItemRenderer.renderItem(stack, slotCenterX, slotCenterY, itemSize);
            }
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
