package net.minecraft.client.renderer.shaders;

/**
 * Interface for shader pipeline implementations.
 * 
 * <p>This interface defines the lifecycle hooks for shader rendering pipelines.
 * Implementations can provide custom shader pack loading, shadow rendering,
 * post-processing effects, and other advanced rendering features.</p>
 * 
 * <p>Two primary implementations exist:
 * <ul>
 *   <li>{@link VanillaShaderPipeline} - Preserves vanilla Minecraft shader behavior (no-ops)</li>
 *   <li>{@link IrisShaderPipeline} - Provides Iris shader pack rendering (implemented in later steps)</li>
 * </ul>
 * </p>
 * 
 * <p><b>Lifecycle Order:</b>
 * <pre>
 * initialize() - Called once during game startup
 * For each frame:
 *   beginFrame()
 *   beginShadowPass() (if shadows enabled)
 *     [render shadow pass]
 *   endShadowPass()
 *   beginMainPass()
 *     [render main scene]
 *   endMainPass()
 *   applyPostProcessing() (if post-processing enabled)
 *   endFrame()
 * cleanup() - Called during game shutdown
 * </pre>
 * </p>
 * 
 * @since Iris Integration Step 2
 */
public interface ShaderPipeline {
    
    /**
     * Initializes the shader pipeline.
     * Called once during game startup or when switching shader packs.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Load shader pack files (if applicable)</li>
     *   <li>Compile shader programs</li>
     *   <li>Allocate render targets and framebuffers</li>
     *   <li>Initialize uniform buffers</li>
     * </ul>
     * </p>
     */
    void initialize();
    
    /**
     * Called at the beginning of each frame.
     * Perform any per-frame setup needed for rendering.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Update time-based uniforms</li>
     *   <li>Prepare render targets</li>
     *   <li>Reset frame state</li>
     * </ul>
     * </p>
     */
    void beginFrame();
    
    /**
     * Called before rendering the shadow pass.
     * Only called if {@link ShaderRenderingConfig#isShadowsEnabled()} returns true.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Bind shadow framebuffers</li>
     *   <li>Set up shadow view/projection matrices</li>
     *   <li>Configure shadow rendering state</li>
     * </ul>
     * </p>
     */
    void beginShadowPass();
    
    /**
     * Called after rendering the shadow pass.
     * Only called if {@link ShaderRenderingConfig#isShadowsEnabled()} returns true.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Unbind shadow framebuffers</li>
     *   <li>Restore main view/projection matrices</li>
     *   <li>Clean up shadow rendering state</li>
     * </ul>
     * </p>
     */
    void endShadowPass();
    
    /**
     * Called before rendering the main scene.
     * This is the primary rendering pass where terrain, entities, and particles are rendered.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Bind main rendering framebuffers</li>
     *   <li>Activate shader programs</li>
     *   <li>Set up rendering state</li>
     * </ul>
     * </p>
     */
    void beginMainPass();
    
    /**
     * Called after rendering the main scene.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Unbind main rendering framebuffers</li>
     *   <li>Deactivate shader programs</li>
     *   <li>Restore rendering state</li>
     * </ul>
     * </p>
     */
    void endMainPass();
    
    /**
     * Called to apply post-processing effects.
     * Only called if {@link ShaderRenderingConfig#isPostProcessingEnabled()} returns true.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Run composite shader passes</li>
     *   <li>Apply blur, bloom, DOF, etc.</li>
     *   <li>Perform color grading</li>
     *   <li>Apply any final effects</li>
     * </ul>
     * </p>
     */
    void applyPostProcessing();
    
    /**
     * Called at the end of each frame.
     * Perform any cleanup needed after rendering is complete.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Flush pending operations</li>
     *   <li>Clean up temporary resources</li>
     *   <li>Update statistics</li>
     * </ul>
     * </p>
     */
    void endFrame();
    
    /**
     * Cleans up the shader pipeline.
     * Called during game shutdown or when switching shader packs.
     * 
     * <p>Implementations should:
     * <ul>
     *   <li>Delete shader programs</li>
     *   <li>Free render targets and framebuffers</li>
     *   <li>Release all allocated resources</li>
     * </ul>
     * </p>
     */
    void cleanup();
}
