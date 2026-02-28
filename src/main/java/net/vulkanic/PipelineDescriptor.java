package net.vulkanic;

/**
 * Opaque descriptor used to create a {@link PipelineHandle} via
 * {@link GraphicsBackend#createPipeline(PipelineDescriptor)}.
 *
 * <p>The descriptor wraps backend-specific pipeline specification data.
 * For OpenGL, this wraps a {@code net.blaze3d.pipeline.RenderPipeline}.
 * For Vulkan, this will wrap SPIR-V bytecode and pipeline state.
 *
 * <p>Use the factory methods to create descriptors from existing objects:
 * <pre>
 * PipelineDescriptor desc = PipelineDescriptor.fromRenderPipeline(myPipeline);
 * PipelineHandle handle = VulkanicAPI.createPipeline(desc);
 * </pre>
 */
public final class PipelineDescriptor {

    private final Object nativeDescriptor;

    private PipelineDescriptor(Object nativeDescriptor) {
        this.nativeDescriptor = nativeDescriptor;
    }

    /**
     * Creates a PipelineDescriptor from an existing Blaze3D RenderPipeline.
     *
     * @param pipeline the Blaze3D pipeline to wrap
     * @return a descriptor for creating a compiled pipeline handle
     */
    public static PipelineDescriptor fromRenderPipeline(net.blaze3d.pipeline.RenderPipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        return new PipelineDescriptor(pipeline);
    }

    /**
     * Returns the underlying native descriptor object.
     * Backend implementations cast this to the appropriate type.
     */
    public Object getNativeDescriptor() {
        return nativeDescriptor;
    }
}
