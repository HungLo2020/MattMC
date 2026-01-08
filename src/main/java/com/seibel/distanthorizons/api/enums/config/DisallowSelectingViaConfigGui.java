package com.seibel.distanthorizons.api.enums.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Add this annotation to enum values that
 * are valid config options, but shouldn't be selectable
 * when toggling through the options. <br><br>
 *
 * Example: A preset's "custom" option shouldn't be selectable
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface DisallowSelectingViaConfigGui
{
	
}
