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
 * Handles rendering of chunks using VBO/VAO with texture atlas and lit shader.
 * Modern rendering approach similar to Minecraft Java Edition.
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
    
    // Voxel lit shader for proper lighting
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
     * Render a chunk using VBO/VAO with lit shader.
     * Returns true if rendering actually happened.
     * 
     * @param chunk The chunk to render
     * @param cameraX Camera X position for fog
     * @param cameraY Camera Y position for fog
     * @param cameraZ Camera Z position for fog
     * @param skyBrightness Sky brightness multiplier (0.0-1.0) from day cycle
     * @param sunDirection Sun direction vector [x, y, z] from day cycle
     * @return true if the chunk was rendered, false if no VAO available
     */
    public boolean renderChunk(LevelChunk chunk, float cameraX, float cameraY, float cameraZ, 
                              float skyBrightness, float[] sunDirection) {
        return renderChunk(chunk, cameraX, cameraY, cameraZ, skyBrightness, sunDirection, false, null, null);
    }
    
    /**
     * Render a chunk using VBO/VAO with lit shader and optional shadows.
     * Returns true if rendering actually happened.
     * 
     * @param chunk The chunk to render
     * @param cameraX Camera X position for fog
     * @param cameraY Camera Y position for fog
     * @param cameraZ Camera Z position for fog
     * @param skyBrightness Sky brightness multiplier (0.0-1.0) from day cycle
     * @param sunDirection Sun direction vector [x, y, z] from day cycle
     * @param shadowsEnabled Whether shadows are enabled
     * @param shadowMatrix Shadow transformation matrix (can be null if shadows disabled)
     * @param shadowRenderer Shadow renderer for binding shadow map (can be null if shadows disabled)
     * @return true if the chunk was rendered, false if no VAO available
     */
    public boolean renderChunk(LevelChunk chunk, float cameraX, float cameraY, float cameraZ, 
                              float skyBrightness, float[] sunDirection, boolean shadowsEnabled,
                              float[] shadowMatrix, mattmc.client.renderer.SimpleShadowRenderer shadowRenderer) {
        // Get VAO
        ChunkVAO vao = vaoCache.get(chunk);
        if (vao == null) {
            // VAO not ready yet - it will be uploaded later from async mesh building
            return false;
        }
        
        // Initialize shader if not already done
        if (shader == null) {
            shader = new VoxelLitShader();
            logger.info("Initialized VoxelLitShader for chunk rendering");
        }
        
        // Use the lit shader
        shader.use();
        
        // Set shader uniforms
        shader.setCameraPosition(cameraX, cameraY, cameraZ);
        shader.setTextureSampler(0); // Texture unit 0
        
        // Sun settings from day cycle
        shader.setSunDirection(sunDirection[0], sunDirection[1], sunDirection[2]);
        shader.setSunColor(0.6f, 0.6f, 0.55f); // Neutral sun color
        
        // Ambient settings - moderate values for natural look
        shader.setAmbientSky(0.4f, 0.45f, 0.5f); // Cool blue sky ambient
        shader.setAmbientBlock(0.9f, 0.7f, 0.4f); // Warm torch/block light
        
        // Sky brightness (dims lighting at night)
        shader.setSkyBrightness(skyBrightness);
        
        // Fog settings
        shader.setFogColor(0.5f, 0.7f, 1.0f); // Sky blue fog (linear space)
        
        // Gamma correction
        shader.setGamma(2.2f);
        
        // Shadow settings
        if (shadowsEnabled && shadowRenderer != null && shadowMatrix != null) {
            shader.setShadowsEnabled(true);
            shader.setShadowMatrix(shadowMatrix);
            shader.setShadowMapSampler(1); // Texture unit 1 for shadow map
            shadowRenderer.bindShadowMap(GL_TEXTURE1);
        } else {
            shader.setShadowsEnabled(false);
        }
        
        // Enable texturing and bind texture atlas to unit 0
        glEnable(GL_TEXTURE_2D);
        glActiveTexture(GL_TEXTURE0); // Ensure we're on texture unit 0
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
     * Render a chunk using VBO/VAO with lit shader (backward compatibility).
     * Uses default camera position at origin and full brightness.
     * 
     * @param chunk The chunk to render
     * @return true if the chunk was rendered, false if no VAO available
     */
    public boolean renderChunk(LevelChunk chunk) {
        return renderChunk(chunk, 0, 0, 0, 1.0f, new float[]{0.3f, -0.8f, 0.5f});
    }
    
    /**
     * Render chunk geometry only (no shader setup) for shadow passes.
     * 
     * @param chunk The chunk to render
     */
    public void renderChunkGeometryOnly(LevelChunk chunk) {
        ChunkVAO vao = vaoCache.get(chunk);
        if (vao != null) {
            vao.render();
        }
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
