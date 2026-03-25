package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.texture.DhFramebuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.VanillaFadeRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

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
	
	
	
	public int fadeTexture = -1;
	
	public DhFramebuffer readFramebuffer;
	public boolean drawToMinecraftTarget = false;
	public boolean drawToLodTarget = false;

	private DhFramebuffer activeReadFramebuffer;
	private boolean activeDrawToMinecraftTarget = false;
	private boolean activeDrawToLodTarget = false;
	
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
	protected boolean onPreRender(CommandContext ctx, float partialTicks)
	{
		this.activeReadFramebuffer = this.readFramebuffer;
		this.activeDrawToMinecraftTarget = this.drawToMinecraftTarget;
		this.activeDrawToLodTarget = this.drawToLodTarget;
		return this.fadeTexture != -1
			&& this.activeReadFramebuffer != null
			&& (this.activeDrawToMinecraftTarget || this.activeDrawToLodTarget);
	}

	@Override
	protected void onApplyUniforms(CommandContext ctx, float partialTicks)
	{
		DhTextureState.setActiveTextureUnitIndex(0);
		DhTextureState.bindTexture2D(this.fadeTexture);
		VulkanicAPI.setUniform1i(ctx, this.uFadeColorTextureUniform, 0);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender(CommandContext ctx)
	{
		VulkanicAPI.setBlendEnabled(ctx, false);
		
		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		
		
		// apply the rendered Fade to Minecraft's framebuffer
		this.activeReadFramebuffer.bindAsReadBuffer(ctx);
		if (this.activeDrawToMinecraftTarget)
		{
			if (!MC_RENDER.bindTargetRenderTarget(ctx))
			{
				VulkanicAPI.bindReadFramebuffer(ctx, 0);
				return;
			}
		}
		else if (this.activeDrawToLodTarget)
		{
			if (!LodRenderer.INSTANCE.bindActiveRenderTarget())
			{
				VulkanicAPI.bindReadFramebuffer(ctx, 0);
				return;
			}
		}
		else
		{
			VulkanicAPI.bindReadFramebuffer(ctx, 0);
			return;
		}
		
		ScreenQuad.INSTANCE.render();
		
		VulkanicAPI.setDepthTestEnabled(ctx, true);
		VulkanicAPI.bindReadFramebuffer(ctx, 0);
		
	}
	
	
	
}
