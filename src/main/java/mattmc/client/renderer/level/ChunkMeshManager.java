package mattmc.client.renderer.level;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;
import mattmc.client.renderer.chunk.ChunkMeshRegistry;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Backend-agnostic interface for managing chunk meshes.
 * 
 * <p>This interface abstracts the details of how chunk meshes are stored, uploaded,
 * and registered with the render backend. It allows the {@link LevelRenderer} to
 * work with any backend implementation without directly importing OpenGL-specific types.
 * 
 * <p><b>Architecture:</b> Implementations of this interface handle:
 * <ul>
 *   <li>Chunk registration and tracking</li>
 *   <li>Mesh buffer uploading to GPU</li>
 *   <li>Mesh registration with the render backend</li>
 *   <li>Texture atlas initialization</li>
 * </ul>
 * 
 * @see mattmc.client.renderer.backend.opengl.OpenGLChunkMeshManager OpenGL implementation
 */
public interface ChunkMeshManager extends ChunkMeshRegistry {
    
    /**
     * Register a chunk for tracking.
     * Call this before uploading mesh buffers.
     * 
     * @param chunk the chunk to register
     */
    void registerChunk(LevelChunk chunk);
    
    /**
     * Upload a mesh buffer to the GPU and register with the backend.
     * 
     * @param meshBuffer the mesh buffer to upload
     * @param backend the render backend to register with
     * @return true if upload was successful
     */
    boolean uploadMeshBuffer(ChunkMeshBuffer meshBuffer, RenderBackend backend);
    
    /**
     * Unregister a mesh from the backend when a chunk is unloaded.
     * 
     * @param chunk the chunk being unloaded
     * @param backend the render backend
     */
    void unregisterMesh(LevelChunk chunk, RenderBackend backend);
    
    /**
     * Remove a chunk from the tracking cache.
     * 
     * @param chunk the chunk to remove
     */
    void removeChunkFromCache(LevelChunk chunk);
    
    /**
     * Get the registered chunk for given coordinates.
     * 
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return the registered chunk or null
     */
    LevelChunk getRegisteredChunk(int chunkX, int chunkZ);
    
    /**
     * Initialize the mesh manager and register materials with the backend.
     * 
     * @param backend the render backend
     */
    void initializeBackend(RenderBackend backend);
    
    /**
     * Initialize the texture atlas and set it on the async loader.
     * 
     * @param level the level to initialize for
     */
    void initializeTextureAtlas(Level level);
    
    /**
     * Check if the texture atlas has been initialized.
     * 
     * @return true if initialized
     */
    boolean isTextureAtlasInitialized();
    
    /**
     * Check if the backend has been initialized.
     * 
     * @return true if initialized
     */
    boolean isBackendInitialized();
    
    /**
     * Register all chunks that already have meshes with the backend.
     * 
     * @param level the level containing the chunks
     * @param backend the render backend
     */
    void registerExistingChunks(Level level, RenderBackend backend);
}
