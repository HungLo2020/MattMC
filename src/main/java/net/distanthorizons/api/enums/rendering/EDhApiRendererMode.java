package net.distanthorizons.api.enums.rendering;

/**
 * Default <br>
 * Debug <br>
 * Disabled <br>
 *
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiRendererMode
{
	DEFAULT,
	DEBUG,
	DISABLED;
	
	
	/** Used by the config GUI to cycle through the available rendering options */
	public static EDhApiRendererMode next(EDhApiRendererMode type)
	{
		switch (type)
		{
			case DEFAULT:
				return DEBUG;
			case DEBUG:
				return DISABLED;
			default:
				return DEFAULT;
		}
	}
	
	/** Used by the config GUI to cycle through the available rendering options */
	public static EDhApiRendererMode previous(EDhApiRendererMode type)
	{
		switch (type)
		{
			case DEFAULT:
				return DISABLED;
			case DEBUG:
				return DEFAULT;
			default:
				return DEBUG;
		}
	}
	
}
