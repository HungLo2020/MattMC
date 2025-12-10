package net.minecraft.client.renderer.shaders.pipeline;

import net.minecraft.client.renderer.RenderType;

/**
 * IRIS VERBATIM: WorldRenderingPhase.java
 * Represents the current rendering phase in the world rendering pipeline.
 */
public enum WorldRenderingPhase {
	NONE,
	SKY,
	SUNSET,
	CUSTOM_SKY,
	SUN,
	MOON,
	STARS,
	VOID,
	TERRAIN_SOLID,
	TERRAIN_CUTOUT_MIPPED,
	TERRAIN_CUTOUT,
	ENTITIES,
	BLOCK_ENTITIES,
	DESTROY,
	OUTLINE,
	DEBUG,
	HAND_SOLID,
	TERRAIN_TRANSLUCENT,
	TRIPWIRE,
	PARTICLES,
	CLOUDS,
	RAIN_SNOW,
	WORLD_BORDER,
	HAND_TRANSLUCENT;

	/**
	 * IRIS VERBATIM: fromTerrainRenderType method
	 * Convert terrain render type to WorldRenderingPhase
	 */
	public static WorldRenderingPhase fromTerrainRenderType(RenderType renderType) {
		// Minecraft 1.21+ uses RenderType directly
		// Map to appropriate phase based on render type characteristics
		String name = renderType.toString().toLowerCase();
		
		if (name.contains("solid")) {
			return WorldRenderingPhase.TERRAIN_SOLID;
		} else if (name.contains("cutout_mipped")) {
			return WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
		} else if (name.contains("cutout")) {
			return WorldRenderingPhase.TERRAIN_CUTOUT;
		} else if (name.contains("translucent")) {
			return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
		} else if (name.contains("tripwire")) {
			return WorldRenderingPhase.TRIPWIRE;
		} else {
			throw new IllegalStateException("Illegal render type!");
		}
	}
}
