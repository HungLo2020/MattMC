package com.seibel.distanthorizons.api.enums.rendering;

/**
 * DISABLED,					<br>
 * FAKE,						<br>
 * COMPLETE,					<br>
 * 
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiTransparency
{
	DISABLED(false, false),
	FAKE(true, true),
	COMPLETE(true, false);
	
	public final boolean transparencyEnabled;
	public final boolean fakeTransparencyEnabled;
	
	EDhApiTransparency(boolean transparencyEnabled, boolean fakeTransparencyEnabled)
	{
		this.transparencyEnabled = transparencyEnabled;
		this.fakeTransparencyEnabled = fakeTransparencyEnabled;
	}
	
}
