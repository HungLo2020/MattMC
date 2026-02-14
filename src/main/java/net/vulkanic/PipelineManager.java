package net.vulkanic;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pipeline Manager - Bridges stateful rendering API to pipeline-based API.
 * 
 * This class tracks rendering state (blend mode, depth test, cull mode, shaders)
 * and automatically creates/caches Pipeline objects for each unique state combination.
 * 
 * Purpose: Enable gradual migration from stateful OpenGL-style API to Vulkan-compatible
 * Pipeline State Objects without breaking existing code.
 * 
 * Usage:
 * <pre>
 * PipelineManager manager = new PipelineManager();
 * manager.setBlendMode(BlendMode.ALPHA_BLEND);
 * manager.setDepthTest(true, CompareOp.LESS);
 * Pipeline pipeline = manager.getCurrentPipeline();
 * VulkanicAPI.bindPipeline(cmd, pipeline);
 * </pre>
 */
public class PipelineManager {
    
    // Current state
    private BlendMode blendMode = BlendMode.NONE;
    private int blendSrcFactor = GL11.GL_ONE;
    private int blendDstFactor = GL11.GL_ZERO;
    private int blendSrcFactorAlpha = GL11.GL_ONE;
    private int blendDstFactorAlpha = GL11.GL_ZERO;
    private int blendEquation = GL14.GL_FUNC_ADD;
    private boolean depthTestEnabled = true;
    private CompareOp depthCompareOp = CompareOp.LESS;
    private boolean depthWriteEnabled = true;
    private CullMode cullMode = CullMode.NONE;
    private int frontFaceMode = GL11.GL_CCW;
    private boolean colorMaskR = true;
    private boolean colorMaskG = true;
    private boolean colorMaskB = true;
    private boolean colorMaskA = true;
    private float lineWidth = 1.0f;
    private boolean scissorTestEnabled = false;
    private int polygonMode = GL11.GL_FILL;
    private int logicOp = GL11.GL_COPY;
    private double clearDepth = 1.0;
    private float clearColorR = 0.0f;
    private float clearColorG = 0.0f;
    private float clearColorB = 0.0f;
    private float clearColorA = 0.0f;
    private long vertexShader = 0;
    private long fragmentShader = 0;
    private float polygonOffsetFactor = 0.0f;
    private float polygonOffsetUnits = 0.0f;
    private boolean polygonOffsetEnabled = false;
    private float pointSize = 1.0f;
    private boolean stencilTestEnabled = false;
    private int stencilFunc = GL11.GL_ALWAYS;
    private int stencilRef = 0;
    private int stencilMask = 0xFFFFFFFF;
    private int stencilOpFail = GL11.GL_KEEP;
    private int stencilOpZFail = GL11.GL_KEEP;
    private int stencilOpZPass = GL11.GL_KEEP;
    
