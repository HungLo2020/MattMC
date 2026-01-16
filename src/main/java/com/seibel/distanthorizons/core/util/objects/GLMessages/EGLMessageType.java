package com.seibel.distanthorizons.core.util.objects.GLMessages;

import java.util.HashMap;

public enum EGLMessageType
{
	ERROR,
	DEPRECATED_BEHAVIOR,
	UNDEFINED_BEHAVIOR,
	PORTABILITY,
	PERFORMANCE,
	MARKER,
	PUSH_GROUP,
	POP_GROUP,
	OTHER;
	
	
	private static final HashMap<String, EGLMessageType> ENUM_BY_NAME = new HashMap<>();
	
	public final String name;
	
	
	static
	{
		for (EGLMessageType type : EGLMessageType.values())
		{
			ENUM_BY_NAME.put(type.name, type);
		}
	}
	
	EGLMessageType() { this.name = super.toString().toUpperCase(); }
	
	
	@Override
	public final String toString() { return this.name; }
	
	public static EGLMessageType get(String name) { return ENUM_BY_NAME.get(name.toUpperCase()); }
	
}
