package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.vulkanic.VulkanicAPI;

public class DhFadeShader extends AbstractShaderRenderer
{
	public static DhFadeShader INSTANCE = new DhFadeShader();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	public int frameBuffer = -1;
	
	private Mat4f inverseDhMvmProjMatrix;
	
	
	// Uniforms
	
	/** Inverted Model View Projection matrix */
	public int uDhInvMvmProj = -1;
	
	public int uDhDepthTexture = -1;
	public int uMcColorTexture = -1;
	public int uDhColorTexture = -1;
	
	public int uStartFadeBlockDistance = -1;
	public int uEndFadeBlockDistance = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhFadeShader() {  }

	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert", 
				"shaders/fade/dhFade.frag",
				"fragColor", 
				new String[]{"vPosition"}
		);
		
		// all uniforms should be tryGet...
		// because disabling fade can cause the GLSL to optimize out most (if not all) uniforms
		
		// near fade
		this.uDhInvMvmProj = this.shader.tryGetUniformLocation("uDhInvMvmProj");
		
		this.uDhDepthTexture = this.shader.tryGetUniformLocation("uDhDepthTexture");
		this.uMcColorTexture = this.shader.tryGetUniformLocation("uMcColorTexture");
		this.uDhColorTexture = this.shader.tryGetUniformLocation("uDhColorTexture");
		
		this.uStartFadeBlockDistance = this.shader.tryGetUniformLocation("uStartFadeBlockDistance");
		this.uEndFadeBlockDistance = this.shader.tryGetUniformLocation("uEndFadeBlockDistance");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		this.shader.setUniform(this.uDhInvMvmProj, this.inverseDhMvmProjMatrix);
		
		
		float dhFarClipDistance = RenderUtil.getFarClipPlaneDistanceInBlocks();
		float fadeStartDistance = dhFarClipDistance * 0.5f;
		float fadeEndDistance = dhFarClipDistance * 0.9f;
		
		this.shader.setUniform(this.uStartFadeBlockDistance, fadeStartDistance);
		this.shader.setUniform(this.uEndFadeBlockDistance, fadeEndDistance);
		
	}
	
	public void setProjectionMatrix(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks)
	{
		Mat4f dhProjectionMatrix = RenderUtil.createLodProjectionMatrix(mcProjectionMatrix, partialTicks);
		Mat4f dhModelViewMatrix = RenderUtil.createLodModelViewMatrix(mcModelViewMatrix);
		
		Mat4f inverseDhModelViewProjectionMatrix = new Mat4f(dhProjectionMatrix);
		inverseDhModelViewProjectionMatrix.multiply(dhModelViewMatrix);
		inverseDhModelViewProjectionMatrix.invert();
		this.inverseDhMvmProjMatrix = inverseDhModelViewProjectionMatrix;
	}
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		int depthTextureId = LodRenderer.INSTANCE.getActiveDepthTextureId();
		int colorTextureId = LodRenderer.INSTANCE.getActiveColorTextureId();
		
		if (depthTextureId == -1
			|| colorTextureId == -1)
		{
			// the renderer is currently being re-built and/or inactive,
			// we don't need to/can't render fading
			return;
		}
		
		
		
		VulkanicAPI.bindFramebuffer(VulkanicAPI.getImmediateContext(), this.frameBuffer);
		VulkanicAPI.setScissorTestEnabled(VulkanicAPI.getImmediateContext(), false);
		VulkanicAPI.setDepthTestEnabled(VulkanicAPI.getImmediateContext(), false);
		VulkanicAPI.setBlendEnabled(VulkanicAPI.getImmediateContext(), false);
		
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0);
		DhTextureState.bindTexture2D(depthTextureId);
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.uDhDepthTexture, 0);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE1);
		DhTextureState.bindTexture2D(MC_RENDER.getColorTextureId());
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.uMcColorTexture, 1);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE2);
		DhTextureState.bindTexture2D(colorTextureId);
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), this.uDhColorTexture, 2);
		
		
		ScreenQuad.INSTANCE.render();
	}
	
}
