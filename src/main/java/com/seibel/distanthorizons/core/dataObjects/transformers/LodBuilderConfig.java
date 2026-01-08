package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;

/**
 * This is used to easily configure how LodChunks are generated.
 * Generally this will only be used if we want to generate a
 * LodChunk using incomplete data, otherwise the defaults
 * should work best for a fully generated chunk (IE has correct surface blocks).
 *
 * @author James Seibel
 * @version 2022-12-10
 */
public class LodBuilderConfig
{
	/** default: false */
	public boolean useHeightmap;
	/** default: false */
	public boolean useBiomeColors;
	/** default: true */
	public boolean useSolidBlocksInColorGen;
	/** default: false */
	public boolean quickFillWithVoid;
	public EDhApiDistantGeneratorMode distantGeneratorDetailLevel;
	
	
	/**
	 * default settings for a normal chunk <br>
	 * useHeightmap = false <br>
	 * useBiomeColors = false <br>
	 * useSolidBlocksInColorGen = true <br>
	 */
	public LodBuilderConfig(EDhApiDistantGeneratorMode distantGeneratorDetailLevel)
	{
		this.useHeightmap = false;
		this.useBiomeColors = false;
		this.useSolidBlocksInColorGen = true;
		this.quickFillWithVoid = false;
		this.distantGeneratorDetailLevel = distantGeneratorDetailLevel;
	}
	
	/** Default settings used when generating LODs for pre-generated terrain. */
	public static LodBuilderConfig getFillVoidConfig()
	{
		LodBuilderConfig config = new LodBuilderConfig(EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY);
		config.quickFillWithVoid = true;
		return config;
	}
	
}