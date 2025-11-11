package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.gui.screens.inventory.CreativeInventoryManager;
import mattmc.client.gui.screens.inventory.InventoryInputHandler;
import mattmc.client.gui.screens.inventory.InventoryRenderer;
import mattmc.client.gui.screens.inventory.InventorySlotManager;
import mattmc.world.entity.player.PlayerInput;
import mattmc.world.item.Item;
import mattmc.world.item.ItemStack;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Inventory screen overlay - displays the inventory.png centered on screen.
 * Also displays creative inventory on the right for item selection.
 * Does not pause the game. Press E (or configured inventory key) to close.
 * 
 * Refactored to use component-based architecture with clear separation of concerns.
 */
public final class InventoryScreen implements Screen {
    private static final float GUI_SCALE = 3.0f;
    private static final float CONTENT_OFFSET_X = 40f;
    private static final float CONTENT_OFFSET_Y = 45f;
    
    private final Minecraft game;
    private final Window window;
    private final DevplayScreen gameScreen;
    
    // Mouse tracking for slot highlighting
    private double mouseXWin, mouseYWin;
    
    // Component-based architecture
    private final InventorySlotManager slotManager;
    private final InventoryInputHandler inputHandler;
    private final CreativeInventoryManager creativeManager;
    private final InventoryRenderer renderer;

