package net.distanthorizons.core.config;

import net.distanthorizons.core.config.types.ConfigEntry;

import java.util.HashMap;
import java.util.HashSet;

public class ConfigPresetOptions<TQuickEnum, TConfig>
{
	public final ConfigEntry<TConfig> configEntry;
	
	private final HashMap<TQuickEnum, TConfig> configOptionByQualityOption;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ConfigPresetOptions(ConfigEntry<TConfig> configEntry, HashMap<TQuickEnum, TConfig> configOptionByQualityOption)
	{
		this.configEntry = configEntry;
		this.configOptionByQualityOption = configOptionByQualityOption;
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	public void updateConfigEntry(TQuickEnum quickQuality)
	{
		TConfig newValue = this.configOptionByQualityOption.get(quickQuality);
		this.configEntry.set(newValue);
	}
	
	public HashSet<TQuickEnum> getPossibleQualitiesFromCurrentOptionValue()
	{
		// get true value so we can ignore API overrides,
		// users find this confusing if their preset is set to "CUSTOM" 
		TConfig inputOptionValue = this.configEntry.getTrueValue();
		HashSet<TQuickEnum> possibleQualities = new HashSet<>();
		
		for (TQuickEnum key : this.configOptionByQualityOption.keySet())
		{
			TConfig optionValue = this.configOptionByQualityOption.get(key);
			if (optionValue.equals(inputOptionValue))
			{
				possibleQualities.add(key);
			}
		}
		
		return possibleQualities;
	}
	
}
