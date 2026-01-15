package net.distanthorizons.api.enums.config;

/**
 * AUTO <br>
 * ENABLED <br>
 * DISABLED <br>
 *
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiLodShading
{
	/** 
	 * Uses Minecraft's shading for LODs. <Br>
	 * This means if Minecraft's shading is disabled DH's shading will be as well.
	 */
	AUTO,
	
	/** 
	 * Simulates Minecraft's shading. <Br>
	 * This is most useful for shaders that disable Minecraft's shading
	 * but still require shading on LODs.
	 */
	ENABLED,
	
	/** LODs will have no shading */
	DISABLED;
	
}
