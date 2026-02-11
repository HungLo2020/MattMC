package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.renderer.shaders.FogApplyShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.FogShader;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.CommandContext;

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
	private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
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
	
	private void createFramebuffer(int width, int height)
	{
		if (this.fogFramebuffer != -1)
		{
			VulkanicAPI.destroyFramebufferObject(this.fogFramebuffer);
			this.fogFramebuffer = -1;
		}
		
		if (this.fogTexture != -1)
		{
			GLMC.glDeleteTextures(this.fogTexture);
			this.fogTexture = -1;
		}
		
		this.fogFramebuffer = VulkanicAPI.generateFramebufferObject();
		GLMC.glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, this.fogFramebuffer);
		
		this.fogTexture = GLMC.glGenTextures();
		{
			GLMC.glBindTexture(this.fogTexture);
			VulkanicAPI.transferTexture2DImage(CTX, VulkanicAPI.GL_TEXTURE_2D, 0, VulkanicAPI.GL_RGBA16, width, height, 0, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_SHORT_4_4_4_4, (ByteBuffer) null);
			VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_LINEAR);
			VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_LINEAR);
			VulkanicAPI.glFramebufferTexture2D(CTX, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D, this.fogTexture, 0);
			
			// disable mip-mapping since DH is just going to draw straight to the screen
			VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_BASE_LEVEL, 0);
			VulkanicAPI.configureTextureParameter(CTX, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAX_LEVEL, 0);
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f modelViewProjectionMatrix, float partialTicks)
	{
		// needed to preserve GL state - MC may not manually set each GL state before the next rendering step
		GLState state = new GLState();
		
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
		
		FogShader.INSTANCE.frameBuffer = this.fogFramebuffer;
		FogShader.INSTANCE.setProjectionMatrix(modelViewProjectionMatrix);
		FogShader.INSTANCE.render(partialTicks);
		
		FogApplyShader.INSTANCE.fogTexture = this.fogTexture;
		FogApplyShader.INSTANCE.render(partialTicks);
		
		state.restore();
	}
	
	public void free()
	{
		FogShader.INSTANCE.free();
		FogApplyShader.INSTANCE.free();
	}
	
}
