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
import mattmc.client.renderer.BlockNameHUD;
import mattmc.client.renderer.block.BlockFaceGeometry;
import mattmc.client.renderer.ColorUtils;
import mattmc.world.item.Inventory;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import mattmc.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Devplay screen - infinite world with dynamic chunk loading.
 * Chunks load/unload based on player position.
 * ESC opens pause menu.
 * 
 * Refactored to delegate command handling to CommandSystem,
 * input handling to DevplayInputHandler, and UI state to DevplayUIState.
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
    private final BlockNameHUD blockNameHUD;
    
    // Level name for saving
    private final String worldName;
    
    private double lastFrameTimeSec = now();
    
    // Extracted components
    private final DevplayUIState uiState;
    private final CommandSystem commandSystem;
    private final DevplayInputHandler inputHandler;
    
    // Flag to track if world should be shut down on close
    private boolean shouldShutdownWorld = false;
    
    // Flag to control whether HUD overlays should be rendered
    // Set to false when this screen is being rendered as background for overlay screens
    private boolean renderHudOverlays = true;

    public DevplayScreen(Minecraft game, String worldName) {
        this(game, worldName, new java.util.Random().nextLong());
    }
    
    public DevplayScreen(Minecraft game, String worldName, long seed) {
        this(game, worldName, null, seed, 0f, 0f, 0f, 0f, 0f);
    }
    
    public DevplayScreen(Minecraft game, String worldName, Level world, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) {
        this(game, worldName, world, new java.util.Random().nextLong(), playerX, playerY, playerZ, playerYaw, playerPitch, null);
    }
    
    public DevplayScreen(Minecraft game, String worldName, Level world, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch, Inventory playerInventory) {
        this(game, worldName, world, new java.util.Random().nextLong(), playerX, playerY, playerZ, playerYaw, playerPitch, playerInventory);
    }
    
    public DevplayScreen(Minecraft game, String worldName, Level world, long seed, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) {
        this(game, worldName, world, seed, playerX, playerY, playerZ, playerYaw, playerPitch, null);
    }
    
    public DevplayScreen(Minecraft game, String worldName, Level world, long seed, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch, Inventory playerInventory) {
        this.game = game;
        this.window = game.window();
        this.worldName = worldName;
        
        // Initialize infinite world (use provided or create new)
        this.world = world != null ? world : new Level();
        
        // Apply render distance from settings
        int renderDistance = OptionsManager.getRenderDistance();
        this.world.setRenderDistance(renderDistance);
        // logger.info("Set render distance to: {}{}", renderDistance, " chunks");
        
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
        
        // Load player inventory if provided
        if (playerInventory != null) {
            Inventory inventory = this.player.getInventory();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setStack(i, playerInventory.getStack(i));
            }
            inventory.setSelectedSlot(playerInventory.getSelectedSlot());
        }
        
        this.playerPhysics = new PlayerPhysics(player, this.world);
        this.player.setPhysics(playerPhysics);
        this.playerController = new PlayerController(player);
        this.blockInteraction = new BlockInteraction(player, this.world);
        this.worldRenderer = new LevelRenderer();
        this.worldRenderer.initWithLevel(this.world);
        this.uiRenderer = new UIRenderer();
        this.blockNameHUD = new BlockNameHUD();

        // Initialize UI state, command system, and input handler
        this.uiState = new DevplayUIState(now());
        this.commandSystem = new CommandSystem(player, this.world);
        this.inputHandler = new DevplayInputHandler(
            game, window, player, this.world, blockInteraction, uiState, commandSystem,
            playerController, uiRenderer,
            () -> game.setScreen(new PauseScreen(game, this)),
            () -> game.setScreen(new InventoryScreen(game, this))
        );

        // Register input callbacks
        inputHandler.registerCallbacks();
    }

    @Override
    public void tick() {
        // Save previous position for interpolation before updating
        player.updatePreviousPosition();
        
        // Fixed timestep: since tick() is called at exactly 20 TPS, use fixed dt
        final float FIXED_DT = 0.05f; // 1/20 second per tick
        
        // Update chunks based on player position (load/unload) with frustum prioritization
        world.updateChunksAroundPlayer(player.getX(), player.getZ(), player.getYaw());
        
        // Update physics (gravity, collision) - only if command overlay is not visible
        if (!uiState.isCommandOverlayVisible()) {
            playerPhysics.update(FIXED_DT);
        }
        
        // Update player movement based on input - only if command overlay is not visible
        if (!uiState.isCommandOverlayVisible()) {
            playerController.updateMovement(window.handle(), FIXED_DT);
            
            // Handle continuous jump when space is held
            if (PlayerInput.getInstance().isPressed(window.handle(), PlayerInput.JUMP)) {
                playerController.handleSpaceHeld();
            }
        }
        
        // Decrease feedback display time
        if (uiState.hasCommandFeedback()) {
            double now = now();
            double dt = now - lastFrameTimeSec;
            lastFrameTimeSec = now;
            uiState.updateFeedbackTimer(dt);
        }
    }

    @Override
    public void render(double alpha) {
        // Track FPS in render() method for accurate measurement
        uiState.updateFPS(now());
        
        // Convert alpha to float for interpolation
        float alphaF = (float) alpha;
        
        int w = window.width(), h = window.height();
        
        // Ensure viewport is set correctly (in case it was modified by other screens)
        // Query actual framebuffer size for high-DPI displays
        int fbWidth, fbHeight;
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.IntBuffer pWidth = stack.mallocInt(1);
            java.nio.IntBuffer pHeight = stack.mallocInt(1);
            org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(window.handle(), pWidth, pHeight);
            fbWidth = pWidth.get(0);
            fbHeight = pHeight.get(0);
            
            // Fallback to window dimensions if framebuffer query fails
            if (fbWidth <= 0) fbWidth = w;
            if (fbHeight <= 0) fbHeight = h;
            
            glViewport(0, 0, fbWidth, fbHeight);
        }

        // Clear color + depth (sky blue)
        glClearColor(0.53f, 0.81f, 0.92f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Perspective projection - use framebuffer dimensions for aspect ratio
        // Ensure we don't divide by zero
        float aspect = fbHeight > 0 ? (float) fbWidth / (float) fbHeight : 1.0f;
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
        
        // Set up 2D orthographic projection for all UI rendering
        // This creates a clean separation between 3D world rendering and 2D UI rendering
        // IMPORTANT: Use framebuffer dimensions (not window dimensions) to match viewport
        // This ensures consistency when AbstractBlurBox restores the projection
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, fbWidth, fbHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        // Draw debug information in top-left corner (only if F3 is pressed)
        if (uiState.isDebugMenuVisible()) {
            int loadedChunks = world.getLoadedChunkCount();
            int pendingChunks = world.getAsyncLoader().getPendingTaskCount();
            int activeWorkers = world.getAsyncLoader().getActiveTaskCount();
            int renderedChunks = worldRenderer.getRenderedChunkCount();
            int culledChunks = worldRenderer.getCulledChunkCount();
            // Use interpolated position for smooth debug display
            uiRenderer.drawDebugInfo(w, h, player.getX(alphaF), player.getY(alphaF), player.getZ(alphaF), 
                                     player.getYaw(alphaF), player.getPitch(alphaF), 0f, uiState.getFPS(),
                                     loadedChunks, pendingChunks, activeWorkers, renderedChunks, culledChunks);
        }
        
        // Draw command overlay if visible
        if (uiState.isCommandOverlayVisible()) {
            uiRenderer.drawCommandOverlay(w, h, uiState.getCommandText());
        }
        
        // Draw command feedback message (independent of command overlay)
        if (uiState.hasCommandFeedback()) {
            uiRenderer.drawCommandFeedback(w, h, uiState.getCommandFeedbackMessage());
        }
        
        // Draw hotbar at bottom center (always visible)
        // Use framebuffer dimensions to match the 2D projection
        uiRenderer.drawHotbar(fbWidth, fbHeight, player);
        
        // Draw block name HUD in top-left corner (only when not in debug mode or command overlay, and when HUD overlays are enabled)
        if (renderHudOverlays && !uiState.isDebugMenuVisible() && !uiState.isCommandOverlayVisible()) {
            blockNameHUD.render(player, world, fbWidth, fbHeight);
        }
        
        // Draw crosshair on top of everything (but not when command overlay is open)
        if (!uiState.isCommandOverlayVisible()) {
            uiRenderer.drawCrosshair(fbWidth, fbHeight);
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
            player.getYaw(), player.getPitch(), player.getInventory());
    }
    
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Get the local player.
     * @return The player instance
     */
    public LocalPlayer getPlayer() {
        return player;
    }
    
    /**
     * Sync player previous position to current position.
     * Called by overlay screens (pause, inventory) to prevent flickering during interpolation.
     */
    public void syncPlayerPosition() {
        player.updatePreviousPosition();
    }
    
    /**
     * Set whether HUD overlays (like BlockNameHUD) should be rendered.
     * Should be set to false when this screen is being rendered as background for overlay screens.
     */
    public void setRenderHudOverlays(boolean render) {
        this.renderHudOverlays = render;
    }

    @Override 
    public void onOpen() {
        // Re-register callbacks when returning from pause menu
        inputHandler.registerCallbacks();
        
        // Reset frame time to prevent huge delta time on first tick after pause
        lastFrameTimeSec = now();
        
        // Re-enable HUD overlays when returning to game
        renderHudOverlays = true;
        
        // Sync previous position to current position when resuming
        // This prevents visual "teleport" due to interpolation after pause
        player.updatePreviousPosition();
        
        // Reset mouse state to prevent camera jump on first movement
        playerController.resetMouseState();
    }
    
    @Override 
    public void onClose() {
        // Clean up BlockNameHUD resources
        if (blockNameHUD != null) {
            blockNameHUD.close();
        }
        
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
