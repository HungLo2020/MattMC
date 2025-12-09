package net.minecraft.client.renderer.shaders.pipeline;

/**
 * Represents the different rendering phases during world rendering.
 * These phases match Iris's WorldRenderingPhase enum and are used to
 * coordinate shader program switching during rendering.
 * 
 * The phases follow the order in which Minecraft renders the world:
 * 1. Sky and celestial objects
 * 2. Terrain (solid, cutout, translucent)
 * 3. Entities and particles
 * 4. Post-processing (composite passes)
 * 5. Final output to screen
 */
public enum WorldRenderingPhase {
	/**
	 * No active rendering phase.
	 */
	NONE,
	
	/**
	 * Sky rendering phase (sky box, void plane).
	 */
	SKY,
	
	/**
	 * Sunset/sunrise rendering phase.
	 */
	SUNSET,
	
	/**
	 * Custom sky rendering (sun, moon, stars).
	 */
	CUSTOM_SKY,
	
	/**
	 * Shadow map rendering phase (pre-pass).
	 */
	SHADOW,
	
	/**
	 * Setup phase before main rendering.
	 */
	SETUP,
	
	/**
	 * Solid terrain rendering.
	 */
	TERRAIN_SOLID,
	
	/**
	 * Cutout terrain rendering (e.g., grass, leaves without mipmaps).
	 */
	TERRAIN_CUTOUT,
	
	/**
	 * Cutout terrain with mipmaps.
	 */
	TERRAIN_CUTOUT_MIPPED,
	
	/**
	 * Translucent terrain rendering (e.g., water, stained glass).
	 */
	TRANSLUCENT_TERRAIN,
	
	/**
	 * Particle rendering.
	 */
	PARTICLES,
	
	/**
	 * Entity rendering.
	 */
	ENTITIES,
	
	/**
	 * Block entity (tile entity) rendering.
	 */
	BLOCK_ENTITIES,
	
	/**
	 * Hand/held item rendering (first-person view).
	 */
	HAND,
	
	/**
	 * Composite rendering phase (post-processing passes).
	 */
	COMPOSITE,
	
	/**
	 * Final pass to screen.
	 */
	FINAL
}
