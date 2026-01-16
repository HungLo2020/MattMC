package com.seibel.distanthorizons.api.enums.config;

/**
 * NONE, <br>
 * NON_COLLIDING, <br>
 *
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiBlocksToAvoid
{
	NONE(false),
	NON_COLLIDING(true);
	
	public final boolean noCollision;
	
	EDhApiBlocksToAvoid(boolean noCollision) { this.noCollision = noCollision; }
	
}