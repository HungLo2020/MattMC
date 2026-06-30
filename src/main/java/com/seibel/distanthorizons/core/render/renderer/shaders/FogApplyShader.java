package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.FogRenderer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicBlendEquation;
import net.vulkanic.VulkanicBlendFactor;
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
	
	
	public int fogTexture = -1;
	
	// uniforms
	public int colorTextureUniform;
	public int depthTextureUniform;
	public int dhColorTextureUniform;

	private int activeDepthTextureId = -1;
	private int activeColorTextureId = -1;
	
	
	
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
		this.dhColorTextureUniform = this.shader.getUniformLocation("uDhColorTexture");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected boolean onPreRender(CommandContext ctx, float partialTicks)
	{
		this.activeDepthTextureId = LodRenderer.INSTANCE.getActiveDepthTextureId();
		this.activeColorTextureId = LodRenderer.INSTANCE.getActiveColorTextureId();
		return this.fogTexture != -1
			&& this.activeDepthTextureId != -1
			&& this.activeColorTextureId != -1
			&& FogShader.INSTANCE.frameBuffer != null
			&& LodRenderer.INSTANCE.hasActiveRenderTarget();
	}

	@Override
	protected void onApplyUniforms(CommandContext ctx, float partialTicks)
	{
		DhTextureState.setActiveTextureUnitIndex(0);
		DhTextureState.bindTexture2D(this.fogTexture);
		VulkanicAPI.setUniform1i(ctx, this.colorTextureUniform, 0);
		
		DhTextureState.setActiveTextureUnitIndex(1);
		DhTextureState.bindTexture2D(this.activeDepthTextureId);
		VulkanicAPI.setUniform1i(ctx, this.depthTextureUniform, 1);

		DhTextureState.setActiveTextureUnitIndex(2);
		DhTextureState.bindTexture2D(this.activeColorTextureId);
		VulkanicAPI.setUniform1i(ctx, this.dhColorTextureUniform, 2);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender(CommandContext ctx)
	{
		VulkanicAPI.setBlendEnabled(ctx, true);
		VulkanicAPI.setBlendEquation(ctx, VulkanicBlendEquation.ADD);
		VulkanicAPI.setBlendFunction(
			ctx,
			VulkanicBlendFactor.SRC_ALPHA,
			VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA,
			VulkanicBlendFactor.ONE,
			VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA
		);
		
		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		VulkanicAPI.setColorMask(ctx, true, true, true, true);
		
		
		// apply the rendered Fog to DH's framebuffer
		FogShader.INSTANCE.frameBuffer.bindAsReadBuffer(ctx);
		if (!LodRenderer.INSTANCE.bindActiveRenderTarget())
		{
			VulkanicAPI.bindReadFramebuffer(ctx, 0);
			return;
		}

		ScreenQuad.INSTANCE.render(ctx, LodRenderer.INSTANCE.getActiveDhFramebuffer());
		
		VulkanicAPI.bindReadFramebuffer(ctx, 0);
	}
	
}
