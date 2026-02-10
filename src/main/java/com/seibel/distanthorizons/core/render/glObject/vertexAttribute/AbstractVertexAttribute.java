package com.seibel.distanthorizons.core.render.glObject.vertexAttribute;

import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.backends.opengl.OpenGLCommandContext;

/**
 * Base for binding/unbinding Vertex Attribute objects (VAO's).
 * 
 * @see VertexAttributePostGL43
 * @see VertexAttributePreGL43
 */
public abstract class AbstractVertexAttribute
{
	private static final CommandContext CTX = OpenGLCommandContext.IMMEDIATE;
	/** Stores the handle of the AbstractVertexAttribute. */
	public final int id;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	// This will bind AbstractVertexAttribute
	protected AbstractVertexAttribute()
	{
		this.id = VulkanicAPI.glGenVertexArrays();
		VulkanicAPI.glBindVertexArray(this.id);
	}
	
	public static AbstractVertexAttribute create()
	{
		if (GLProxy.getInstance().vertexAttributeBufferBindingSupported)
		{
			return new VertexAttributePostGL43();
		}
		else
		{
			return new VertexAttributePreGL43();
		}
	}
	
	
	
	//=========//
	// binding //
	//=========//
	
	public void bind() { VulkanicAPI.glBindVertexArray(this.id); }
	public void unbind() { VulkanicAPI.glBindVertexArray(0); }
	
	/** Always remember to always free your resources! */
	public void free() { VulkanicAPI.deleteVertexArray(CTX, this.id); }
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	public abstract void bindBufferToAllBindingPoints(int buffer);
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	public abstract void bindBufferToBindingPoint(int buffer, int bindingPoint);
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void unbindBuffersFromAllBindingPoint();
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void unbindBuffersFromBindingPoint(int bindingPoint);
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void setVertexAttribute(int bindingPoint, int attributeIndex, VertexPointer attribute);
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void completeAndCheck(int expectedStrideSize);
	
}
