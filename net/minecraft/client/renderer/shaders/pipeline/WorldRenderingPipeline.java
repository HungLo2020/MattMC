package net.minecraft.client.renderer.shaders.pipeline;

/**
 * Interface for world rendering pipelines.
 * Represents a complete shader pipeline that can render a world.
 * 
 * Based on IRIS's WorldRenderingPipeline interface (verbatim pattern match).
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/WorldRenderingPipeline.java
 * 
 * This is a critical interface - following IRIS exactly is paramount for future compatibility.
 */
public interface WorldRenderingPipeline {
	/**
	 * Called at the beginning of level rendering.
	 * Sets up framebuffers, clears buffers, and prepares for rendering.
	 */
	void beginLevelRendering();
	
	/**
	 * Called at the end of level rendering.
	 * Finalizes rendering, applies post-processing, and cleans up state.
	 */
	void finalizeLevelRendering();
	
	/**
	 * Gets the current rendering phase.
	 * @return The current WorldRenderingPhase
	 */
	WorldRenderingPhase getPhase();
	
	/**
	 * Sets the current rendering phase.
	 * This is called by the rendering system as it progresses through different rendering stages.
	 * 
	 * @param phase The phase to set
	 */
	void setPhase(WorldRenderingPhase phase);
	
	/**
	 * Determines whether frustum culling should be disabled.
	 * Shader packs can request this for shadow rendering or other effects.
	 * 
	 * @return true if frustum culling should be disabled
	 */
	boolean shouldDisableFrustumCulling();
	
	/**
	 * Determines whether occlusion culling should be disabled.
	 * Shader packs can request this for shadow rendering or other effects.
	 * 
	 * @return true if occlusion culling should be disabled
	 */
	boolean shouldDisableOcclusionCulling();
	
	/**
	 * Determines whether the underwater overlay should be rendered.
	 * Shader packs can disable this if they handle underwater effects themselves.
	 * 
	 * @return true if underwater overlay should be rendered
	 */
	boolean shouldRenderUnderwaterOverlay();
	
	/**
	 * Determines whether the vignette effect should be rendered.
	 * Shader packs can disable this if they handle vignetting themselves.
	 * 
	 * @return true if vignette should be rendered
	 */
	boolean shouldRenderVignette();
	
	/**
	 * Determines whether the sun should be rendered.
	 * Shader packs can disable this if they render the sun themselves.
	 * 
	 * @return true if sun should be rendered
	 */
	boolean shouldRenderSun();
	
	/**
	 * Determines whether the moon should be rendered.
	 * Shader packs can disable this if they render the moon themselves.
	 * 
	 * @return true if moon should be rendered
	 */
	boolean shouldRenderMoon();
	
	/**
	 * Determines whether weather (rain/snow) should be rendered.
	 * Shader packs can disable this if they handle weather effects themselves.
	 * 
	 * @return true if weather should be rendered
	 */
	boolean shouldRenderWeather();
	
	/**
	 * Destroys the pipeline and releases all resources.
	 * Must be called when the pipeline is no longer needed.
	 */
	void destroy();
}
