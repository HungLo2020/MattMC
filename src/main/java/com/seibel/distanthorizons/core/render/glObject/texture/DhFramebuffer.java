package com.seibel.distanthorizons.core.render.glObject.texture;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIntegerQuery;
import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.VulkanicResourceUsage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class DhFramebuffer implements IDhApiFramebuffer
{
	private final Int2IntMap attachments;
	private final int maxDrawBuffers;
	private final int maxColorAttachments;
	private boolean hasDepthAttachment;
	private int depthAttachmentTextureId;
	private int[] drawBuffers;
	private boolean drawsToNoColorBuffers;
	private int id;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhFramebuffer() 
	{
		CommandContext ctx = VulkanicAPI.getCommandContext();
		this.id = VulkanicAPI.createFramebuffer(ctx);

		this.attachments = new Int2IntArrayMap();
		this.maxDrawBuffers = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.MAX_DRAW_BUFFERS);
		this.maxColorAttachments = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
		this.depthAttachmentTextureId = 0;
		this.drawBuffers = new int[0];
		this.drawsToNoColorBuffers = false;
	}

	/** For internal use by Iris, do not remove. */
	public DhFramebuffer(int id) 
	{
		this.id = id;
		
		this.attachments = new Int2IntArrayMap();
		CommandContext ctx = VulkanicAPI.getCommandContext();
		this.maxDrawBuffers = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.MAX_DRAW_BUFFERS);
		this.maxColorAttachments = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
		this.depthAttachmentTextureId = 0;
		this.drawBuffers = new int[0];
		this.drawsToNoColorBuffers = false;
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
		VulkanicAPI.framebufferTexture2D(ctx, depthAttachment, textureId, 0);
		
		this.hasDepthAttachment = true;
		this.depthAttachmentTextureId = textureId;
	}
	
	@Override
	public void addColorAttachment(int textureIndex, int textureId)
	{
		this.addColorAttachment(VulkanicAPI.getCommandContext(), textureIndex, textureId);
	}

	public void addColorAttachment(CommandContext ctx, int textureIndex, int textureId)
	{
		this.bind(ctx);
		
		VulkanicAPI.framebufferColorAttachmentTexture2D(ctx, textureIndex, textureId, 0);
		if (this.attachments.isEmpty())
		{
			VulkanicAPI.drawBuffers(ctx, new int[]{VulkanicAPI.colorAttachment(textureIndex)});
			this.drawBuffers = new int[]{textureIndex};
		}
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
		this.drawBuffers = new int[0];
		this.drawsToNoColorBuffers = true;
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
			
			glBuffers[index++] = VulkanicAPI.colorAttachment(buffer);
		}
		
		this.bind(ctx); 
		VulkanicAPI.drawBuffers(ctx, glBuffers);
		this.drawBuffers = Arrays.copyOf(buffers, buffers.length);
		this.drawsToNoColorBuffers = false;
	}
	
	public void readBuffer(int buffer)
	{
		this.readBuffer(VulkanicAPI.getCommandContext(), buffer);
	}

	public void readBuffer(CommandContext ctx, int buffer)
	{
		this.bind(ctx);
		VulkanicAPI.setReadBufferColorAttachment(ctx, buffer);
	}
	
	public int getColorAttachment(int index) { return this.attachments.get(index); }
	
	public boolean hasDepthAttachment() { return this.hasDepthAttachment; }

	public boolean canCreateRenderTargetDescriptor()
	{
		int[] activeDrawBuffers = this.drawsToNoColorBuffers ? new int[0] : (this.drawBuffers.length > 0 ? this.drawBuffers : new int[]{0});
		for (int drawBuffer : activeDrawBuffers)
		{
			if (this.attachments.get(drawBuffer) > 0)
			{
				return true;
			}
		}

		return this.hasDepthAttachment && this.depthAttachmentTextureId > 0;
	}

	public VulkanicRenderTargetDescriptor createRenderTargetDescriptor(Supplier<String> label)
	{
		return this.createRenderTargetDescriptor(label, -1, -1);
	}

	public VulkanicRenderTargetDescriptor createRenderTargetDescriptor(Supplier<String> label, int width, int height)
	{
		int[] activeDrawBuffers = this.drawsToNoColorBuffers ? new int[0] : (this.drawBuffers.length > 0 ? this.drawBuffers : new int[]{0});
		List<VulkanicRenderTargetDescriptor.ColorAttachment> colorAttachments = new ArrayList<>(activeDrawBuffers.length);
		for (int drawBuffer : activeDrawBuffers)
		{
			int textureId = this.attachments.get(drawBuffer);
			if (textureId <= 0)
			{
				throw new IllegalStateException("DH framebuffer draw buffer " + drawBuffer + " has no color attachment");
			}
			colorAttachments.add(colorAttachmentDescriptor(textureId));
		}

		VulkanicRenderTargetDescriptor.DepthAttachment depthAttachment = null;
		if (this.hasDepthAttachment)
		{
			if (this.depthAttachmentTextureId <= 0)
			{
				throw new IllegalStateException("DH framebuffer has depth enabled but no depth texture");
			}
			depthAttachment = depthAttachmentDescriptor(this.depthAttachmentTextureId);
		}

		return new VulkanicRenderTargetDescriptor(label, colorAttachments, depthAttachment, width, height);
	}

	private static VulkanicRenderTargetDescriptor.ColorAttachment colorAttachmentDescriptor(int textureId)
	{
		return new VulkanicRenderTargetDescriptor.ColorAttachment(
				textureId,
				VulkanicRenderPassDescriptor.LoadOp.LOAD,
				VulkanicRenderPassDescriptor.StoreOp.STORE,
				OptionalInt.empty(),
				VulkanicResourceUsage.SAMPLED_READ,
				VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE,
				VulkanicResourceUsage.SAMPLED_READ);
	}

	private static VulkanicRenderTargetDescriptor.DepthAttachment depthAttachmentDescriptor(int textureId)
	{
		return new VulkanicRenderTargetDescriptor.DepthAttachment(
				textureId,
				VulkanicRenderPassDescriptor.LoadOp.LOAD,
				VulkanicRenderPassDescriptor.StoreOp.STORE,
				OptionalDouble.empty(),
				VulkanicResourceUsage.SAMPLED_READ,
				VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE,
				VulkanicResourceUsage.SAMPLED_READ);
	}
	
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
		int status = VulkanicAPI.checkFramebufferStatus(ctx);
		return status;
	}
	
	@Override
	public int getId() { return this.id; }
	
	
	
	//=============//
	// API methods //
	//=============//
	
	public boolean overrideThisFrame() { return true; }
	
}
