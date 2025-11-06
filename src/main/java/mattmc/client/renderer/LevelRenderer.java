package mattmc.client.renderer;

import mattmc.client.renderer.texture.TextureAtlas;

import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders all loaded chunks in an infinite world.
 * Similar to RegionRenderer but works with the Level system.
 * 
 * Now handles async mesh uploads from background threads and texture atlas.
 * Implements frustum culling to skip rendering chunks outside the camera view.
 */
public class LevelRenderer {
    private static final Logger logger = LoggerFactory.getLogger(LevelRenderer.class);

    private final ChunkRenderer chunkRenderer;
    private final Frustum frustum;
    private Level currentLevel;
    private boolean textureAtlasInitialized = false;
    
    // Statistics for debugging
    private int totalChunks = 0;
    private int renderedChunks = 0;
    private int culledChunks = 0;
    
    public LevelRenderer() {
        this.chunkRenderer = new ChunkRenderer();
        this.frustum = new Frustum();
    }
    
    /**
     * Initialize the renderer with a level.
     * This sets up the chunk unload listener and builds the texture atlas.
     */
    public void initWithLevel(Level level) {
        if (currentLevel != null) {
            currentLevel.setChunkUnloadListener(null);
        }
        this.currentLevel = level;
        level.setChunkUnloadListener(chunk -> chunkRenderer.removeChunkFromCache(chunk));
        
        // Build texture atlas once on first initialization
        if (!textureAtlasInitialized) {
            logger.info("Initializing texture atlas for VBO rendering...");
            TextureAtlas atlas = new TextureAtlas();
            chunkRenderer.setTextureAtlas(atlas);
            level.getAsyncLoader().setTextureAtlas(atlas);
            textureAtlasInitialized = true;
            logger.info("Texture atlas initialized with {}{}", atlas.getTextureCount(), " textures");
        }
    }
    
    /**
     * Render all loaded chunks in the world.
     * Also processes pending mesh uploads from background threads and handles dirty chunks.
     * Uses frustum culling to skip chunks outside the camera view.
     */
    public void render(Level world, float playerX, float playerY, float playerZ) {
        // Update frustum from current GL matrices (must be called after camera setup)
        frustum.update();
        
        // Reset statistics
        totalChunks = 0;
        renderedChunks = 0;
        culledChunks = 0;
        
        // First, register all loaded chunks with the renderer so uploads can find them
        for (LevelChunk chunk : world.getLoadedChunks()) {
            chunkRenderer.registerChunk(chunk);
            
            // Check if chunk is dirty and needs mesh rebuild
            if (chunk.isDirty()) {
                // Don't invalidate the old VAO yet - keep it visible until new one is ready
                chunk.setDirty(false);
                world.getAsyncLoader().requestChunkMeshRebuild(chunk);
            }
        }
        
        // Process completed mesh buffers from async loader
        List<ChunkMeshBuffer> completedMeshBuffers = world.getAsyncLoader().collectCompletedMeshBuffers();
        for (ChunkMeshBuffer meshBuffer : completedMeshBuffers) {
            chunkRenderer.uploadMeshBuffer(meshBuffer);
        }
        
        glPushMatrix();
        
        // Render each loaded chunk with frustum culling
        for (LevelChunk chunk : world.getLoadedChunks()) {
            totalChunks++;
            
            // Frustum culling: skip chunks outside the camera view
            if (!frustum.isChunkVisible(chunk.chunkX(), chunk.chunkZ(), 
                                       LevelChunk.WIDTH, LevelChunk.DEPTH, 
                                       LevelChunk.MIN_Y, LevelChunk.MAX_Y)) {
                culledChunks++;
                continue;
            }
            
            renderedChunks++;
            
            // Calculate chunk world position
            int chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            int chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            
            glPushMatrix();
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            chunkRenderer.renderChunk(chunk);
            glPopMatrix();
        }
        
        glPopMatrix();
    }
    
    /**
     * Get the number of chunks that were rendered in the last frame.
     */
    public int getRenderedChunkCount() {
        return renderedChunks;
    }
    
    /**
     * Get the number of chunks that were culled (not rendered) in the last frame.
     */
    public int getCulledChunkCount() {
        return culledChunks;
    }
    
    /**
     * Get the total number of loaded chunks in the last frame.
     */
    public int getTotalChunkCount() {
        return totalChunks;
    }
}
