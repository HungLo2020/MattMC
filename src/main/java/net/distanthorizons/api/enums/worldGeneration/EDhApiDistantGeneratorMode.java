package net.distanthorizons.api.enums.worldGeneration;

/**
 * PRE_EXISTING_ONLY <br>
 * SURFACE <br>
 * FEATURES <br>
 * FULL <br><br>
 *
 * In order of fastest to slowest.
 *
 * @author James Seibel
 * @author Leonardo Amato
 * @version 2024-12-13
 * @since API 1.0.0
 */
public enum EDhApiDistantGeneratorMode
{
	/** Don't generate any new terrain, just generate LODs for already generated chunks. */
	PRE_EXISTING_ONLY((byte) 1),
	
	/*
	 * Not currently implemented <br><br>
	 * 
	 * Only generate the biomes and use biome
	 * grass/foliage color, water color, or ice color
	 * to generate the color. <br>
	 * Doesn't generate height, everything is shown at sea level.
	 */
	//BIOME_ONLY((byte) 2),
	
	/*
	 * Not currently implemented <br><br>
	 * 
	 * Same as BIOME_ONLY, except instead
	 * of always using sea level as the LOD height
	 * different biome types (mountain, ocean, forest, etc.)
	 * use predetermined heights to simulate having height data.
	 */
	//BIOME_ONLY_SIMULATE_HEIGHT((byte) 3),
	
	/**
	 * Generate the world surface,
	 * this does NOT include caves, trees,
	 * or structures.
	 */
	SURFACE((byte) 4),
	
	/**
	 * Generate including structures.
	 * NOTE: This may cause world generation bugs or instability,
	 * since some features can cause concurrentModification exceptions.
	 */
	FEATURES((byte) 5),
	
	/**
	 * Ask the server to generate/load each chunk.
	 * This is the most compatible and will generate structures correctly, 
	 * but may cause server/simulation lag. <br><br>
	 * 
	 * Unlike other modes this option DOES save generated chunks to
	 * Minecraft's region files.
	 */
	INTERNAL_SERVER((byte) 6);
	
	
	
	/** The higher the number the more complete the generation is. */
	public final byte complexity;
	
	
	EDhApiDistantGeneratorMode(byte complexity) { this.complexity = complexity; }
	
}
