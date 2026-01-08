package com.seibel.distanthorizons.core.config.eventHandlers;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.Config;

public class QuickRenderToggleConfigEventHandler
{
	public static QuickRenderToggleConfigEventHandler INSTANCE = new QuickRenderToggleConfigEventHandler();
	
	private final ConfigChangeListener<Boolean> quickRenderChangeListener;
	private final ConfigChangeListener<EDhApiRendererMode> rendererModeChangeListener;
	
	
	
	/** private since we only ever need one handler at a time */
	private QuickRenderToggleConfigEventHandler()
	{
		this.quickRenderChangeListener = new ConfigChangeListener<>(Config.Client.quickEnableRendering,
				(val) -> {
					Config.Client.Advanced.Debugging.rendererMode.set(Config.Client.quickEnableRendering.get()
							? EDhApiRendererMode.DEFAULT
							: EDhApiRendererMode.DISABLED);
				});
		this.rendererModeChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Debugging.rendererMode,
				(val) -> {
					Config.Client.quickEnableRendering.set(
							Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DISABLED);
				});
	}
	
	/**
	 * Set the UI only config based on what is set in the file. <br>
	 * This should only be called once.
	 */
	public void setUiOnlyConfigValues()
	{
		boolean enableRendering = Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DISABLED;
		Config.Client.quickEnableRendering.set(enableRendering);
	}
	
}
