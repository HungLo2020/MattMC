package mattmc.client.renderer;

import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;
import mattmc.client.renderer.chunk.ChunkVAO;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders all loaded chunks in an infinite world.
 * 
 * <p><b>Stage 3 Refactor:</b> This class now uses the {@link RenderBackend} abstraction
 * to render chunks. Direct OpenGL calls have been removed and replaced with:
 * <ul>
 *   <li>{@link ChunkRenderLogic} - Builds draw commands (what to render)</li>
 *   <li>{@link OpenGLRenderBackend} - Executes draw commands (how to render)</li>
 * </ul>
 * 
 * <p>Now handles async mesh uploads from background threads and texture atlas.
 * Implements frustum culling to skip rendering chunks outside the camera view.
 */
public class LevelRenderer {
    private static final Logger logger = LoggerFactory.getLogger(LevelRenderer.class);

    private final ChunkRenderer chunkRenderer;
    private final Frustum frustum;
    private final ChunkRenderLogic chunkLogic;
    private final OpenGLRenderBackend renderBackend;
    private final CommandBuffer commandBuffer;
    
    private Level currentLevel;
    private boolean textureAtlasInitialized = false;
    private boolean backendInitialized = false;
    private boolean firstRenderLogged = false;
    
    public LevelRenderer() {
        this.chunkRenderer = new ChunkRenderer();
        this.frustum = new Frustum();
        this.chunkLogic = new ChunkRenderLogic(chunkRenderer, frustum);
        this.renderBackend = new OpenGLRenderBackend();
        this.commandBuffer = new CommandBuffer(1000); // Initial capacity for ~1000 chunks
    }
    
    /**
     * Initialize the renderer with a level.
     * This sets up the chunk unload listener and builds the texture atlas.
     * Also initializes the rendering backend with materials.
     */
    public void initWithLevel(Level level) {
        if (currentLevel != null) {
            currentLevel.setChunkUnloadListener(null);
        }
        this.currentLevel = level;
        level.setChunkUnloadListener(chunk -> {
            // Unregister mesh from backend
            int meshId = chunkRenderer.getMeshIdForChunk(chunk);
            if (meshId >= 0) {
                renderBackend.unregisterMesh(meshId);
            }
            // Remove from chunk renderer cache
            chunkRenderer.removeChunkFromCache(chunk);
        });
        
        // Build texture atlas once on first initialization
        if (!textureAtlasInitialized) {
            TextureAtlas atlas = new TextureAtlas();
            chunkRenderer.setTextureAtlas(atlas);
            level.getAsyncLoader().setTextureAtlas(atlas);
            textureAtlasInitialized = true;
        }
        
        // Initialize backend with materials
        if (!backendInitialized) {
            initializeBackend();
            backendInitialized = true;
        }
    }
    
    /**
     * Initialize the rendering backend with shaders and materials.
     */
    private void initializeBackend() {
        // Ensure shader is initialized
        chunkRenderer.ensureShaderInitialized();
        
        // Register default material (shader + texture atlas)
        int materialId = chunkRenderer.getDefaultMaterialId();
        renderBackend.registerMaterial(materialId, 
                                      chunkRenderer.getShader(), 
                                      chunkRenderer.getTextureAtlas());
        
        // Register any chunks that already have meshes
        // This handles chunks that were loaded before backend initialization
        if (currentLevel != null) {
            registerExistingChunks();
        }
    }
    
    /**
     * Register all existing chunks with meshes to the backend.
     * Called during initialization to handle chunks loaded before backend setup.
     */
    private void registerExistingChunks() {
        int registeredCount = 0;
        for (LevelChunk chunk : currentLevel.getLoadedChunks()) {
            // Check if chunk has a mesh
            if (!chunkRenderer.hasChunkMesh(chunk)) {
                continue;
            }
            
            // Get mesh ID
            int meshId = chunkRenderer.getMeshIdForChunk(chunk);
            if (meshId < 0) {
                continue;
            }
            
            // Get VAO
            ChunkVAO vao = chunkRenderer.getVAOByMeshId(meshId);
            if (vao == null) {
                continue;
            }
            
            // Register mesh with backend
            renderBackend.registerMesh(meshId, vao);
            
            // Register transform
            int transformId = (chunk.chunkX() << 16) | (chunk.chunkZ() & 0xFFFF);
            float worldX = chunk.chunkX() * LevelChunk.WIDTH;
            float worldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            renderBackend.registerTransform(transformId, worldX, 0, worldZ);
            
            registeredCount++;
        }
        
        if (registeredCount > 0) {
            logger.info("Registered {} existing chunks with backend during initialization", registeredCount);
        }
    }
    
