package net.distanthorizons.core.render.renderer.shaders;

import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.render.glObject.shader.ShaderProgram;
import net.distanthorizons.core.render.renderer.LodRenderer;
import net.distanthorizons.core.render.renderer.ScreenQuad;
import net.distanthorizons.core.util.RenderUtil;
import net.distanthorizons.core.util.math.Mat4f;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;

public class DhFadeShader extends AbstractShaderRenderer
{
	public static DhFadeShader INSTANCE = new DhFadeShader();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
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
		
		
		
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, this.frameBuffer);
		GLMC.disableScissorTest();
		GLMC.disableDepthTest();
		GLMC.disableBlend();
		
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(depthTextureId);
		GL32.glUniform1i(this.uDhDepthTexture, 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		GLMC.glBindTexture(MC_RENDER.getColorTextureId());
		GL32.glUniform1i(this.uMcColorTexture, 1);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE2);
		GLMC.glBindTexture(colorTextureId);
		GL32.glUniform1i(this.uDhColorTexture, 2);
		
		
		ScreenQuad.INSTANCE.render();
	}
	
}
