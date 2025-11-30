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
import mattmc.client.renderer.SkyRenderer;
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
    private final SkyRenderer skyRenderer;
    
    // Particle system
    private final mattmc.client.particle.ParticleEngine particleEngine;
    private final java.util.Random animateTickRandom = new java.util.Random();
    
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
        this.skyRenderer = new SkyRenderer(this.worldRenderer.getRenderBackend());
        
        // Initialize particle engine
        this.particleEngine = new mattmc.client.particle.ParticleEngine(this.world);
        initializeParticleSystem();
        
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
        
        // Client-side block animations (particle spawning for torches, leaves, etc.)
        // This is called every tick, mirroring Minecraft's animateTick behavior
        animateBlockTick();
        
        // Tick particles
        particleEngine.tick();
        
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
    
    /** Interval in milliseconds between particle debug log messages. */
    private static final long PARTICLE_DEBUG_LOG_INTERVAL_MS = 5000;
    
    /** Debug counter for particles spawned since last log. */
    private int particleSpawnCount = 0;
    
    /** Timestamp of last particle debug log. */
    private long lastParticleLogTime = 0;
    
    /**
     * Animate blocks near the player (spawn particles for torches, leaves, etc.)
     * Called every tick to mimic Minecraft's ClientLevel.animateTick behavior.
     */
    private void animateBlockTick() {
        int playerBlockX = (int) Math.floor(player.getX());
        int playerBlockY = (int) Math.floor(player.getY());
        int playerBlockZ = (int) Math.floor(player.getZ());
        
        // Create a particle spawner that maps particle type names to actual particles
        mattmc.world.level.block.Block.ParticleSpawner spawner = new mattmc.world.level.block.Block.ParticleSpawner() {
            @Override
            public void spawn(String particleType, double x, double y, double z, 
                              double xSpeed, double ySpeed, double zSpeed) {
                mattmc.core.particles.ParticleOptions options = getParticleOptions(particleType);
                if (options != null) {
                    mattmc.client.particle.Particle particle = particleEngine.createParticle(options, x, y, z, xSpeed, ySpeed, zSpeed);
                    if (particle != null) {
                        particleSpawnCount++;
                    }
                }
            }
            
            @Override
            public void spawnTinted(String particleType, double x, double y, double z,
                                    double xSpeed, double ySpeed, double zSpeed,
                                    float red, float green, float blue) {
                if ("falling_leaves".equals(particleType)) {
                    mattmc.client.particle.Particle particle = createTintedFallingLeavesParticle(
                        x, y, z, xSpeed, ySpeed, zSpeed, red, green, blue);
                    if (particle != null) {
                        particleSpawnCount++;
                    }
                } else {
                    // Fall back to regular spawn for non-tinted particle types
                    spawn(particleType, x, y, z, xSpeed, ySpeed, zSpeed);
                }
            }
        };
        
        world.animateTick(playerBlockX, playerBlockY, playerBlockZ, animateTickRandom, spawner);
        
        // Log particle stats periodically for debugging
        long now = System.currentTimeMillis();
        if (now - lastParticleLogTime > PARTICLE_DEBUG_LOG_INTERVAL_MS) {
            int activeParticles = particleEngine.countParticles();
            // Use debug level to avoid excessive log output in production
            logger.debug("[Particle Debug] animateBlockTick running - Spawned {} particles in last {}s, {} active particles", 
                       particleSpawnCount, PARTICLE_DEBUG_LOG_INTERVAL_MS / 1000, activeParticles);
            particleSpawnCount = 0;
            lastParticleLogTime = now;
        }
    }
    
    /**
     * Create a tinted falling leaves particle with the specified RGB color.
     * 
     * @param x spawn X position
     * @param y spawn Y position
     * @param z spawn Z position
     * @param xSpeed initial X velocity
     * @param ySpeed initial Y velocity
     * @param zSpeed initial Z velocity
     * @param red red color component (0.0-1.0)
     * @param green green color component (0.0-1.0)
     * @param blue blue color component (0.0-1.0)
     * @return the created particle, or null if creation failed
     */
    private mattmc.client.particle.Particle createTintedFallingLeavesParticle(double x, double y, double z,
                                                                               double xSpeed, double ySpeed, double zSpeed,
                                                                               float red, float green, float blue) {
        // Get the sprite set for falling_leaves
        mattmc.client.particle.SpriteSet sprites = particleEngine.getSpriteSet(
            new mattmc.util.ResourceLocation("mattmc", "falling_leaves"));
        if (sprites == null) {
            return null;
        }
        
        // Create a provider with the specified color, then use it to create the particle
        mattmc.client.particle.FallingLeavesParticle.ColoredProvider provider = 
            new mattmc.client.particle.FallingLeavesParticle.ColoredProvider(sprites, red, green, blue);
        
        mattmc.client.particle.Particle particle = provider.createParticle(
            mattmc.registries.ParticleTypes.FALLING_LEAVES, world, x, y, z, xSpeed, ySpeed, zSpeed);
        
        if (particle != null) {
            particleEngine.add(particle);
        }
        return particle;
    }
    
    /**
     * Get particle options for a particle type name.
     */
    private mattmc.core.particles.ParticleOptions getParticleOptions(String particleType) {
        switch (particleType) {
            case "smoke":
                return mattmc.registries.ParticleTypes.SMOKE;
            case "flame":
                return mattmc.registries.ParticleTypes.FLAME;
            case "cherry_leaves":
                return mattmc.registries.ParticleTypes.CHERRY_LEAVES;
            case "falling_leaves":
                return mattmc.registries.ParticleTypes.FALLING_LEAVES;
            case "poof":
                return mattmc.registries.ParticleTypes.POOF;
            default:
                logger.debug("Unknown particle type: {}", particleType);
                return null;
        }
    }
    
    /**
     * Initialize the particle system with providers and atlas.
     */
    private void initializeParticleSystem() {
        // Create and set the particle atlas using the OpenGL backend
        mattmc.client.renderer.backend.opengl.OpenGLParticleAtlas atlas = 
            new mattmc.client.renderer.backend.opengl.OpenGLParticleAtlas();
        particleEngine.setParticleAtlas(atlas);
        
        // Register particle providers (sprite-based)
        particleEngine.register(mattmc.registries.ParticleTypes.SMOKE, 
            mattmc.client.particle.SmokeParticle.Provider::new);
        particleEngine.register(mattmc.registries.ParticleTypes.FLAME, 
            mattmc.client.particle.FlameParticle.Provider::new);
        particleEngine.register(mattmc.registries.ParticleTypes.POOF, 
            mattmc.client.particle.PoofParticle.Provider::new);
        particleEngine.register(mattmc.registries.ParticleTypes.CHERRY_LEAVES, 
            mattmc.client.particle.CherryParticle.Provider::new);
        // Register falling_leaves with default white color. Actual tinting is done dynamically
        // via spawnTinted() in animateBlockTick() using the block's RGB values.
        particleEngine.register(mattmc.registries.ParticleTypes.FALLING_LEAVES, 
            sprites -> new mattmc.client.particle.FallingLeavesParticle.ColoredProvider(sprites, 1.0f, 1.0f, 1.0f));
        
        // Load particle definitions (binds textures to sprite sets)
        particleEngine.loadParticleDefinitions();
        
        logger.info("Particle system initialized");
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

        // Render sky first (sun, moon, stars) - sky only needs rotation, not translation
        // SkyRenderer handles all its own state management
        skyRenderer.render(world.getDayCycle(), player.getPitch(alphaF), player.getYaw(alphaF));

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
        
        // Render particles (after world, before outline and UI)
        renderParticles(player.getX(alphaF), player.getEyeY(alphaF), player.getZ(alphaF), alphaF);
        
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
    
    /**
     * Render particles.
     * 
     * @param cameraX camera X position
     * @param cameraY camera Y position
     * @param cameraZ camera Z position
     * @param partialTicks interpolation factor (0-1)
     */
    private void renderParticles(double cameraX, double cameraY, double cameraZ, float partialTicks) {
        mattmc.client.particle.ParticleAtlas atlas = particleEngine.getParticleAtlas();
        if (atlas == null) {
            return;
        }
        
        int particleCount = particleEngine.countParticles();
        if (particleCount == 0) {
            return;
        }
        
        // Save the current modelview matrix
        org.lwjgl.opengl.GL11.glPushMatrix();
        
        // Load identity then apply only camera ROTATION (not translation)
        // Particles already have camera-relative positions (they subtract camera pos in render())
        // but they still need to be rotated to face the camera correctly
        org.lwjgl.opengl.GL11.glLoadIdentity();
        
        // Apply camera rotation (pitch then yaw) - same as in main render()
        // This makes particles appear in the correct screen position
        float pitch = player.getPitch(partialTicks);
        float yaw = player.getYaw(partialTicks);
        org.lwjgl.opengl.GL11.glRotatef(pitch, 1f, 0f, 0f);
        org.lwjgl.opengl.GL11.glRotatef(yaw, 0f, 1f, 0f);
        
        // Set up render state for particles
        backend.disableCullFace();
        backend.enableDepthTest();
        
        // Enable texturing
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        
        // Enable alpha testing to discard transparent pixels (alpha < 0.1)
        // This is how Minecraft handles particles with transparency in legacy OpenGL
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
        org.lwjgl.opengl.GL11.glAlphaFunc(org.lwjgl.opengl.GL11.GL_GREATER, 0.1f);
        
        // Enable blending for proper alpha handling
        backend.enableBlend();
        
        // Bind particle atlas texture
        atlas.bind();
        
        // Create a simple vertex builder for particle rendering
        // Particle positions are already camera-relative from SingleQuadParticle.render()
        particleEngine.render(
            new mattmc.client.particle.ParticleVertexBuilder() {
                @Override
                public void begin() {
                    // Begin immediate mode rendering
                    org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
                }
                
                @Override
                public void vertex(float x, float y, float z, float u, float v, 
                                   float r, float g, float b, float a, int light) {
                    org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
                    org.lwjgl.opengl.GL11.glTexCoord2f(u, v);
                    // Positions are already camera-relative from particle render method
                    org.lwjgl.opengl.GL11.glVertex3f(x, y, z);
                }
                
                @Override
                public void end() {
                    org.lwjgl.opengl.GL11.glEnd();
                }
            },
            cameraX, cameraY, cameraZ,
            partialTicks,
            renderType -> {
                // Set up render state based on render type
                switch (renderType) {
                    case PARTICLE_SHEET_TRANSLUCENT:
                        backend.enableBlend();
                        org.lwjgl.opengl.GL11.glDepthMask(false);
                        break;
                    case PARTICLE_SHEET_OPAQUE:
                    case PARTICLE_SHEET_LIT:
                        // Even opaque particles need blend for texture alpha
                        backend.enableBlend();
                        org.lwjgl.opengl.GL11.glDepthMask(true);
                        break;
                    default:
                        break;
                }
                
                // Enable or disable lighting based on render type
                // Particles like flames (PARTICLE_SHEET_OPAQUE) should be fullbright
                if (renderType.usesLighting()) {
                    backend.enableLighting();
                } else {
                    backend.disableLighting();
                }
            }
        );
        
        // Restore render state
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
        org.lwjgl.opengl.GL11.glDepthMask(true);
        backend.disableBlend();
        backend.enableCullFace();
        
        // Restore lighting state to enabled (it was enabled before particle rendering)
        backend.enableLighting();
        
        // Restore the modelview matrix
        org.lwjgl.opengl.GL11.glPopMatrix();
    }
}