    // Pipeline cache with LRU eviction
    private final Map<StateKey, Pipeline> pipelineCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<StateKey, Pipeline> eldest) {
            boolean shouldRemove = size() > MAX_CACHE_SIZE;
            if (shouldRemove && eldest.getValue() != null) {
                // Clean up old pipeline
                VulkanicAPI.destroyPipeline(eldest.getValue());
            }
            return shouldRemove;
        }
    };
    
    private static final int MAX_CACHE_SIZE = 256;
    
    /**
     * Set blend mode
     */
    public void setBlendMode(BlendMode mode) {
        this.blendMode = mode != null ? mode : BlendMode.NONE;
    }
    
    /**
     * Set blend function factors
     */
    public void setBlendFunc(int srcFactor, int dstFactor) {
        this.blendSrcFactor = srcFactor;
        this.blendDstFactor = dstFactor;
        this.blendSrcFactorAlpha = srcFactor;
        this.blendDstFactorAlpha = dstFactor;
    }
    
    /**
     * Set blend function factors separately for RGB and Alpha
     */
    public void setBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        this.blendSrcFactor = srcRGB;
        this.blendDstFactor = dstRGB;
        this.blendSrcFactorAlpha = srcAlpha;
        this.blendDstFactorAlpha = dstAlpha;
    }
    
    /**
     * Set blend equation
     */
    public void setBlendEquation(int mode) {
        this.blendEquation = mode;
    }
    
    /**
     * Set depth test configuration
     */
    public void setDepthTest(boolean enabled, CompareOp compareOp) {
        this.depthTestEnabled = enabled;
        this.depthCompareOp = compareOp != null ? compareOp : CompareOp.LESS;
    }
    
    /**
     * Set depth write enabled
     */
    public void setDepthWrite(boolean enabled) {
        this.depthWriteEnabled = enabled;
    }
    
    /**
     * Set cull mode
     */
    public void setCullMode(CullMode mode) {
        this.cullMode = mode != null ? mode : CullMode.NONE;
    }
    
    /**
     * Set front face winding order
     */
    public void setFrontFace(int mode) {
        this.frontFaceMode = mode;
    }
    
    /**
     * Set color write mask
     */
    public void setColorMask(boolean r, boolean g, boolean b, boolean a) {
        this.colorMaskR = r;
        this.colorMaskG = g;
        this.colorMaskB = b;
        this.colorMaskA = a;
    }
    
    /**
     * Set line width
     */
    public void setLineWidth(float width) {
        this.lineWidth = width;
    }
    
    /**
     * Set scissor test enabled
     */
    public void setScissorTest(boolean enabled) {
        this.scissorTestEnabled = enabled;
    }
    
    /**
     * Set polygon mode
     */
    public void setPolygonMode(int mode) {
        this.polygonMode = mode;
    }
    
    /**
     * Set logic operation
     */
    public void setLogicOp(int opcode) {
        this.logicOp = opcode;
    }
    
    /**
     * Set clear depth value
     */
    public void setClearDepth(double depth) {
        this.clearDepth = depth;
    }
    
    /**
     * Set clear color value
     */
    public void setClearColor(float r, float g, float b, float a) {
        this.clearColorR = r;
        this.clearColorG = g;
        this.clearColorB = b;
        this.clearColorA = a;
    }
    
    /**
     * Set polygon offset
     */
    public void setPolygonOffset(float factor, float units) {
        this.polygonOffsetFactor = factor;
        this.polygonOffsetUnits = units;
    }
    
    /**
     * Set polygon offset enabled
     */
    public void setPolygonOffsetEnabled(boolean enabled) {
        this.polygonOffsetEnabled = enabled;
    }
    
    /**
     * Set point size
     */
    public void setPointSize(float size) {
        this.pointSize = size;
    }
    
    /**
     * Set stencil test enabled
     */
    public void setStencilTestEnabled(boolean enabled) {
        this.stencilTestEnabled = enabled;
    }
    
    /**
     * Set stencil function
     */
    public void setStencilFunc(int func, int ref, int mask) {
        this.stencilFunc = func;
        this.stencilRef = ref;
        this.stencilMask = mask;
    }
    
    /**
     * Set stencil operations
     */
    public void setStencilOp(int fail, int zfail, int zpass) {
        this.stencilOpFail = fail;
        this.stencilOpZFail = zfail;
        this.stencilOpZPass = zpass;
    }
    
    /**
     * Set shader for a stage
     */
    public void setShader(ShaderStage stage, long shaderHandle) {
        if (stage == ShaderStage.VERTEX) {
            this.vertexShader = shaderHandle;
        } else if (stage == ShaderStage.FRAGMENT) {
            this.fragmentShader = shaderHandle;
        }
    }
    
    /**
     * Get or create pipeline for current state.
     * Pipelines are cached and reused for identical state.
     */
    public Pipeline getCurrentPipeline() {
        StateKey key = new StateKey(
            blendMode,
            depthTestEnabled,
            depthCompareOp,
            depthWriteEnabled,
            cullMode,
            vertexShader,
            fragmentShader
        );
        
        return pipelineCache.computeIfAbsent(key, k -> createPipeline(k));
    }
    
    /**
     * Create a new pipeline from state key
     */
    private Pipeline createPipeline(StateKey key) {
        PipelineStateDesc desc = new PipelineStateDesc()
            .setBlendMode(key.blendMode)
            .setDepthTest(key.depthTestEnabled, key.depthCompareOp)
            .setDepthWrite(key.depthWriteEnabled)
            .setCullMode(key.cullMode);
        
        if (key.vertexShader != 0) {
            desc.setShader(ShaderStage.VERTEX, key.vertexShader);
        }
        if (key.fragmentShader != 0) {
            desc.setShader(ShaderStage.FRAGMENT, key.fragmentShader);
        }
        
        return VulkanicAPI.createPipeline(desc);
    }
    
    /**
     * Clear pipeline cache and destroy all cached pipelines
     */
    public void clearCache() {
        for (Pipeline pipeline : pipelineCache.values()) {
            if (pipeline != null) {
                VulkanicAPI.destroyPipeline(pipeline);
            }
        }
        pipelineCache.clear();
    }
    
    /**
     * Get cache statistics
     */
    public int getCacheSize() {
        return pipelineCache.size();
    }
    
    /**
     * State key for pipeline caching
     */
    private static class StateKey {
        final BlendMode blendMode;
        final boolean depthTestEnabled;
        final CompareOp depthCompareOp;
        final boolean depthWriteEnabled;
        final CullMode cullMode;
        final long vertexShader;
        final long fragmentShader;
        
        StateKey(BlendMode blendMode, boolean depthTestEnabled, CompareOp depthCompareOp,
                 boolean depthWriteEnabled, CullMode cullMode, long vertexShader, long fragmentShader) {
            this.blendMode = blendMode;
            this.depthTestEnabled = depthTestEnabled;
            this.depthCompareOp = depthCompareOp;
            this.depthWriteEnabled = depthWriteEnabled;
            this.cullMode = cullMode;
            this.vertexShader = vertexShader;
            this.fragmentShader = fragmentShader;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StateKey stateKey = (StateKey) o;
            return depthTestEnabled == stateKey.depthTestEnabled &&
                   depthWriteEnabled == stateKey.depthWriteEnabled &&
                   vertexShader == stateKey.vertexShader &&
                   fragmentShader == stateKey.fragmentShader &&
                   blendMode == stateKey.blendMode &&
                   depthCompareOp == stateKey.depthCompareOp &&
                   cullMode == stateKey.cullMode;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(blendMode, depthTestEnabled, depthCompareOp,
                               depthWriteEnabled, cullMode, vertexShader, fragmentShader);
        }
    }
}
