package net.vulkanic;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating immutable pipeline state objects.
 * 
 * Use this to configure all pipeline state before creating a Pipeline.
 * Pipelines are immutable once created.
 * 
 * Example:
 * <pre>
 * PipelineStateDesc desc = new PipelineStateDesc()
 *     .setShader(ShaderStage.VERTEX, vertexShader)
 *     .setShader(ShaderStage.FRAGMENT, fragmentShader)
 *     .setBlendMode(BlendMode.ALPHA_BLEND)
 *     .setDepthTest(true, CompareOp.LESS)
 *     .setCullMode(CullMode.BACK);
 * Pipeline pipeline = VulkanicAPI.createPipeline(desc);
 * </pre>
 */
public class PipelineStateDesc {
    
    // Shader stages
    private final Map<ShaderStage, Long> shaders = new HashMap<>();
    
    // Rasterization state
    private CullMode cullMode = CullMode.BACK;
    private boolean frontFaceCounterClockwise = true;
    
    // Blend state
    private BlendMode blendMode = BlendMode.NONE;
    
    // Depth/stencil state
    private boolean depthTestEnabled = true;
    private boolean depthWriteEnabled = true;
    private CompareOp depthCompareOp = CompareOp.LESS;
    
    // Debug
    private String debugName;
    
    /**
     * Sets a shader for a specific stage.
     * 
     * @param stage Shader stage
     * @param shaderHandle Backend-specific shader handle
     * @return this for method chaining
     */
    public PipelineStateDesc setShader(ShaderStage stage, long shaderHandle) {
        this.shaders.put(stage, shaderHandle);
        return this;
    }
    
    /**
     * Sets the blend mode for color blending.
     * 
     * @param mode Blend mode (NONE, ALPHA_BLEND, ADDITIVE, etc.)
     * @return this for method chaining
     */
    public PipelineStateDesc setBlendMode(BlendMode mode) {
        this.blendMode = mode;
        return this;
    }
    
    /**
     * Configures depth testing.
     * 
     * @param enabled Whether depth testing is enabled
     * @param compareOp Comparison operation for depth test
     * @return this for method chaining
     */
    public PipelineStateDesc setDepthTest(boolean enabled, CompareOp compareOp) {
        this.depthTestEnabled = enabled;
        this.depthCompareOp = compareOp;
        return this;
    }
    
    /**
     * Sets whether depth writes are enabled.
     * 
     * @param enabled Whether to write to depth buffer
     * @return this for method chaining
     */
    public PipelineStateDesc setDepthWrite(boolean enabled) {
        this.depthWriteEnabled = enabled;
        return this;
    }
    
    /**
     * Sets the face culling mode.
     * 
     * @param mode Cull mode (NONE, FRONT, BACK)
     * @return this for method chaining
     */
    public PipelineStateDesc setCullMode(CullMode mode) {
        this.cullMode = mode;
        return this;
    }
    
    /**
     * Sets the front face winding order.
     * 
     * @param counterClockwise true for counter-clockwise, false for clockwise
     * @return this for method chaining
     */
    public PipelineStateDesc setFrontFace(boolean counterClockwise) {
        this.frontFaceCounterClockwise = counterClockwise;
        return this;
    }
    
    /**
     * Sets a debug name for this pipeline (for debugging/profiling).
     * 
     * @param name Debug name
     * @return this for method chaining
     */
    public PipelineStateDesc setDebugName(String name) {
        this.debugName = name;
        return this;
    }
    
    // Getters
    public Map<ShaderStage, Long> getShaders() { return shaders; }
    public CullMode getCullMode() { return cullMode; }
    public boolean isFrontFaceCounterClockwise() { return frontFaceCounterClockwise; }
    public BlendMode getBlendMode() { return blendMode; }
    public boolean isDepthTestEnabled() { return depthTestEnabled; }
    public boolean isDepthWriteEnabled() { return depthWriteEnabled; }
    public CompareOp getDepthCompareOp() { return depthCompareOp; }
    public String getDebugName() { return debugName; }
}