    /**
     * Render all loaded chunks in the world.
     * 
     * <p><b>Stage 3:</b> Now uses {@link RenderBackend} abstraction:
     * <ol>
     *   <li>Process async mesh uploads and register them with backend</li>
     *   <li>Build draw commands via {@link ChunkRenderLogic}</li>
     *   <li>Submit commands to {@link OpenGLRenderBackend}</li>
     * </ol>
     * 
     * <p>This method no longer makes direct OpenGL calls for rendering chunks.
     */
    public void render(Level world, float playerX, float playerY, float playerZ) {
        // IMPORTANT: Register all chunks FIRST before processing mesh uploads
        // This ensures chunks are in the registry when uploadMeshBuffer tries to look them up
        for (LevelChunk chunk : world.getLoadedChunks()) {
            chunkRenderer.registerChunk(chunk);
        }
        
        // Process completed mesh buffers from async loader
        // This makes newly loaded chunk meshes available for rendering
        List<ChunkMeshBuffer> completedMeshBuffers = world.getAsyncLoader().collectCompletedMeshBuffers();
        for (ChunkMeshBuffer meshBuffer : completedMeshBuffers) {
            boolean uploaded = chunkRenderer.uploadMeshBuffer(meshBuffer);
            
            // Register mesh with backend if upload was successful
            if (uploaded) {
                registerMeshWithBackend(meshBuffer);
            }
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
        
        // Build draw commands (front-end logic, no GL calls)
        chunkLogic.buildCommands(world, commandBuffer);
        
        // Render via backend
        glPushMatrix();
        
        renderBackend.beginFrame();
        for (DrawCommand cmd : commandBuffer.getCommands()) {
            renderBackend.submit(cmd);
        }
        renderBackend.endFrame();
        
        glPopMatrix();
        
        // Log rendering stats on first render only
        if (!firstRenderLogged && chunkLogic.getVisibleChunkCount() > 0) {
            logger.info("Rendering {} chunks via RenderBackend (Stage 3)", 
                       chunkLogic.getVisibleChunkCount());
            firstRenderLogged = true;
        }
    }
    
    /**
     * Register a mesh with the backend after it's been uploaded.
     */
    private void registerMeshWithBackend(ChunkMeshBuffer meshBuffer) {
        // Get the chunk from the renderer's registry (same one used by uploadMeshBuffer)
        LevelChunk chunk = chunkRenderer.getRegisteredChunk(
            meshBuffer.getChunkX(), 
            meshBuffer.getChunkZ()
        );
        
        // If chunk is not registered or was unloaded, skip
        if (chunk == null) {
            return;
        }
        
        // Check if the chunk now has a mesh (it should after upload)
        if (!chunkRenderer.hasChunkMesh(chunk)) {
            // Mesh was empty, skip registration
            return;
        }
        
        // Get mesh ID for this chunk
        int meshId = chunkRenderer.getMeshIdForChunk(chunk);
        if (meshId < 0) {
            return;
        }
        
        // Get the VAO that was just created
        ChunkVAO vao = chunkRenderer.getVAOByMeshId(meshId);
        if (vao == null) {
            // VAO lookup failed, this shouldn't happen
            return;
        }
        
        // Register mesh with backend
        renderBackend.registerMesh(meshId, vao);
        
        // Register transform for this chunk
        int transformId = (chunk.chunkX() << 16) | (chunk.chunkZ() & 0xFFFF);
        float worldX = chunk.chunkX() * LevelChunk.WIDTH;
        float worldZ = chunk.chunkZ() * LevelChunk.DEPTH;
        renderBackend.registerTransform(transformId, worldX, 0, worldZ);
        
        // Debug: Log chunk registration
        // logger.debug("Registered chunk ({},{}) with backend: meshId={}, transformId={}", 
        //             chunk.chunkX(), chunk.chunkZ(), meshId, transformId);
    }
    
    /**
     * Get the number of chunks that were rendered in the last frame.
     */
    public int getRenderedChunkCount() {
        return chunkLogic.getVisibleChunkCount();
    }
    
    /**
     * Get the number of chunks that were culled (not rendered) in the last frame.
     */
    public int getCulledChunkCount() {
        return chunkLogic.getCulledChunkCount();
    }
    
    /**
     * Get the total number of loaded chunks in the last frame.
     */
    public int getTotalChunkCount() {
        return chunkLogic.getTotalChunkCount();
    }
}
