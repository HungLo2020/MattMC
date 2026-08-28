package com.seibel.distanthorizons.core.render.glObject.texture;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;
import net.vulkanic.VulkanicTextureTarget;

import java.nio.ByteBuffer;

public class DHDepthTexture
{

	private int id;
	public DHDepthTexture(int width, int height, EDhDepthBufferFormat format)
	{
		this(VulkanicAPI.getCommandContext(), width, height, format);
	}

	public DHDepthTexture(CommandContext ctx, int width, int height, EDhDepthBufferFormat format)
	{
		rejectRustWholeFrameJavaTexture();
		this.id = VulkanicAPI.createTexture2D(ctx);
		
		this.resize(ctx, width, height, format);
		
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.NEAREST);
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.NEAREST);
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.WRAP_S, VulkanicTextureParameterValue.CLAMP_TO_EDGE);
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.WRAP_T, VulkanicTextureParameterValue.CLAMP_TO_EDGE);
		
		// disable mip-mapping since DH is just going to draw straight to the screen
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.BASE_LEVEL, 0);
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAX_LEVEL, 0);
		
		VulkanicAPI.bindTexture(ctx, VulkanicTextureTarget.TEXTURE_2D, 0);
	}
	
	// For internal use by Iris for copying data. Do not use this in DH.
	public DHDepthTexture(int id) {
		rejectRustWholeFrameJavaTexture();
		this.id = id;
	}

	private static void rejectRustWholeFrameJavaTexture()
	{
		if (VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
		{
			throw new IllegalStateException(
				"Java Distant Horizons depth textures are unavailable while Rust owns whole-frame presentation");
		}
	}
	
	public void resize(int width, int height, EDhDepthBufferFormat format)
	{
		this.resize(VulkanicAPI.getCommandContext(), width, height, format);
	}

	public void resize(CommandContext ctx, int width, int height, EDhDepthBufferFormat format)
	{
		VulkanicAPI.bindTexture(ctx, VulkanicTextureTarget.TEXTURE_2D, this.getTextureId());
		VulkanicAPI.uploadTexture2D(ctx, 0, format.getGlInternalFormat(), width, height, 0,
				format.getGlType(), format.getGlFormat(), (ByteBuffer) null);
	}
	
	public int getTextureId()
	{
		if (this.id == -1)
		{
			throw new IllegalStateException("Depth texture does not exist!");
		}
		
		return this.id;
	}
	
	public void destroy()
	{
		this.destroy(VulkanicAPI.getCommandContext());
	}

	public void destroy(CommandContext ctx)
	{
		int textureId = this.getTextureId();
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !VulkanicAPI.isVulkanBackendSelected()) {
			VulkanicAPI.deleteTexture(ctx, textureId);
		}
		this.id = -1;
	}
	
	
	
}
