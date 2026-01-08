package com.seibel.distanthorizons.core.config.eventHandlers;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Listens to the config and will automatically
 * clear the current render cache if certain settings are changed. <br> <br>
 *
 * Note: if additional settings should clear the render cache, add those to this listener, don't create a new listener
 */
public class WorldCurvatureConfigEventHandler implements IConfigListener
{
	public static WorldCurvatureConfigEventHandler INSTANCE = new WorldCurvatureConfigEventHandler();
	
	private static final int MIN_VALID_CURVE_VALUE = 50; 
	
	
	/** private since we only ever need one handler at a time */
	private WorldCurvatureConfigEventHandler() { }
	
	
	
	@Override
	public void onConfigValueSet()
	{
		int curveRatio = Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
		if (curveRatio > 0 && curveRatio < MIN_VALID_CURVE_VALUE)
		{
			// shouldn't update the UI, otherwise we may end up fighting the user
			Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.set(MIN_VALID_CURVE_VALUE);
		}
		
	}
	
	@Override
	public void onUiModify() { /* do nothing, we only care about modified config values */ }
	
	
}
