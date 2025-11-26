package mattmc.client.renderer.backend;

/**
 * Constants for UI mesh IDs used in DrawCommand objects.
 * 
 * <p>UI elements use negative mesh IDs to distinguish them from regular 3D mesh IDs
 * (which are positive integers assigned by the backend). The render backend uses these
 * IDs to determine how to render each UI element type.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * // Creating a draw command for crosshair
 * DrawCommand cmd = new DrawCommand(UIMeshIds.CROSSHAIR, materialId, transformId, RenderPass.UI);
 * 
 * // In backend implementation
 * if (cmd.meshId == UIMeshIds.CROSSHAIR) {
 *     renderCrosshair(cmd);
 * }
 * </pre>
 * 
 * @see DrawCommand
 * @see RenderPass#UI
 */
public final class UIMeshIds {
    
    /** Crosshair rendering (center of screen) */
    public static final int CROSSHAIR = -1;
    
    /** Item rendering - fallback (magenta square for missing textures) */
    public static final int ITEM_FALLBACK = -2;
    
    /** Item rendering - cube (isometric 3D block items) */
    public static final int ITEM_CUBE = -3;
    
    /** Item rendering - stairs (isometric 3D stair items) */
    public static final int ITEM_STAIRS = -4;
    
    /** Item rendering - flat (2D items like tools, food) */
    public static final int ITEM_FLAT = -5;
    
    /** Hotbar background and selection highlight */
    public static final int HOTBAR = -6;
    
    /** Debug information text (F3 menu) */
    public static final int DEBUG_TEXT = -7;
    
    /** Command UI overlay and feedback messages */
    public static final int COMMAND_UI = -8;
    
    /** System information text (right side of F3 menu) */
    public static final int SYSTEM_INFO = -9;
    
    /** Tooltip rendering */
    public static final int TOOLTIP = -10;
    
    // Private constructor to prevent instantiation
    private UIMeshIds() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    /**
     * Check if a mesh ID represents an item.
     * 
     * @param meshId the mesh ID to check
     * @return true if the mesh ID is for item rendering
     */
    public static boolean isItemMeshId(int meshId) {
        return meshId <= ITEM_FALLBACK && meshId >= ITEM_FLAT;
    }
}
