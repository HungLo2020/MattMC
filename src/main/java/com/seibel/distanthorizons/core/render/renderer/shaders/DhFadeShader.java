package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.DhTextureState;
import com.seibel.distanthorizons.core.render.glObject.texture.DhFramebuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public class DhFadeShader extends AbstractShaderRenderer
{
	public static DhFadeShader INSTANCE = new DhFadeShader();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	public DhFramebuffer frameBuffer;
	private DhFramebuffer activeFrameBuffer;
	private int activeDepthTextureId = -1;
	private int activeColorTextureId = -1;
	private int activeMcColorTextureId = -1;
	
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
	protected void onApplyUniforms(CommandContext ctx, float partialTicks)
	{
		this.shader.setUniform(ctx, this.uDhInvMvmProj, this.inverseDhMvmProjMatrix);
		
		
		float dhFarClipDistance = RenderUtil.getFarClipPlaneDistanceInBlocks();
		float fadeStartDistance = dhFarClipDistance * 0.5f;
		float fadeEndDistance = dhFarClipDistance * 0.9f;
		
		this.shader.setUniform(ctx, this.uStartFadeBlockDistance, fadeStartDistance);
		this.shader.setUniform(ctx, this.uEndFadeBlockDistance, fadeEndDistance);
		
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
	protected boolean onPreRender(CommandContext ctx, float partialTicks)
	{
		int depthTextureId = LodRenderer.INSTANCE.getActiveDepthTextureId();
		int colorTextureId = LodRenderer.INSTANCE.getActiveColorTextureId();
		int mcColorTextureId = MC_RENDER.getColorTextureId();

		if (depthTextureId == -1
			|| colorTextureId == -1
			|| mcColorTextureId == -1
			|| this.frameBuffer == null)
		{
			this.activeDepthTextureId = -1;
			this.activeColorTextureId = -1;
			this.activeMcColorTextureId = -1;
			this.activeFrameBuffer = null;
			return false;
		}

		this.activeDepthTextureId = depthTextureId;
		this.activeColorTextureId = colorTextureId;
		this.activeMcColorTextureId = mcColorTextureId;
		this.activeFrameBuffer = this.frameBuffer;
		return true;
	}
	
	@Override
	protected void onRender(CommandContext ctx)
	{
		this.activeFrameBuffer.bind(ctx);
		VulkanicAPI.setScissorTestEnabled(ctx, false);
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		VulkanicAPI.setBlendEnabled(ctx, false);
		
		
		DhTextureState.setActiveTextureUnitIndex(0);
		DhTextureState.bindTexture2D(this.activeDepthTextureId);
		VulkanicAPI.setUniform1i(ctx, this.uDhDepthTexture, 0);
		
		DhTextureState.setActiveTextureUnitIndex(1);
		DhTextureState.bindTexture2D(this.activeMcColorTextureId);
		VulkanicAPI.setUniform1i(ctx, this.uMcColorTexture, 1);
		
		DhTextureState.setActiveTextureUnitIndex(2);
		DhTextureState.bindTexture2D(this.activeColorTextureId);
		VulkanicAPI.setUniform1i(ctx, this.uDhColorTexture, 2);
		
		
		ScreenQuad.INSTANCE.render(ctx, this.activeFrameBuffer);
	}
	
}
