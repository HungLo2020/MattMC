package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.FogRenderer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.vulkanic.VulkanicAPI;

/**
 * Draws the Fog texture onto DH's FrameBuffer. <br><br>
 * 
 * See Also: <br>
 * {@link FogRenderer} - Parent to this shader. <br>
 * {@link FogShader} - draws the Fog texture. <br>
 */
public class FogApplyShader extends AbstractShaderRenderer
{
	public static FogApplyShader INSTANCE = new FogApplyShader();
	
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	public int fogTexture;
	
	// uniforms
	public int colorTextureUniform;
	public int depthTextureUniform;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/fog/apply.frag",
				"fragColor",
				new String[]{ "vPosition" });
		
		// uniform setup
		this.colorTextureUniform = this.shader.getUniformLocation("uColorTexture");
		this.depthTextureUniform = this.shader.getUniformLocation("uDepthTexture");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE0);
		GLMC.glBindTexture(this.fogTexture);
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.colorTextureUniform, 0);
		
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE1);
		GLMC.glBindTexture(LodRenderer.INSTANCE.getActiveDepthTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.depthTextureUniform, 1);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GLMC.enableBlend();
		VulkanicAPI.setBlendEquation(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FUNC_ADD);
		GLMC.glBlendFuncSeparate(VulkanicAPI.GL_SRC_ALPHA, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA, VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		GLMC.disableDepthTest();
		
		
		// apply the rendered Fog to DH's framebuffer
		GLMC.glBindFramebuffer(VulkanicAPI.GL_READ_FRAMEBUFFER, FogShader.INSTANCE.frameBuffer);
		GLMC.glBindFramebuffer(VulkanicAPI.GL_DRAW_FRAMEBUFFER, LodRenderer.INSTANCE.getActiveFramebufferId());
		
		ScreenQuad.INSTANCE.render();
		
		GLMC.glBindFramebuffer(VulkanicAPI.GL_READ_FRAMEBUFFER, 0);
	}
	
}
