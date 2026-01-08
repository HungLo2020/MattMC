package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.VanillaFadeRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;

/**
 * Draws the Fade texture onto Minecraft's FrameBuffer. <br><br>
 * 
 * See Also: <br>
 * {@link VanillaFadeRenderer} - Parent to this shader. <br>
 * {@link VanillaFadeShader} - draws the Fade texture. <br>
 */
public class FadeApplyShader extends AbstractShaderRenderer
{
	public static FadeApplyShader INSTANCE = new FadeApplyShader();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	
	public int fadeTexture;
	
	public int readFramebuffer;
	public int drawFramebuffer;
	
	// uniforms
	public int uFadeColorTextureUniform = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/fade/apply.frag",
				"fragColor",
				new String[]{ "vPosition" });
		
		// uniform setup
		this.uFadeColorTextureUniform = this.shader.getUniformLocation("uFadeColorTextureUniform");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(this.fadeTexture);
		GL32.glUniform1i(this.uFadeColorTextureUniform, 0);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GLMC.disableBlend();
		
		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		GLMC.disableDepthTest();
		
		
		// apply the rendered Fade to Minecraft's framebuffer
		GLMC.glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, this.readFramebuffer);
		GLMC.glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
		
		ScreenQuad.INSTANCE.render();
		
		GLMC.enableDepthTest();
		
	}
	
	
	
}
