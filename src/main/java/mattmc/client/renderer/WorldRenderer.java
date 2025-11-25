package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.world.level.Level;

/**
 * API-agnostic interface for rendering the game world/level.
 * 
 * <p>This interface abstracts away the details of how the world is rendered
 * (e.g., OpenGL, Vulkan), allowing game logic to use world rendering without
 * depending on specific backend implementations.
 * 
 * <p><b>Architecture:</b> This allows screen code to be defined outside the
 * backend/ directory while the actual world rendering remains in the backend.
 * 
 * @see mattmc.client.renderer.backend.opengl.LevelRenderer OpenGL implementation
 */
public interface WorldRenderer {
    
    /**
     * Initialize the renderer with a level.
     * 
     * @param level the level to render
     */
    void initWithLevel(Level level);
    
    /**
     * Render the world from the given camera position.
     * 
     * @param level the level to render
     * @param cameraX camera X position
     * @param cameraY camera Y position (eye level)
     * @param cameraZ camera Z position
     */
    void render(Level level, float cameraX, float cameraY, float cameraZ);
    
    /**
     * Get the render backend used by this world renderer.
     * 
     * @return the render backend
     */
    RenderBackend getRenderBackend();
    
    /**
     * Get the count of chunks that were actually rendered in the last frame.
     * 
     * @return number of rendered chunks
     */
    int getRenderedChunkCount();
    
    /**
     * Get the count of chunks that were culled (not rendered) in the last frame.
     * 
     * @return number of culled chunks
     */
    int getCulledChunkCount();
}
