package net.vulkanic.pipeline;

/**
 * Descriptor used to create a compiled graphics pipeline.
 *
 * <p>A {@code PipelineDescriptor} is a plain-data object that captures everything
 * needed to compile and cache a pipeline:
 * <ul>
 *   <li>Vertex and fragment shader source (GLSL for OpenGL; SPIR-V for Vulkan)</li>
 *   <li>Blend function, depth test, cull mode, polygon offset, etc.</li>
 *   <li>Vertex format (attribute layout)</li>
 * </ul>
 *
 * <p>Pass this to {@link net.vulkanic.VulkanicAPI#createPipeline(PipelineDescriptor)} to
 * obtain an opaque {@link PipelineHandle} that can be bound at draw time.
 */
public class PipelineDescriptor {

    private final String vertexShaderSource;
    private final String fragmentShaderSource;
    private final String debugLabel;

    // Rasterisation state
    private final int cullMode;      // 0 = none, GL_FRONT, GL_BACK, GL_FRONT_AND_BACK
    private final boolean depthTestEnabled;
    private final int depthTestFunction; // GL_LESS, GL_LEQUAL, etc.
    private final boolean depthWriteEnabled;
    private final boolean blendEnabled;

    private PipelineDescriptor(Builder builder) {
        this.vertexShaderSource  = builder.vertexShaderSource;
        this.fragmentShaderSource = builder.fragmentShaderSource;
        this.debugLabel          = builder.debugLabel;
        this.cullMode            = builder.cullMode;
        this.depthTestEnabled    = builder.depthTestEnabled;
        this.depthTestFunction   = builder.depthTestFunction;
        this.depthWriteEnabled   = builder.depthWriteEnabled;
        this.blendEnabled        = builder.blendEnabled;
    }

    public String getVertexShaderSource()   { return vertexShaderSource; }
    public String getFragmentShaderSource() { return fragmentShaderSource; }
    public String getDebugLabel()           { return debugLabel; }
    public int    getCullMode()             { return cullMode; }
    public boolean isDepthTestEnabled()     { return depthTestEnabled; }
    public int    getDepthTestFunction()    { return depthTestFunction; }
    public boolean isDepthWriteEnabled()    { return depthWriteEnabled; }
    public boolean isBlendEnabled()         { return blendEnabled; }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static Builder builder(String vertexShaderSource, String fragmentShaderSource) {
        return new Builder(vertexShaderSource, fragmentShaderSource);
    }

    public static final class Builder {
        private final String vertexShaderSource;
        private final String fragmentShaderSource;
        private String debugLabel        = "pipeline";
        private int    cullMode          = 0;
        private boolean depthTestEnabled = false;
        private int    depthTestFunction = 0x0203; // GL_LESS
        private boolean depthWriteEnabled = true;
        private boolean blendEnabled      = false;

        private Builder(String vertexShaderSource, String fragmentShaderSource) {
            this.vertexShaderSource  = vertexShaderSource;
            this.fragmentShaderSource = fragmentShaderSource;
        }

        public Builder debugLabel(String label)            { this.debugLabel = label; return this; }
        public Builder cullMode(int mode)                  { this.cullMode = mode; return this; }
        public Builder depthTestEnabled(boolean enabled)   { this.depthTestEnabled = enabled; return this; }
        public Builder depthTestFunction(int func)         { this.depthTestFunction = func; return this; }
        public Builder depthWriteEnabled(boolean enabled)  { this.depthWriteEnabled = enabled; return this; }
        public Builder blendEnabled(boolean enabled)       { this.blendEnabled = enabled; return this; }

        public PipelineDescriptor build() {
            return new PipelineDescriptor(this);
        }
    }
}
