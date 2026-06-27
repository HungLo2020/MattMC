package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.texture.DhFramebuffer;
import com.seibel.distanthorizons.core.render.renderer.shaders.DhFadeShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.FadeApplyShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.VanillaFadeShader;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.logging.DhLogger;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;
import net.vulkanic.VulkanicTextureTarget;

import java.nio.ByteBuffer;

/**
 * Handles fading MC and DH together via {@link VanillaFadeShader} and {@link FadeApplyShader}. <br><br>
 * 
 * {@link VanillaFadeShader} - draws the Fade to a texture. <br>
 * {@link FadeApplyShader} - draws the Fade texture to MC's FrameBuffer. <br>
 */
public class VanillaFadeRenderer
{
	public static VanillaFadeRenderer INSTANCE = new VanillaFadeRenderer();
	
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
	
	private VanillaFadeRenderer() { }
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		VanillaFadeShader.INSTANCE.init();
		FadeApplyShader.INSTANCE.init();
	}
	
	private void createFramebuffer(CommandContext ctx, int width, int height, int mcColorTextureId)
	{
		if (this.fadeFramebuffer != null)
		{
			this.fadeFramebuffer.destroy(ctx);
			this.fadeFramebuffer = null;
		}
		
		this.fadeFramebuffer = new DhFramebuffer();
		
		
		// Applying the fade texture is only needed if MC is drawing to their own frame buffer,
		// otherwise we can directly render to their texture
		if (MC_RENDER.mcRendersToFrameBuffer())
		{
			if (this.fadeTexture != -1)
			{
				VulkanicAPI.deleteTexture(ctx, this.fadeTexture);
				this.fadeTexture = -1;
			}
			
			this.fadeTexture = VulkanicAPI.createTexture2D(ctx);
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
			this.fadeFramebuffer.addColorAttachment(ctx, 0, this.fadeTexture);
		}
		else
		{
			this.fadeFramebuffer.addColorAttachment(ctx, 0, mcColorTextureId);
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks, IClientLevelWrapper level)
	{
		int depthTextureId = LodRenderer.INSTANCE.getActiveDepthTextureId();
		if (depthTextureId == -1)
		{
			// the renderer hasn't been set up yet
			// trying to render fading may cause GL errors
			return;
		}
		
		
		
		IProfilerWrapper profiler = MC_CLIENT.getProfiler();
		profiler.pop(); // get out of "terrain"
		profiler.push("DH-Vanilla Fade");
		
		
		CommandContext ctx = VulkanicAPI.getCommandContext();
		GLState mcState = new GLState(ctx);
		
		try
		{
			profiler.push("Vanilla Fade Generate");
			
			this.init();
			int mcColorTextureId = -1;
			if (!MC_RENDER.mcRendersToFrameBuffer())
			{
				mcColorTextureId = MC_RENDER.getColorTextureId();
				if (mcColorTextureId == -1)
				{
					return;
				}
			}
			
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
				this.createFramebuffer(ctx, width, height, mcColorTextureId);
			}

			if (this.fadeFramebuffer == null)
			{
				return;
			}

			if (MC_RENDER.mcRendersToFrameBuffer() && this.fadeTexture == -1)
			{
				return;
			}
			
			
			VanillaFadeShader.INSTANCE.frameBuffer = this.fadeFramebuffer;
			VanillaFadeShader.INSTANCE.setProjectionMatrix(mcModelViewMatrix, mcProjectionMatrix, partialTicks);
			VanillaFadeShader.INSTANCE.setLevelMaxHeight(level.getMaxHeight());
			VanillaFadeShader.INSTANCE.render(partialTicks);
			
			// Applying the fade texture is only needed if MC is drawing to their own frame buffer,
			// otherwise we can directly render to their texture
			if (MC_RENDER.mcRendersToFrameBuffer())
			{
				profiler.popPush("Vanilla Fade Apply");
				
				FadeApplyShader.INSTANCE.fadeTexture = this.fadeTexture;
				FadeApplyShader.INSTANCE.readFramebuffer = DhFadeShader.INSTANCE.frameBuffer;
				FadeApplyShader.INSTANCE.drawToMinecraftTarget = true;
				FadeApplyShader.INSTANCE.drawToLodTarget = false;
				FadeApplyShader.INSTANCE.render(partialTicks);
			}
			
			profiler.pop(); 
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected error during fade render, error: ["+e.getMessage()+"].", e);
		}
		finally
		{
			// make sure we always revert to MC's state to prevent GL state corruption
			// this is especially important on MC 1.16.5 or when other rendering mods are present
			mcState.restore(ctx);
		}
	}
	
	public void free()
	{
		VanillaFadeShader.INSTANCE.free();
		FadeApplyShader.INSTANCE.free();
	}
	
}
