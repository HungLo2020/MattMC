package net.minecraft.client.renderer.shaders.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vanilla rendering pipeline - represents normal Minecraft rendering without shaders.
 * 
 * Based on IRIS's VanillaRenderingPipeline (verbatim pattern match).
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/VanillaRenderingPipeline.java
 * 
 * This is essentially a pass-through pipeline that doesn't modify rendering.
 */
public class VanillaRenderingPipeline implements WorldRenderingPipeline {
	private static final Logger LOGGER = LoggerFactory.getLogger(VanillaRenderingPipeline.class);
	
	public VanillaRenderingPipeline() {
		// Constructor matches IRIS pattern - no initialization needed for vanilla
	}
	
	@Override
	public void beginLevelRendering() {
		// Use the default Minecraft framebuffer - no custom setup needed
		// Matches IRIS: stub implementation, vanilla doesn't need custom setup
	}
	
	@Override
	public void finalizeLevelRendering() {
		// Matches IRIS: stub implementation, vanilla doesn't need finalization
	}
	
	@Override
	public WorldRenderingPhase getPhase() {
		// Matches IRIS: VanillaRenderingPipeline always returns NONE
		return WorldRenderingPhase.NONE;
	}
	
	@Override
	public void setPhase(WorldRenderingPhase phase) {
		// Matches IRIS: stub implementation, vanilla doesn't track phases
	}
	
	@Override
	public boolean shouldDisableFrustumCulling() {
		// Matches IRIS: vanilla uses normal frustum culling
		return false;
	}
	
	@Override
	public boolean shouldDisableOcclusionCulling() {
		// Matches IRIS: vanilla uses normal occlusion culling
		return false;
	}
	
	@Override
	public boolean shouldRenderUnderwaterOverlay() {
		// Matches IRIS: vanilla renders underwater overlay
		return true;
	}
	
	@Override
	public boolean shouldRenderVignette() {
		// Matches IRIS: vanilla renders vignette
		return true;
	}
	
	@Override
	public boolean shouldRenderSun() {
		// Matches IRIS: vanilla renders sun
		return true;
	}
	
	@Override
	public boolean shouldRenderMoon() {
		// Matches IRIS: vanilla renders moon
		return true;
	}
	
	@Override
	public boolean shouldRenderWeather() {
		// Matches IRIS: vanilla renders weather
		return true;
	}
	
	@Override
	public void destroy() {
		// Matches IRIS: stub implementation, vanilla has nothing to destroy
		LOGGER.debug("Destroying vanilla pipeline");
	}
}
