package mattmc.client.renderer;
import mattmc.client.renderer.backend.RenderPass;
import mattmc.client.renderer.backend.DrawCommand;

import mattmc.client.renderer.chunk.ChunkMeshRegistry;
import mattmc.client.renderer.Frustum;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Front-end logic for chunk rendering that builds draw commands without making GL calls.
 * 
 * <p>This class is responsible for determining what to render (which chunks are visible,
 * which have meshes, etc.) and creating {@link DrawCommand} objects that describe the
 * rendering work. It does NOT make any OpenGL calls directly - that's delegated to the
 * {@link RenderBackend}.
 * 
 * <p><b>Architecture:</b> This is the "front-end" of chunk rendering:
 * <ul>
 *   <li><b>Front-end (this class):</b> Decides <em>what</em> to draw, builds commands</li>
 *   <li><b>Back-end (RenderBackend):</b> Decides <em>how</em> to draw, issues GL calls</li>
 * </ul>
 * 
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Determine which chunks are visible (frustum culling)</li>
 *   <li>Check which chunks have mesh data ready</li>
 *   <li>Compute chunk transforms (world positions)</li>
 *   <li>Assign mesh/material/transform IDs</li>
 *   <li>Create and accumulate DrawCommand objects</li>
 * </ul>
 * 
 * <p><b>Design Note:</b> This separation allows:
 * <ul>
 *   <li>Testing without OpenGL context</li>
 *   <li>Easier debugging (inspect commands before rendering)</li>
 *   <li>Future optimization (sort/batch commands before submission)</li>
 *   <li>Support for multiple backends (OpenGL now, Vulkan later)</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is NOT thread-safe and must only be called from
 * the rendering thread.
 * 
 * @since Stage 3 of rendering refactor
 * @see CommandBuffer
 * @see DrawCommand
 * @see RenderBackend
 */
public class ChunkRenderLogic {
    private static final Logger logger = LoggerFactory.getLogger(ChunkRenderLogic.class);
    
    private final ChunkMeshRegistry meshRegistry;
    private final Frustum frustum;
    
    // Statistics
    private int totalChunks = 0;
    private int visibleChunks = 0;
    private int culledChunks = 0;
    
    /**
     * Creates chunk render logic with the given mesh registry and frustum.
     * 
     * @param meshRegistry the mesh registry for checking mesh availability
     * @param frustum the frustum for visibility culling
     */
    public ChunkRenderLogic(ChunkMeshRegistry meshRegistry, Frustum frustum) {
        this.meshRegistry = meshRegistry;
        this.frustum = frustum;
    }
    
    /**
     * Builds draw commands for all visible chunks in the world.
     * 
     * <p>This method:
     * <ol>
     *   <li>Iterates through all loaded chunks</li>
     *   <li>Culls chunks outside the frustum</li>
     *   <li>Skips chunks without mesh data</li>
     *   <li>Creates DrawCommand for each visible chunk</li>
     * </ol>
     * 
     * <p>Commands are added to the provided buffer. The buffer is NOT cleared first,
     * allowing multiple logic classes to contribute commands to the same buffer.
     * 
     * <p><b>Note:</b> The frustum should be updated externally before calling this method.
     * 
     * @param world the world containing chunks to render
     * @param buffer the buffer to add commands to
     */
    public void buildCommands(Level world, CommandBuffer buffer) {
        // Reset statistics
        totalChunks = 0;
        visibleChunks = 0;
        culledChunks = 0;
        
        // Iterate through all loaded chunks
        for (LevelChunk chunk : world.getLoadedChunks()) {
            totalChunks++;
            
            // Frustum culling: skip chunks outside the camera view
            if (!frustum.isChunkVisible(chunk.chunkX(), chunk.chunkZ(), 
                                       LevelChunk.WIDTH, LevelChunk.DEPTH, 
                                       LevelChunk.MIN_Y, LevelChunk.MAX_Y)) {
                culledChunks++;
                continue;
            }
            
            // Skip chunks without mesh data
            // Note: chunks are pre-registered in LevelRenderer.render() before buildCommands() is called
            if (!meshRegistry.hasChunkMesh(chunk)) {
                culledChunks++;
                continue;
            }
            
            // Get mesh ID for this chunk
            int meshId = meshRegistry.getMeshIdForChunk(chunk);
            if (meshId < 0) {
                // Chunk doesn't have a mesh ID assigned yet
                culledChunks++;
                continue;
            }
            
            // Get material ID (all chunks use the same material for now)
            int materialId = meshRegistry.getDefaultMaterialId();
            
            // Get transform ID for this chunk's world position
            int transformId = getTransformIdForChunk(chunk);
            
            // Create draw command for this chunk
            DrawCommand cmd = new DrawCommand(meshId, materialId, transformId, RenderPass.OPAQUE);
            buffer.add(cmd);
            
            visibleChunks++;
        }
    }
    
    /**
     * Computes a unique transform ID for a chunk based on its world position.
     * 
     * <p>This is a simple hash-based ID that encodes the chunk coordinates.
     * The actual transform (translation) is computed elsewhere based on this ID.
     * 
     * @param chunk the chunk to get a transform ID for
     * @return the transform ID
     */
    private int getTransformIdForChunk(LevelChunk chunk) {
        // Use chunk coordinates as a unique ID
        // We use a simple encoding: (x << 16) | (z & 0xFFFF)
        // This works for chunk coordinates in the range -32768 to 32767
        int x = chunk.chunkX();
        int z = chunk.chunkZ();
        return (x << 16) | (z & 0xFFFF);
    }
    
    /**
     * Returns the number of chunks that were visible (had commands generated) in the last
     * buildCommands call.
     * 
     * @return number of visible chunks
     */
    public int getVisibleChunkCount() {
        return visibleChunks;
    }
    
    /**
     * Returns the number of chunks that were culled (not rendered) in the last
     * buildCommands call.
     * 
     * @return number of culled chunks
     */
    public int getCulledChunkCount() {
        return culledChunks;
    }
    
    /**
     * Returns the total number of loaded chunks in the last buildCommands call.
     * 
     * @return total number of chunks
     */
    public int getTotalChunkCount() {
        return totalChunks;
    }
}
