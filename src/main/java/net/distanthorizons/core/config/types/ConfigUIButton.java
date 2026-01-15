package net.distanthorizons.core.config.types;

import net.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

public class ConfigUIButton extends AbstractConfigBase<Runnable>
{
	//=============//
	// constructor //
	//=============//
	
	public ConfigUIButton(Runnable runnable)
	{ super(EConfigEntryAppearance.ONLY_IN_GUI, runnable); }
	
	
	
	//=========//
	// actions //
	//=========//
	
	/** 
	 * Runs the action of the button. 
	 * NOTE: This will run on the render thread 
	 * (so it can halt the main process if it takes too long and isn't offloaded to another thread)
	 */
	public void runAction() { this.value.run(); }
	
	
	
	//=========//
	// builder //
	//=========//
	
	public static class Builder extends AbstractConfigBase.Builder<Runnable, Builder>
	{
		/** Appearance shouldn't be changed */
		@Override
		public Builder setAppearance(EConfigEntryAppearance newAppearance) { return this; }
		
		public ConfigUIButton build()
		{ return new ConfigUIButton(this.tmpValue); }
		
	}
	
}
