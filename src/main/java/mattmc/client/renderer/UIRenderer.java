package mattmc.client.renderer;

// Backend-agnostic renderers (refactored to be outside backend/)
import mattmc.client.renderer.CrosshairRenderer;
import mattmc.client.renderer.HotbarRenderer;

// Backend interface
import mattmc.client.renderer.backend.RenderBackend;

// Still OpenGL-specific (TODO: refactor these as well)
import mattmc.client.renderer.backend.opengl.DebugInfoRenderer;
import mattmc.client.renderer.backend.opengl.CommandUIRenderer;
import mattmc.client.renderer.backend.opengl.LightingDebugRenderer;
import mattmc.client.renderer.backend.opengl.SystemInfoRenderer;
import mattmc.client.renderer.backend.opengl.BlockNameDisplay;

/**
 * Handles rendering of UI elements.
 * Similar to Minecraft's GuiIngame class.
 * Delegates rendering to specialized renderer classes.
 * 
 * <p><b>Stage 4:</b> Now supports backend-based rendering for UI elements.
 * Components that have been refactored to use the backend will use it when available.
 */
public class UIRenderer {
    
    // Delegate renderers for different UI components
    private final CrosshairRenderer crosshairRenderer;
    private final DebugInfoRenderer debugInfoRenderer;
    private final HotbarRenderer hotbarRenderer;
    private final CommandUIRenderer commandUIRenderer;
    private final LightingDebugRenderer lightingDebugRenderer;
    private final SystemInfoRenderer systemInfoRenderer;
    
    // Block name display with blur effect
    private BlockNameDisplay blockNameDisplay;
    
    // Stage 4: Backend for UI rendering (optional, for backward compatibility)
    private RenderBackend backend;
    
    public UIRenderer() {
        this.crosshairRenderer = new CrosshairRenderer();
        this.debugInfoRenderer = new DebugInfoRenderer();
        this.hotbarRenderer = new HotbarRenderer();
        this.commandUIRenderer = new CommandUIRenderer();
        this.lightingDebugRenderer = new LightingDebugRenderer();
        this.systemInfoRenderer = new SystemInfoRenderer();
    }
    
    /**
     * Set the render backend for UI rendering (Stage 4).
     * Propagates the backend to all child renderers.
     * 
     * <p>Note: BlockNameDisplay is not included as it's a specialized effect renderer
     * (with blur effects) which is out of Stage 4 scope per RENDERINGREFACTOR.md.
     * 
     * @param backend the backend to use, or null to use legacy rendering
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
        // Propagate backend to all Stage 4 child renderers
        crosshairRenderer.setBackend(backend);
        hotbarRenderer.setBackend(backend);
        commandUIRenderer.setBackend(backend);
        debugInfoRenderer.setBackend(backend);
        systemInfoRenderer.setBackend(backend);
        // Note: BlockNameDisplay (specialized blur effect) not in Stage 4 scope
    }
    
    /**
     * Draw crosshair in the center of the screen.
     * 
     * <p><b>Stage 4:</b> Uses backend architecture via child renderer.
     */
    public void drawCrosshair(int screenWidth, int screenHeight) {
        crosshairRenderer.render(screenWidth, screenHeight);
    }
    
    /**
     * Draw debug information in the top-left corner.
     * Shows version, FPS, player position, chunk position, region position, and culling stats.
     */
    public void drawDebugInfo(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ, 
                               float yaw, float pitch, float roll, double fps, 
                               int loadedChunks, int pendingChunks, int activeWorkers, int renderedChunks, int culledChunks) {
        debugInfoRenderer.render(screenWidth, screenHeight, playerX, playerY, playerZ, 
                                 yaw, pitch, roll, fps, loadedChunks, pendingChunks, 
                                 activeWorkers, renderedChunks, culledChunks);
    }
    
    /**
     * Draw hotbar at the bottom center of the screen.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param player The player whose inventory to display
     */
    public void drawHotbar(int screenWidth, int screenHeight, mattmc.world.entity.player.LocalPlayer player) {
        hotbarRenderer.render(screenWidth, screenHeight, player);
    }
    
    /**
     * Draw command overlay at bottom of screen (like Minecraft).
     * Shows command input box with cursor.
     */
    public void drawCommandOverlay(int screenWidth, int screenHeight, String commandText) {
        commandUIRenderer.renderCommandOverlay(screenWidth, screenHeight, commandText);
    }
    
    /**
     * Draw command feedback message above the hotbar area.
     * This message appears independently of the command input overlay and fades after a few seconds.
     * Similar to Minecraft's action bar messages.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param message The feedback message to display
     */
    public void drawCommandFeedback(int screenWidth, int screenHeight, String message) {
        commandUIRenderer.renderCommandFeedback(screenWidth, screenHeight, message);
    }
    
    /**
     * Get the selected hotbar slot (0-8 for slots 1-9).
     */
    public int getSelectedHotbarSlot() {
        return hotbarRenderer.getSelectedHotbarSlot();
    }
    
    /**
     * Set the selected hotbar slot (0-8 for slots 1-9).
     */
    public void setSelectedHotbarSlot(int slot) {
        hotbarRenderer.setSelectedHotbarSlot(slot);
    }
    
    /**
     * Draw the name of the block the player is looking at in the top-left corner.
     * Uses a blurred background effect.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param targetedBlock The block the player is looking at (can be null)
     */
    public void drawBlockNameDisplay(int screenWidth, int screenHeight, mattmc.world.level.block.Block targetedBlock) {
        // Lazy initialization of block name display
        if (blockNameDisplay == null) {
            blockNameDisplay = new BlockNameDisplay();
        }
        
        blockNameDisplay.render(screenWidth, screenHeight, targetedBlock);
    }
    
    /**
     * Draw lighting debug overlay showing relight scheduler statistics.
     * Displays backlog size, nodes processed, and time spent.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param backlogSize Number of pending light updates
     * @param nodesProcessed Number of nodes processed last frame
     * @param timeSpent Time spent in milliseconds last frame
     */
    public void drawLightingDebug(int screenWidth, int screenHeight, int backlogSize, 
                                  int nodesProcessed, double timeSpent) {
        lightingDebugRenderer.render(screenWidth, screenHeight, backlogSize, nodesProcessed, timeSpent);
    }
    
    /**
     * Draw system information on the right side of the screen.
     * Shows Java version, memory usage, CPU info, display resolution, GPU info.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param windowHandle GLFW window handle for display resolution
     */
    public void drawSystemInfo(int screenWidth, int screenHeight, long windowHandle) {
        systemInfoRenderer.render(screenWidth, screenHeight, windowHandle);
    }
}
