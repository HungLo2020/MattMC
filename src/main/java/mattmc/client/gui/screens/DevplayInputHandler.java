package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.world.entity.player.BlockInteraction;
import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.entity.player.PlayerInput;
import mattmc.world.entity.player.PlayerController;
import mattmc.world.level.Level;
import mattmc.client.renderer.UIRenderer;
import mattmc.world.item.ItemStack;

/**
 * Handles input callbacks for the Devplay screen.
 * Manages keyboard and mouse input, delegates to appropriate handlers.
 */
public class DevplayInputHandler {
    private final Minecraft game;
    private final WindowHandle window;
    private final RenderBackend backend;
    private final LocalPlayer player;
    private final Level world;
    private final BlockInteraction blockInteraction;
    private final DevplayUIState uiState;
    private final CommandSystem commandSystem;
    private final PlayerController playerController;
    private final UIRenderer uiRenderer;
    
    // Callback to request pause menu
    private final Runnable onPauseRequested;
    // Callback to open inventory
    private final Runnable onInventoryRequested;
    
    public DevplayInputHandler(Minecraft game, WindowHandle window, RenderBackend backend, 
                               LocalPlayer player, Level world, 
                               BlockInteraction blockInteraction, DevplayUIState uiState,
                               CommandSystem commandSystem, PlayerController playerController,
                               UIRenderer uiRenderer, Runnable onPauseRequested,
                               Runnable onInventoryRequested) {
        this.game = game;
        this.window = window;
        this.backend = backend;
        this.player = player;
        this.world = world;
        this.blockInteraction = blockInteraction;
        this.uiState = uiState;
        this.commandSystem = commandSystem;
        this.playerController = playerController;
        this.uiRenderer = uiRenderer;
        this.onPauseRequested = onPauseRequested;
        this.onInventoryRequested = onInventoryRequested;
    }
    
    /**
     * Register all input callbacks.
     * Called from constructor and when returning from pause menu.
     */
    public void registerCallbacks() {
        // Capture mouse for FPS-style controls
        backend.setCursorMode(window.handle(), RenderBackend.CURSOR_DISABLED);
        
        // Resize -> update viewport
        backend.setFramebufferSizeCallback(window.handle(), (w, h) -> {
            backend.setViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        });

        // Setup character callback for command input
        backend.setCharCallback(window.handle(), (codepoint) -> {
            if (uiState.isCommandOverlayVisible() && codepoint < 128) {
                uiState.appendToCommand((char) codepoint);
            }
        });
        
        // ESC to release mouse and go back; Space for jumping/flying; F3 to toggle debug menu; F4 to toggle lighting debug; / to toggle command overlay
        backend.setKeyCallback(window.handle(), (key, scancode, action, mods) -> {
            if (uiState.isCommandOverlayVisible()) {
                handleCommandOverlayInput(key, action);
            } else {
                handleGameInput(key, action);
            }
        });
        
        // Mouse callback for looking around
        backend.setCursorPosCallback(window.handle(), (xpos, ypos) -> {
            playerController.handleMouseMovement(xpos, ypos);
        });
        
        // Setup mouse button callback for block interaction
        backend.setMouseButtonCallback(window.handle(), (button, action, mods) -> {
            if (!uiState.isCommandOverlayVisible()) {
                handleMouseInput(button, action);
            }
        });
        
        // Setup scroll callback for hotbar scrolling
        backend.setScrollCallback(window.handle(), (xOffset, yOffset) -> {
            if (!uiState.isCommandOverlayVisible()) {
                handleScroll(yOffset);
            }
        });
    }
    
    private void handleCommandOverlayInput(int key, int action) {
        if (action == RenderBackend.ACTION_PRESS || action == RenderBackend.ACTION_REPEAT) {
            if (key == RenderBackend.KEY_ESCAPE) {
                // Close command overlay without executing
                uiState.closeCommandOverlay();
            } else if (key == RenderBackend.KEY_ENTER) {
                // Execute command and close overlay
                String feedback = commandSystem.executeCommand(uiState.getCommandText());
                if (feedback != null) {
                    uiState.setCommandFeedback(feedback, 3.0);
                }
                uiState.closeCommandOverlay();
            } else if (key == RenderBackend.KEY_BACKSPACE) {
                uiState.deleteFromCommand();
            }
        }
    }
    
