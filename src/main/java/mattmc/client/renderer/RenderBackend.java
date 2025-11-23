package mattmc.client.renderer;

/**
 * Interface for graphics API-specific rendering backends.
 * 
 * <p>This interface abstracts away the details of specific graphics APIs (OpenGL, Vulkan, etc.)
 * and provides a common interface for submitting rendering work. The rendering front-end
 * (game logic) works entirely with {@link DrawCommand} objects and this interface, without
 * needing to know about the underlying graphics API.
 * 
 * <h2>Architecture</h2>
 * <p>The rendering system is split into three layers:
 * <ol>
 *   <li><b>Game/World Layer:</b> Game logic that knows about blocks, chunks, entities, etc.
 *       but has no knowledge of graphics APIs.</li>
 *   <li><b>Rendering Front-End:</b> Decides what to draw and creates {@link DrawCommand}
 *       objects. Still API-agnostic.</li>
 *   <li><b>Rendering Back-End (this interface):</b> Translates abstract draw commands into
 *       actual graphics API calls. API-specific implementations live here.</li>
 * </ol>
 * 
 * <h2>Typical Usage Pattern</h2>
 * <pre>
 * // Start of frame
 * backend.beginFrame();
 * 
 * // Game logic builds draw commands
 * List&lt;DrawCommand&gt; commands = buildDrawCommands();
 * 
 * // Submit commands to backend
 * for (DrawCommand cmd : commands) {
 *     backend.submit(cmd);
 * }
 * 
 * // End of frame
 * backend.endFrame();
 * </pre>
 * 
 * <h2>Implementations</h2>
 * <p>Current and future implementations:
 * <ul>
 *   <li><b>OpenGLRenderBackend</b> (to be implemented in Stage 2): The current production
 *       backend that translates commands to OpenGL calls.</li>
 *   <li><b>DebugRenderBackend</b> (to be implemented in Stage 6): A headless backend that
 *       records commands for inspection without requiring a graphics context. Useful for
 *       testing and debugging.</li>
 *   <li><b>VulkanRenderBackend</b> (future, not to be implemented yet): A future backend
 *       for Vulkan support. The design should make this feasible but it's not part of
 *       the current refactor.</li>
 * </ul>
 * 
 * <p><b>Design Constraints:</b> To maintain compatibility with future backends:
 * <ul>
 *   <li>This interface must not expose or accept any OpenGL-specific types (GLuint,
 *       GLint, etc.)</li>
 *   <li>All resources must be referenced through abstract IDs, not API handles</li>
 *   <li>Frame structure must be simple enough to map to different API models</li>
 * </ul>
 * 
 * <p><b>Design Note:</b> This abstraction is designed to support future Vulkan implementation.
 * However, <em>Vulkan must not be implemented yet</em>. The focus is on OpenGL plus testability
 * first. The design ensures Vulkan will be feasible when we're ready for it.
 * 
 * <p><b>TODO for Vulkan backend (future work, do not implement yet):</b>
 * <ul>
 *   <li>beginFrame() would acquire swapchain image and begin command buffer recording</li>
 *   <li>submit() would record draw commands into the command buffer</li>
 *   <li>endFrame() would end recording, submit to queue, and present</li>
 *   <li>Resource IDs would map to Vulkan buffer/pipeline/descriptor handles</li>
 * </ul>
 * 
 * @since Stage 1 of rendering refactor
 * @see DrawCommand
 * @see RenderPass
 */
public interface RenderBackend {
    /**
     * Called at the beginning of a frame, before any draw commands are submitted.
     * 
     * <p>This method allows the backend to perform any necessary setup for the frame:
     * <ul>
     *   <li><b>OpenGL:</b> Clear buffers, reset state, prepare for rendering</li>
     *   <li><b>Vulkan (future):</b> Acquire next swapchain image, begin command buffer</li>
     *   <li><b>Debug:</b> Clear recorded command list</li>
     * </ul>
     * 
     * <p>After this call, the backend should be ready to accept {@link #submit(DrawCommand)}
     * calls.
     * 
     * @see #endFrame()
     */
    void beginFrame();
    
    /**
     * Submits a single draw command to the backend for rendering.
     * 
     * <p>The backend is responsible for:
     * <ul>
     *   <li>Translating the abstract {@link DrawCommand} into API-specific calls</li>
     *   <li>Looking up mesh, material, and transform resources from the provided IDs</li>
     *   <li>Configuring API state appropriately (shaders, textures, blending, etc.)</li>
     *   <li>Issuing the actual draw call</li>
     * </ul>
     * 
     * <p><b>Implementation Note:</b> Backends may choose to batch or reorder commands
     * for efficiency (e.g., grouping by material to reduce state changes), but should
     * respect render pass ordering to maintain correct rendering results.
     * 
     * <p><b>Thread Safety:</b> This method is expected to be called from the rendering
     * thread only. Implementations do not need to be thread-safe.
     * 
     * @param cmd the draw command to submit, must not be null
     * @throws NullPointerException if cmd is null
     * @see DrawCommand
     */
    void submit(DrawCommand cmd);
    
    /**
     * Called at the end of a frame, after all draw commands have been submitted.
     * 
     * <p>This method allows the backend to perform any necessary finalization:
     * <ul>
     *   <li><b>OpenGL:</b> Flush commands, swap buffers (if managed by backend), cleanup</li>
     *   <li><b>Vulkan (future):</b> End command buffer, submit to queue, present frame</li>
     *   <li><b>Debug:</b> Finalize command recording for inspection</li>
     * </ul>
     * 
     * <p>After this call, no more {@link #submit(DrawCommand)} calls should be made until
     * the next {@link #beginFrame()}.
     * 
     * @see #beginFrame()
     */
    void endFrame();
}
