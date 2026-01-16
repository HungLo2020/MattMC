package com.seibel.distanthorizons.core.config.eventHandlers;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;
import com.seibel.distanthorizons.core.util.TimerUtil;

import java.util.Timer;
import java.util.TimerTask;

public class ReloadLodsConfigEventHandler implements IConfigListener
{
	/** 
	 * should be used for user facing UI options
	 * this allows the user a second to click through options before they're applied
	 */
	public static ReloadLodsConfigEventHandler DELAYED_INSTANCE = new ReloadLodsConfigEventHandler(2_000L);
	/** should be used for debug options so their change can be seen instantly */
	public static ReloadLodsConfigEventHandler INSTANT_INSTANCE = new ReloadLodsConfigEventHandler(0);
	
	/** how long to wait in milliseconds before applying the config changes */
	private final long timeoutInMs;
	private Timer cacheClearingTimer;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ReloadLodsConfigEventHandler(long timeoutInMs)
	{
		this.timeoutInMs = timeoutInMs;
	}
	
	
	
	//========//
	// events //
	//========//
	
	@Override
	public void onConfigValueSet()
	{ 
		if (this.timeoutInMs > 0)
		{
			this.refreshRenderDataAfterTimeout();
		}
		else
		{
			clearRenderDataCache();
		}
	}
	
	/** Calling this method multiple times will reset the timer */
	private synchronized void refreshRenderDataAfterTimeout() // synchronized to prevent potential threading issues when adding/removing the timer
	{
		// stop the previous timer if one exists
		if (this.cacheClearingTimer != null)
		{
			this.cacheClearingTimer.cancel();
		}
		
		// create a new timer task
		TimerTask timerTask = new TimerTask()
		{
			public void run()
			{
				clearRenderDataCache();
			}
		};
		this.cacheClearingTimer = TimerUtil.CreateTimer("RenderCacheClearConfigTimer");
		this.cacheClearingTimer.schedule(timerTask, this.timeoutInMs);
	}
	
	private static void clearRenderDataCache()
	{
		IDhApiRenderProxy renderProxy = DhApi.Delayed.renderProxy;
		if (renderProxy != null)
		{
			renderProxy.clearRenderDataCache();
		}
	}
	
	
}
