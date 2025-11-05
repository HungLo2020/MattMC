package mattmc.client.renderer.chunk;

import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkUtils;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of chunks using VBO/VAO with texture atlas.
 * Modern rendering approach similar to Minecraft Java Edition.
 */
public class ChunkRenderer {
    
    // VAO cache: maps chunks to their VAOs
    private final Map<LevelChunk, ChunkVAO> vaoCache = new HashMap<>();
    
    // Cache for chunk key to chunk mapping (for mesh data uploads)
    private final Map<Long, LevelChunk> chunkByKey = new HashMap<>();
    
    // Texture atlas for VBO rendering
    private TextureAtlas textureAtlas = null;
    
    /**
     * Register a chunk for tracking (so mesh buffers can be uploaded to it).
     * Call this before uploading mesh buffers.
     */
    public void registerChunk(LevelChunk chunk) {
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.put(key, chunk);
    }
    
    /**
     * Render a chunk using VBO/VAO.
     */
    public void renderChunk(LevelChunk chunk) {
        // Check if chunk has been marked as dirty
        if (chunk.isDirty()) {
            invalidateChunk(chunk);
            chunk.setDirty(false);
        }
        
        // Get VAO
        ChunkVAO vao = vaoCache.get(chunk);
        if (vao == null) {
            // VAO not ready yet - it will be uploaded later from async mesh building
            return;
        }
        
        // Enable texturing and bind texture atlas
        glEnable(GL_TEXTURE_2D);
        if (textureAtlas != null) {
            textureAtlas.bind();
        }
        
        // Render using VAO (single draw call!)
        vao.render();
        
        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    /**
     * Upload mesh buffer to GPU and create VAO.
     * This is called on the render thread with pre-built mesh buffer from a worker thread.
     * Returns true if upload was successful.
     */
    public boolean uploadMeshBuffer(ChunkMeshBuffer meshBuffer) {
        long key = chunkKey(meshBuffer.getChunkX(), meshBuffer.getChunkZ());
        LevelChunk chunk = chunkByKey.get(key);
        
        if (chunk == null) {
            // Chunk not loaded yet or was unloaded
            return false;
        }
        
        // Remove old VAO if it exists
        ChunkVAO oldVAO = vaoCache.remove(chunk);
        if (oldVAO != null) {
            oldVAO.delete();
        }
        
        // Create new VAO from mesh buffer
        if (!meshBuffer.isEmpty()) {
            ChunkVAO vao = new ChunkVAO(meshBuffer);
            vaoCache.put(chunk, vao);
            System.out.println("Uploaded VAO for chunk (" + meshBuffer.getChunkX() + ", " + meshBuffer.getChunkZ() + 
                             ") with " + meshBuffer.getIndexCount() + " indices");
        } else {
            System.out.println("Skipped empty mesh for chunk (" + meshBuffer.getChunkX() + ", " + meshBuffer.getChunkZ() + ")");
        }
        
        return true;
    }
    
    /**
     * Invalidate a chunk's rendering resources, forcing it to be rebuilt.
     * Call this when blocks in the chunk are modified.
     */
    public void invalidateChunk(LevelChunk chunk) {
        // Delete VAO if it exists
        ChunkVAO vao = vaoCache.remove(chunk);
        if (vao != null) {
            vao.delete();
        }
    }
    
    /**
     * Helper to create chunk key from coordinates.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ChunkUtils.chunkKey(chunkX, chunkZ);
    }
    
    /**
     * Remove a chunk from the tracking cache.
     * Call this when a chunk is unloaded.
     */
    public void removeChunkFromCache(LevelChunk chunk) {
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.remove(key);
        invalidateChunk(chunk);
    }
    
    /**
     * Set the texture atlas for VBO rendering.
     */
    public void setTextureAtlas(TextureAtlas atlas) {
        this.textureAtlas = atlas;
        System.out.println("Texture atlas set for chunk renderer");
    }
    
    /**
     * Get the texture atlas.
     */
    public TextureAtlas getTextureAtlas() {
        return textureAtlas;
    }
}
