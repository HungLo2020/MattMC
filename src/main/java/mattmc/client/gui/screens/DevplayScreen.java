package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.settings.OptionsManager;
import mattmc.world.entity.player.BlockInteraction;
import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.entity.player.PlayerController;
import mattmc.world.entity.player.PlayerPhysics;
import mattmc.world.entity.player.PlayerInput;
import mattmc.client.renderer.LevelRenderer;
import mattmc.client.renderer.UIRenderer;
import mattmc.client.renderer.block.BlockFaceGeometry;
import mattmc.client.renderer.ColorUtils;
import mattmc.world.level.block.Block;
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
    
    // Maximum region size for /set command to prevent UI freezing (100,000 blocks)
    private static final long MAX_REGION_SIZE = 100_000;

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
    
    // Command feedback message (shown above hotbar area, independent of command overlay)
    private String commandFeedbackMessage = "";
    private double commandFeedbackDisplayTime = 0;
    
    // Region selection state for /pos1, /pos2, and /set commands
    private int[] regionPos1 = null; // [x, y, z]
    private int[] regionPos2 = null; // [x, y, z]
    
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
                // Check for inventory key (respects user configuration)
                Integer inventoryKey = PlayerInput.getInstance().getKeybind(PlayerInput.INVENTORY);
                if (inventoryKey != null && key == inventoryKey && action == GLFW_PRESS) {
                    // Open inventory screen
                    game.setScreen(new InventoryScreen(game, this));
                }
                
                // Check for hotbar selection keys (1-9)
                if (action == GLFW_PRESS) {
                    PlayerInput input = PlayerInput.getInstance();
                    for (int i = 1; i <= 9; i++) {
                        String hotbarAction = switch(i) {
                            case 1 -> PlayerInput.HOTBAR_1;
                            case 2 -> PlayerInput.HOTBAR_2;
                            case 3 -> PlayerInput.HOTBAR_3;
                            case 4 -> PlayerInput.HOTBAR_4;
                            case 5 -> PlayerInput.HOTBAR_5;
                            case 6 -> PlayerInput.HOTBAR_6;
                            case 7 -> PlayerInput.HOTBAR_7;
                            case 8 -> PlayerInput.HOTBAR_8;
                            case 9 -> PlayerInput.HOTBAR_9;
                            default -> null;
                        };
                        
                        if (hotbarAction != null) {
                            Integer hotbarKey = input.getKeybind(hotbarAction);
                            if (hotbarKey != null && key == hotbarKey) {
                                // Select hotbar slot (0-indexed, so slot 1 is index 0)
                                uiRenderer.setSelectedHotbarSlot(i - 1);
                                break;
                            }
                        }
                    }
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
            commandFeedbackMessage = "Commands must start with /";
            commandFeedbackDisplayTime = 3.0;
            return;
        }
        
        // Parse and execute command
        if (cmd.startsWith("/tp ")) {
            executeTeleportCommand(cmd);
        } else if (cmd.equals("/pos1")) {
            executePos1Command();
        } else if (cmd.equals("/pos2")) {
            executePos2Command();
        } else if (cmd.startsWith("/set ")) {
            executeSetCommand(cmd);
        } else {
            commandFeedbackMessage = "Unknown command: " + cmd;
            commandFeedbackDisplayTime = 3.0; // Show error for 3 seconds
        }
    }
    
    private void executeTeleportCommand(String cmd) {
        try {
            // Parse: /tp x y z
            String[] parts = cmd.substring(4).trim().split("\\s+");
            
            if (parts.length != 3) {
                commandFeedbackMessage = "Usage: /tp x y z";
                commandFeedbackDisplayTime = 3.0;
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
            commandFeedbackMessage = "Invalid coordinates. Usage: /tp x y z";
            commandFeedbackDisplayTime = 3.0;
        }
    }
    
    /**
     * Execute /pos1 command - sets the first position of the region to the player's current position.
     */
    private void executePos1Command() {
        // Get player's current block position (floor of the feet position)
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        
        regionPos1 = new int[]{x, y, z};
        logger.info("Position 1 set to: {}, {}, {}", x, y, z);
        
        // Show confirmation message to user
        commandFeedbackMessage = "Position 1 set to: " + x + ", " + y + ", " + z;
        commandFeedbackDisplayTime = 3.0;
    }
    
    /**
     * Execute /pos2 command - sets the second position of the region to the player's current position.
     */
    private void executePos2Command() {
        // Get player's current block position (floor of the feet position)
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        
        regionPos2 = new int[]{x, y, z};
        logger.info("Position 2 set to: {}, {}, {}", x, y, z);
        
        // Show confirmation message to user
        commandFeedbackMessage = "Position 2 set to: " + x + ", " + y + ", " + z;
        commandFeedbackDisplayTime = 3.0;
    }
    
    /**
     * Execute /set command - fills the region defined by pos1 and pos2 with the specified block.
     * @param cmd The full command string (e.g., "/set stone" or "/set mattmc:stone")
     */
    private void executeSetCommand(String cmd) {
        // Check if both positions are set
        if (regionPos1 == null || regionPos2 == null) {
            commandFeedbackMessage = "Please set both positions first with /pos1 and /pos2";
            commandFeedbackDisplayTime = 3.0;
            return;
        }
        
        // Parse the block name from the command
        String blockName = cmd.substring(5).trim(); // Remove "/set "
        
        if (blockName.isEmpty()) {
            commandFeedbackMessage = "Usage: /set <block>";
            commandFeedbackDisplayTime = 3.0;
            return;
        }
        
        // Look up the block - try with namespace first, then without
        Block block = null;
        if (blockName.contains(":")) {
            // Already has namespace (e.g., "mattmc:stone")
            block = Blocks.getBlock(blockName);
        } else {
            // Try adding default namespace (e.g., "stone" -> "mattmc:stone")
            block = Blocks.getBlock("mattmc:" + blockName);
        }
        
        if (block == null) {
            commandFeedbackMessage = "Unknown block: " + blockName;
            commandFeedbackDisplayTime = 3.0;
            return;
        }
        
        // Calculate the bounds of the region
        int minX = Math.min(regionPos1[0], regionPos2[0]);
        int maxX = Math.max(regionPos1[0], regionPos2[0]);
        int minY = Math.min(regionPos1[1], regionPos2[1]);
        int maxY = Math.max(regionPos1[1], regionPos2[1]);
        int minZ = Math.min(regionPos1[2], regionPos2[2]);
        int maxZ = Math.max(regionPos1[2], regionPos2[2]);
        
        // Calculate region size and check for maximum limit to prevent UI freezing
        long sizeX = (long)(maxX - minX + 1);
        long sizeY = (long)(maxY - minY + 1);
        long sizeZ = (long)(maxZ - minZ + 1);
        long totalBlocks = sizeX * sizeY * sizeZ;
        
        if (totalBlocks > MAX_REGION_SIZE) {
            commandFeedbackMessage = "Region too large (" + totalBlocks + " blocks). Maximum is " + MAX_REGION_SIZE;
            commandFeedbackDisplayTime = 3.0;
            return;
        }
        
        // Calculate the number of blocks to set
        int blocksSet = 0;
        
        // Fill the region with the specified block
        // Note: Level.setBlock expects chunk-local Y coordinates (0-383)
        for (int x = minX; x <= maxX; x++) {
            for (int worldY = minY; worldY <= maxY; worldY++) {
                // Convert world Y to chunk Y for setBlock call
                int chunkY = LevelChunk.worldYToChunkY(worldY);
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlock(x, chunkY, z, block);
                    blocksSet++;
                }
            }
        }
        
        logger.info("Filled region ({}, {}, {}) to ({}, {}, {}) with {} - {} blocks set",
                    minX, minY, minZ, maxX, maxY, maxZ, block.getIdentifier(), blocksSet);
        
        // Show confirmation message to user
        commandFeedbackMessage = "Filled " + blocksSet + " blocks with " + blockName;
        commandFeedbackDisplayTime = 3.0;
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
        if (commandFeedbackDisplayTime > 0) {
            commandFeedbackDisplayTime -= dt;
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
            uiRenderer.drawDebugInfo(w, h, player.getX(alphaF), player.getY(alphaF), player.getZ(alphaF), 
                                     player.getYaw(alphaF), player.getPitch(alphaF), 0f, fps,
                                     loadedChunks, pendingChunks, activeWorkers, renderedChunks, culledChunks);
        }
        
        // Draw command overlay if visible
        if (commandOverlayVisible) {
            uiRenderer.drawCommandOverlay(w, h, commandText.toString());
        }
        
        // Draw command feedback message (independent of command overlay)
        if (commandFeedbackDisplayTime > 0) {
            uiRenderer.drawCommandFeedback(w, h, commandFeedbackMessage);
        }
        
        // Draw hotbar at bottom center (always visible)
        uiRenderer.drawHotbar(w, h);
        
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
    
    /**
     * Sync player previous position to current position.
     * Called by overlay screens (pause, inventory) to prevent flickering during interpolation.
     */
    public void syncPlayerPosition() {
        player.updatePreviousPosition();
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
        
        // Reset mouse state to prevent camera jump on first movement
        playerController.resetMouseState();
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
