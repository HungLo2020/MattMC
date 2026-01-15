package net.distanthorizons.core.config.types;

import net.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

/**
 * Adds empty space the height of a button.
 * Useful for separating different categories.
 */
public class ConfigUISpacer extends AbstractConfigBase<String>
{
	//=============//
	// constructor //
	//=============//
	
	public ConfigUISpacer()
	{ super(EConfigEntryAppearance.ONLY_IN_GUI, ""); }
	
	
	
	//=========//
	// setters //
	//=========//
	
	/** Appearance shouldn't be changed */
	@Override
	public void setAppearance(EConfigEntryAppearance newAppearance) { }
	
	/** Pointless to set the value */
	@Override
	public void set(String newValue) { }
	
	
	
	//=========//
	// builder //
	//=========//
	
	public static class Builder extends AbstractConfigBase.Builder<String, Builder>
	{
		/** Appearance shouldn't be changed */
		@Override
		public Builder setAppearance(EConfigEntryAppearance newAppearance) { return this; }
		
		/** Pointless to set the value */
		@Override
		public Builder set(String newValue) { return this; }
		
		public ConfigUISpacer build() { return new ConfigUISpacer(); }
		
	}
	
}
