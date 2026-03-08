package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.SSAORenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.vulkanic.VulkanicAPI;

/**
 * Draws the SSAO texture onto DH's FrameBuffer. <br><br>
 * 
 * See Also: <br>
 * {@link SSAORenderer} - Parent to this shader. <br>
 * {@link SSAOShader} - draws the SSAO texture. <br>
 */
public class SSAOApplyShader extends AbstractShaderRenderer
{
	public static SSAOApplyShader INSTANCE = new SSAOApplyShader();
	
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	public int ssaoTexture;
	
	// uniforms
	public int gSSAOMapUniform;
	public int gDepthMapUniform;
	public int gViewSizeUniform;
	public int gBlurRadiusUniform;
	public int gNearUniform;
	public int gFarUniform;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/ssao/apply.frag",
				"fragColor",
				new String[]{"vPosition"});
		
		// uniform setup
		this.gSSAOMapUniform = this.shader.getUniformLocation("gSSAOMap");
		this.gDepthMapUniform = this.shader.getUniformLocation("gDepthMap");
		this.gViewSizeUniform = this.shader.tryGetUniformLocation("gViewSize");
		this.gBlurRadiusUniform = this.shader.tryGetUniformLocation("gBlurRadius");
		this.gNearUniform = this.shader.tryGetUniformLocation("gNear");
		this.gFarUniform = this.shader.tryGetUniformLocation("gFar");
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE0);
		GLMC.glBindTexture(LodRenderer.INSTANCE.getActiveDepthTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.gDepthMapUniform, 0);
		
		GLMC.glActiveTexture(VulkanicAPI.GL_TEXTURE1);
		GLMC.glBindTexture(this.ssaoTexture);
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.gSSAOMapUniform, 1);
		
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.gBlurRadiusUniform, Config.Client.Advanced.Graphics.Ssao.blurRadius.get());
		
		if (this.gViewSizeUniform >= 0)
		{
			VulkanicAPI.setUniform2f(VulkanicAPI.getImmediateContext(), this.gViewSizeUniform,
					MC_RENDER.getTargetFramebufferViewportWidth(),
					MC_RENDER.getTargetFramebufferViewportHeight());
		}
		
		if (this.gNearUniform >= 0)
		{
			VulkanicAPI.setUniform1f(VulkanicAPI.getImmediateContext(), this.gNearUniform,
					RenderUtil.getNearClipPlaneDistanceInBlocks(partialTicks));
		}
		
		if (this.gFarUniform >= 0)
		{
			float farClipPlane = RenderUtil.getFarClipPlaneDistanceInBlocks();
			VulkanicAPI.setUniform1f(VulkanicAPI.getImmediateContext(), this.gFarUniform, farClipPlane);
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GLMC.enableBlend();
		VulkanicAPI.setBlendEquation(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FUNC_ADD);
		GLMC.glBlendFuncSeparate(VulkanicAPI.GL_ZERO, VulkanicAPI.GL_SRC_ALPHA, VulkanicAPI.GL_ZERO, VulkanicAPI.GL_ONE);

		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		GLMC.disableDepthTest();
		
		// apply the rendered SSAO to the LODs 
		VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getImmediateContext(), SSAOShader.INSTANCE.frameBuffer);
		VulkanicAPI.bindDrawFramebuffer(VulkanicAPI.getImmediateContext(), LodRenderer.INSTANCE.getActiveFramebufferId());
		
		
		ScreenQuad.INSTANCE.render();
		
	}
}
