package com.seibel.distanthorizons.api.enums.config.quickOptions;

import com.seibel.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;

/**
 * CUSTOM, <br><br>
 *
 * MINIMAL_IMPACT, <br>
 * LOW_IMPACT, <br>
 * BALANCED, <br>
 * AGGRESSIVE, <br>
 * 
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiThreadPreset
{
	@DisallowSelectingViaConfigGui
	CUSTOM,
	
	MINIMAL_IMPACT,
	LOW_IMPACT,
	BALANCED,
	AGGRESSIVE,
	I_PAID_FOR_THE_WHOLE_CPU,
	
}