package com.seibel.distanthorizons.core.util.objects.GLMessages;

import java.util.HashMap;

public enum EGLMessageSource
{
	API,
	WINDOW_SYSTEM,
	SHADER_COMPILER,
	THIRD_PARTY,
	APPLICATION,
	OTHER;
	
	
	private static final HashMap<String, EGLMessageSource> ENUM_BY_NAME = new HashMap<>();
	
	public final String name;
	
	
	static
	{
		for (EGLMessageSource source : EGLMessageSource.values())
		{
			ENUM_BY_NAME.put(source.name, source);
		}
	}
	
	EGLMessageSource() { this.name = super.toString().toUpperCase(); }
	
	
	@Override
	public final String toString() { return this.name; }
	
	public static EGLMessageSource get(String name) { return ENUM_BY_NAME.get(name.toUpperCase()); }
	
}
