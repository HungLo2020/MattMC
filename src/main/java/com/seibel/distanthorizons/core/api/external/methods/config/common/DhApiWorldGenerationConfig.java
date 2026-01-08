package com.seibel.distanthorizons.core.api.external.methods.config.common;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig;
import com.seibel.distanthorizons.core.config.api.DhApiConfigValue;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.core.config.Config;

/**
 * Distant Horizons' world generation configuration. <br><br>
 *
 * Note: LODs are NOT saved in Minecraft's save system.
 *
 * @author James Seibel
 * @version 2023-9-14
 */
public class DhApiWorldGenerationConfig implements IDhApiWorldGenerationConfig
{
	public static DhApiWorldGenerationConfig INSTANCE = new DhApiWorldGenerationConfig();
	
	private DhApiWorldGenerationConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<Boolean> enableDistantWorldGeneration()
	{ return new DhApiConfigValue<>(Config.Common.WorldGenerator.enableDistantGeneration); }
	
	@Override
	public IDhApiConfigValue<EDhApiDistantGeneratorMode> distantGeneratorMode()
	{ return new DhApiConfigValue<>(Config.Common.WorldGenerator.distantGeneratorMode); }
	
	
}
