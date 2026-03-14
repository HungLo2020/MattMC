package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.logging.DhLogger;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

/**
 * Copies {@link LodRenderer}'s currently active color and depth texture to Minecraft's framebuffer. 
 */
public class DhApplyShader extends AbstractShaderRenderer
{
	public static DhApplyShader INSTANCE = new DhApplyShader();
	
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	// uniforms
	public int gDhColorTextureUniform;
	public int gDepthMapUniform;

	private boolean activeRenderToFrameBuffer = false;
	private int activeTargetColorTextureId = -1;
	private int activeDhColorTextureId = -1;
	private int activeDhDepthTextureId = -1;
	
	
	
	private DhApplyShader() { }
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/apply.frag",
				"fragColor",
				new String[]{"vPosition"});
		
		// uniform setup
		this.gDhColorTextureUniform = this.shader.getUniformLocation("gDhColorTexture");
		this.gDepthMapUniform = this.shader.getUniformLocation("gDhDepthTexture");
		
	}
	
	@Override
	protected void onApplyUniforms(CommandContext ctx, float partialTicks) { }
	
	
	//========//
	// render //
	//========//

	@Override
	protected boolean onPreRender(CommandContext ctx, float partialTicks)
	{
		this.activeRenderToFrameBuffer = MC_RENDER.mcRendersToFrameBuffer();
		this.activeDhColorTextureId = LodRenderer.INSTANCE.getActiveColorTextureId();
		this.activeDhDepthTextureId = LodRenderer.INSTANCE.getActiveDepthTextureId();

		if (this.activeDhColorTextureId == -1 || this.activeDhDepthTextureId == -1)
		{
			this.activeTargetColorTextureId = -1;
			return false;
		}

		if (this.activeRenderToFrameBuffer)
		{
			this.activeTargetColorTextureId = -1;
			return MC_RENDER.hasTargetRenderTarget();
		}

		this.activeTargetColorTextureId = MC_RENDER.getColorTextureId();

		return this.activeTargetColorTextureId != -1
			&& LodRenderer.INSTANCE.hasActiveRenderTarget()
			&& MC_RENDER.hasTargetRenderTarget();
	}
	
	@Override
	protected void onRender(CommandContext ctx)
	{
		if (this.activeRenderToFrameBuffer)
		{
			this.renderToFrameBuffer(ctx);
		}
		else
		{
			this.renderToMcTexture(ctx);
		}
	}
	// TODO merge duplicate code between these to render methods
	private void renderToFrameBuffer(CommandContext ctx)
	{
		GLState state = new GLState(ctx);
		
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		
		// blending isn't needed, we're manually merging the MC and DH textures
		// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
		// however this also fixes transparent LODs from glowing when rendered against the sky during the day
		VulkanicAPI.setBlendEnabled(ctx, false);
		
		// old blending logic in case it's ever needed:
		//VulkanicAPI.setBlendEnabled(ctx, true);
		//VulkanicAPI.setBlendEquation(ctx, VulkanicAPI.GL_FUNC_ADD);
		//VulkanicAPI.blendFunc(ctx, VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		DhTextureState.setActiveTextureUnitIndex(0);
		DhTextureState.bindTexture2D(this.activeDhColorTextureId);
		VulkanicAPI.setUniform1i(ctx, this.gDhColorTextureUniform, 0);
		
		DhTextureState.setActiveTextureUnitIndex(1);
		DhTextureState.bindTexture2D(this.activeDhDepthTextureId);
		VulkanicAPI.setUniform1i(ctx, this.gDepthMapUniform, 1);
		
		// Copy to MC's framebuffer
		if (!MC_RENDER.bindTargetRenderTarget(ctx))
		{
			state.restore(ctx);
			return;
		}
		
		ScreenQuad.INSTANCE.render();
		
		
		// restore everything, except at this point the MC framebuffer should now be used instead
		state.restore(ctx);
		MC_RENDER.bindTargetRenderTarget(ctx);
		
	}
	private void renderToMcTexture(CommandContext ctx)
	{
		GLState state = new GLState(ctx);
		
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		
		// blending isn't needed, we're just directly merging the MC and DH textures
		// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
		// however this also fixes
		VulkanicAPI.setBlendEnabled(ctx, false);
		
		// old blending logic in case it's ever needed:
		//VulkanicAPI.setBlendEnabled(ctx, true);
		//VulkanicAPI.setBlendEquation(ctx, VulkanicAPI.GL_FUNC_ADD);
		//VulkanicAPI.blendFunc(ctx, VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		DhTextureState.setActiveTextureUnitIndex(0);
		DhTextureState.bindTexture2D(this.activeDhColorTextureId);
		VulkanicAPI.setUniform1i(ctx, this.gDhColorTextureUniform, 0);
		
		DhTextureState.setActiveTextureUnitIndex(1);
		DhTextureState.bindTexture2D(this.activeDhDepthTextureId);
		VulkanicAPI.setUniform1i(ctx, this.gDepthMapUniform, 1);
		
		
		
		VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER, this.activeTargetColorTextureId, 0);
		
		// Copy to MC's texture via MC's framebuffer
		if (!LodRenderer.INSTANCE.bindActiveRenderTarget())
		{
			state.restore(ctx);
			MC_RENDER.bindTargetRenderTarget(ctx);
			return;
		}
		
		ScreenQuad.INSTANCE.render();
		
		
		// restore everything, except at this point the MC framebuffer should now be used instead
		state.restore(ctx);
		MC_RENDER.bindTargetRenderTarget(ctx);
		
	}
	
	
	
}