    public InventoryScreen(Minecraft game, DevplayScreen gameScreen) {
        this.game = game;
        this.window = game.window();
        this.gameScreen = gameScreen;
        
        // Sync player position to prevent flickering during interpolation
        gameScreen.syncPlayerPosition();

        // Initialize components
        this.slotManager = new InventorySlotManager();
        this.inputHandler = new InventoryInputHandler(slotManager);
        this.creativeManager = new CreativeInventoryManager();
        this.renderer = new InventoryRenderer(window, slotManager);
        
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
                    // Check if this is the delete item key
                    Integer deleteKey = PlayerInput.getInstance().getKeybind(PlayerInput.DELETE_ITEM);
                    if (deleteKey != null && key == deleteKey) {
                        handleDeleteItem();
                    }
                }
            }
        });
        
        // Handle scroll wheel for creative inventory
        glfwSetScrollCallback(window.handle(), (win, xoffset, yoffset) -> {
            creativeManager.handleScroll(yoffset);
        });

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
        });
    }

    private void closeInventory() {
        // Return held item to inventory if any
        ItemStack heldItem = inputHandler.getHeldItem();
        if (heldItem != null) {
            mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
            if (player != null && player.getInventory() != null) {
                mattmc.world.item.Inventory inventory = player.getInventory();
                
                // Try to add the held item back to inventory
                boolean added = inventory.addItem(heldItem);
                if (!added) {
                    // If inventory is full, item will be lost
                    // In production, consider dropping it in the world
                }
            }
            
            // Clear held item state
            inputHandler.clearHeldItem();
        }
        
        // Recapture mouse for FPS controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        game.setScreen(gameScreen);
    }
    
    private void handleLeftClick(int mods) {
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        
        // Check if clicking on creative inventory first
        int creativeItemIndex = creativeManager.findClickedCreativeItem(mouseXWin, mouseYWin, window);
        if (creativeItemIndex >= 0 && creativeItemIndex < creativeManager.getAllItems().size()) {
            creativeManager.handleCreativeItemClick(inventory, creativeItemIndex);
            return;
        }
        
        // Otherwise handle normal inventory click
        if (renderer.getInventoryTexture() != null) {
            float texWidth = renderer.getInventoryTexture().width * GUI_SCALE;
            float texHeight = renderer.getInventoryTexture().height * GUI_SCALE;
            float guiX = (window.width() - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
            float guiY = (window.height() - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
            
            int slotIndex = slotManager.findClickedSlot(mouseXWin, mouseYWin, guiX, guiY, GUI_SCALE, window);
            if (slotIndex >= 0) {
                inputHandler.handleLeftClick(inventory, slotIndex, mods);
            }
        }
    }
    
    private void handleRightClick(int mods) {
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        
        if (renderer.getInventoryTexture() != null) {
            float texWidth = renderer.getInventoryTexture().width * GUI_SCALE;
            float texHeight = renderer.getInventoryTexture().height * GUI_SCALE;
            float guiX = (window.width() - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
            float guiY = (window.height() - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
            
            int slotIndex = slotManager.findClickedSlot(mouseXWin, mouseYWin, guiX, guiY, GUI_SCALE, window);
            inputHandler.handleRightClick(inventory, slotIndex);
        }
    }
    
    private void handleDeleteItem() {
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player == null || player.getInventory() == null) {
            return;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        
        if (renderer.getInventoryTexture() != null) {
            float texWidth = renderer.getInventoryTexture().width * GUI_SCALE;
            float texHeight = renderer.getInventoryTexture().height * GUI_SCALE;
            float guiX = (window.width() - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
            float guiY = (window.height() - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
            
            int slotIndex = slotManager.findClickedSlot(mouseXWin, mouseYWin, guiX, guiY, GUI_SCALE, window);
            inputHandler.handleDeleteItem(inventory, slotIndex);
        }
    }
    
    private Item getHoveredItem() {
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player == null || player.getInventory() == null) {
            return null;
        }
        
        mattmc.world.item.Inventory inventory = player.getInventory();
        
        // Check creative inventory first
        int creativeItemIndex = creativeManager.findClickedCreativeItem(mouseXWin, mouseYWin, window);
        if (creativeItemIndex >= 0) {
            return creativeManager.getItemAt(creativeItemIndex);
        }
        
        // Check regular inventory slots
        if (renderer.getInventoryTexture() != null) {
            float texWidth = renderer.getInventoryTexture().width * GUI_SCALE;
            float texHeight = renderer.getInventoryTexture().height * GUI_SCALE;
            float guiX = (window.width() - texWidth) / 2f + (CONTENT_OFFSET_X * GUI_SCALE);
            float guiY = (window.height() - texHeight) / 2f + (CONTENT_OFFSET_Y * GUI_SCALE);
            
            int slotIndex = slotManager.findClickedSlot(mouseXWin, mouseYWin, guiX, guiY, GUI_SCALE, window);
            if (slotIndex >= 0) {
                ItemStack stack = inventory.getStack(slotIndex);
                if (stack != null) {
                    return stack.getItem();
                }
            }
        }
        
        return null;
    }

    @Override
    public void tick() {
        // Update game world in background
        gameScreen.tick();
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();
        
        // Disable HUD overlays (like BlockNameHUD) when rendering game screen as background
        gameScreen.setRenderHudOverlays(false);
        
        // First render the game screen behind this overlay
        gameScreen.render(alpha);
        
        // Re-enable HUD overlays for when we return to game
        gameScreen.setRenderHudOverlays(true);
        
        // Render background overlay with blur
        renderer.renderBackground(w, h);
        
        // Draw inventory texture centered
        renderer.renderInventoryBackground();
        renderer.renderSlotHighlight(mouseXWin, mouseYWin);
        
        // Draw items in inventory slots
        mattmc.world.entity.player.LocalPlayer player = gameScreen.getPlayer();
        if (player != null && player.getInventory() != null) {
            renderer.renderInventoryItems(player.getInventory());
        }
        
        // Draw creative inventory on the right side
        renderer.renderCreativeInventory(creativeManager.getAllItems(), creativeManager.getScrollRow());
        renderer.renderCreativeHoverHighlight(mouseXWin, mouseYWin, creativeManager.getAllItems(), creativeManager.getScrollRow());
        
        // Draw held item under mouse cursor
        renderer.renderHeldItem(inputHandler.getHeldItem(), mouseXWin, mouseYWin);
        
        // Draw tooltip for hovered item (but not when holding an item)
        if (inputHandler.getHeldItem() == null) {
            Item hoveredItem = getHoveredItem();
            renderer.renderTooltip(hoveredItem, mouseXWin, mouseYWin);
        }
        
        glDisable(GL_BLEND);
    }

    @Override
    public void onOpen() {}
    
    @Override
    public void onClose() {
        // Clear GLFW callbacks to prevent memory leaks
        glfwSetCursorPosCallback(window.handle(), null);
        glfwSetMouseButtonCallback(window.handle(), null);
        glfwSetKeyCallback(window.handle(), null);
        glfwSetScrollCallback(window.handle(), null);
        glfwSetFramebufferSizeCallback(window.handle(), null);
        
        // Close renderer resources
        renderer.close();
    }
}
