package mattmc.client.renderer;

import mattmc.client.renderer.texture.TextureAtlas;

import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders all loaded chunks in an infinite world.
 * Similar to RegionRenderer but works with the Level system.
 * 
 * Now handles async mesh uploads from background threads and texture atlas.
 */
public class LevelRenderer {
    private final ChunkRenderer chunkRenderer;
    private Level currentLevel;
    private boolean textureAtlasInitialized = false;
    
    public LevelRenderer() {
        this.chunkRenderer = new ChunkRenderer();
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
            System.out.println("Initializing texture atlas for VBO rendering...");
            TextureAtlas atlas = new TextureAtlas();
            chunkRenderer.setTextureAtlas(atlas);
            level.getAsyncLoader().setTextureAtlas(atlas);
            textureAtlasInitialized = true;
            System.out.println("Texture atlas initialized with " + atlas.getTextureCount() + " textures");
        }
    }
    
    /**
     * Render all loaded chunks in the world.
     * Also processes pending mesh uploads from background threads and handles dirty chunks.
     */
    public void render(Level world, float playerX, float playerY, float playerZ) {
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
        
        // Render each loaded chunk
        for (LevelChunk chunk : world.getLoadedChunks()) {
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
}
