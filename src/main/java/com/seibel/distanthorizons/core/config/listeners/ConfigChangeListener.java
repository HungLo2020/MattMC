package com.seibel.distanthorizons.core.config.listeners;

import com.seibel.distanthorizons.core.config.types.ConfigEntry;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * A basic {@link IConfigListener} that will fire a {@link Consumer}
 * when the value changes from the value the config started with
 * when this object was created.
 *
 * @param <T> the config value type
 */
public class ConfigChangeListener<T> implements IConfigListener, Closeable
{
	private final ConfigEntry<T> configEntry;
	private final Consumer<T> onValueChangeFunc;
	
	private T previousValue;
	
	
	
	public ConfigChangeListener(ConfigEntry<T> configEntry, Consumer<T> onValueChangeFunc)
	{
		this.configEntry = configEntry;
		this.onValueChangeFunc = onValueChangeFunc;
		
		this.configEntry.addListener(this);
		this.previousValue = this.configEntry.get();
	}
	
	
	@Override
	public void onConfigValueSet()
	{
		T newValue = this.configEntry.get();
		if (newValue != this.previousValue)
		{
			this.previousValue = newValue;
			this.onValueChangeFunc.accept(newValue);
		}
	}
	
	@Override
	public void onUiModify() { /* do nothing, we only care about when the actual value is modified */ }
	
	
	
	/**
	 * Removes the config event listener. <br>
	 * Must be fired to prevent memory leaks.
	 */
	@Override
	public void close() { this.configEntry.removeListener(this); }
	
}
