package net.distanthorizons.api.interfaces.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigGroup;
import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/**
 * Distant Horizons' noise texture configuration. <br><br>
 *
 * @author James Seibel
 * @version 2022-6-14
 * @since API 1.0.0
 */
public interface IDhApiNoiseTextureConfig extends IDhApiConfigGroup
{
	/** If enabled a noise texture will be rendered on the LODs. */
	IDhApiConfigValue<Boolean> noiseEnabled();
	
	/** Defines how many steps of noise should be applied. */
	IDhApiConfigValue<Integer> noiseSteps();
	
	/** Defines how intense the noise will be. */
	IDhApiConfigValue<Double> noiseIntensity();
	
	/**
	 * Defines how far should the noise texture render before it fades away. (in blocks) <br>
	 * Set to 0 to disable noise from fading away
	 */
	IDhApiConfigValue<Integer> noiseDropoff();
	
}
