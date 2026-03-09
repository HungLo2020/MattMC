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
	protected void onRender(CommandContext ctx)
	{
		if (MC_RENDER.mcRendersToFrameBuffer())
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
		int targetFrameBuffer = MC_RENDER.getTargetFramebuffer();
		if (targetFrameBuffer == -1)
		{
			return;
		}
		
		
		GLState state = new GLState();
		
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		
		// blending isn't needed, we're manually merging the MC and DH textures
		// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
		// however this also fixes transparent LODs from glowing when rendered against the sky during the day
		VulkanicAPI.setBlendEnabled(ctx, false);
		
		// old blending logic in case it's ever needed:
		//VulkanicAPI.setBlendEnabled(ctx, true);
		//VulkanicAPI.setBlendEquation(ctx, VulkanicAPI.GL_FUNC_ADD);
		//VulkanicAPI.blendFunc(ctx, VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0);
		DhTextureState.bindTexture2D(LodRenderer.INSTANCE.getActiveColorTextureId());
		VulkanicAPI.setUniform1i(ctx, this.gDhColorTextureUniform, 0);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE1);
		DhTextureState.bindTexture2D(LodRenderer.INSTANCE.getActiveDepthTextureId());
		VulkanicAPI.setUniform1i(ctx, this.gDepthMapUniform, 1);
		
		// Copy to MC's framebuffer
		VulkanicAPI.bindFramebuffer(ctx, targetFrameBuffer);
		
		ScreenQuad.INSTANCE.render();
		
		
		// restore everything, except at this point the MC framebuffer should now be used instead
		state.restore();
		VulkanicAPI.bindFramebuffer(ctx, targetFrameBuffer);
		
	}
	private void renderToMcTexture(CommandContext ctx)
	{
		int targetColorTextureId = MC_RENDER.getColorTextureId();
		if (targetColorTextureId == -1)
		{
			return;
		}
		
		int dhFrameBufferId = LodRenderer.INSTANCE.getActiveFramebufferId();
		if (dhFrameBufferId == -1)
		{
			return;
		}
		
		int mcFrameBufferId = MC_RENDER.getTargetFramebuffer();
		if (mcFrameBufferId == -1)
		{
			return;
		}
		
		
		
		GLState state = new GLState();
		
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		
		// blending isn't needed, we're just directly merging the MC and DH textures
		// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
		// however this also fixes
		VulkanicAPI.setBlendEnabled(ctx, false);
		
		// old blending logic in case it's ever needed:
		//VulkanicAPI.setBlendEnabled(ctx, true);
		//VulkanicAPI.setBlendEquation(ctx, VulkanicAPI.GL_FUNC_ADD);
		//VulkanicAPI.blendFunc(ctx, VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0);
		DhTextureState.bindTexture2D(LodRenderer.INSTANCE.getActiveColorTextureId());
		VulkanicAPI.setUniform1i(ctx, this.gDhColorTextureUniform, 0);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE1);
		DhTextureState.bindTexture2D(LodRenderer.INSTANCE.getActiveDepthTextureId());
		VulkanicAPI.setUniform1i(ctx, this.gDepthMapUniform, 1);
		
		
		
		VulkanicAPI.framebufferTexture(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D, targetColorTextureId, 0);
		
		// Copy to MC's texture via MC's framebuffer
		VulkanicAPI.bindFramebuffer(ctx, dhFrameBufferId);
		
		ScreenQuad.INSTANCE.render();
		
		
		// restore everything, except at this point the MC framebuffer should now be used instead
		state.restore();
		VulkanicAPI.bindFramebuffer(ctx, mcFrameBufferId);
		
	}
	
	
	
}
