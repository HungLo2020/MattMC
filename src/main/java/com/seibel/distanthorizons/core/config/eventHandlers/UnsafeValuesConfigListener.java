package com.seibel.distanthorizons.core.config.eventHandlers;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigHandler;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;

/** 
 * handles enabling/disabling config validation when the
 * {@link Config.Client.Advanced.Debugging#allowUnsafeValues} option
 * is changed.
 */
public class UnsafeValuesConfigListener implements IConfigListener
{
	public static UnsafeValuesConfigListener INSTANCE = new UnsafeValuesConfigListener();
	
	@Override
	public void onConfigValueSet()
	{ ConfigHandler.INSTANCE.runMinMaxValidation = !Config.Client.Advanced.Debugging.allowUnsafeValues.get(); }
	
	
	
}
