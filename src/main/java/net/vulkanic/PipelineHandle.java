package net.vulkanic;

/**
 * Opaque handle to a compiled render pipeline.
 *
 * <p>In OpenGL, a pipeline compiles and links vertex + fragment shaders into a GL program.
 * In Vulkan, a pipeline includes SPIR-V shaders, fixed-function state, and a VkPipeline object.
 *
 * <p>Pipeline handles are created via {@link VulkanicAPI#createPipeline(PipelineDescriptor)}.
 * They should be closed when no longer needed to free GPU resources.
 */
public interface PipelineHandle extends AutoCloseable {

    /**
     * Returns true if this pipeline compiled and linked successfully.
     * A pipeline may be invalid if shader compilation failed.
     */
    boolean isValid();

    /** Frees GPU resources held by this pipeline. */
    @Override
    void close();
}