    private void handleGameInput(int key, int action) {
        if (action == RenderBackend.ACTION_PRESS) {
            if (key == RenderBackend.KEY_ESCAPE) {
                // Open pause menu
                onPauseRequested.run();
            } else if (key == RenderBackend.KEY_SPACE) {
                // Handle jump/fly
                playerController.handleSpacePress();
            } else if (key == RenderBackend.KEY_F3) {
                // Toggle debug menu
                uiState.toggleDebugMenu();
            } else if (key == RenderBackend.KEY_F4) {
                // Toggle lighting debug overlay
                uiState.toggleLightingDebug();
            } else if (key == RenderBackend.KEY_SLASH) {
                // Open command overlay
                uiState.openCommandOverlay();
            }
            
            // Check for inventory key (respects user configuration)
            Integer inventoryKey = PlayerInput.getInstance().getKeybind(PlayerInput.INVENTORY);
            if (inventoryKey != null && key == inventoryKey) {
                onInventoryRequested.run();
            }
            
            // Check for hotbar selection keys (1-9)
            PlayerInput input = PlayerInput.getInstance();
            String[] hotbarActions = {
                PlayerInput.HOTBAR_1, PlayerInput.HOTBAR_2, PlayerInput.HOTBAR_3,
                PlayerInput.HOTBAR_4, PlayerInput.HOTBAR_5, PlayerInput.HOTBAR_6,
                PlayerInput.HOTBAR_7, PlayerInput.HOTBAR_8, PlayerInput.HOTBAR_9
            };
            
            for (int i = 0; i < hotbarActions.length; i++) {
                Integer hotbarKey = input.getKeybind(hotbarActions[i]);
                if (hotbarKey != null && key == hotbarKey) {
                    // Select hotbar slot (0-indexed) - sync both UIRenderer and player inventory
                    uiRenderer.setSelectedHotbarSlot(i);
                    player.getInventory().setSelectedSlot(i);
                    break;
                }
            }
        }
    }
    
    private void handleMouseInput(int button, int action) {
        if (action == RenderBackend.ACTION_PRESS) {
            if (button == RenderBackend.MOUSE_BUTTON_LEFT) {
                // Break block
                blockInteraction.breakBlock();
            } else if (button == RenderBackend.MOUSE_BUTTON_RIGHT) {
                // Place block from hotbar
                ItemStack selectedStack = player.getInventory().getSelectedStack();
                if (selectedStack != null) {
                    // Try to use the item
                    selectedStack.getItem().onUse(blockInteraction);
                }
            } else if (button == RenderBackend.MOUSE_BUTTON_MIDDLE) {
                // Pick block - raycast and add to inventory
                blockInteraction.pickBlock();
            }
        }
    }
    
    /**
     * Handle mouse scroll for hotbar slot selection.
     * Scroll up selects previous slot, scroll down selects next slot.
     * 
     * @param yOffset Scroll offset (positive = scroll up, negative = scroll down)
     */
    private void handleScroll(double yOffset) {
        int currentSlot = player.getInventory().getSelectedSlot();
        int newSlot;
        
        if (yOffset > 0) {
            // Scroll up = select previous slot (with wrapping)
            newSlot = (currentSlot - 1 + 9) % 9;
        } else if (yOffset < 0) {
            // Scroll down = select next slot (with wrapping)
            newSlot = (currentSlot + 1) % 9;
        } else {
            // No scroll
            return;
        }
        
        // Sync both UIRenderer and player inventory
        uiRenderer.setSelectedHotbarSlot(newSlot);
        player.getInventory().setSelectedSlot(newSlot);
    }
}
