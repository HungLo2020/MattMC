package com.seibel.distanthorizons.api.interfaces.config.client;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/**
 * Distant Horizons' fog configuration. <br><br>
 *
 * @author James Seibel
 * @version 2022-9-6
 * @since API 1.0.0
 */
public interface IDhApiAmbientOcclusionConfig extends IDhApiConfigGroup
{
	/** Determines if Ambient Occlusion is rendered */
	IDhApiConfigValue<Boolean> enabled();
	
	/** 
	 * Determines how many points in space are sampled for the occlusion test. 
	 * Higher numbers will improve quality and reduce banding, but will increase GPU load. 
	 */
	IDhApiConfigValue<Integer> sampleCount();
	
	/** Determines the radius Screen Space Ambient Occlusion is applied, measured in blocks. */
	IDhApiConfigValue<Double> radius();
	
	/** Determines how dark the Screen Space Ambient Occlusion effect will be. */
	IDhApiConfigValue<Double> strength();
	
	/** Increasing the value can reduce banding at the cost of reducing the strength of the effect. */
	IDhApiConfigValue<Double> bias();
	
	/** 
	 * Determines how dark the occlusion shadows can be. <br> 
	 * 0 = totally black at the corners <br>
	 * 1 = no shadow
	 */
	IDhApiConfigValue<Double> minLight();
	
	/**
	 * The radius, measured in pixels, that blurring is calculated. <br>
	 * Higher numbers will reduce banding at the cost of GPU performance.
	 */
	IDhApiConfigValue<Integer> blurRadius();
	
}
