package mattmc.client.renderer.chunk;

import mattmc.world.level.chunk.LevelChunk;

/**
 * Backend-agnostic interface for chunk mesh registry.
 * 
 * <p>This interface abstracts away the details of how chunk meshes are stored and managed,
 * allowing game logic to query mesh availability without depending on OpenGL-specific
 * implementations.
 * 
 * <p><b>Architecture:</b> This allows chunk rendering logic to be defined outside the
 * backend/ directory while the actual mesh storage implementation remains in the backend.
 * 
 * @see mattmc.client.renderer.backend.opengl.ChunkRenderer OpenGL implementation
 */
public interface ChunkMeshRegistry {
    
    /**
     * Check if a chunk has mesh data available for rendering.
     * 
     * @param chunk the chunk to check
     * @return true if the chunk has a mesh ready to render
     */
    boolean hasChunkMesh(LevelChunk chunk);
    
    /**
     * Get the mesh ID for a specific chunk.
     * 
     * <p>The mesh ID is an abstract identifier used in draw commands.
     * The backend is responsible for mapping this ID to actual GPU resources.
     * 
     * @param chunk the chunk to get the mesh ID for
     * @return the mesh ID, or -1 if no mesh is available
     */
    int getMeshIdForChunk(LevelChunk chunk);
    
    /**
     * Get the default material ID for chunk rendering.
     * 
     * <p>The material ID is an abstract identifier used in draw commands.
     * The backend is responsible for mapping this ID to actual shader/texture resources.
     * 
     * @return the default material ID for chunks
     */
    int getDefaultMaterialId();
}
