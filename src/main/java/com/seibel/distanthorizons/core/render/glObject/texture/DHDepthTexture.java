package com.seibel.distanthorizons.core.render.glObject.texture;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

import java.nio.ByteBuffer;

public class DHDepthTexture
{
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
	
	
	private int id;
	public DHDepthTexture(int width, int height, EDhDepthBufferFormat format)
	{
		this.id = VulkanicAPI.glGenTextures();
		
		this.resize(width, height, format);
		
		VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_NEAREST);
		VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_NEAREST);
		VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_CLAMP_TO_EDGE);
		VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_T, VulkanicAPI.GL_CLAMP_TO_EDGE);
		
		// disable mip-mapping since DH is just going to draw straight to the screen
		VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_BASE_LEVEL, 0);
		VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAX_LEVEL, 0);
		
		VulkanicAPI.bindTexture(CTX, VulkanicAPI.GL_TEXTURE_2D, 0);
	}
	
	// For internal use by Iris for copying data. Do not use this in DH.
	public DHDepthTexture(int id) { this.id = id; }
	
	public void resize(int width, int height, EDhDepthBufferFormat format)
	{
		VulkanicAPI.bindTexture(CTX, VulkanicAPI.GL_TEXTURE_2D, this.getTextureId());
		VulkanicAPI.transferTexture2DImage(CTX, VulkanicAPI.GL_TEXTURE_2D, 0, format.getGlInternalFormat(), width, height, 0,
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
		GLMC.glDeleteTextures(this.getTextureId());
		this.id = -1;
	}
	
	
	
}
