package com.seibel.distanthorizons.core.render.glObject.vertexAttribute;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

/**
 * In OpenGL 4.3 and later, Vertex Attribute got a make-over.
 * Now it provides support for buffer binding points natively.
 * This means that setting up the VAO is just use ONE native call when
 * binding to a buffer. <br><br>
 * 
 * Since I no longer need to implement binding points, I also no
 * longer needs to keep track of Pointers.
 */
public final class VertexAttributePostGL43 extends AbstractVertexAttribute
{
	private static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererGLEventToFile)
			.chatLevelConfig(Config.Common.Logging.logRendererGLEventToChat)
			.build();
	
	
	int numberOfBindingPoints = 0;
	int strideSize = 0;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** This will bind the {@link AbstractVertexAttribute} */
	public VertexAttributePostGL43()
	{
		this(VulkanicAPI.getCommandContext());
	}

	public VertexAttributePostGL43(CommandContext ctx)
	{
		super(ctx); // also bind AbstractVertexAttribute
	}
	
	
	
	//=========//
	// binding //
	//=========//
	
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	@Override
	public void bindBufferToAllBindingPoints(int buffer)
	{
		CommandContext ctx = VulkanicAPI.getCommandContext();
		for (int i = 0; i < this.numberOfBindingPoints; i++)
		{
			VulkanicAPI.bindVertexBuffer(ctx, i, buffer, 0, this.strideSize);
		}
	}
	
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	@Override
	public void bindBufferToBindingPoint(int buffer, int bindingPoint)
	{
		VulkanicAPI.bindVertexBuffer(VulkanicAPI.getCommandContext(), bindingPoint, buffer, 0, this.strideSize);
	}
	
	
	
	//===========//
	// unbinding //
	//===========//
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void unbindBuffersFromAllBindingPoint()
	{
		CommandContext ctx = VulkanicAPI.getCommandContext();
		for (int i = 0; i < this.numberOfBindingPoints; i++)
		{
			VulkanicAPI.bindVertexBuffer(ctx, i, 0, 0, 0);
		}
	}
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void unbindBuffersFromBindingPoint(int bindingPoint)
	{
		VulkanicAPI.bindVertexBuffer(VulkanicAPI.getCommandContext(), bindingPoint, 0, 0, 0);
	}
	
	
	
	//==========================//
	// manual attribute setting //
	//==========================//
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void setVertexAttribute(int bindingPoint, int attributeIndex, VertexPointer attribute)
	{
		CommandContext ctx = VulkanicAPI.getCommandContext();
		if (attribute.useInteger)
		{
			VulkanicAPI.setVertexAttribIFormat(ctx, attributeIndex, attribute.elementCount, attribute.glType, this.strideSize);
		}
		else
		{
			VulkanicAPI.setVertexAttribFormat(ctx, attributeIndex, attribute.elementCount, attribute.glType,
					attribute.normalized, this.strideSize); // strideSize used as relative offset here
		}
		
		this.strideSize += attribute.byteSize;
		if (this.numberOfBindingPoints <= bindingPoint)
		{
			this.numberOfBindingPoints = bindingPoint + 1;
		}
		VulkanicAPI.setVertexAttribBinding(ctx, attributeIndex, bindingPoint);
		VulkanicAPI.enableVertexAttribArray(ctx, attributeIndex);
	}
	
	
	
	//============//
	// validation //
	//============//
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void completeAndCheck(int expectedStrideSize)
	{
		if (this.strideSize != expectedStrideSize)
		{
			LOGGER.error("Vertex Attribute calculated stride size " + this.strideSize +
					" does not match the provided expected stride size " + expectedStrideSize + "!");
			throw new IllegalArgumentException("Vertex Attribute Incorrect Format");
		}
		
		LOGGER.info("Vertex Attribute (GL43+) completed. It contains " + this.numberOfBindingPoints
				+ " binding points and a stride size of " + this.strideSize);
	}
	
}
