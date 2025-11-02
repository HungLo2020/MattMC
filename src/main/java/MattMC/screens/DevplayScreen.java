package MattMC.screens;

import MattMC.command.CommandHandler;
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
    private final CommandHandler commandHandler;
    
    private double lastFrameTimeSec = now();
    private boolean showDebugInfo = false; // F3 debug menu toggle
    
    // FPS tracking
    private int fps = 0;
    private int frameCount = 0;
    private double lastFpsUpdateTime = now();
    
    // Command overlay state
    private boolean commandOverlayActive = false;
    private StringBuilder commandInput = new StringBuilder();
    private String commandResult = "";
    private double commandResultTime = 0;

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
        this.commandHandler = new CommandHandler(player);

        // Capture mouse for FPS-style controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        
        // Resize -> update viewport
        glfwSetFramebufferSizeCallback(window.handle(), (win, w, h) -> {
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        });

        // ESC to release mouse and go back; Space for jumping/flying; F3 for debug menu; / for chat/commands
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (commandOverlayActive) {
                handleCommandInput(key, action);
            } else {
                if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                    glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    game.setScreen(new SingleplayerScreen(game));
                }
                if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
                    playerController.handleSpacePress();
                }
                if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
                    showDebugInfo = !showDebugInfo;
                }
                if (key == GLFW_KEY_SLASH && action == GLFW_PRESS) {
                    openCommandOverlay();
                }
            }
        });
        
        // Character callback for text input in command overlay
        glfwSetCharCallback(window.handle(), (win, codepoint) -> {
            if (commandOverlayActive) {
                commandInput.append((char) codepoint);
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

    @Override
    public void tick() {
        double now = now();
        double dt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (dt < 0) dt = 0;
        if (dt > 0.5) dt = 0.5;
        
        // Update FPS counter
        frameCount++;
        if (now - lastFpsUpdateTime >= 1.0) {
            fps = frameCount;
            frameCount = 0;
            lastFpsUpdateTime = now;
        }
        
        // Update chunks based on player position (load/unload)
        world.updateChunksAroundPlayer(player.getX(), player.getZ());
        
        // Update physics (gravity, collision)
        playerPhysics.update((float)dt);
        
        // Update player movement based on input (only if command overlay is not active)
        if (!commandOverlayActive) {
            playerController.updateMovement(window.handle(), (float)dt);
        }
        
        // Clear command result after 5 seconds
        if (commandResult != null && !commandResult.isEmpty() && now - commandResultTime > 5.0) {
            commandResult = "";
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
        
        // Draw crosshair on top of everything
        uiRenderer.drawCrosshair(w, h);
        
        // Draw debug info if enabled (F3)
        if (showDebugInfo) {
            uiRenderer.drawDebugInfo(w, h, fps, player.getX(), player.getY(), player.getZ());
        }
        
        // Draw command overlay if active
        if (commandOverlayActive) {
            uiRenderer.drawCommandOverlay(w, h, commandInput.toString(), commandResult);
        }
    }
    
    /**
     * Open command overlay for text input.
     */
    private void openCommandOverlay() {
        commandOverlayActive = true;
        commandInput.setLength(0);
        commandResult = "";
    }
    
    /**
     * Close command overlay and restore normal input.
     */
    private void closeCommandOverlay() {
        commandOverlayActive = false;
        commandInput.setLength(0);
    }
    
    /**
     * Handle keyboard input when command overlay is active.
     */
    private void handleCommandInput(int key, int action) {
        if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            if (key == GLFW_KEY_ENTER) {
                // Execute command
                String cmd = commandInput.toString().trim();
                if (!cmd.isEmpty()) {
                    commandResult = commandHandler.executeCommand(cmd);
                    commandResultTime = now();
                }
                closeCommandOverlay();
            } else if (key == GLFW_KEY_ESCAPE) {
                // Cancel command input
                closeCommandOverlay();
            } else if (key == GLFW_KEY_BACKSPACE && commandInput.length() > 0) {
                // Delete last character
                commandInput.deleteCharAt(commandInput.length() - 1);
            }
        }
    }

    private static double now() { return System.nanoTime() * 1e-9; }

    @Override public void onOpen() {}
    @Override public void onClose() {}
}
