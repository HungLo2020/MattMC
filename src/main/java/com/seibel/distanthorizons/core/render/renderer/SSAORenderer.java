package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.renderer.shaders.SSAOApplyShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.SSAOShader;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureTarget;

import java.nio.ByteBuffer;

/**
 * Handles adding SSAO via {@link SSAOShader} and {@link SSAOApplyShader}. <br><br>
 * 
 * {@link SSAOShader} - draws the SSAO to a texture. <br>
 * {@link SSAOApplyShader} - draws the SSAO texture to DH's FrameBuffer. <br>
 */
public class SSAORenderer
{
	public static SSAORenderer INSTANCE = new SSAORenderer();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	private boolean init = false;
	
	private int width = -1;
	private int height = -1;
	private int ssaoFramebuffer = -1;
	
	private int ssaoTexture = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private SSAORenderer() { }
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		SSAOShader.INSTANCE.init();
		SSAOApplyShader.INSTANCE.init();
	}
	
	private void createFramebuffer(CommandContext ctx, int width, int height)
	{
		if (this.ssaoFramebuffer != -1)
		{
			VulkanicAPI.deleteFramebuffer(ctx, this.ssaoFramebuffer);
			this.ssaoFramebuffer = -1;
		}
		
		if (this.ssaoTexture != -1)
		{
			VulkanicAPI.deleteTexture(ctx, this.ssaoTexture);
			this.ssaoTexture = -1;
		}
		
		this.ssaoFramebuffer = VulkanicAPI.createFramebuffer(ctx);
		VulkanicAPI.bindFramebuffer(ctx, this.ssaoFramebuffer);
		
		this.ssaoTexture = VulkanicAPI.createTexture2D(ctx);
		{
			DhTextureState.bindTexture2D(this.ssaoTexture);
			VulkanicAPI.uploadTexture2D(ctx, VulkanicAPI.GL_TEXTURE_2D, 0, VulkanicAPI.GL_R16F, width, height, 0, VulkanicAPI.GL_RED, VulkanicAPI.GL_HALF_FLOAT, (ByteBuffer) null);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MIN_FILTER, VulkanicAPI.GL_LINEAR);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAG_FILTER, VulkanicAPI.GL_LINEAR);
			
			// disable mip-mapping since DH is just going to draw straight to the screen
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.BASE_LEVEL, 0);
			VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAX_LEVEL, 0);
		}
		
		VulkanicAPI.framebufferTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D, this.ssaoTexture, 0);
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f projectionMatrix, float partialTicks)
	{
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
		
		SSAOShader.INSTANCE.frameBuffer = this.ssaoFramebuffer;
		SSAOShader.INSTANCE.setProjectionMatrix(projectionMatrix);
		SSAOShader.INSTANCE.render(partialTicks);
		
		SSAOApplyShader.INSTANCE.ssaoTexture = this.ssaoTexture;
		SSAOApplyShader.INSTANCE.render(partialTicks);
		
		state.restore(ctx);
	}
	
	public void free()
	{
		SSAOShader.INSTANCE.free();
		SSAOApplyShader.INSTANCE.free();
	}
	
}
