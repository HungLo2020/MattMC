package mattmc.client.renderer.level;

import mattmc.client.renderer.chunk.ChunkMeshRegistry;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Backend-agnostic interface for rendering chunks.
 * 
 * <p>This interface abstracts the actual chunk rendering operations,
 * allowing different backend implementations (OpenGL, Vulkan, etc.)
 * to provide their own rendering logic.
 * 
 * <p><b>Architecture:</b> This interface is used by:
 * <ul>
 *   <li>{@link RegionRenderer} - for rendering chunks within regions</li>
 *   <li>Any other renderer that needs to render individual chunks</li>
 * </ul>
 * 
 * <p>Note: This interface extends {@link RegionChunkRenderer} for backwards
 * compatibility and adds additional rendering operations.
 * 
 * @see RegionChunkRenderer
 * @see mattmc.client.renderer.backend.opengl.OpenGLChunkRenderer OpenGL implementation
 */
public interface ChunkRenderer extends RegionChunkRenderer {
    
    /**
     * Render a chunk using the current graphics state.
     * 
     * <p>The chunk should already be positioned correctly via matrix transformations
     * before this method is called.
     * 
     * @param chunk the chunk to render
     * @return true if rendering occurred, false if no mesh was available
     */
    @Override
    boolean renderChunk(LevelChunk chunk);
    
    /**
     * Check if a chunk has mesh data ready for rendering.
     * 
     * @param chunk the chunk to check
     * @return true if the chunk has a mesh ready to render
     */
    @Override
    boolean hasChunkMesh(LevelChunk chunk);
    
    /**
     * Set the mesh registry used for looking up chunk meshes.
     * 
     * @param registry the mesh registry
     */
    void setMeshRegistry(ChunkMeshRegistry registry);
}
