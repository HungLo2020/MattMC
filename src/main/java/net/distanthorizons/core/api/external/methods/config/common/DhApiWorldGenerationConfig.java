package net.distanthorizons.core.api.external.methods.config.common;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig;
import net.distanthorizons.core.config.api.DhApiConfigValue;
import net.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import net.distanthorizons.core.config.Config;

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
