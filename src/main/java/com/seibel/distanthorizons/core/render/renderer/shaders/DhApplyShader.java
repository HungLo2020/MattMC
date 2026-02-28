package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.logging.DhLogger;
import net.vulkanic.VulkanicAPI;

/**
 * Copies {@link LodRenderer}'s currently active color and depth texture to Minecraft's framebuffer. 
 */
public class DhApplyShader extends AbstractShaderRenderer
{
	public static DhApplyShader INSTANCE = new DhApplyShader();
	
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
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
	protected void onApplyUniforms(float partialTicks) { }
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		if (MC_RENDER.mcRendersToFrameBuffer())
		{
			this.renderToFrameBuffer();
		}
		else
		{
			this.renderToMcTexture();
		}
	}
	// TODO merge duplicate code between these to render methods
	private void renderToFrameBuffer()
	{
		int targetFrameBuffer = MC_RENDER.getTargetFramebuffer();
		if (targetFrameBuffer == -1)
		{
			return;
		}
		
		
		GLState state = new GLState();
		
		GLMC.disableDepthTest();
		
		// blending isn't needed, we're manually merging the MC and DH textures
		// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
		// however this also fixes transparent LODs from glowing when rendered against the sky during the day
		GLMC.disableBlend();
		
		// old blending logic in case it's ever needed:
		//GLMC.enableBlend();
		//GL32.glBlendEquation(VulkanicAPI.GL_FUNC_ADD);
		//GLMC.glBlendFunc(VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE0);
		GLMC.glBindTexture(LodRenderer.INSTANCE.getActiveColorTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.gDhColorTextureUniform, 0);
		
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE1);
		GLMC.glBindTexture(LodRenderer.INSTANCE.getActiveDepthTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.gDepthMapUniform, 1);
		
		// Copy to MC's framebuffer
		GLMC.glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, targetFrameBuffer);
		
		ScreenQuad.INSTANCE.render();
		
		
		// restore everything, except at this point the MC framebuffer should now be used instead
		state.restore();
		GLMC.glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, targetFrameBuffer);
		
	}
	private void renderToMcTexture()
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
		
		GLMC.disableDepthTest();
		
		// blending isn't needed, we're just directly merging the MC and DH textures
		// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
		// however this also fixes
		GLMC.disableBlend();
		
		// old blending logic in case it's ever needed:
		//GLMC.enableBlend();
		//GL32.glBlendEquation(VulkanicAPI.GL_FUNC_ADD);
		//GLMC.glBlendFunc(VulkanicAPI.GL_ONE, VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA);
		
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE0);
		GLMC.glBindTexture(LodRenderer.INSTANCE.getActiveColorTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.gDhColorTextureUniform, 0);
		
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE1);
		GLMC.glBindTexture(LodRenderer.INSTANCE.getActiveDepthTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.gDepthMapUniform, 1);
		
		
		
		VulkanicAPI.framebufferTexture(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_DRAW_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D, targetColorTextureId, 0);
		
		// Copy to MC's texture via MC's framebuffer
		GLMC.glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, dhFrameBufferId);
		
		ScreenQuad.INSTANCE.render();
		
		
		// restore everything, except at this point the MC framebuffer should now be used instead
		state.restore();
		GLMC.glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, mcFrameBufferId);
		
	}
	
	
	
}
