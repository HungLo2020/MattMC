package net.distanthorizons.api.enums.config.quickOptions;

import net.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;

/**
 * CUSTOM, <br><br>
 *
 * MINIMUM, <br>
 * LOW, <br>
 * MEDIUM, <br>
 * HIGH, <br>
 * EXTREME, <br>
 * 
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiQualityPreset
{
	@DisallowSelectingViaConfigGui
	CUSTOM,
	
	MINIMUM,
	LOW,
	MEDIUM,
	HIGH,
	EXTREME;
	
}