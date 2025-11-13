package mattmc.client.renderer.chunk;

import mattmc.client.renderer.VoxelLitShader;
import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkUtils;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles rendering of chunks using VBO/VAO with texture atlas and basic lighting.
 * Implements simple Lambert diffuse lighting from the sun.
 */
public class ChunkRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ChunkRenderer.class);

    // Calculate expected chunk count based on maximum render distance
    // For render distance of 32, we have (32*2+1)^2 = 4225 chunks
    // Using this as initial capacity prevents HashMap resize operations
    private static final int EXPECTED_CHUNK_COUNT = (32 * 2 + 1) * (32 * 2 + 1);
    
    // VAO cache: maps chunks to their VAOs (ISSUE-003 fix: added initial capacity)
    private final Map<LevelChunk, ChunkVAO> vaoCache = new HashMap<>(EXPECTED_CHUNK_COUNT);
    
    // Cache for chunk key to chunk mapping (ISSUE-003 fix: added initial capacity)
    private final Map<Long, LevelChunk> chunkByKey = new HashMap<>(EXPECTED_CHUNK_COUNT);
    
    // Texture atlas for VBO rendering
    private TextureAtlas textureAtlas = null;
    
    // Shader for rendering with basic lighting
    private VoxelLitShader shader = null;
    
    /**
     * Register a chunk for tracking (so mesh buffers can be uploaded to it).
     * Call this before uploading mesh buffers.
     * This method is idempotent - calling it multiple times has no effect.
     */
    public void registerChunk(LevelChunk chunk) {
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        // Only add if not already present to avoid unnecessary HashMap operations
        chunkByKey.putIfAbsent(key, chunk);
    }
    
    /**
     * Check if a chunk has mesh data ready for rendering.
     * This should be called before doing any GL state changes to avoid wasted operations.
     * 
     * @param chunk The chunk to check
     * @return true if the chunk has a VAO and can be rendered
     */
    public boolean hasChunkMesh(LevelChunk chunk) {
        return vaoCache.get(chunk) != null;
    }
    
    /**
     * Render a chunk using VBO/VAO with basic directional lighting.
     * Returns true if rendering actually happened.
     * 
     * @param chunk The chunk to render
     * @param skyBrightness Sky brightness multiplier (0.0-1.0) from day cycle
     * @param sunDirection Sun direction vector [x, y, z] from day cycle
     * @return true if the chunk was rendered, false if no VAO available
     */
    public boolean renderChunk(LevelChunk chunk, float skyBrightness, float[] sunDirection) {
        // Get VAO
        ChunkVAO vao = vaoCache.get(chunk);
        if (vao == null) {
            // VAO not ready yet - it will be uploaded later from async mesh building
            return false;
        }
        
        // Initialize shader if not already done
        if (shader == null) {
            shader = new VoxelLitShader();
            logger.info("Initialized VoxelLitShader for chunk rendering with Lambert lighting");
        }
        
        // Use the shader
        shader.use();
        
        // Set shader uniforms
        shader.setTextureSampler(0); // Texture unit 0
        shader.setSunDirection(sunDirection[0], sunDirection[1], sunDirection[2]);
        shader.setSkyBrightness(skyBrightness);
        
        // Enable texturing and bind texture atlas to unit 0
        glEnable(GL_TEXTURE_2D);
        glActiveTexture(GL_TEXTURE0);
        if (textureAtlas != null) {
            textureAtlas.bind();
        }
        
        // Render using VAO (single draw call!)
        vao.render();
        
        // Unbind shader and texture
        VoxelLitShader.unbind();
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return true;
    }
    
    /**
     * Render a chunk with default lighting parameters (for legacy compatibility).
     * Uses default sun direction and full brightness.
     * 
     * @param chunk The chunk to render
     * @return true if the chunk was rendered, false if no VAO available
     */
    public boolean renderChunk(LevelChunk chunk) {
        // Default: sun at noon position, full brightness
        float[] defaultSunDirection = {0.3f, -0.8f, 0.5f};
        return renderChunk(chunk, 1.0f, defaultSunDirection);
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
        
        // Create new VAO from mesh buffer first
        ChunkVAO newVAO = null;
        if (!meshBuffer.isEmpty()) {
            newVAO = new ChunkVAO(meshBuffer);
            // logger.info("Uploaded VAO for chunk ({},{}) with {} indices", meshBuffer.getChunkX(), meshBuffer.getChunkZ(), meshBuffer.getIndexCount());
        } else {
            // logger.info("Skipped empty mesh for chunk ({},{})", meshBuffer.getChunkX(), meshBuffer.getChunkZ());
        }
        
        // ONLY NOW replace and delete old VAO (after new one is ready)
        // This prevents flickering - old VAO stays visible until replaced
        ChunkVAO oldVAO = vaoCache.remove(chunk);
        if (newVAO != null) {
            vaoCache.put(chunk, newVAO);
        }
        if (oldVAO != null) {
            oldVAO.delete();
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
        // logger.info("Texture atlas set for chunk renderer");
    }
    
    /**
     * Get the texture atlas.
     */
    public TextureAtlas getTextureAtlas() {
        return textureAtlas;
    }
    
    /**
     * Cleanup all OpenGL resources (VAOs).
     * Call this when the renderer is no longer needed.
     */
    public void cleanup() {
        logger.info("Cleaning up ChunkRenderer - deleting {} VAOs", vaoCache.size());
        for (ChunkVAO vao : vaoCache.values()) {
            if (vao != null) {
                vao.delete();
            }
        }
        vaoCache.clear();
        chunkByKey.clear();
    }
}
