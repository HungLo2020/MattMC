package net.distanthorizons.api.interfaces.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigGroup;
import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/**
 * Distant Horizons' generic rendering configuration. <br><br>
 *
 * @author James Seibel
 * @version 2024-7-11
 * @since API 3.0.0
 */
public interface IDhApiGenericRenderingConfig extends IDhApiConfigGroup
{
	/** 
	 * If enabled DH will render generic objects into its terrain pass. <br>
	 * This includes: clouds, beacons, and API added objects.
	 */
	IDhApiConfigValue<Boolean> renderingEnabled();
	
	/** If enabled DH will render beacon beams. */
	IDhApiConfigValue<Boolean> beaconRenderingEnabled();
	
	/** If enabled DH will render clouds. */
	IDhApiConfigValue<Boolean> cloudRenderingEnabled();
	
}
