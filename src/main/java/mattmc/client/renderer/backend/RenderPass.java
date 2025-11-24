package mattmc.client.renderer.backend;

/**
 * Represents the different rendering passes used in the rendering pipeline.
 * 
 * <p>This enum defines the logical order and grouping of rendered objects.
 * Each pass corresponds to a specific type of rendering that may require
 * different sorting, blending, or depth testing configurations.
 * 
 * <p><b>Design Note:</b> This abstraction is intentionally API-agnostic to support
 * multiple graphics backends in the future. The current implementation uses OpenGL,
 * but this design will allow for a future Vulkan backend without requiring changes
 * to higher-level game logic.
 * 
 * <p><b>TODO:</b> When implementing Vulkan backend (not yet - future work), these
 * passes can map to Vulkan subpasses or separate render passes as appropriate.
 * 
 * @since Stage 1 of rendering refactor
 * @see DrawCommand
 * @see RenderBackend
 */
public enum RenderPass {
    /**
     * Opaque geometry pass - renders solid objects that don't require blending.
     * This should typically be rendered first with depth testing and writing enabled.
     * Examples: solid blocks, terrain, most world geometry.
     */
    OPAQUE,
    
    /**
     * Transparent geometry pass - renders objects requiring alpha blending.
     * This should typically be rendered after opaque geometry, often with back-to-front
     * sorting to ensure proper transparency blending.
     * Examples: glass blocks, water, transparent textures.
     */
    TRANSPARENT,
    
    /**
     * Shadow pass - renders geometry for shadow map generation.
     * This pass is currently optional and may not be implemented in early stages.
     * When implemented, it would render depth information from light's perspective.
     */
    SHADOW,
    
    /**
     * UI pass - renders user interface elements.
     * This should typically be rendered last, on top of all 3D geometry, often with
     * different projection/view matrices and depth testing disabled.
     * Examples: inventory screens, HUD elements, text overlays.
     */
    UI
}
