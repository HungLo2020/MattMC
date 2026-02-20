package net.vulkanic.pipeline;

/**
 * Opaque handle to a compiled graphics pipeline.
 *
 * <p>Obtained from {@link net.vulkanic.VulkanicAPI#createPipeline(PipelineDescriptor)}.
 * Pass to {@link net.vulkanic.VulkanicAPI#setPipeline} inside a render pass to bind it.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> stores a linked {@code GlProgram} ID and cached state</li>
 *   <li><b>Vulkan backend:</b> will store a {@code VkPipeline} handle</li>
 * </ul>
 *
 * Pipelines are immutable once created.  Free via
 * {@link net.vulkanic.VulkanicAPI#deletePipeline(PipelineHandle)}.
 */
public interface PipelineHandle {

    /**
     * Returns the backend-native handle for this pipeline.
     * <ul>
     *   <li>OpenGL: the linked GL program object name (int)</li>
     *   <li>Vulkan: the {@code VkPipeline} handle (long)</li>
     * </ul>
     */
    long getNativeHandle();

    /** Returns {@code true} if this pipeline is valid (not deleted). */
    boolean isValid();

    /** Human-readable label for debugging. */
    String getDebugLabel();
}
