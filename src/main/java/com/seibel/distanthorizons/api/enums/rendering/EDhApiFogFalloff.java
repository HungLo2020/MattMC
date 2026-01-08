package com.seibel.distanthorizons.api.enums.rendering;

/**
 * LINEAR,				<br>
 * EXPONENTIAL, 		<br>
 * EXPONENTIAL_SQUARED 	<br>
 *
 * @author Leetom
 * @version 2024-11-09
 * @since API 2.0.0
 */
public enum EDhApiFogFalloff
{
	LINEAR(0),
	EXPONENTIAL(1),
	EXPONENTIAL_SQUARED(2);
	
	
	/** 
	 * Stable version of {@link EDhApiFogFalloff#ordinal()} 
	 * @since API 4.0.0
	 */
	public final int value;
	
	EDhApiFogFalloff(int value) { this.value = value; }
	
}
