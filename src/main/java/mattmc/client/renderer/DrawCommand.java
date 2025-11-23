package mattmc.client.renderer;

/**
 * Represents a single API-agnostic draw command for rendering an object.
 * 
 * <p>This is a Plain Old Data (POD) structure that contains all the information
 * needed to render a single object without directly referencing any graphics API
 * (OpenGL, Vulkan, etc.). The actual rendering backend interprets these IDs and
 * translates them into appropriate API calls.
 * 
 * <p><b>Design Principle:</b> This class intentionally does not contain any OpenGL
 * handles (VAO IDs, VBO IDs, texture IDs) or Vulkan handles (buffers, pipelines, etc.).
 * Instead, it uses abstract integer IDs that the backend can map to its own internal
 * resource management system.
 * 
 * <p><b>Usage:</b> The rendering front-end (game logic, chunk renderer logic, etc.)
 * creates these commands each frame to describe <em>what</em> to draw. The rendering
 * back-end then processes these commands to determine <em>how</em> to draw using the
 * actual graphics API.
 * 
 * <p><b>Design Note:</b> This abstraction is intended to support multiple graphics
 * backends in the future (currently OpenGL, potentially Vulkan later). By keeping
 * this class API-agnostic, we enable:
 * <ul>
 *   <li>Headless testing without requiring an OpenGL context</li>
 *   <li>Easier debugging through command inspection and logging</li>
 *   <li>Future Vulkan backend implementation without changing game logic</li>
 * </ul>
 * 
 * <p><b>TODO:</b> When implementing Vulkan backend (not yet - future work):
 * <ul>
 *   <li>meshId would map to Vulkan vertex/index buffers</li>
 *   <li>materialId would map to Vulkan pipelines and descriptor sets</li>
 *   <li>transformIndex would map to transform uniform buffer offsets</li>
 * </ul>
 * 
 * @since Stage 1 of rendering refactor
 * @see RenderPass
 * @see RenderBackend
 */
public final class DrawCommand {
    /**
     * Abstract ID referencing a mesh/geometry resource.
     * 
     * <p>The backend maintains a registry mapping these IDs to actual mesh data:
     * <ul>
     *   <li>In OpenGL: maps to VAO/VBO handles and vertex counts</li>
     *   <li>In Vulkan (future): would map to vertex/index buffer handles</li>
     *   <li>In debug/headless mode: maps to mesh metadata for inspection</li>
     * </ul>
     */
    public final int meshId;
    
    /**
     * Abstract ID referencing a material/shader resource.
     * 
     * <p>The backend maintains a registry mapping these IDs to actual material data:
     * <ul>
     *   <li>In OpenGL: maps to shader program IDs, texture bindings, uniforms</li>
     *   <li>In Vulkan (future): would map to pipeline and descriptor set handles</li>
     *   <li>In debug/headless mode: maps to material metadata for inspection</li>
     * </ul>
     */
    public final int materialId;
    
    /**
     * Index into a transform buffer or array.
     * 
     * <p>The backend uses this index to look up the model/view transformation matrix
     * for this draw call:
     * <ul>
     *   <li>In OpenGL: index into a transform array or UBO offset</li>
     *   <li>In Vulkan (future): would be an offset into a transform uniform buffer</li>
     *   <li>In debug/headless mode: index into a transform metadata array</li>
     * </ul>
     */
    public final int transformIndex;
    
    /**
     * The render pass this command belongs to.
     * 
     * <p>Determines when and how this object should be rendered (opaque, transparent,
     * shadow, UI, etc.). The backend may group and sort commands by pass for efficiency.
     * 
     * @see RenderPass
     */
    public final RenderPass pass;
    
    /**
     * Creates a new draw command with the specified parameters.
     * 
     * @param meshId abstract mesh resource ID
     * @param materialId abstract material resource ID
     * @param transformIndex index into transform buffer
     * @param pass the render pass for this command
     */
    public DrawCommand(int meshId, int materialId, int transformIndex, RenderPass pass) {
        this.meshId = meshId;
        this.materialId = materialId;
        this.transformIndex = transformIndex;
        this.pass = pass;
    }
    
    /**
     * Returns a string representation of this draw command for debugging.
     * Useful for logging and inspecting rendering behavior in headless tests.
     */
    @Override
    public String toString() {
        return String.format("DrawCommand{meshId=%d, materialId=%d, transformIndex=%d, pass=%s}",
                           meshId, materialId, transformIndex, pass);
    }
    
    /**
     * Equality comparison based on all fields.
     * Useful for testing and verification.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DrawCommand that = (DrawCommand) obj;
        return meshId == that.meshId &&
               materialId == that.materialId &&
               transformIndex == that.transformIndex &&
               pass == that.pass;
    }
    
    /**
     * Hash code based on all fields.
     * Useful for using DrawCommand in hash-based collections during testing.
     */
    @Override
    public int hashCode() {
        int result = meshId;
        result = 31 * result + materialId;
        result = 31 * result + transformIndex;
        result = 31 * result + (pass != null ? pass.hashCode() : 0);
        return result;
    }
}
