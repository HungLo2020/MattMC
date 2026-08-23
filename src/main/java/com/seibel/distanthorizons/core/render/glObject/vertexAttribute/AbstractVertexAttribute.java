package com.seibel.distanthorizons.core.render.glObject.vertexAttribute;

import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

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
		this(VulkanicAPI.getCommandContext());
	}

	protected AbstractVertexAttribute(CommandContext ctx)
	{
		if (VulkanicAPI.isVulkanBackendInitializedAndSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
		{
			throw new IllegalStateException(
				"Java Distant Horizons vertex arrays are unavailable while Rust owns whole-frame presentation");
		}
		this.id = VulkanicAPI.createVertexArray(ctx);
		VulkanicAPI.bindVertexArray(ctx, this.id);
	}
	
	public static AbstractVertexAttribute create()
	{
		return create(VulkanicAPI.getCommandContext());
	}

	public static AbstractVertexAttribute create(CommandContext ctx)
	{
		if (GLProxy.getInstance().vertexAttributeBufferBindingSupported)
		{
			return new VertexAttributePostGL43(ctx);
		}
		else
		{
			return new VertexAttributePreGL43(ctx);
		}
	}
	
	
	
	//=========//
	// binding //
	//=========//
	
	public void bind() { this.bind(VulkanicAPI.getCommandContext()); }
	public void bind(CommandContext ctx) { VulkanicAPI.bindVertexArray(ctx, this.id); }
	public void unbind() { this.unbind(VulkanicAPI.getCommandContext()); }
	public void unbind(CommandContext ctx) { VulkanicAPI.bindVertexArray(ctx, 0); }
	
	/** Always remember to always free your resources! */
	public void free() { this.free(VulkanicAPI.getCommandContext()); }
	public void free(CommandContext ctx) { VulkanicAPI.deleteVertexArrays(ctx, this.id); }
	
	
	
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
