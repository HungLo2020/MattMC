package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;
import mattmc.client.renderer.level.ChunkMeshManager;
import mattmc.client.renderer.VoxelLitShader;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkUtils;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of chunk mesh management.
 * 
 * <p>This class handles OpenGL-specific operations for chunk meshes:
 * <ul>
 *   <li>VAO/VBO management via {@link ChunkVAO}</li>
 *   <li>Texture atlas management</li>
 *   <li>Shader initialization</li>
 *   <li>Backend material registration</li>
 * </ul>
 * 
 * <p><b>Design Note:</b> This class is created by the {@link OpenGLBackendFactory}
 * and is designed to work specifically with {@link OpenGLRenderBackend}. The factory
 * ensures this pairing at construction time. Methods that accept {@link RenderBackend}
 * will fail fast with an {@link IllegalArgumentException} if passed a non-OpenGL backend.
 * This design maintains the interface abstraction while ensuring type safety.
 * 
 * @see ChunkMeshManager
 * @see OpenGLBackendFactory
 */
public class OpenGLChunkMeshManager implements ChunkMeshManager {
	private static final Logger logger = LoggerFactory.getLogger(OpenGLChunkMeshManager.class);

	// Calculate expected chunk count based on maximum render distance
	private static final int EXPECTED_CHUNK_COUNT = (32 * 2 + 1) * (32 * 2 + 1);
	
	// VAO cache: maps chunks to their VAOs
	private final Map<LevelChunk, ChunkVAO> vaoCache = new HashMap<>(EXPECTED_CHUNK_COUNT);
	
	// Cache for chunk key to chunk mapping
	private final Map<Long, LevelChunk> chunkByKey = new HashMap<>(EXPECTED_CHUNK_COUNT);
	
	/**
	 * Texture atlas for VBO rendering.
	 * 
	 * <p><b>Architecture:</b> The TextureAtlas is created at initialization time
	 * (when the OpenGLChunkMeshManager is constructed), before any world is loaded.
	 * This allows string→int texture ID mapping to be established early.
	 * Blocks/items and game logic use string paths, but the rendering backend
	 * uses fast integer texture IDs internally for performance.
	 */
	private final TextureAtlas textureAtlas;
	
	// Shader for rendering
	private VoxelLitShader shader = null;
	
	// Initialization state
	private boolean backendInitialized = false;
	
	/**
	 * Create an OpenGL chunk mesh manager.
	 * 
	 * <p><b>Note:</b> The TextureAtlas is built during construction,
	 * before any world is selected. This establishes the string→int
	 * texture ID mapping that the rendering backend uses for performance.
	 */
	public OpenGLChunkMeshManager() {
		// Build the texture atlas at startup, before any world is loaded.
		// This converts all block/item string texture paths to integer IDs.
		// The atlas instance will be passed to chunk loaders when levels are set.
		this.textureAtlas = new TextureAtlas();
		logger.info("TextureAtlas initialized at startup with {} textures", textureAtlas.getTextureCount());
	}
	
	@Override
	public void registerChunk(LevelChunk chunk) {
		long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
		chunkByKey.putIfAbsent(key, chunk);
	}
	
	@Override
	public boolean hasChunkMesh(LevelChunk chunk) {
		return vaoCache.get(chunk) != null;
	}
	
	@Override
	public int getMeshIdForChunk(LevelChunk chunk) {
		if (!hasChunkMesh(chunk)) {
			return -1;
		}
		return System.identityHashCode(chunk);
	}
	
	@Override
	public int getDefaultMaterialId() {
		return 0;
    }
    
    @Override
    public boolean uploadMeshBuffer(ChunkMeshBuffer meshBuffer, RenderBackend backend) {
        long key = chunkKey(meshBuffer.getChunkX(), meshBuffer.getChunkZ());
        LevelChunk chunk = chunkByKey.get(key);
        
        if (chunk == null) {
            return false;
        }
        
        // Create new VAO from mesh buffer first
        ChunkVAO newVAO = null;
        if (!meshBuffer.isEmpty()) {
            newVAO = new ChunkVAO(meshBuffer);
        }
        
        // Replace and delete old VAO (after new one is ready)
        ChunkVAO oldVAO = vaoCache.remove(chunk);
        if (newVAO != null) {
            vaoCache.put(chunk, newVAO);
        }
        if (oldVAO != null) {
            oldVAO.delete();
        }
        
        // Register mesh with backend if upload was successful
        if (newVAO != null) {
            OpenGLRenderBackend glBackend = requireOpenGLBackend(backend, "uploadMeshBuffer");
            registerMeshWithBackend(chunk, glBackend);
        }
        
        return true;
    }
    
    /**
     * Register a mesh with the OpenGL backend after it's been uploaded.
     */
    private void registerMeshWithBackend(LevelChunk chunk, OpenGLRenderBackend backend) {
        int meshId = getMeshIdForChunk(chunk);
        if (meshId < 0) {
            return;
        }
        
        ChunkVAO vao = getVAOByMeshId(meshId);
        if (vao == null) {
            return;
        }
        
        // Register mesh with backend
        backend.registerMesh(meshId, vao);
        
        // Register transform for this chunk
        int transformId = (chunk.chunkX() << 16) | (chunk.chunkZ() & 0xFFFF);
        float worldX = chunk.chunkX() * LevelChunk.WIDTH;
        float worldZ = chunk.chunkZ() * LevelChunk.DEPTH;
        backend.registerTransform(transformId, worldX, 0, worldZ);
    }
    
