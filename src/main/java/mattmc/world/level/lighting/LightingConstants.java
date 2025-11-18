package mattmc.world.level.lighting;

/**
 * Constants for the lighting system.
 * 
 * Centralizes magic numbers and thresholds used throughout the lighting
 * subsystem to ensure consistent behavior.
 */
public class LightingConstants {
	
	/**
	 * Full opacity value (0-15 scale).
	 * Blocks with opacity >= FULL_OPACITY are treated as hard blockers
	 * that completely stop light propagation.
	 */
	public static final int FULL_OPACITY = 15;
	
	/**
	 * Maximum light level (0-15 scale).
	 * Represents full brightness from sky or emissive blocks.
	 */
	public static final int MAX_LIGHT_LEVEL = 15;
	
	/**
	 * Minimum light level (0-15 scale).
	 * Represents complete darkness.
	 */
	public static final int MIN_LIGHT_LEVEL = 0;
	
	// Private constructor to prevent instantiation
	private LightingConstants() {
		throw new AssertionError("LightingConstants should not be instantiated");
	}
}
