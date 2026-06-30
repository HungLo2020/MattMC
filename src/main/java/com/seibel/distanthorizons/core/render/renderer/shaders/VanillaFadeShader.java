package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.config.Config;
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

public class VanillaFadeShader extends AbstractShaderRenderer
{
	public static VanillaFadeShader INSTANCE = new VanillaFadeShader();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	public DhFramebuffer frameBuffer;
	private DhFramebuffer activeFrameBuffer;
	private int activeDepthTextureId = -1;
	private int activeColorTextureId = -1;
	private int activeMcDepthTextureId = -1;
	private int activeMcColorTextureId = -1;
	
	private Mat4f inverseMcMvmProjMatrix;
	private Mat4f inverseDhMvmProjMatrix;
	private float levelMaxHeight;
	
	
	// Uniforms
	public int uMcDepthTexture = -1;
	public int uDhDepthTexture = -1;
	public int uCombinedMcDhColorTexture = -1;
	public int uDhColorTexture = -1;
	
	/** Inverted Model View Projection matrix */
	public int uDhInvMvmProj = -1;
	public int uMcInvMvmProj = -1;
	
	public int uStartFadeBlockDistance = -1;
	public int uEndFadeBlockDistance = -1;
	public int uMaxLevelHeight = -1;
	
	public int uOnlyRenderLods = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public VanillaFadeShader() {  }

	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert", 
				"shaders/fade/vanillaFade.frag",
				"fragColor", 
				new String[]{"vPosition"}
		);
		
		// all uniforms should be tryGet...
		// because disabling fade can cause the GLSL to optimize out most (if not all) uniforms
		
		// near fade
		this.uDhInvMvmProj = this.shader.tryGetUniformLocation("uDhInvMvmProj");
		this.uMcInvMvmProj = this.shader.tryGetUniformLocation("uMcInvMvmProj");
		
		this.uMcDepthTexture = this.shader.tryGetUniformLocation("uMcDepthTexture");
		this.uDhDepthTexture = this.shader.tryGetUniformLocation("uDhDepthTexture");
		this.uCombinedMcDhColorTexture = this.shader.tryGetUniformLocation("uCombinedMcDhColorTexture");
		this.uDhColorTexture = this.shader.tryGetUniformLocation("uDhColorTexture");
		
		this.uStartFadeBlockDistance = this.shader.tryGetUniformLocation("uStartFadeBlockDistance");
		this.uEndFadeBlockDistance = this.shader.tryGetUniformLocation("uEndFadeBlockDistance");
		this.uMaxLevelHeight = this.shader.tryGetUniformLocation("uMaxLevelHeight");
		
		this.uOnlyRenderLods = this.shader.tryGetUniformLocation("uOnlyRenderLods");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(CommandContext ctx, float partialTicks)
	{
		this.shader.setUniform(ctx, this.uMcInvMvmProj, this.inverseMcMvmProjMatrix);
		this.shader.setUniform(ctx, this.uDhInvMvmProj, this.inverseDhMvmProjMatrix);
		
		
		float dhNearClipDistance = RenderUtil.getNearClipPlaneInBlocksForFading(partialTicks);
		// this added value prevents the near clip plane and discard circle from touching, which looks bad
		dhNearClipDistance += 16f;
		
		// measured in blocks
		// these multipliers in James' tests should provide a fairly smooth transition
		// without having underdraw issues
		float fadeStartDistance = dhNearClipDistance * 1.5f;
		float fadeEndDistance = dhNearClipDistance * 1.9f;
		
		this.shader.setUniform(ctx, this.uStartFadeBlockDistance, fadeStartDistance);
		this.shader.setUniform(ctx, this.uEndFadeBlockDistance, fadeEndDistance);
		
		this.shader.setUniform(ctx, this.uMaxLevelHeight, this.levelMaxHeight);
		
		this.shader.setUniform(ctx, this.uOnlyRenderLods, Config.Client.Advanced.Debugging.lodOnlyMode.get());
	}
	
	public void setProjectionMatrix(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks)
	{
		Mat4f inverseMcModelViewProjectionMatrix = new Mat4f(mcProjectionMatrix);
		inverseMcModelViewProjectionMatrix.multiply(mcModelViewMatrix);
		inverseMcModelViewProjectionMatrix.invert();
		this.inverseMcMvmProjMatrix = inverseMcModelViewProjectionMatrix;
		
		
		Mat4f dhProjectionMatrix = RenderUtil.createLodProjectionMatrix(mcProjectionMatrix, partialTicks);
		Mat4f dhModelViewMatrix = RenderUtil.createLodModelViewMatrix(mcModelViewMatrix);
		
		Mat4f inverseDhModelViewProjectionMatrix = new Mat4f(dhProjectionMatrix);
		inverseDhModelViewProjectionMatrix.multiply(dhModelViewMatrix);
		inverseDhModelViewProjectionMatrix.invert();
		this.inverseDhMvmProjMatrix = inverseDhModelViewProjectionMatrix;
	}
	public void setLevelMaxHeight(int levelMaxHeight) { this.levelMaxHeight = levelMaxHeight; }
	
	
	
	//========//
	// render //
	//========//

	@Override
	protected boolean onPreRender(CommandContext ctx, float partialTicks)
	{
		int depthTextureId = LodRenderer.INSTANCE.getActiveDepthTextureId();
		int colorTextureId = LodRenderer.INSTANCE.getActiveColorTextureId();
		int mcDepthTextureId = MC_RENDER.getDepthTextureId();
		int mcColorTextureId = MC_RENDER.getColorTextureId();

		if (depthTextureId == -1
			|| colorTextureId == -1
			|| mcDepthTextureId == -1
			|| mcColorTextureId == -1
			|| this.frameBuffer == null)
		{
			this.activeDepthTextureId = -1;
			this.activeColorTextureId = -1;
			this.activeMcDepthTextureId = -1;
			this.activeMcColorTextureId = -1;
			this.activeFrameBuffer = null;
			return false;
		}

		this.activeDepthTextureId = depthTextureId;
		this.activeColorTextureId = colorTextureId;
		this.activeMcDepthTextureId = mcDepthTextureId;
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
		DhTextureState.bindTexture2D(this.activeMcDepthTextureId);
		VulkanicAPI.setUniform1i(ctx, this.uMcDepthTexture, 0);
		
		DhTextureState.setActiveTextureUnitIndex(1);
		DhTextureState.bindTexture2D(this.activeDepthTextureId);
		VulkanicAPI.setUniform1i(ctx, this.uDhDepthTexture, 1);
		
		DhTextureState.setActiveTextureUnitIndex(2);
		DhTextureState.bindTexture2D(this.activeMcColorTextureId);
		VulkanicAPI.setUniform1i(ctx, this.uCombinedMcDhColorTexture, 2);
		
		DhTextureState.setActiveTextureUnitIndex(3);
		DhTextureState.bindTexture2D(this.activeColorTextureId);
		VulkanicAPI.setUniform1i(ctx, this.uDhColorTexture, 3);
		
		
		ScreenQuad.INSTANCE.render(ctx, this.activeFrameBuffer);
	}
	
}
