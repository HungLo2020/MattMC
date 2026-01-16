package com.seibel.distanthorizons.api.interfaces.config.both;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;

/**
 * Distant Horizons' world generation configuration. <br><br>
 *
 * Note: Chunks generated via DH's world generator are NOT saved in Minecraft's vanilla save system.
 *
 * @author James Seibel
 * @version 2022-9-15
 * @since API 1.0.0
 */
public interface IDhApiWorldGenerationConfig extends IDhApiConfigGroup
{
	
	/**
	 * Defines whether LOD chunks will be generated
	 * outside Minecraft's vanilla render distance.
	 */
	IDhApiConfigValue<Boolean> enableDistantWorldGeneration();
	
	/** Defines to what level LOD chunks will be generated. */
	IDhApiConfigValue<EDhApiDistantGeneratorMode> distantGeneratorMode();
	
}
