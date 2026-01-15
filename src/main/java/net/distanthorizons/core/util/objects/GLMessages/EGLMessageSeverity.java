package net.distanthorizons.core.util.objects.GLMessages;

import java.util.HashMap;

public enum EGLMessageSeverity
{
	HIGH,
	MEDIUM,
	LOW,
	NOTIFICATION;
	
	
	public final String name;
	
	static final HashMap<String, EGLMessageSeverity> ENUM_BY_NAME = new HashMap<>();
	
	
	static
	{
		for (EGLMessageSeverity severity : EGLMessageSeverity.values())
		{
			ENUM_BY_NAME.put(severity.name, severity);
		}
	}
	
	EGLMessageSeverity() { this.name = super.toString().toUpperCase(); }
	
	
	@Override
	public final String toString() { return this.name; }
	
	public static EGLMessageSeverity get(String name) { return ENUM_BY_NAME.get(name.toUpperCase()); }
	
}
	