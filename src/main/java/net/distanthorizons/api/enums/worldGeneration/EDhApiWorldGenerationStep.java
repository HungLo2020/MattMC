package net.distanthorizons.api.enums.worldGeneration;

/**
 * DOWN_SAMPLED, <br>
 * 
 * EMPTY, <br>
 * STRUCTURE_START, <br>
 * STRUCTURE_REFERENCE, <br>
 * BIOMES, <br>
 * NOISE, <br>
 * SURFACE, <br>
 * CARVERS, <br>
 * LIQUID_CARVERS, <br>
 * FEATURES, <br>
 * LIGHT, <br>
 *
 * @author James Seibel
 * @version 2023-4-20
 * @since API 1.0.0
 */
public enum EDhApiWorldGenerationStep
{
	/** 
	 * Only used when using N-sized world generators or server-side retrieval.
	 * This denotes that the given datasource was created using lower quality LOD data from above it in the quad tree. <br>
	 * 
	 * This isn't a valid option for queuing world generation.
	 */
	DOWN_SAMPLED(-1, "down_sampled"),
	
	EMPTY(0, "empty"),
	STRUCTURE_START(1, "structure_start"),
	STRUCTURE_REFERENCE(2, "structure_reference"),
	BIOMES(3, "biomes"),
	NOISE(4, "noise"),
	SURFACE(5, "surface"),
	CARVERS(6, "carvers"),
	LIQUID_CARVERS(7, "liquid_carvers"),
	FEATURES(8, "features"),
	LIGHT(9, "light");
	
	
	
	/** used when serializing this enum. */
	public final String name;
	public final byte value;
	
	
	EDhApiWorldGenerationStep(int value, String name) 
	{ 
		this.value = (byte) value; 
		this.name = name; 
	}
	
	
	//=========//
	// parsing //
	//=========//
	
	/** @return null if the value doesn't correspond to a {@link EDhApiWorldGenerationStep}. */
	public static EDhApiWorldGenerationStep fromValue(int value)
	{
		for (EDhApiWorldGenerationStep genStep : EDhApiWorldGenerationStep.values())
		{
			if (genStep.value == value)
			{
				return genStep;
			}
		}
		
		return null;
	}
	
	/** @return null if the value doesn't correspond to a {@link EDhApiWorldGenerationStep}. */
	public static EDhApiWorldGenerationStep fromName(String name)
	{
		for (EDhApiWorldGenerationStep genStep : EDhApiWorldGenerationStep.values())
		{
			if (genStep.name.equals(name))
			{
				return genStep;
			}
		}
		
		return null;
	}
	
}
