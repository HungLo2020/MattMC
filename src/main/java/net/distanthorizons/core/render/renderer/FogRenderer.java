package net.distanthorizons.core.render.renderer;

import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.render.glObject.GLState;
import net.distanthorizons.core.render.renderer.shaders.FogApplyShader;
import net.distanthorizons.core.render.renderer.shaders.FogShader;
import net.distanthorizons.core.util.math.Mat4f;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43C;

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
			GL32.glDeleteFramebuffers(this.fogFramebuffer);
			this.fogFramebuffer = -1;
		}
		
		if (this.fogTexture != -1)
		{
			GLMC.glDeleteTextures(this.fogTexture);
			this.fogTexture = -1;
		}
		
		this.fogFramebuffer = GL32.glGenFramebuffers();
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, this.fogFramebuffer);
		
		this.fogTexture = GLMC.glGenTextures();
		{
			GLMC.glBindTexture(this.fogTexture);
			GL32.glTexImage2D(GL32.GL_TEXTURE_2D, 0, GL32.GL_RGBA16, width, height, 0, GL32.GL_RGBA, GL32.GL_UNSIGNED_SHORT_4_4_4_4, (ByteBuffer) null);
			GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MIN_FILTER, GL32.GL_LINEAR);
			GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MAG_FILTER, GL32.GL_LINEAR);
			GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, GL32.GL_TEXTURE_2D, this.fogTexture, 0);
			
			// disable mip-mapping since DH is just going to draw straight to the screen
			GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_BASE_LEVEL, 0);
			GL43C.glTexParameteri(GL43C.GL_TEXTURE_2D, GL43C.GL_TEXTURE_MAX_LEVEL, 0);
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
