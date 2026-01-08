package com.seibel.distanthorizons.api.enums.config;

/**
 * Handles how Minecraft's rendering
 * is faded out to smooth the transition
 * between MC and DH rendering. <br><br>
 * 
 * NONE, <br>
 * SINGLE_PASS, <br>
 * DOUBLE_PASS, <br>
 *
 * @since API 4.0.0
 * @version 2024-10-3
 */
public enum EDhApiMcRenderingFadeMode
{
	/**
	 * No fading is done, there will be a pronounced border between
	 * Minecraft and Distant Horizons. <br>
	 * Fastest.
	 */
	NONE,
	/**
	 * Fading only runs after the translucent render pass. <br>
	 * Looks good for the tops of oceans and rivers, but
	 * doesn't fade the opaque blocks underwater.
	 */
	SINGLE_PASS,
	/** 
	 * Fading runs after both opaque and translucent render passes. 
	 * Slowest, but oceans and rivers look better.
	 */
	DOUBLE_PASS;
	
}