package net.distanthorizons.core.config.eventHandlers;

import net.distanthorizons.core.config.Config;
import net.distanthorizons.core.config.ConfigHandler;
import net.distanthorizons.core.config.listeners.IConfigListener;

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
