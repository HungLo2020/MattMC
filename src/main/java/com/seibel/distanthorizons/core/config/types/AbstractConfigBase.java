package com.seibel.distanthorizons.core.config.types;

import com.seibel.distanthorizons.core.config.gui.IConfigGuiInfo;
import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

/**
 * The class all config options should extend
 *
 * @author coolGi
 */
public abstract class AbstractConfigBase<T>
{
	public String category = "";    // This should only be set once in the init
	public String name;            // This should only be set once in the init
	protected final T defaultValue;
	protected final boolean isFloatingPointNumber;
	protected T value;
	
	/** 
	 * This stores information related to the GUI state.
	 * This is set during config UI setup.
	 */
	public IConfigGuiInfo guiValue;
	
	protected EConfigEntryAppearance appearance;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	protected AbstractConfigBase(EConfigEntryAppearance appearance, T defaultValue)
	{
		this.defaultValue = defaultValue;
		this.value = defaultValue;
		this.appearance = appearance;
		
		Class<?> defaultValueClass = defaultValue.getClass();
		this.isFloatingPointNumber = (defaultValueClass == Double.class || defaultValueClass == Float.class);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	/** Gets the value */
	public T get() { return this.value; }
	/** Sets the value */
	public void set(T newValue) { this.value = newValue; }
	
	public EConfigEntryAppearance getAppearance() { return this.appearance; }
	public void setAppearance(EConfigEntryAppearance newAppearance) { this.appearance = newAppearance; }
	
	
	public String getCategory() { return this.category; }
	public String getName() { return this.name; }
	public String getNameAndCategory() { return (this.category.isEmpty() ? "" : this.category + ".") + this.name; }
	
	
	/** Gets the class of T */
	public Class<?> getType() { return this.defaultValue.getClass(); }
	public boolean typeIsFloatingPointNumber() { return this.isFloatingPointNumber; }
	
	protected static abstract class Builder<T, S>
	{
		protected EConfigEntryAppearance tmpAppearance = EConfigEntryAppearance.ALL;
		protected T tmpValue;
		
		
		// Put this into your own builder
		@SuppressWarnings("unchecked")
		public S setAppearance(EConfigEntryAppearance newAppearance)
		{
			this.tmpAppearance = newAppearance;
			return (S) this;
		}
		@SuppressWarnings("unchecked")
		public S set(T newValue)
		{
			this.tmpValue = newValue;
			return (S) this;
		}
		
	}
	
}
