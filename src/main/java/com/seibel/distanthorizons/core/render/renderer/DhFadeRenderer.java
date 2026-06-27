package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.texture.DhFramebuffer;
import com.seibel.distanthorizons.core.render.renderer.shaders.DhFadeShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.FadeApplyShader;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;
import net.vulkanic.VulkanicTextureTarget;

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
	
	
	private boolean init = false;
	
	private int width = -1;
	private int height = -1;
	private DhFramebuffer fadeFramebuffer;
	
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
	
	private void createFramebuffer(CommandContext ctx, int width, int height)
	{
		if (this.fadeFramebuffer != null)
		{
			this.fadeFramebuffer.destroy(ctx);
			this.fadeFramebuffer = null;
		}
		
		this.fadeFramebuffer = new DhFramebuffer();
		
		
		if (this.fadeTexture != -1)
		{
			VulkanicAPI.deleteTexture(ctx, this.fadeTexture);
			this.fadeTexture = -1;
		}
		
		this.fadeTexture = VulkanicAPI.createTexture2D(ctx);
		{
			DhTextureState.bindTexture2D(this.fadeTexture);
			if (VulkanicAPI.isVulkanBackendSelected())
			{
				VulkanicAPI.uploadTexture2D(ctx, 0, VulkanicAPI.GL_RGBA8, width, height, 0, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_BYTE, (ByteBuffer) null);
			}
			else
			{
				VulkanicAPI.uploadTexture2D(ctx, 0, VulkanicAPI.GL_RGBA16, width, height, 0, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4, (ByteBuffer) null);
			}
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.LINEAR);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.LINEAR);
			
			// disable mip-mapping since DH is just going to draw straight to the screen
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.BASE_LEVEL, 0);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAX_LEVEL, 0);
		}
		
		this.fadeFramebuffer.addColorAttachment(ctx, 0, this.fadeTexture);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks, IProfilerWrapper profiler)
	{
		try
		{
			CommandContext ctx = VulkanicAPI.getCommandContext();
			profiler.push("Fade Generate");
			
			this.init();
			
			// resize the framebuffer if necessary
			int width = MC_RENDER.getTargetFramebufferViewportWidth();
			int height = MC_RENDER.getTargetFramebufferViewportHeight();
			if (width <= 0 || height <= 0)
			{
				this.width = -1;
				this.height = -1;
				return;
			}

			if (this.width != width || this.height != height)
			{
				this.width = width;
				this.height = height;
				this.createFramebuffer(ctx, width, height);
			}

			if (this.fadeFramebuffer == null || this.fadeTexture == -1)
			{
				return;
			}
			
			
			DhFadeShader.INSTANCE.frameBuffer = this.fadeFramebuffer;
			DhFadeShader.INSTANCE.setProjectionMatrix(mcModelViewMatrix, mcProjectionMatrix, partialTicks);
			DhFadeShader.INSTANCE.render(partialTicks);
			
			// restored so we can write the fade texture to the main frame buffer
			//mcState.restore();
			
			profiler.popPush("Fade Apply");
			
			FadeApplyShader.INSTANCE.fadeTexture = this.fadeTexture;
			FadeApplyShader.INSTANCE.readFramebuffer = DhFadeShader.INSTANCE.frameBuffer;
			FadeApplyShader.INSTANCE.drawToMinecraftTarget = false;
			FadeApplyShader.INSTANCE.drawToLodTarget = true;
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
