package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.world.entity.player.BlockInteraction;
import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.entity.player.PlayerInput;
import mattmc.world.entity.player.PlayerController;
import mattmc.world.level.Level;
import mattmc.client.renderer.UIRenderer;
import mattmc.world.item.ItemStack;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Handles input callbacks for the Devplay screen.
 * Manages keyboard and mouse input, delegates to appropriate handlers.
 */
public class DevplayInputHandler {
    private final Minecraft game;
    private final Window window;
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
    
    public DevplayInputHandler(Minecraft game, Window window, LocalPlayer player, Level world, 
                               BlockInteraction blockInteraction, DevplayUIState uiState,
                               CommandSystem commandSystem, PlayerController playerController,
                               UIRenderer uiRenderer, Runnable onPauseRequested,
                               Runnable onInventoryRequested) {
        this.game = game;
        this.window = window;
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
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        
        // Resize -> update viewport
        glfwSetFramebufferSizeCallback(window.handle(), (win, w, h) -> {
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        });

        // Setup character callback for command input
        glfwSetCharCallback(window.handle(), (win, codepoint) -> {
            if (uiState.isCommandOverlayVisible() && codepoint < 128) {
                uiState.appendToCommand((char) codepoint);
            }
        });
        
        // ESC to release mouse and go back; Space for jumping/flying; F3 to toggle debug menu; F4 to toggle lighting debug; / to toggle command overlay
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (uiState.isCommandOverlayVisible()) {
                handleCommandOverlayInput(key, action);
            } else {
                handleGameInput(key, action);
            }
        });
        
        // Mouse callback for looking around
        glfwSetCursorPosCallback(window.handle(), (win, xpos, ypos) -> {
            playerController.handleMouseMovement(xpos, ypos);
        });
        
        // Setup mouse button callback for block interaction
        glfwSetMouseButtonCallback(window.handle(), (win, button, action, mods) -> {
            if (!uiState.isCommandOverlayVisible()) {
                handleMouseInput(button, action);
            }
        });
    }
    
    private void handleCommandOverlayInput(int key, int action) {
        if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            if (key == GLFW_KEY_ESCAPE) {
                // Close command overlay without executing
                uiState.closeCommandOverlay();
            } else if (key == GLFW_KEY_ENTER) {
                // Execute command and close overlay
                String feedback = commandSystem.executeCommand(uiState.getCommandText());
                if (feedback != null) {
                    uiState.setCommandFeedback(feedback, 3.0);
                }
                uiState.closeCommandOverlay();
            } else if (key == GLFW_KEY_BACKSPACE) {
                uiState.deleteFromCommand();
            }
        }
    }
    
    private void handleGameInput(int key, int action) {
        if (action == GLFW_PRESS) {
            if (key == GLFW_KEY_ESCAPE) {
                // Open pause menu
                onPauseRequested.run();
            } else if (key == GLFW_KEY_SPACE) {
                // Handle jump/fly
                playerController.handleSpacePress();
            } else if (key == GLFW_KEY_F3) {
                // Toggle debug menu
                uiState.toggleDebugMenu();
            } else if (key == GLFW_KEY_F4) {
                // Toggle lighting debug overlay
                uiState.toggleLightingDebug();
            } else if (key == GLFW_KEY_SLASH) {
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
        if (action == GLFW_PRESS) {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                // Break block
                blockInteraction.breakBlock();
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                // Place block from hotbar
                ItemStack selectedStack = player.getInventory().getSelectedStack();
                if (selectedStack != null) {
                    // Try to use the item
                    selectedStack.getItem().onUse(blockInteraction);
                }
            } else if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
                // Pick block - raycast and add to inventory
                blockInteraction.pickBlock();
            }
        }
    }
}
