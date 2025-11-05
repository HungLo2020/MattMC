package mattmc.client.renderer;

import mattmc.client.renderer.chunk.RegionRenderer;
import mattmc.client.renderer.texture.TextureAtlas;

import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.client.renderer.chunk.ChunkMeshData;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;
import mattmc.world.level.Level;

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
     * Also processes pending mesh uploads from background threads.
     */
    public void render(Level world, float playerX, float playerY, float playerZ) {
        // Process completed mesh data from async loader (old display list path)
        List<ChunkMeshData> completedMeshes = world.getAsyncLoader().collectCompletedMeshes();
        for (ChunkMeshData meshData : completedMeshes) {
            chunkRenderer.uploadMeshData(meshData);
        }
        
        // Process completed mesh buffers from async loader (new VBO/VAO path)
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