    @Override
    public void unregisterMesh(LevelChunk chunk, RenderBackend backend) {
        int meshId = getMeshIdForChunk(chunk);
        if (meshId >= 0) {
            OpenGLRenderBackend glBackend = requireOpenGLBackend(backend, "unregisterMesh");
            glBackend.unregisterMesh(meshId);
        }
    }
    
    @Override
    public void removeChunkFromCache(LevelChunk chunk) {
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.remove(key);
        
        // Delete VAO if it exists
        ChunkVAO vao = vaoCache.remove(chunk);
        if (vao != null) {
            vao.delete();
        }
    }
    
    @Override
    public LevelChunk getRegisteredChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        return chunkByKey.get(key);
    }
    
	@Override
	public void initializeBackend(RenderBackend backend) {
		OpenGLRenderBackend glBackend = requireOpenGLBackend(backend, "initializeBackend");
		
		// Ensure shader is initialized
		ensureShaderInitialized();
		
		// Register default material (shader + texture atlas)
		int materialId = getDefaultMaterialId();
		glBackend.registerMaterial(materialId, shader, textureAtlas);
		
		backendInitialized = true;
	}
	
	/**
	 * Set the texture atlas on the level's async loader.
	 * 
	 * <p><b>Note:</b> The TextureAtlas was already built at startup.
	 * This method just passes the pre-built atlas to the level so it
	 * can be used during async chunk mesh building.
	 */
	@Override
	public void initializeTextureAtlas(Level level) {
		// The atlas was built in constructor; just pass it to the level's async loader
		level.getAsyncLoader().setTextureAtlas(textureAtlas);
	}
	
	/**
	 * Returns true since the TextureAtlas is built at construction time.
	 */
	@Override
	public boolean isTextureAtlasInitialized() {
		return textureAtlas != null;
	}
	
	@Override
	public boolean isBackendInitialized() {
		return backendInitialized;
	}
	
	@Override
    public void registerExistingChunks(Level level, RenderBackend backend) {
        OpenGLRenderBackend glBackend = requireOpenGLBackend(backend, "registerExistingChunks");
        int registeredCount = 0;
        
        for (LevelChunk chunk : level.getLoadedChunks()) {
            if (!hasChunkMesh(chunk)) {
                continue;
            }
            
            int meshId = getMeshIdForChunk(chunk);
            if (meshId < 0) {
                continue;
            }
            
            ChunkVAO vao = getVAOByMeshId(meshId);
            if (vao == null) {
                continue;
            }
            
            // Register mesh with backend
            glBackend.registerMesh(meshId, vao);
            
            // Register transform
            int transformId = (chunk.chunkX() << 16) | (chunk.chunkZ() & 0xFFFF);
            float worldX = chunk.chunkX() * LevelChunk.WIDTH;
            float worldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            glBackend.registerTransform(transformId, worldX, 0, worldZ);
            
            registeredCount++;
        }
        
        if (registeredCount > 0) {
            logger.info("Registered {} existing chunks with backend during initialization", registeredCount);
        }
    }
    
    /**
     * Validate that the provided backend is an OpenGLRenderBackend.
     * 
     * @param backend the backend to validate
     * @param methodName the method name for error reporting
     * @return the backend cast to OpenGLRenderBackend
     * @throws IllegalArgumentException if backend is not an OpenGLRenderBackend
     */
    private OpenGLRenderBackend requireOpenGLBackend(RenderBackend backend, String methodName) {
        if (!(backend instanceof OpenGLRenderBackend)) {
            throw new IllegalArgumentException(
                "OpenGLChunkMeshManager." + methodName + "() requires OpenGLRenderBackend, " +
                "but was given " + (backend != null ? backend.getClass().getName() : "null") +
                ". This class must be paired with OpenGLRenderBackend via OpenGLBackendFactory.");
        }
        return (OpenGLRenderBackend) backend;
    }
    
    /**
     * Get the VAO for a chunk by mesh ID.
     */
    public ChunkVAO getVAOByMeshId(int meshId) {
        for (Map.Entry<LevelChunk, ChunkVAO> entry : vaoCache.entrySet()) {
            if (System.identityHashCode(entry.getKey()) == meshId) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Ensure the shader is initialized.
     */
    private void ensureShaderInitialized() {
        if (shader == null) {
            Shader openglShader = new Shader(
                mattmc.client.renderer.ShaderLoader.loadShader("voxel_lit.vs"),
                mattmc.client.renderer.ShaderLoader.loadShader("voxel_lit.fs")
            );
            shader = new VoxelLitShader(openglShader);
            logger.info("Initialized VoxelLitShader for chunk rendering");
        }
    }
    
    /**
     * Helper to create chunk key from coordinates.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ChunkUtils.chunkKey(chunkX, chunkZ);
    }
    
    /**
     * Get the texture atlas.
     */
    public TextureAtlas getTextureAtlas() {
        return textureAtlas;
    }
    
    /**
     * Get the shader.
     */
    public VoxelLitShader getShader() {
        return shader;
    }
    
    /**
     * Cleanup all OpenGL resources.
     */
    public void cleanup() {
        logger.info("Cleaning up OpenGLChunkMeshManager - deleting {} VAOs", vaoCache.size());
        for (ChunkVAO vao : vaoCache.values()) {
            if (vao != null) {
                vao.delete();
            }
        }
        vaoCache.clear();
        chunkByKey.clear();
    }
}
