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
		CommandContext ctx = VulkanicAPI.getCommandContext();
		this.id = VulkanicAPI.createFramebuffer(ctx);

		this.attachments = new Int2IntArrayMap();
		this.maxDrawBuffers = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}

	/** For internal use by Iris, do not remove. */
	public DhFramebuffer(int id) 
	{
		this.id = id;
		
		this.attachments = new Int2IntArrayMap();
		CommandContext ctx = VulkanicAPI.getCommandContext();
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
		this.addDepthAttachment(VulkanicAPI.getCommandContext(), textureId, isCombinedStencil);
	}

	public void addDepthAttachment(CommandContext ctx, int textureId, boolean isCombinedStencil)
	{
		this.bind(ctx);
		
		int depthAttachment = isCombinedStencil ? VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT : VulkanicAPI.GL_DEPTH_ATTACHMENT;
		VulkanicAPI.framebufferTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, depthAttachment, VulkanicAPI.GL_TEXTURE_2D, textureId, 0);
		
		this.hasDepthAttachment = true;
	}
	
	@Override
	public void addColorAttachment(int textureIndex, int textureId)
	{
		this.addColorAttachment(VulkanicAPI.getCommandContext(), textureIndex, textureId);
	}

	public void addColorAttachment(CommandContext ctx, int textureIndex, int textureId)
	{
		this.bind(ctx);
		
		VulkanicAPI.framebufferTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0 + textureIndex, VulkanicAPI.GL_TEXTURE_2D, textureId, 0);
		this.attachments.put(textureIndex, textureId);
	}

	public void noDrawBuffers()
	{
		this.noDrawBuffers(VulkanicAPI.getCommandContext());
	}

	public void noDrawBuffers(CommandContext ctx)
	{
		this.bind(ctx); 
		VulkanicAPI.drawBuffers(ctx, new int[]{VulkanicAPI.GL_NONE});
	}
	
	public void drawBuffers(int[] buffers)
	{
		this.drawBuffers(VulkanicAPI.getCommandContext(), buffers);
	}

	public void drawBuffers(CommandContext ctx, int[] buffers)
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
		
		this.bind(ctx); 
		VulkanicAPI.drawBuffers(ctx, new int[]{VulkanicAPI.GL_NONE});
	}
	
	public void readBuffer(int buffer)
	{
		this.readBuffer(VulkanicAPI.getCommandContext(), buffer);
	}

	public void readBuffer(CommandContext ctx, int buffer)
	{
		this.bind(ctx);
		VulkanicAPI.setReadBuffer(ctx, VulkanicAPI.GL_COLOR_ATTACHMENT0 + buffer);
	}
	
	public int getColorAttachment(int index) { return this.attachments.get(index); }
	
	public boolean hasDepthAttachment() { return this.hasDepthAttachment; }
	
	@Override
	public void bind()
	{
		this.bind(VulkanicAPI.getCommandContext());
	}

	public void bind(CommandContext ctx)
	{
		if (this.id == -1)
		{
			throw new IllegalStateException("Framebuffer does not exist!");
		} 
		VulkanicAPI.bindFramebuffer(ctx, this.id);
	}
	
	public void bindAsReadBuffer() { this.bindAsReadBuffer(VulkanicAPI.getCommandContext()); }

	public void bindAsReadBuffer(CommandContext ctx) { VulkanicAPI.bindReadFramebuffer(ctx, this.id); }
	
	public void bindAsDrawBuffer() { this.bindAsDrawBuffer(VulkanicAPI.getCommandContext()); }

	public void bindAsDrawBuffer(CommandContext ctx) { VulkanicAPI.bindDrawFramebuffer(ctx, this.id); }
	
	@Override
	public void destroy()
	{
		this.destroy(VulkanicAPI.getCommandContext());
	}

	public void destroy(CommandContext ctx)
	{
		VulkanicAPI.deleteFramebuffer(ctx, this.id); 
		this.id = -1;
	}
	
	@Override
	public int getStatus()
	{
		return this.getStatus(VulkanicAPI.getCommandContext());
	}

	public int getStatus(CommandContext ctx)
	{
		this.bind(ctx); 
		int status = VulkanicAPI.checkFramebufferStatus(ctx, VulkanicAPI.GL_FRAMEBUFFER);
		return status;
	}
	
	@Override
	public int getId() { return this.id; }
	
	
	
	//=============//
	// API methods //
	//=============//
	
	public boolean overrideThisFrame() { return true; }
	
}
