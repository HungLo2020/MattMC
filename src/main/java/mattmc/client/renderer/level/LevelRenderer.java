package mattmc.client.renderer.level;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.CommandBuffer;
import mattmc.client.renderer.ChunkRenderLogic;
import mattmc.client.renderer.Frustum;
import mattmc.client.renderer.WorldRenderer;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renderer-agnostic implementation for rendering the game world/level.
 * 
 * <p>This class coordinates world rendering using the {@link RenderBackend} abstraction.
 * It does not make any direct graphics API calls (no OpenGL, Vulkan, etc.).
 * All rendering is done through the backend interface.
 * 
 * <p><b>Architecture:</b> This class sits in the rendering front-end layer:
 * <ul>
 *   <li><b>Game/World Layer:</b> Provides chunks, blocks, entities</li>
 *   <li><b>Rendering Front-End (this class):</b> Decides what to draw, builds commands</li>
 *   <li><b>Rendering Back-End:</b> Executes commands with specific graphics API</li>
 * </ul>
 * 
 * <p>The class uses:
 * <ul>
 *   <li>{@link ChunkRenderLogic} - Builds draw commands for visible chunks</li>
 *   <li>{@link ChunkMeshManager} - Manages chunk mesh registration and uploading</li>
 *   <li>{@link RenderBackend} - Executes the actual rendering</li>
 * </ul>
 * 
 * @see RenderBackend
 * @see ChunkMeshManager
 */
public class LevelRenderer implements WorldRenderer {
    private static final Logger logger = LoggerFactory.getLogger(LevelRenderer.class);

    private final ChunkMeshManager meshManager;
    private final Frustum frustum;
    private final ChunkRenderLogic chunkLogic;
    private final RenderBackend renderBackend;
    private final CommandBuffer commandBuffer;
    
    private Level currentLevel;
    private boolean firstRenderLogged = false;
    
    /**
     * Create a new LevelRenderer with the given dependencies.
     * 
     * @param meshManager the chunk mesh manager for handling GPU resources
     * @param renderBackend the render backend for executing draw commands
     */
    public LevelRenderer(ChunkMeshManager meshManager, RenderBackend renderBackend) {
        this.meshManager = meshManager;
        this.frustum = new Frustum();
        this.chunkLogic = new ChunkRenderLogic(meshManager, frustum);
        this.renderBackend = renderBackend;
        this.commandBuffer = new CommandBuffer(1000); // Initial capacity for ~1000 chunks
    }
    
    /**
     * Initialize the renderer with a level.
     * This sets up the chunk unload listener and builds the texture atlas.
     * Also initializes the rendering backend with materials.
     */
    @Override
    public void initWithLevel(Level level) {
        if (currentLevel != null) {
            currentLevel.setChunkUnloadListener(null);
        }
        this.currentLevel = level;
        
        // Set up chunk unload listener
        level.setChunkUnloadListener(chunk -> {
            meshManager.unregisterMesh(chunk, renderBackend);
            meshManager.removeChunkFromCache(chunk);
        });
        
        // Build texture atlas once on first initialization
        if (!meshManager.isTextureAtlasInitialized()) {
            meshManager.initializeTextureAtlas(level);
        }
        
        // Initialize backend with materials
        if (!meshManager.isBackendInitialized()) {
            meshManager.initializeBackend(renderBackend);
            
            // Register any chunks that already have meshes
            if (currentLevel != null) {
                meshManager.registerExistingChunks(currentLevel, renderBackend);
            }
        }
    }
    
    /**
     * Render all loaded chunks in the world.
     * 
     * <p>Uses the {@link RenderBackend} abstraction for all rendering operations:
     * <ol>
     *   <li>Register all chunks with mesh manager</li>
     *   <li>Process async mesh uploads and register them with backend</li>
     *   <li>Handle dirty chunks</li>
     *   <li>Build draw commands via {@link ChunkRenderLogic}</li>
     *   <li>Submit commands to backend</li>
     * </ol>
     */
    @Override
    public void render(Level world, float playerX, float playerY, float playerZ) {
        // IMPORTANT: Register all chunks FIRST before processing mesh uploads
        // This ensures chunks are in the registry when uploadMeshBuffer tries to look them up
        for (LevelChunk chunk : world.getLoadedChunks()) {
            meshManager.registerChunk(chunk);
        }
        
        // Process completed mesh buffers from async loader
        // This makes newly loaded chunk meshes available for rendering
        List<ChunkMeshBuffer> completedMeshBuffers = world.getAsyncLoader().collectCompletedMeshBuffers();
        for (ChunkMeshBuffer meshBuffer : completedMeshBuffers) {
            meshManager.uploadMeshBuffer(meshBuffer, renderBackend);
        }
        
        // Handle dirty chunks
        for (LevelChunk chunk : world.getLoadedChunks()) {
            if (chunk.isDirty()) {
                chunk.setDirty(false);
                world.getAsyncLoader().requestChunkMeshRebuild(chunk);
            }
        }
        
        // Clear command buffer from previous frame
        commandBuffer.clear();
        
        // Update frustum with current matrices from the backend
        // This enables proper frustum culling based on the current view
        renderBackend.updateFrustum(frustum);
        
        // Build draw commands (front-end logic, no graphics API calls)
        chunkLogic.buildCommands(world, commandBuffer);
        
        // Render via backend - use backend's matrix operations instead of direct GL calls
        renderBackend.pushMatrix();
        
        renderBackend.beginFrame();
        for (DrawCommand cmd : commandBuffer.getCommands()) {
            renderBackend.submit(cmd);
        }
        renderBackend.endFrame();
        
        renderBackend.popMatrix();
        
        // Log rendering stats on first render only
        if (!firstRenderLogged && chunkLogic.getVisibleChunkCount() > 0) {
            logger.info("Started world rendering with {} visible chunks", 
                       chunkLogic.getVisibleChunkCount());
            firstRenderLogged = true;
        }
    }
    
    /**
     * Get the number of chunks that were rendered in the last frame.
     */
    @Override
    public int getRenderedChunkCount() {
        return chunkLogic.getVisibleChunkCount();
    }
    
    /**
     * Get the number of chunks that were culled (not rendered) in the last frame.
     */
    @Override
    public int getCulledChunkCount() {
        return chunkLogic.getCulledChunkCount();
    }
    
    /**
     * Get the total number of loaded chunks in the last frame.
     */
    public int getTotalChunkCount() {
        return chunkLogic.getTotalChunkCount();
    }
    
    /**
     * Get the render backend used by this renderer.
     * 
     * @return the render backend
     */
    @Override
    public RenderBackend getRenderBackend() {
        return renderBackend;
    }
}
