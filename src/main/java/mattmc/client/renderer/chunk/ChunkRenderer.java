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
 * Handles rendering of chunks using VBO/VAO with texture atlas and per-vertex lighting.
 * Supports gamma-corrected lighting with configurable gamma curve.
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
    
    // Basic shader for rendering
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
	 * Render a chunk using VBO/VAO with lighting shader.
	 * Returns true if rendering actually happened.
	 * 
	 * @param chunk The chunk to render
	 * @return true if the chunk was rendered, false if no VAO available
	 */
	public boolean renderChunk(LevelChunk chunk) {
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
		
		// Use the shader
		shader.use();
		
		// Set shader uniforms
		shader.setTextureSampler(0); // Texture unit 0
		shader.applyDefaultLighting(); // Apply default gamma and emissive boost
		
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
	 * Get the shader instance for configuration.
	 * Returns null if shader hasn't been initialized yet.
	 */
	public VoxelLitShader getShader() {
		return shader;
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
	
	// ===== Stage 3: Backend Integration Support =====
	
	/**
	 * Get the mesh ID for a chunk.
	 * Returns the hash code of the chunk object, which serves as a unique identifier.
	 * Returns -1 if the chunk has no mesh.
	 * 
	 * @param chunk the chunk to get mesh ID for
	 * @return mesh ID or -1 if no mesh
	 */
	public int getMeshIdForChunk(LevelChunk chunk) {
		if (!hasChunkMesh(chunk)) {
			return -1;
		}
		// Use chunk's identity hash code as mesh ID
		return System.identityHashCode(chunk);
	}
	
	/**
	 * Get the VAO for a chunk by mesh ID.
	 * This is used by the backend to look up the actual VAO to render.
	 * 
	 * @param meshId the mesh ID (from getMeshIdForChunk)
	 * @return the ChunkVAO or null if not found
	 */
	public ChunkVAO getVAOByMeshId(int meshId) {
		// Search through cache to find chunk with matching identity hash
		for (Map.Entry<LevelChunk, ChunkVAO> entry : vaoCache.entrySet()) {
			if (System.identityHashCode(entry.getKey()) == meshId) {
				return entry.getValue();
			}
		}
		return null;
	}
	
	/**
	 * Get the default material ID for chunk rendering.
	 * All chunks currently use the same material (shader + texture atlas).
	 * 
	 * @return default material ID (always 0 for now)
	 */
	public int getDefaultMaterialId() {
		return 0;
	}
	
	/**
	 * Initialize the shader if not already done.
	 * Call this before registering materials with the backend.
	 */
	public void ensureShaderInitialized() {
		if (shader == null) {
			shader = new VoxelLitShader();
			logger.info("Initialized VoxelLitShader for chunk rendering");
		}
	}
	
	/**
	 * Get the chunk for given coordinates from the chunk registry.
	 * Returns null if the chunk is not registered.
	 * 
	 * @param chunkX chunk X coordinate
	 * @param chunkZ chunk Z coordinate
	 * @return the registered chunk or null
	 */
	public LevelChunk getRegisteredChunk(int chunkX, int chunkZ) {
		long key = chunkKey(chunkX, chunkZ);
		return chunkByKey.get(key);
	}
}
