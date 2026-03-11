package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.renderer.shaders.FogApplyShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.FogShader;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;
import net.vulkanic.VulkanicTextureTarget;

import java.nio.ByteBuffer;

/**
 * Handles adding SSAO via {@link FogShader} and {@link FogApplyShader}. <br><br>
 * 
 * {@link FogShader} - draws the Fog to a texture. <br>
 * {@link FogApplyShader} - draws the Fog texture to DH's FrameBuffer. <br>
 */
public class FogRenderer
{
	public static FogRenderer INSTANCE = new FogRenderer();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	private boolean init = false;
	
	private int width = -1;
	private int height = -1;
	private int fogFramebuffer = -1;
	
	private int fogTexture = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private FogRenderer() { }
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		FogShader.INSTANCE.init();
		FogApplyShader.INSTANCE.init();
	}
	
	private void createFramebuffer(CommandContext ctx, int width, int height)
	{
		if (this.fogFramebuffer != -1)
		{
			VulkanicAPI.deleteFramebuffer(ctx, this.fogFramebuffer);
			this.fogFramebuffer = -1;
		}
		
		if (this.fogTexture != -1)
		{
			VulkanicAPI.deleteTexture(ctx, this.fogTexture);
			this.fogTexture = -1;
		}
		
		this.fogFramebuffer = VulkanicAPI.createFramebuffer(ctx);
		VulkanicAPI.bindFramebuffer(ctx, this.fogFramebuffer);
		
		this.fogTexture = VulkanicAPI.createTexture2D(ctx);
		{
			DhTextureState.bindTexture2D(this.fogTexture);
			VulkanicAPI.uploadTexture2D(ctx, 0, VulkanicAPI.GL_RGBA16, width, height, 0, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4, (ByteBuffer) null);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.LINEAR);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.LINEAR);
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, this.fogTexture, 0);
			
			// disable mip-mapping since DH is just going to draw straight to the screen
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.BASE_LEVEL, 0);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAX_LEVEL, 0);
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f modelViewProjectionMatrix, float partialTicks)
	{
		// needed to preserve GL state - MC may not manually set each GL state before the next rendering step
		CommandContext ctx = VulkanicAPI.getCommandContext();
		GLState state = new GLState(ctx);
		
		this.init();
		
		// resize the framebuffer if necessary
		int width = MC_RENDER.getTargetFramebufferViewportWidth();
		int height = MC_RENDER.getTargetFramebufferViewportHeight();
		if (this.width != width || this.height != height)
		{
			this.width = width;
			this.height = height;
			this.createFramebuffer(ctx, width, height);
		}
		
		FogShader.INSTANCE.frameBuffer = this.fogFramebuffer;
		FogShader.INSTANCE.setProjectionMatrix(modelViewProjectionMatrix);
		FogShader.INSTANCE.render(partialTicks);
		
		FogApplyShader.INSTANCE.fogTexture = this.fogTexture;
		FogApplyShader.INSTANCE.render(partialTicks);
		
		state.restore(ctx);
	}
	
	public void free()
	{
		FogShader.INSTANCE.free();
		FogApplyShader.INSTANCE.free();
	}
	
}
