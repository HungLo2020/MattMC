package com.seibel.distanthorizons.core.render.glObject.texture;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public class DhFramebuffer implements IDhApiFramebuffer
{
	private final Int2IntMap attachments;
	private final int maxDrawBuffers;
	private final int maxColorAttachments;
	private boolean hasDepthAttachment;
	private int id;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhFramebuffer() 
	{
		this.id = VulkanicAPI.createFramebuffer(VulkanicAPI.getImmediateContext());

		this.attachments = new Int2IntArrayMap();
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		this.maxDrawBuffers = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}

	/** For internal use by Iris, do not remove. */
	public DhFramebuffer(int id) 
	{
		this.id = id;
		
		this.attachments = new Int2IntArrayMap();
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		this.maxDrawBuffers = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public void addDepthAttachment(int textureId, boolean isCombinedStencil) 
	{
		this.bind();
		
		int depthAttachment = isCombinedStencil ? VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT : VulkanicAPI.GL_DEPTH_ATTACHMENT;
		VulkanicAPI.framebufferTexture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER, depthAttachment, VulkanicAPI.GL_TEXTURE_2D, textureId, 0);
		
		this.hasDepthAttachment = true;
	}
	
	@Override
	public void addColorAttachment(int textureIndex, int textureId)
	{
		this.bind();
		
		VulkanicAPI.framebufferTexture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0 + textureIndex, VulkanicAPI.GL_TEXTURE_2D, textureId, 0);
		this.attachments.put(textureIndex, textureId);
	}

	public void noDrawBuffers()
	{
		this.bind(); 
		VulkanicAPI.drawBuffers(VulkanicAPI.getImmediateContext(), new int[]{VulkanicAPI.GL_NONE});
	}
	
	public void drawBuffers(int[] buffers)
	{
		int[] glBuffers = new int[buffers.length]; 
		int index = 0;
		
		if (buffers.length > this.maxDrawBuffers)
		{
			throw new IllegalArgumentException("Cannot write to more than " + this.maxDrawBuffers + " draw buffers on this GPU");
		}
		
		for (int buffer : buffers)
		{
			if (buffer >= this.maxColorAttachments)
			{
				throw new IllegalArgumentException("Only " + this.maxColorAttachments + " color attachments are supported on this GPU, but an attempt was made to write to a color attachment with index " + buffer);
			}
			
			glBuffers[index++] = VulkanicAPI.GL_COLOR_ATTACHMENT0 + buffer;
		}
		
		this.bind(); 
		VulkanicAPI.drawBuffers(VulkanicAPI.getImmediateContext(), new int[]{VulkanicAPI.GL_NONE});
	}
	
	public void readBuffer(int buffer)
	{
		this.bind();
		VulkanicAPI.setReadBuffer(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_COLOR_ATTACHMENT0 + buffer);
	}
	
	public int getColorAttachment(int index) { return this.attachments.get(index); }
	
	public boolean hasDepthAttachment() { return this.hasDepthAttachment; }
	
	@Override
	public void bind()
	{
		if (this.id == -1)
		{
			throw new IllegalStateException("Framebuffer does not exist!");
		} 
		VulkanicAPI.bindFramebuffer(VulkanicAPI.getImmediateContext(), this.id);
	}
	
	public void bindAsReadBuffer() { VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getImmediateContext(), this.id); }
	
	public void bindAsDrawBuffer() { VulkanicAPI.bindDrawFramebuffer(VulkanicAPI.getImmediateContext(), this.id); }
	
	@Override
	public void destroy()
	{
		VulkanicAPI.deleteFramebuffer(VulkanicAPI.getImmediateContext(), this.id); 
		this.id = -1;
	}
	
	@Override
	public int getStatus()
	{
		this.bind(); 
		int status = VulkanicAPI.checkFramebufferStatus(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER);
		return status;
	}
	
	@Override
	public int getId() { return this.id; }
	
	
	
	//=============//
	// API methods //
	//=============//
	
	public boolean overrideThisFrame() { return true; }
	
}
