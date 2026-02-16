package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.renderer.shaders.DhFadeShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.FadeApplyShader;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.CommandContext;

import java.nio.ByteBuffer;

/**
 * Handles fading MC and DH together via {@link DhFadeShader} and {@link FadeApplyShader}. <br><br>
 * 
 * {@link DhFadeShader} - draws the Fade to a texture. <br>
 * {@link FadeApplyShader} - draws the Fade texture to DH's framebuffer. <br>
 */
public class DhFadeRenderer
{
	
	public static DhFadeRenderer INSTANCE = new DhFadeRenderer();
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	private boolean init = false;
	
	private int width = -1;
	private int height = -1;
	private int fadeFramebuffer = -1;
	
	private int fadeTexture = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private DhFadeRenderer() { }
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		DhFadeShader.INSTANCE.init();
		FadeApplyShader.INSTANCE.init();
	}
	
	private void createFramebuffer(int width, int height)
	{
		if (this.fadeFramebuffer != -1)
		{
			VulkanicAPI.destroyFramebufferObject(this.fadeFramebuffer);
			this.fadeFramebuffer = -1;
		}
		
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		this.fadeFramebuffer = VulkanicAPI.createFramebufferObject(ctx);
		GLMC.glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, this.fadeFramebuffer);
		
		
		if (this.fadeTexture != -1)
		{
			GLMC.glDeleteTextures(this.fadeTexture);
			this.fadeTexture = -1;
		}
		
		this.fadeTexture = VulkanicAPI.createTexture2D(net.vulkanic.VulkanicAPI.getImmediateContext());
		{
			GLMC.glBindTexture(this.fadeTexture);
			VulkanicAPI.glTexImage2D(VulkanicAPI.GL_TEXTURE_2D, 0, VulkanicAPI.GL_RGBA16, width, height, 0, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4, (ByteBuffer) null);
			VulkanicAPI.glTexParameteri(VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_LINEAR);
			VulkanicAPI.glTexParameteri(VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_LINEAR);
			
			// disable mip-mapping since DH is just going to draw straight to the screen
			VulkanicAPI.glTexParameteri(VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_BASE_LEVEL, 0);
			VulkanicAPI.glTexParameteri(VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAX_LEVEL, 0);
		}
		
		VulkanicAPI.glFramebufferTexture2D(VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D, this.fadeTexture, 0);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks, IProfilerWrapper profiler)
	{
		try
		{
			profiler.push("Fade Generate");
			
			this.init();
			
			// resize the framebuffer if necessary
			int width = MC_RENDER.getTargetFramebufferViewportWidth();
			int height = MC_RENDER.getTargetFramebufferViewportHeight();
			if (this.width != width || this.height != height)
			{
				this.width = width;
				this.height = height;
				this.createFramebuffer(width, height);
			}
			
			
			DhFadeShader.INSTANCE.frameBuffer = this.fadeFramebuffer;
			DhFadeShader.INSTANCE.setProjectionMatrix(mcModelViewMatrix, mcProjectionMatrix, partialTicks);
			DhFadeShader.INSTANCE.render(partialTicks);
			
			// restored so we can write the fade texture to the main frame buffer
			//mcState.restore();
			
			profiler.popPush("Fade Apply");
			
			FadeApplyShader.INSTANCE.fadeTexture = this.fadeTexture;
			FadeApplyShader.INSTANCE.readFramebuffer = DhFadeShader.INSTANCE.frameBuffer;
			FadeApplyShader.INSTANCE.drawFramebuffer = LodRenderer.INSTANCE.getActiveFramebufferId();
			FadeApplyShader.INSTANCE.render(partialTicks);
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected error during fade render, error: ["+e.getMessage()+"].", e);
		}
		finally
		{
			profiler.pop();
		}
	}
	
	
	
}
