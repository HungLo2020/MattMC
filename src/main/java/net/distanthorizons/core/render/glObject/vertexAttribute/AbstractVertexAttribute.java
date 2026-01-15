package net.distanthorizons.core.render.glObject.vertexAttribute;

import net.distanthorizons.core.render.glObject.GLProxy;
import org.lwjgl.opengl.GL32;

/**
 * Base for binding/unbinding Vertex Attribute objects (VAO's).
 * 
 * @see VertexAttributePostGL43
 * @see VertexAttributePreGL43
 */
public abstract class AbstractVertexAttribute
{
	/** Stores the handle of the AbstractVertexAttribute. */
	public final int id;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	// This will bind AbstractVertexAttribute
	protected AbstractVertexAttribute()
	{
		this.id = GL32.glGenVertexArrays();
		GL32.glBindVertexArray(this.id);
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
	
	public void bind() { GL32.glBindVertexArray(this.id); }
	public void unbind() { GL32.glBindVertexArray(0); }
	
	/** Always remember to always free your resources! */
	public void free() { GL32.glDeleteVertexArrays(this.id); }
	
	
	
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
