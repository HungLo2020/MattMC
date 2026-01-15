package net.distanthorizons.core.config.types;

import net.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

/**
 * Adds a category to the config
 * See our config file for more information on how to use it
 *
 * @author coolGi
 */
public class ConfigCategory extends AbstractConfigBase<Class<?>>
{
	/** 
	 * Defines where this category points to. <br>
	 * May be defined during config setup.
	 */
	public String destination;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private ConfigCategory(EConfigEntryAppearance appearance, Class<?> value, String destination)
	{
		super(appearance, value);
		this.destination = destination;
	}
	
	
	
	//==================//
	// property getters //
	//==================//
	
	public String getDestination() { return this.destination; }
	
	/** Use get() instead for category */
	@Override
	@Deprecated
	public Class<?> getType() { return this.value; }
	
	
	
	//=========//
	// builder //
	//=========//
	
	public static class Builder extends AbstractConfigBase.Builder<Class<?>, Builder>
	{
		private String tmpDestination = null;
		
		public Builder setDestination(String newDestination)
		{
			this.tmpDestination = newDestination;
			return this;
		}
		
		public Builder setAppearance(EConfigEntryAppearance newAppearance)
		{
			this.tmpAppearance = newAppearance;
			return this;
		}
		
		public ConfigCategory build()
		{
			return new ConfigCategory(tmpAppearance, tmpValue, tmpDestination);
		}
		
	}
	
}
