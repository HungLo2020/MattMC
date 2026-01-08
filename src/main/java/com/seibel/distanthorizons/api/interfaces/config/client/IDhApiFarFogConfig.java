package com.seibel.distanthorizons.api.interfaces.config.client;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogFalloff;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/**
 * Distant Horizons' fog configuration. <br><br>
 *
 * Note: unless an option explicitly states that it modifies
 * Minecraft's vanilla rendering (like DisableVanillaFog)
 * these settings will only affect Distant horizons' fog.
 *
 * @author James Seibel
 * @version 2022-6-14
 * @since API 1.0.0
 */
public interface IDhApiFarFogConfig extends IDhApiConfigGroup
{
	/**
	 * Defines where the fog starts as a percent of the
	 * fake chunks render distance radius. <br>
	 * Can be greater than the fog end distance to invert the fog direction. <br> <br>
	 *
	 * 0.0 = fog starts at the camera <br>
	 * 1.0 = fog starts at the edge of the fake chunk render distance <br>
	 */
	IDhApiConfigValue<Double> farFogStartDistance();
	
	/**
	 * Defines where the fog ends as a percent of the radius
	 * of the fake chunks render distance. <br>
	 * Can be less than the fog start distance to invert the fog direction. <br> <br>
	 *
	 * 0.0 = fog ends at the camera <br>
	 * 1.0 = fog ends at the edge of the fake chunk render distance <br>
	 */
	IDhApiConfigValue<Double> farFogEndDistance();
	
	/** Defines how opaque the fog is at its thinnest point. */
	IDhApiConfigValue<Double> farFogMinThickness();
	
	/** Defines how opaque the fog is at its thickest point. */
	IDhApiConfigValue<Double> farFogMaxThickness();
	
	/** Defines how the fog changes in thickness. */
	IDhApiConfigValue<EDhApiFogFalloff> farFogFalloff();
	
	/** Defines the fog density. */
	IDhApiConfigValue<Double> farFogDensity();
	
}
