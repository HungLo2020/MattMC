package com.seibel.distanthorizons.api.enums.rendering;

/**
 * USE_DEFAULT_FOG_COLOR, <br>
 * USE_SKY_COLOR, <br>
 *
 * @author James Seibel
 * @version 2024-4-6
 * @since API 2.0.0
 */
public enum EDhApiFogColorMode
{
	// Reminder:
	// when adding items: up the API minor version
	// when removing items: up the API major version
	
	/** Fog uses Minecraft's fog color. */
	USE_WORLD_FOG_COLOR,
	
	/**
	 * Replicates the effect of the clear sky mod.
	 * Making the fog blend in with the sky better
	 * For it to look good you need one of the following mods:
	 * https://www.curseforge.com/minecraft/mc-mods/clear-skies
	 * https://www.curseforge.com/minecraft/mc-mods/clear-skies-forge-port
	 */
	USE_SKY_COLOR,
	
}
