package net.vulkanic.backends.opengl;

import net.vulkanic.*;

import java.util.Map;

/**
 * OpenGL implementation of Pipeline interface.
 * 
 * In OpenGL, this tracks pipeline state that will be applied when drawing.
 * Unlike Vulkan's VkPipeline, this doesn't create a compiled pipeline object.
 */
public class GLPipeline implements Pipeline {
    
    private final long handle;
    private final PipelineStateDesc desc;
    private final String debugName;
    
    public GLPipeline(long handle, PipelineStateDesc desc) {
        this.handle = handle;
        this.desc = desc;
        this.debugName = desc.getDebugName();
    }
    
    @Override
    public long getHandle() {
        return handle;
    }
    
    @Override
    public String getDebugName() {
        return debugName;
    }
    
    // OpenGL-specific accessor
    public PipelineStateDesc getDesc() {
        return desc;
    }
    
    public Map<ShaderStage, Long> getShaders() {
        return desc.getShaders();
    }
    
    public BlendMode getBlendMode() {
        return desc.getBlendMode();
    }
    
    public boolean isDepthTestEnabled() {
        return desc.isDepthTestEnabled();
    }
    
    public boolean isDepthWriteEnabled() {
        return desc.isDepthWriteEnabled();
    }
    
    public CompareOp getDepthCompareOp() {
        return desc.getDepthCompareOp();
    }
    
    public CullMode getCullMode() {
        return desc.getCullMode();
    }
    
    public boolean isFrontFaceCounterClockwise() {
        return desc.isFrontFaceCounterClockwise();
    }
}
