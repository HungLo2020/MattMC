package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.player.BlockInteraction;
import MattMC.player.Player;
import MattMC.player.PlayerController;
import MattMC.player.PlayerPhysics;
import MattMC.renderer.WorldRenderer;
import MattMC.renderer.UIRenderer;
import MattMC.world.Blocks;
import MattMC.world.World;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Devplay screen - infinite world with dynamic chunk loading.
 * Chunks load/unload based on player position.
 * ESC returns to Singleplayer.
 */
public final class DevplayScreen implements Screen {
    private final Game game;
    private final Window window;
    
    // Game components (following Minecraft's architecture)
    private final World world;
    private final Player player;
    private final PlayerController playerController;
    private final PlayerPhysics playerPhysics;
    private final BlockInteraction blockInteraction;
    private final WorldRenderer worldRenderer;
    private final UIRenderer uiRenderer;
    
    private double lastFrameTimeSec = now();
    
    // Debug menu toggle state
    private boolean debugMenuVisible = false;
    
    // Command overlay state
    private boolean commandOverlayVisible = false;
    private StringBuilder commandText = new StringBuilder("/");
    private String commandErrorMessage = "";
    private double commandErrorDisplayTime = 0;

    public DevplayScreen(Game game) {
        this.game = game;
        this.window = game.window();
        
        // Initialize infinite world
        this.world = new World();
        
        // Initialize player - spawn at world origin
        float spawnX = 0f;
        float spawnZ = 0f;
        
        // Pre-load spawn chunks before finding spawn height
        world.updateChunksAroundPlayer(spawnX, spawnZ);
        
        // Find proper spawn height on top of terrain
        float spawnY = PlayerPhysics.findSpawnHeight(world, spawnX, spawnZ);
        
        this.player = new Player(spawnX, spawnY, spawnZ);
        this.playerPhysics = new PlayerPhysics(player, world);
        this.player.setPhysics(playerPhysics);
        this.playerController = new PlayerController(player);
        this.blockInteraction = new BlockInteraction(player, world);
        this.worldRenderer = new WorldRenderer();
        this.uiRenderer = new UIRenderer();

        // Capture mouse for FPS-style controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        
        // Resize -> update viewport
        glfwSetFramebufferSizeCallback(window.handle(), (win, w, h) -> {
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        });

        // Setup character callback for command input
        glfwSetCharCallback(window.handle(), (win, codepoint) -> {
            if (commandOverlayVisible && commandText.length() < 100) {
                commandText.append((char) codepoint);
            }
        });
        
        // ESC to release mouse and go back; Space for jumping/flying; F3 to toggle debug menu; / to toggle command overlay
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (commandOverlayVisible) {
                // Command overlay is open - handle command input
                if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                    if (key == GLFW_KEY_ESCAPE) {
                        // Close command overlay
                        closeCommandOverlay();
                    } else if (key == GLFW_KEY_ENTER) {
                        // Execute command and close
                        executeCommand();
                        closeCommandOverlay();
                    } else if (key == GLFW_KEY_BACKSPACE) {
                        // Delete last character
                        if (commandText.length() > 0) {
                            commandText.deleteCharAt(commandText.length() - 1);
                        }
                    }
                }
            } else {
                // Normal game input
                if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                    glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    game.setScreen(new SingleplayerScreen(game));
                }
                if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
                    playerController.handleSpacePress();
                }
                if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
                    debugMenuVisible = !debugMenuVisible;
                }
                if (key == GLFW_KEY_SLASH && action == GLFW_PRESS) {
                    // Toggle command overlay
                    openCommandOverlay();
                }
            }
        });
        
        // Mouse callback for looking around
        glfwSetCursorPosCallback(window.handle(), (win, xpos, ypos) -> {
            playerController.handleMouseMovement(xpos, ypos);
        });
        
        // Mouse button callback for breaking/placing blocks
        glfwSetMouseButtonCallback(window.handle(), (win, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    blockInteraction.breakBlock();
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    blockInteraction.placeBlock(Blocks.STONE);
                }
            }
        });
    }

    private void openCommandOverlay() {
        commandOverlayVisible = true;
        commandText = new StringBuilder();
        commandErrorMessage = "";
        commandErrorDisplayTime = 0;
        // Keep cursor captured but allow typing
    }
    
    private void closeCommandOverlay() {
        commandOverlayVisible = false;
        commandText = new StringBuilder();
    }
    
    private void executeCommand() {
        String cmd = commandText.toString().trim();
        
        // Empty command, just close
        if (cmd.isEmpty() || cmd.equals("/")) {
            return;
        }
        
        // Ensure command starts with /
        if (!cmd.startsWith("/")) {
            commandErrorMessage = "Commands must start with /";
            commandErrorDisplayTime = 3.0;
            return;
        }
        
        // Parse and execute command
        if (cmd.startsWith("/tp ")) {
            executeTeleportCommand(cmd);
        } else {
            commandErrorMessage = "Unknown command: " + cmd;
            commandErrorDisplayTime = 3.0; // Show error for 3 seconds
        }
    }
    
    private void executeTeleportCommand(String cmd) {
        try {
            // Parse: /tp x y z
            String[] parts = cmd.substring(4).trim().split("\\s+");
            
            if (parts.length != 3) {
                commandErrorMessage = "Usage: /tp x y z";
                commandErrorDisplayTime = 3.0;
                return;
            }
            
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            float z = Float.parseFloat(parts[2]);
            
            // Teleport the player
            player.setX(x);
            player.setY(y);
            player.setZ(z);
            
            System.out.println("Teleported to: " + x + ", " + y + ", " + z);
            
        } catch (NumberFormatException e) {
            commandErrorMessage = "Invalid coordinates. Usage: /tp x y z";
            commandErrorDisplayTime = 3.0;
        }
    }

    @Override
    public void tick() {
        double now = now();
        double dt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (dt < 0) dt = 0;
        if (dt > 0.5) dt = 0.5;
        
        // Update chunks based on player position (load/unload)
        world.updateChunksAroundPlayer(player.getX(), player.getZ());
        
        // Update physics (gravity, collision) - only if command overlay is not visible
        if (!commandOverlayVisible) {
            playerPhysics.update((float)dt);
        }
        
        // Update player movement based on input - only if command overlay is not visible
        if (!commandOverlayVisible) {
            playerController.updateMovement(window.handle(), (float)dt);
        }
        
        // Decrease error display time
        if (commandErrorDisplayTime > 0) {
            commandErrorDisplayTime -= dt;
        }
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();

        // Clear color + depth (sky blue)
        glClearColor(0.53f, 0.81f, 0.92f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Perspective projection
        float aspect = Math.max(1f, (float) w / Math.max(1, h));
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float fov = 70f, zn = 0.1f, zf = 500f;
        float top = (float) (Math.tan(Math.toRadians(fov * 0.5)) * zn);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        glFrustum(left, right, bottom, top, zn, zf);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Apply camera transformations (pitch, yaw, then position)
        // Camera is at eye level (1.62 blocks above feet)
        glRotatef(player.getPitch(), 1f, 0f, 0f);
        glRotatef(player.getYaw(), 0f, 1f, 0f);
        glTranslatef(-player.getX(), -player.getEyeY(), -player.getZ());

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        
        // Render all loaded chunks in the infinite world
        worldRenderer.render(world, player.getX(), player.getEyeY(), player.getZ());
        
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        
        // Draw debug information in top-left corner (only if F3 is pressed)
        if (debugMenuVisible) {
            uiRenderer.drawDebugInfo(w, h, player.getX(), player.getY(), player.getZ());
        }
        
        // Draw command overlay if visible
        if (commandOverlayVisible) {
            uiRenderer.drawCommandOverlay(w, h, commandText.toString(), commandErrorMessage, commandErrorDisplayTime > 0);
        }
        
        // Draw crosshair on top of everything (but not when command overlay is open)
        if (!commandOverlayVisible) {
            uiRenderer.drawCrosshair(w, h);
        }
    }

    private static double now() { return System.nanoTime() * 1e-9; }

    @Override public void onOpen() {}
    @Override public void onClose() {}
}
