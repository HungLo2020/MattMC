package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.gui.screens.inventory.CreativeInventoryManager;
import mattmc.client.gui.screens.inventory.InventoryInputHandler;
import mattmc.client.gui.screens.inventory.InventoryRenderer;
import mattmc.client.gui.screens.inventory.InventorySlotManager;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.world.entity.player.PlayerInput;
import mattmc.world.item.Item;
import mattmc.world.item.ItemStack;

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
    private final WindowHandle window;
    private final RenderBackend backend;
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
        this.backend = game.getRenderBackend();
        this.gameScreen = gameScreen;
        
        // Sync player position to prevent flickering during interpolation
        gameScreen.syncPlayerPosition();

        // Initialize components
        this.slotManager = new InventorySlotManager();
        this.inputHandler = new InventoryInputHandler(slotManager);
        this.creativeManager = new CreativeInventoryManager();
        this.renderer = new InventoryRenderer(window, slotManager);
        
        // Set the render backend for rendering operations
        this.renderer.setBackend(backend);
    }
    
    @Override
    public void onOpen() {
        // Release mouse cursor
        backend.setCursorMode(window.handle(), RenderBackend.CURSOR_NORMAL);

        // Track mouse position for slot highlighting
        backend.setCursorPosCallback(window.handle(), (x, y) -> { 
            mouseXWin = x; 
            mouseYWin = y; 
        });

        // Handle mouse button clicks for inventory interaction
        backend.setMouseButtonCallback(window.handle(), (button, action, mods) -> {
            if (action == RenderBackend.ACTION_PRESS) {
                if (button == RenderBackend.MOUSE_BUTTON_LEFT) {
                    handleLeftClick(mods);
                } else if (button == RenderBackend.MOUSE_BUTTON_RIGHT) {
                    handleRightClick(mods);
                }
            }
        });

        // Set up key callback for inventory key (respects user configuration) or ESC to close
        backend.setKeyCallback(window.handle(), (key, scancode, action, mods) -> {
            if (action == RenderBackend.ACTION_PRESS) {
                if (key == RenderBackend.KEY_ESCAPE) {
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
        backend.setScrollCallback(window.handle(), (xoffset, yoffset) -> {
            creativeManager.handleScroll(yoffset);
        });

        backend.setFramebufferSizeCallback(window.handle(), (newW, newH) -> {
            backend.setViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
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
        backend.setCursorMode(window.handle(), RenderBackend.CURSOR_DISABLED);
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
        if (renderer.hasInventoryTexture()) {
            float texWidth = renderer.getInventoryWidth() * GUI_SCALE;
            float texHeight = renderer.getInventoryHeight() * GUI_SCALE;
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
        
        if (renderer.hasInventoryTexture()) {
            float texWidth = renderer.getInventoryWidth() * GUI_SCALE;
            float texHeight = renderer.getInventoryHeight() * GUI_SCALE;
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
        
        if (renderer.hasInventoryTexture()) {
            float texWidth = renderer.getInventoryWidth() * GUI_SCALE;
            float texHeight = renderer.getInventoryHeight() * GUI_SCALE;
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
        if (renderer.hasInventoryTexture()) {
            float texWidth = renderer.getInventoryWidth() * GUI_SCALE;
            float texHeight = renderer.getInventoryHeight() * GUI_SCALE;
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
        
        // First render the game screen behind this overlay
        gameScreen.render(alpha);
        
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
        
        backend.disableBlend();
    }

    @Override
    public void onClose() {
        // Close renderer resources
        renderer.close();
    }
}
