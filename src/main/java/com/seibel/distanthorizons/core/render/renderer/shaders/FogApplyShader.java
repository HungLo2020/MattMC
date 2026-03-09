package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.FogRenderer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
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
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0);
		DhTextureState.bindTexture2D(this.fogTexture);
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.colorTextureUniform, 0);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE1);
		DhTextureState.bindTexture2D(LodRenderer.INSTANCE.getActiveDepthTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.depthTextureUniform, 1);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		VulkanicAPI.setBlendEnabled(VulkanicAPI.getImmediateContext(), true);
		VulkanicAPI.setBlendEquation(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FUNC_ADD);
		VulkanicAPI.setBlendFunction(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_SRC_ALPHA, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA, VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		VulkanicAPI.setDepthTestEnabled(VulkanicAPI.getImmediateContext(), false);
		
		
		// apply the rendered Fog to DH's framebuffer
		VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getImmediateContext(), FogShader.INSTANCE.frameBuffer);
		VulkanicAPI.bindDrawFramebuffer(VulkanicAPI.getImmediateContext(), LodRenderer.INSTANCE.getActiveFramebufferId());
		
		ScreenQuad.INSTANCE.render();
		
		VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getImmediateContext(), 0);
	}
	
}
