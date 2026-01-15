package net.distanthorizons.core.util.objects.GLMessages;

public final class GLMessage
{
	static final String HEADER = "[LWJGL] OpenGL debug message";
	public final EGLMessageType type;
	public final EGLMessageSeverity severity;
	public final EGLMessageSource source;
	public final String id;
	public final String message;
	
	
	
	GLMessage(EGLMessageType type, EGLMessageSeverity severity, EGLMessageSource source, String id, String message)
	{
		this.type = type;
		this.source = source;
		this.severity = severity;
		this.id = id;
		this.message = message;
	}
	
	
	
	@Override
	public String toString() 
	{ 
		return "level: [" + this.severity + "], " +
				"type: [" + this.type + "], " +
				"source: [" + this.source + "], " +
				"id: [" + this.id + "], " +
				"msg: [" + this.message + "]"; 
	}
	
	
	
}