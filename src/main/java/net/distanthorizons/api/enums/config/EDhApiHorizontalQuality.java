package net.distanthorizons.api.enums.config;

/**
 * LOWEST <br>
 * LOW <br>
 * MEDIUM <br>
 * HIGH <br>
 * EXTREME <br>
 *
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiHorizontalQuality
{
	// Note: any quadraticBase less than 2.0f has issues with DetailDistanceUtil, and will always return the lowest detail level.
	//  So for now we are limiting the lowest value to 2.0
	//  LOWEST was originally 1.0f and LOW was 1.5f
	
	LOWEST(2.0f, 4),
	LOW(2.0f, 8),
	MEDIUM(2.0f, 12),
	HIGH(2.2f, 16),
	EXTREME(2.4f, 32),
	;
	
	
	
	public final double quadraticBase;
	public final int distanceUnitInBlocks;
	
	EDhApiHorizontalQuality(double quadraticBase, int distanceUnitInBlocks)
	{
		this.quadraticBase = quadraticBase;
		this.distanceUnitInBlocks = distanceUnitInBlocks;
	}
	
}