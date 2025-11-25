package mattmc.client.renderer.level;

import mattmc.world.level.chunk.LevelChunk;

/**
 * Backend-agnostic interface for rendering individual chunks within a region.
 * 
 * <p>This interface abstracts the details of how chunk rendering is performed,
 * allowing the {@link RegionRenderer} to work with any backend implementation
 * without directly importing OpenGL-specific types.
 * 
 * <p><b>Architecture:</b> Implementations of this interface handle the actual
 * rendering of chunk geometry to the graphics API.
 * 
 * @see mattmc.client.renderer.backend.opengl.ChunkRenderer OpenGL implementation
 */
public interface RegionChunkRenderer {
    
    /**
     * Render a single chunk.
     * 
     * <p>The chunk should already be positioned correctly via matrix transformations
     * before this method is called.
     * 
     * @param chunk the chunk to render
     * @return true if rendering occurred, false if no mesh was available
     */
    boolean renderChunk(LevelChunk chunk);
    
    /**
     * Check if a chunk has mesh data ready for rendering.
     * 
     * @param chunk the chunk to check
     * @return true if the chunk has a mesh ready to render
     */
    boolean hasChunkMesh(LevelChunk chunk);
}
