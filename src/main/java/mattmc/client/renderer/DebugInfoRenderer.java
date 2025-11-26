package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;

/**
 * Backend-agnostic debug info renderer.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (debug information)
 * and coordinating with the backend to actually render it. It contains NO OpenGL-specific
 * code and works purely through the {@link RenderBackend} abstraction.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Delegates projection setup to the backend (backend-agnostic)</li>
 * </ul>
 * 
 * <p><b>Abstraction Layer:</b> This class lives outside the backend/ directory
 * and can be safely used by any code in the application. It depends only on the
 * backend interface, not on any specific implementation.
 */
public class DebugInfoRenderer {
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend;
    
    /**
     * Create a new debug info renderer.
     */
    public DebugInfoRenderer() {
        this.logic = new UIRenderLogic();
        this.buffer = new CommandBuffer();
    }
    
    /**
     * Set the render backend to use for rendering.
     * 
     * @param backend the backend to use (must not be null)
     * @throws IllegalArgumentException if backend is null
     */
    public void setBackend(RenderBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend cannot be null");
        }
        this.backend = backend;
    }
    
    /**
     * Render debug information in the top-left corner.
     * 
     * <p>This method is completely backend-agnostic. It delegates projection setup,
     * builds draw commands, and submits them to the backend for rendering.
     * 
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param playerX Player X position
     * @param playerY Player Y position
     * @param playerZ Player Z position
     * @param yaw Player yaw rotation
     * @param pitch Player pitch rotation
     * @param roll Player roll rotation
     * @param fps Current frames per second
     * @param loadedChunks Number of loaded chunks
     * @param pendingChunks Number of pending chunks
     * @param activeWorkers Number of active workers
     * @param renderedChunks Number of rendered chunks
     * @param culledChunks Number of culled chunks
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ, 
                       float yaw, float pitch, float roll, double fps, 
                       int loadedChunks, int pendingChunks, int activeWorkers, int renderedChunks, int culledChunks) {
        render(screenWidth, screenHeight, playerX, playerY, playerZ, yaw, pitch, roll, fps,
               loadedChunks, pendingChunks, activeWorkers, renderedChunks, culledChunks, null, null);
    }
    
    /**
     * Render debug information in the top-left corner with gamemode info.
     * 
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param playerX Player X position
     * @param playerY Player Y position
     * @param playerZ Player Z position
     * @param yaw Player yaw rotation
     * @param pitch Player pitch rotation
     * @param roll Player roll rotation
     * @param fps Current frames per second
     * @param loadedChunks Number of loaded chunks
     * @param pendingChunks Number of pending chunks
     * @param activeWorkers Number of active workers
     * @param renderedChunks Number of rendered chunks
     * @param culledChunks Number of culled chunks
     * @param defaultGamemode The world's default gamemode (can be null)
     * @param playerGamemode The player's current gamemode (can be null)
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ, 
                       float yaw, float pitch, float roll, double fps, 
                       int loadedChunks, int pendingChunks, int activeWorkers, int renderedChunks, int culledChunks,
                       String defaultGamemode, String playerGamemode) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Setup 2D projection for UI rendering (delegated to backend)
        backend.setup2DProjection(screenWidth, screenHeight);
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        logic.beginFrame(); // Clear text registry for this frame using instance method
        logic.buildDebugInfoCommands(screenWidth, screenHeight, 
            playerX, playerY, playerZ, yaw, pitch, roll, fps,
            loadedChunks, pendingChunks, activeWorkers, renderedChunks, culledChunks,
            defaultGamemode, playerGamemode,
            buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection (delegated to backend)
        backend.restore2DProjection();
    }
}
