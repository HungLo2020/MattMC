package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.settings.OptionsManager;
import mattmc.world.entity.player.BlockInteraction;
import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.entity.player.PlayerController;
import mattmc.world.entity.player.PlayerPhysics;
import mattmc.client.renderer.LevelRenderer;
import mattmc.client.renderer.UIRenderer;
import mattmc.client.renderer.block.BlockFaceGeometry;
import mattmc.client.renderer.ColorUtils;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import mattmc.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Devplay screen - infinite world with dynamic chunk loading.
 * Chunks load/unload based on player position.
 * ESC opens pause menu.
 */
public final class DevplayScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(DevplayScreen.class);

    private final Minecraft game;
    private final Window window;
    
    // Minecraft components (following Minecraft's architecture)
    private final Level world;
    private final LocalPlayer player;
    private final PlayerController playerController;
    private final PlayerPhysics playerPhysics;
    private final BlockInteraction blockInteraction;
    private final LevelRenderer worldRenderer;
    private final UIRenderer uiRenderer;
    
    // Level name for saving
    private final String worldName;
    
    private double lastFrameTimeSec = now();
    
    // FPS tracking - moved to render() for accurate measurement
    private double fps = 0.0;
    private double fpsUpdateTimer = 0.0;
    private int frameCount = 0;
    private double lastFpsUpdateTime = now();
    
    // Debug menu toggle state
    private boolean debugMenuVisible = false;
    
    // Command overlay state
    private boolean commandOverlayVisible = false;
    private StringBuilder commandText = new StringBuilder("/");
    private String commandErrorMessage = "";
    private double commandErrorDisplayTime = 0;
    
    // Flag to track if world should be shut down on close
    private boolean shouldShutdownWorld = false;

    public DevplayScreen(Minecraft game, String worldName) {
        this(game, worldName, new java.util.Random().nextLong());
    }
    
    public DevplayScreen(Minecraft game, String worldName, long seed) {
        this(game, worldName, null, seed, 0f, 0f, 0f, 0f, 0f);
    }
    
    public DevplayScreen(Minecraft game, String worldName, Level world, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) {
        this(game, worldName, world, new java.util.Random().nextLong(), playerX, playerY, playerZ, playerYaw, playerPitch);
    }
    
    public DevplayScreen(Minecraft game, String worldName, Level world, long seed, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) {
        this.game = game;
        this.window = game.window();
        this.worldName = worldName;
        
        // Initialize infinite world (use provided or create new)
        this.world = world != null ? world : new Level();
        
        // Apply render distance from settings
        int renderDistance = OptionsManager.getRenderDistance();
        this.world.setRenderDistance(renderDistance);
        logger.info("Set render distance to: {}{}", renderDistance, " chunks");
        
        // Set world directory for new worlds so chunks can be saved during unload
        if (world == null) {
            // Use the provided seed for new worlds
            this.world.setSeed(seed);
            logger.info("Created new world with seed: {}", seed);
            
            try {
                Path worldDir = LevelStorageSource.getSavesDirectory().resolve(worldName);
                this.world.setWorldDirectory(worldDir);
            } catch (IOException e) {
                logger.error("Failed to set world directory: {}", e.getMessage());
            }
        }
        
        // Initialize player - use provided position or spawn at world origin
        float spawnX = playerX;
        float spawnY = playerY;
        float spawnZ = playerZ;
        
        if (world == null) {
            // New world - find spawn position
            spawnX = 0f;
            spawnZ = 0f;
            
            // Pre-load spawn chunks before finding spawn height
            this.world.updateChunksAroundPlayer(spawnX, spawnZ);
            
            // Find proper spawn height on top of terrain
            spawnY = PlayerPhysics.findSpawnHeight(this.world, spawnX, spawnZ);
        } else {
            // Loaded world - pre-load chunks around player
            this.world.updateChunksAroundPlayer(spawnX, spawnZ);
        }
        
        this.player = new LocalPlayer(spawnX, spawnY, spawnZ);
        this.player.setYaw(playerYaw);
        this.player.setPitch(playerPitch);
        this.playerPhysics = new PlayerPhysics(player, this.world);
        this.player.setPhysics(playerPhysics);
        this.playerController = new PlayerController(player);
        this.blockInteraction = new BlockInteraction(player, this.world);
        this.worldRenderer = new LevelRenderer();
        this.worldRenderer.initWithLevel(this.world);
        this.uiRenderer = new UIRenderer();

        // Register input callbacks
        registerCallbacks();
    }
    
    /**
     * Register input callbacks for this screen.
     * Called from constructor and when returning from pause menu.
     */
    private void registerCallbacks() {
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
                    // Open pause menu
                    game.setScreen(new PauseScreen(game, this));
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
            // Don't interact with blocks if command overlay is open
            if (commandOverlayVisible) return;
            
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
            
            logger.info("Teleported to: {}, {}, {}", x, y, z);
            
        } catch (NumberFormatException e) {
            commandErrorMessage = "Invalid coordinates. Usage: /tp x y z";
            commandErrorDisplayTime = 3.0;
        }
    }

    @Override
    public void tick() {
        // Save previous position for interpolation before updating
        player.updatePreviousPosition();
        
        double now = now();
        double dt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (dt < 0) dt = 0;
        if (dt > 0.5) dt = 0.5;
        
        // FPS tracking moved to render() for accurate measurement
        
        // Update chunks based on player position (load/unload) with frustum prioritization
        world.updateChunksAroundPlayer(player.getX(), player.getZ(), player.getYaw());
        
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
        // Track FPS in render() method for accurate measurement
        frameCount++;
        double now = now();
        double timeSinceLastUpdate = now - lastFpsUpdateTime;
        if (timeSinceLastUpdate >= 0.5) { // Update FPS every 0.5 seconds
            fps = frameCount / timeSinceLastUpdate;
            frameCount = 0;
            lastFpsUpdateTime = now;
        }
        
        // Convert alpha to float for interpolation
        float alphaF = (float) alpha;
        
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
        // Use interpolated values for smooth rendering between ticks
        // Camera is at eye level (1.62 blocks above feet)
        glRotatef(player.getPitch(alphaF), 1f, 0f, 0f);
        glRotatef(player.getYaw(alphaF), 0f, 1f, 0f);
        glTranslatef(-player.getX(alphaF), -player.getEyeY(alphaF), -player.getZ(alphaF));

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        
        // Render all loaded chunks in the infinite world
        // Use interpolated position for smooth rendering
        worldRenderer.render(world, player.getX(alphaF), player.getEyeY(alphaF), player.getZ(alphaF));
        
        glDisable(GL_CULL_FACE);
        
        // Render outline on the block the player is looking at
        renderTargetedBlockOutline();
        
        glDisable(GL_DEPTH_TEST);
        
        // Draw debug information in top-left corner (only if F3 is pressed)
        if (debugMenuVisible) {
            int loadedChunks = world.getLoadedChunkCount();
            int pendingChunks = world.getAsyncLoader().getPendingTaskCount();
            int activeWorkers = world.getAsyncLoader().getActiveTaskCount();
            int renderedChunks = worldRenderer.getRenderedChunkCount();
            int culledChunks = worldRenderer.getCulledChunkCount();
            // Use interpolated position for smooth debug display
            uiRenderer.drawDebugInfo(w, h, player.getX(alphaF), player.getY(alphaF), player.getZ(alphaF), fps,
                                     loadedChunks, pendingChunks, activeWorkers, renderedChunks, culledChunks);
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

    /**
     * Render a black outline around the block the player is currently looking at.
     */
    private void renderTargetedBlockOutline() {
        BlockInteraction.BlockHitResult hit = blockInteraction.getTargetedBlock();
        if (hit != null) {
            // Save texture state and disable texturing for outline
            boolean textureEnabled = glIsEnabled(GL_TEXTURE_2D);
            glDisable(GL_TEXTURE_2D);
            
            // Set up line rendering
            glBegin(GL_LINES);
            ColorUtils.setGLColor(0x000000, 1f);  // Black outline
            
            // Draw complete outline around the targeted block
            BlockFaceGeometry.drawCompleteBlockOutline(hit.x, LevelChunk.chunkYToWorldY(hit.y), hit.z);
            
            glEnd();
            
            // Restore texture state
            if (textureEnabled) {
                glEnable(GL_TEXTURE_2D);
            }
        }
    }
    
    private static double now() { return System.nanoTime() * 1e-9; }
    
    /**
     * Save the current world to disk.
     */
    public void saveWorld() throws java.io.IOException {
        LevelStorageSource.saveWorld(world, worldName, 
            player.getX(), player.getY(), player.getZ(),
            player.getYaw(), player.getPitch());
    }
    
    public String getWorldName() {
        return worldName;
    }

    @Override 
    public void onOpen() {
        // Re-register callbacks when returning from pause menu
        registerCallbacks();
        
        // Reset frame time to prevent huge delta time on first tick after pause
        lastFrameTimeSec = now();
        
        // Sync previous position to current position when resuming
        // This prevents visual "teleport" due to interpolation after pause
        player.updatePreviousPosition();
    }
    
    @Override 
    public void onClose() {
        // Only shutdown async chunk loader if we're truly exiting (not just pausing)
        if (shouldShutdownWorld && world != null) {
            world.shutdown();
        }
    }
    
    /**
     * Save the world and mark it for shutdown.
     * Called when exiting to title screen.
     */
    public void saveAndShutdown() throws java.io.IOException {
        shouldShutdownWorld = true;
        saveWorld();
    }
}
