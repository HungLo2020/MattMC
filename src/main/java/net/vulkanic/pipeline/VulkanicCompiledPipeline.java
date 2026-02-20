package net.vulkanic.pipeline;

/**
 * Vulkanic equivalent of Blaze3D's {@code CompiledRenderPipeline}.
 *
 * <p>Represents a backend-compiled pipeline object.  The result of
 * {@link net.vulkanic.VulkanicAPI#precompilePipeline(net.blaze3d.pipeline.RenderPipeline)}
 * is a {@code VulkanicCompiledPipeline}.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> wraps a linked GLSL program ({@code GlProgram}).
 *       The native handle is the GL program object name (int).</li>
 *   <li><b>Vulkan backend (future):</b> wraps a {@code VkPipeline} handle.
 *       Pipeline creation in Vulkan is expensive and must be done up-front
 *       (not at first draw), matching the semantics of
 *       {@link net.vulkanic.VulkanicAPI#precompilePipeline}.</li>
 * </ul>
 *
 * <p>A pipeline is immutable once compiled.  The {@link #isValid()} method
 * can be queried to detect compilation failures (shader compile error,
 * link error, Vulkan pipeline creation failure).
 *
 * <p>Pipelines are managed by the backend — do not attempt to close them
 * individually; they are released via
 * {@link net.vulkanic.VulkanicAPI#clearPipelineCache()}.
 */
public interface VulkanicCompiledPipeline {

    /**
     * Returns {@code true} if the pipeline was compiled successfully.
     *
     * <p>An invalid pipeline is returned (instead of throwing) when shader
     * compilation or linking fails so that the game can continue running
     * with fallback rendering.
     *
     * @return {@code true} if this pipeline can be used for rendering
     */
    boolean isValid();

    /**
     * Returns the backend-native handle for this compiled pipeline.
     * <ul>
     *   <li>OpenGL: GL program object name (as an {@code int} widened to {@code long})</li>
     *   <li>Vulkan: {@code VkPipeline} handle (opaque 64-bit handle)</li>
     * </ul>
     *
     * @return backend-native pipeline identifier
     */
    long getNativePipelineHandle();
}
