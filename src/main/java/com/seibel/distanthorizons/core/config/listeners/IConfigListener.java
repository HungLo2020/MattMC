package com.seibel.distanthorizons.core.config.listeners;

public interface IConfigListener
{
	/** Called whenever the value is set (including in core DH code) */
	default void onConfigValueSet() {}
	
	/** Called whenever the value is changed through the UI */
	default void onUiModify() {}
	
}
