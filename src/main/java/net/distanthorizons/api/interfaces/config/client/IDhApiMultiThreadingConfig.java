package net.distanthorizons.api.interfaces.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.IDhApiConfigGroup;

/**
 * Distant Horizons' threading configuration.
 *
 * @author James Seibel
 * @version 2024-12-26
 * @since API 1.0.0
 */
public interface IDhApiMultiThreadingConfig extends IDhApiConfigGroup
{
	
	/**
	 * Defines how many threads Distant Horizons
	 * uses.
	 * 
	 * @since API 4.0.0
	 */
	IDhApiConfigValue<Integer> threadCount();
	
	/**
	 * Defines how many long Distant Horizons
	 * threads will spend running vs sleeping.
	 * This is helpful when reducing the CPU
	 * load on low end CPUs.
	 * 1.0 = 100% uptime
	 * 0.5 = 50% uptime
	 * 0.1 = 10% uptime
	 *
	 * @since API 4.0.0
	 */
	IDhApiConfigValue<Double> threadRuntimeRatio();
	
}
