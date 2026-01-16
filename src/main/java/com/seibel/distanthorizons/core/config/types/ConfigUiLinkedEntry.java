package com.seibel.distanthorizons.core.config.types;

import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

/**
 * Creates a UI element that copies everything from another element.
 * This element is only visible in the GUI.
 *
 * @author coolGi
 */
public class ConfigUiLinkedEntry extends AbstractConfigBase<AbstractConfigBase<?>>
{
	//=============//
	// constructor //
	//=============//
	
	public ConfigUiLinkedEntry(AbstractConfigBase<?> value)
	{ super(EConfigEntryAppearance.ONLY_IN_GUI, value); }
	
	
	
	//=========//
	// setters //
	//=========//
	
	/** Appearance shouldn't be changed */
	@Override
	public void setAppearance(EConfigEntryAppearance newAppearance) { }
	
	/** Value shouldn't be changed after creation */
	@Override
	public void set(AbstractConfigBase<?> newValue) { }
	
	
	
	//=========//
	// builder //
	//=========//
	
	public static class Builder extends AbstractConfigBase.Builder<AbstractConfigBase<?>, Builder>
	{
		/** Appearance shouldn't be changed */
		@Override
		public Builder setAppearance(EConfigEntryAppearance newAppearance)
		{
			return this;
		}
		
		public ConfigUiLinkedEntry build()
		{
			return new ConfigUiLinkedEntry(this.tmpValue);
		}
		
	}
	
	
	
}
