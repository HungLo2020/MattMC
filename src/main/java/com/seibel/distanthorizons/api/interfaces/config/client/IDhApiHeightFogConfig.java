package com.seibel.distanthorizons.api.interfaces.config.client;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogFalloff;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogDirection;
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
public interface IDhApiHeightFogConfig extends IDhApiConfigGroup
{
	
	/** Defines how the height fog mixes. */
	IDhApiConfigValue<EDhApiHeightFogMixMode> heightFogMixMode();
	
	/**
	 * Defines which direction height fog is drawn relative to the world. 
	 * @since API 4.0.0
	 */
	IDhApiConfigValue<EDhApiHeightFogDirection> heightFogDirection();
	
	/**
	 * Defines the height fog's base height if {@link IDhApiHeightFogConfig#heightFogDirection()}
	 * is set to use a specific height.
	 */
	IDhApiConfigValue<Double> heightFogBaseHeight();
	
	/** Defines the height fog's starting height as a percent of the world height. */
	IDhApiConfigValue<Double> heightFogStartingHeightPercent();
	
	/** Defines the height fog's ending height as a percent of the world height. */
	IDhApiConfigValue<Double> heightFogEndingHeightPercent();
	
	/** Defines how opaque the height fog is at its thinnest point. */
	IDhApiConfigValue<Double> heightFogMinThickness();
	
	/** Defines how opaque the height fog is at its thickest point. */
	IDhApiConfigValue<Double> heightFogMaxThickness();
	
	/** Defines how the height fog changes in thickness. */
	IDhApiConfigValue<EDhApiFogFalloff> heightFogFalloff();
	
	/** Defines the height fog's density. */
	IDhApiConfigValue<Double> heightFogDensity();
	
}
