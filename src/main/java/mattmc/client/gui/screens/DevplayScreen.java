package mattmc.client.gui.screens;

import mattmc.client.MattMC;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.settings.OptionsManager;
import mattmc.world.Gamemode;
import mattmc.world.entity.player.BlockInteraction;
import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.entity.player.PlayerController;
import mattmc.world.entity.player.PlayerPhysics;
import mattmc.world.entity.player.PlayerInput;
import mattmc.client.renderer.WorldRenderer;
import mattmc.client.renderer.UIRenderer;
import mattmc.client.renderer.block.BlockOutlineRenderer;
import mattmc.world.item.Inventory;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.world.level.Level;
import mattmc.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Path;

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
    
    /** Default gamemode for new worlds and legacy worlds without gamemode data. */
    private static final Gamemode DEFAULT_GAMEMODE = Gamemode.CREATIVE;

    private final MattMC game;
    private final WindowHandle window;
    private final RenderBackend backend;
    
    // Game components (following game architecture)
    private final Level world;
    private final LocalPlayer player;
    private final PlayerController playerController;
    private final PlayerPhysics playerPhysics;
    private final BlockInteraction blockInteraction;
    private final WorldRenderer worldRenderer;
    private final UIRenderer uiRenderer;
    
    // Level name for saving
    private final String worldName;
    
    // Default gamemode for the world
    private final Gamemode defaultGamemode;
    
    private double lastFrameTimeSec = now();
    
    // Extracted components
    private final DevplayUIState uiState;
    private final CommandSystem commandSystem;
    private final DevplayInputHandler inputHandler;
    
    // Flag to track if world should be shut down on close
    private boolean shouldShutdownWorld = false;

    public DevplayScreen(MattMC game, String worldName) {
        this(game, worldName, new java.util.Random().nextLong(), DEFAULT_GAMEMODE);
    }
    
    public DevplayScreen(MattMC game, String worldName, long seed) {
        this(game, worldName, null, seed, 0f, 0f, 0f, 0f, 0f, null, DEFAULT_GAMEMODE, DEFAULT_GAMEMODE);
    }
    
    public DevplayScreen(MattMC game, String worldName, long seed, Gamemode defaultGamemode) {
        this(game, worldName, null, seed, 0f, 0f, 0f, 0f, 0f, null, defaultGamemode, defaultGamemode);
    }
    
    public DevplayScreen(MattMC game, String worldName, Level world, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) {
        this(game, worldName, world, new java.util.Random().nextLong(), playerX, playerY, playerZ, playerYaw, playerPitch, null, DEFAULT_GAMEMODE, DEFAULT_GAMEMODE);
    }
    
    public DevplayScreen(MattMC game, String worldName, Level world, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch, Inventory playerInventory) {
        this(game, worldName, world, new java.util.Random().nextLong(), playerX, playerY, playerZ, playerYaw, playerPitch, playerInventory, DEFAULT_GAMEMODE, DEFAULT_GAMEMODE);
    }
    
    public DevplayScreen(MattMC game, String worldName, Level world, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch, Inventory playerInventory, Gamemode defaultGamemode, Gamemode playerGamemode) {
        this(game, worldName, world, new java.util.Random().nextLong(), playerX, playerY, playerZ, playerYaw, playerPitch, playerInventory, defaultGamemode, playerGamemode);
    }
    
    public DevplayScreen(MattMC game, String worldName, Level world, long seed, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) {
        this(game, worldName, world, seed, playerX, playerY, playerZ, playerYaw, playerPitch, null, DEFAULT_GAMEMODE, DEFAULT_GAMEMODE);
    }
    
    public DevplayScreen(MattMC game, String worldName, Level world, long seed, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch, Inventory playerInventory) {
        this(game, worldName, world, seed, playerX, playerY, playerZ, playerYaw, playerPitch, playerInventory, DEFAULT_GAMEMODE, DEFAULT_GAMEMODE);
    }
    
    public DevplayScreen(MattMC game, String worldName, Level world, long seed, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch, Inventory playerInventory, Gamemode defaultGamemode, Gamemode playerGamemode) {
        this.game = game;
        this.window = game.window();
        this.backend = game.getRenderBackend();
        this.worldName = worldName;
        this.defaultGamemode = defaultGamemode;
        
        // Initialize infinite world (use provided or create new)
        this.world = world != null ? world : new Level();
        
        // Apply render distance from settings
        int renderDistance = OptionsManager.getRenderDistance();
        this.world.setRenderDistance(renderDistance);
        
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
        this.player.setGamemode(playerGamemode);
        
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
        this.worldRenderer = game.getBackendFactory().createWorldRenderer();
        this.worldRenderer.initWithLevel(this.world);
        this.uiRenderer = new UIRenderer();
        
        // Stage 4: Share the render backend between world and UI rendering
        // This ensures UI elements use the same backend abstraction as chunks
        this.uiRenderer.setBackend(this.worldRenderer.getRenderBackend());

        // Initialize UI state, command system, and input handler
        this.uiState = new DevplayUIState(now());
        this.commandSystem = new CommandSystem(player, this.world);
        this.inputHandler = new DevplayInputHandler(
            game, window, backend,
            player, this.world, blockInteraction, uiState, commandSystem,
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
        
        // Tick the day/night cycle
        world.tickDayCycle();
        
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

        // Get sky color from day/night cycle
        float[] skyColor = world.getDayCycle().getSkyColor();
        backend.setClearColor(skyColor[0], skyColor[1], skyColor[2], 1f);
        backend.clearBuffers();

        // Perspective projection
        float aspect = Math.max(1f, (float) w / Math.max(1, h));
        float fov = 70f, zn = 0.1f, zf = 500f;
        backend.setupPerspectiveProjection(fov, aspect, zn, zf);

        // Apply camera transformations (pitch, yaw, then position)
        // Use interpolated values for smooth rendering between ticks
        // Camera is at eye level (1.62 blocks above feet)
        backend.rotateMatrix(player.getPitch(alphaF), 1f, 0f, 0f);
        backend.rotateMatrix(player.getYaw(alphaF), 0f, 1f, 0f);
        backend.translateMatrix(-player.getX(alphaF), -player.getEyeY(alphaF), -player.getZ(alphaF));

        // Set up directional light based on sun position
        setupDirectionalLight();

        backend.enableDepthTest();
        backend.enableCullFace();
        
        // Render all loaded chunks in the infinite world
        // Use interpolated position for smooth rendering
        worldRenderer.render(world, player.getX(alphaF), player.getEyeY(alphaF), player.getZ(alphaF));
        
        backend.disableCullFace();
        
        // Render outline on the block the player is looking at
        renderTargetedBlockOutline();
        
        // Disable lighting for UI rendering
        backend.disableLighting();
        
        backend.disableDepthTest();
        
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
                                     loadedChunks, pendingChunks, activeWorkers, renderedChunks, culledChunks,
                                     defaultGamemode.getDisplayName(), player.getGamemode().getDisplayName());
            
            // Draw system information on the right side
            uiRenderer.drawSystemInfo(w, h, window.handle());
        } else {
            // Draw block name display in top-left corner (only if debug menu is not visible and option is enabled)
            if (OptionsManager.isShowBlockNameEnabled()) {
                BlockInteraction.BlockHitResult hit = blockInteraction.getTargetedBlock();
                if (hit != null) {
                    mattmc.world.level.block.Block targetedBlock = world.getBlock(hit.x, hit.y, hit.z);
                    uiRenderer.drawBlockNameDisplay(w, h, targetedBlock);
                }
            }
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
        uiRenderer.drawHotbar(w, h, player);
        
        // Draw crosshair on top of everything (but not when command overlay is open)
        if (!uiState.isCommandOverlayVisible()) {
            uiRenderer.drawCrosshair(w, h);
        }
    }

    /**
     * Set up directional light based on the sun position.
     * The sun acts as a directional light source that rotates through the day/night cycle.
     */
    private void setupDirectionalLight() {
        // Get sun direction from day cycle
        float[] sunDir = world.getDayCycle().getSunDirection();
        float brightness = world.getDayCycle().getSkyBrightness();
        
        // Enable lighting and set up directional light
        backend.enableLighting();
        backend.setupDirectionalLight(sunDir[0], sunDir[1], sunDir[2], brightness);
    }

    /**
     * Render a black outline around the block the player is currently looking at.
     */
    private void renderTargetedBlockOutline() {
        BlockInteraction.BlockHitResult hit = blockInteraction.getTargetedBlock();
        if (hit != null) {
            // Save texture state and disable texturing for outline
            boolean textureEnabled = backend.isTexture2DEnabled();
            backend.disableTexture2D();
            
            // Set up line rendering
            backend.begin3DLines();
            backend.setColor(0x000000, 1f);  // Black outline
            
            // Draw complete outline around the targeted block
            BlockOutlineRenderer.drawBlockOutline(hit.x, ChunkUtils.localToWorldY(hit.y), hit.z, backend);
            
            backend.end3DLines();
            
            // Restore texture state
            if (textureEnabled) {
                backend.enableTexture2D();
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
            player.getYaw(), player.getPitch(), player.getInventory(),
            defaultGamemode, player.getGamemode());
    }
    
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Get the default gamemode for this world.
     * @return The default gamemode
     */
    public Gamemode getDefaultGamemode() {
        return defaultGamemode;
    }
    
    /**
     * Get the local player.
     * @return The player instance
     */
    public LocalPlayer getPlayer() {
        return player;
    }
    
    /**
     * Get the render backend for UI rendering.
     * @return The render backend instance
     */
    public RenderBackend getRenderBackend() {
        return worldRenderer.getRenderBackend();
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
        inputHandler.registerCallbacks();
        
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
