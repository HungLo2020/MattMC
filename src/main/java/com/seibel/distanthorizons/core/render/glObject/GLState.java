package com.seibel.distanthorizons.core.render.glObject;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicBlendEquation;
import net.vulkanic.VulkanicBlendFactor;
import net.vulkanic.VulkanicCapability;
import net.vulkanic.VulkanicCullFaceMode;
import net.vulkanic.VulkanicDepthCompareOp;
import net.vulkanic.VulkanicStencilCompareOp;
import net.vulkanic.VulkanicStencilOperation;
import net.vulkanic.VulkanicPolygonFace;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicIntegerQuery;

// TODO make this Closable or AutoClosable so it can be used with try-resource blocks
public class GLState
{

	private static final int FBO_MAX = 4;
	
	public int program;
	public int vao;
	public int vbo;
	public int ebo;
	public int fbo;
	public int texture2D;
	/** IE: GL_TEXTURE0, GL_TEXTURE1, etc. */
	public int activeTextureNumber;
	public int texture0;
	public int texture1;
	public int texture2;
	public int texture3;
	public int frameBufferTexture0;
	public int frameBufferTexture1;
	public int frameBufferDepthTexture;
	public boolean blend;
	public boolean scissor;
	public int blendEqRGB;
	public int blendEqAlpha;
	public int blendSrcColor;
	public int blendSrcAlpha;
	public int blendDstColor;
	public int blendDstAlpha;
	public boolean depth;
	public boolean writeToDepthBuffer;
	public int depthFunc;
	public boolean stencil;
	public int stencilFunc;
	public int stencilRef;
	public int stencilMask;
	public int stencilFailOp;
	public int stencilDepthFailOp;
	public int stencilDepthPassOp;
	public int stencilWriteMask;
	public int[] view;
	public boolean cull;
	public int cullMode;
	public int polyMode;
	
	
	
	public GLState() { this(VulkanicAPI.getCommandContext()); }

	public GLState(CommandContext ctx) { this.saveState(ctx); }
	
	public void saveState()
	{
		this.saveState(VulkanicAPI.getCommandContext());
	}

	public void saveState(CommandContext ctx)
	{
		if (VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException(
				"Java Distant Horizons GL state is unavailable while Rust owns whole-frame presentation");
		}
		this.program = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.CURRENT_PROGRAM);
		this.vao = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.VERTEX_ARRAY_BINDING);
		this.vbo = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.ARRAY_BUFFER_BINDING);
		this.ebo = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.ELEMENT_ARRAY_BUFFER_BINDING);
		
		this.fbo = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.FRAMEBUFFER_BINDING);
		
		this.texture2D = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D);
		this.activeTextureNumber = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.ACTIVE_TEXTURE);
		
		DhTextureState.setActiveTextureUnitIndex(0);
		this.texture0 = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnitIndex(1);
		this.texture1 = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnitIndex(2); // problem with Iris
		this.texture2 = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnitIndex(3);
		this.texture3 = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnit(this.activeTextureNumber);
		
		if (this.fbo != 0)
		{
			this.frameBufferTexture0 = VulkanicAPI.getFramebufferColorAttachment0ObjectName(ctx);
			this.frameBufferTexture1 = VulkanicAPI.getFramebufferColorAttachment1ObjectName(ctx);
			this.frameBufferDepthTexture = VulkanicAPI.getFramebufferDepthAttachmentObjectName(ctx);
		}
		else
		{
			// attempting to get values from the default framebuffer can throw errors on Linux
			this.frameBufferTexture0 = 0;
			this.frameBufferTexture1 = 0;
			this.frameBufferDepthTexture = 0;
		}
		
		this.blend = VulkanicAPI.isEnabled(ctx, VulkanicCapability.BLEND);
		this.scissor = VulkanicAPI.isEnabled(ctx, VulkanicCapability.SCISSOR_TEST);
		this.blendEqRGB = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.BLEND_EQUATION_RGB);
		this.blendEqAlpha = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.BLEND_EQUATION_ALPHA);
		this.blendSrcColor = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.BLEND_SRC_RGB);
		this.blendSrcAlpha = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.BLEND_SRC_ALPHA);
		this.blendDstColor = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.BLEND_DST_RGB);
		this.blendDstAlpha = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.BLEND_DST_ALPHA);
		this.depth = VulkanicAPI.isEnabled(ctx, VulkanicCapability.DEPTH_TEST);
		this.writeToDepthBuffer = VulkanicAPI.getBoolean(ctx, VulkanicIntegerQuery.DEPTH_WRITEMASK);
		this.depthFunc = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.DEPTH_FUNC);
		this.stencil = VulkanicAPI.isEnabled(ctx, VulkanicCapability.STENCIL_TEST);
		this.stencilFunc = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.STENCIL_FUNC);
		this.stencilRef = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.STENCIL_REF);
		this.stencilMask = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.STENCIL_VALUE_MASK);
		this.stencilFailOp = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.STENCIL_FAIL);
		this.stencilDepthFailOp = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.STENCIL_PASS_DEPTH_FAIL);
		this.stencilDepthPassOp = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.STENCIL_PASS_DEPTH_PASS);
		this.stencilWriteMask = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.STENCIL_WRITEMASK);
		this.view = new int[4];
		VulkanicAPI.getViewport(ctx, this.view);
		this.cull = VulkanicAPI.isEnabled(ctx, VulkanicCapability.CULL_FACE);
		this.cullMode = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.CULL_FACE_MODE);
		this.polyMode = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.POLYGON_MODE);
	}
	
	@Override
	public String toString()
	{
		return "GLState{" +
				"program=" + this.program + ", vao=" + this.vao + ", vbo=" + this.vbo + ", ebo=" + this.ebo + ", fbo=" + this.fbo +
				", text=" + GLEnums.getString(this.texture2D) + "@" + this.activeTextureNumber + ", text0=" + GLEnums.getString(this.texture0) +
				", FB text0=" + this.frameBufferTexture0 +
				", FB text1=" + this.frameBufferTexture1 +
				", FB depth=" + this.frameBufferDepthTexture +
				", blend=" + this.blend + ", scissor=" + this.scissor + ", blendMode=" + GLEnums.getString(this.blendSrcColor) + "," + GLEnums.getString(this.blendDstColor) +
				", depth=" + this.depth +
				", depthFunc=" + GLEnums.getString(this.depthFunc) + ", stencil=" + this.stencil +
				", stencilFunc=" + GLEnums.getString(this.stencilFunc) + ", stencilRef=" + this.stencilRef + ", stencilMask=" + this.stencilMask +
				", stencilOp={" + GLEnums.getString(this.stencilFailOp) + ", " + GLEnums.getString(this.stencilDepthFailOp) + ", " + GLEnums.getString(this.stencilDepthPassOp) + "}" +
				", stencilWriteMask=" + this.stencilWriteMask +
				", view={x:" + this.view[0] + ", y:" + this.view[1] +
				", w:" + this.view[2] + ", h:" + this.view[3] + "}" + ", cull=" + this.cull +
				", cullMode=" + GLEnums.getString(this.cullMode) + ", polyMode=" + GLEnums.getString(this.polyMode) +
				'}';
	}
	
	public void restore()
	{
		this.restore(VulkanicAPI.getCommandContext());
	}

	public void restore(CommandContext ctx)
	{
		// explicitly unbinding the frame buffer is necessary to prevent GL_CLEAR calls from hitting the wrong buffer
		VulkanicAPI.bindDefaultFramebuffer(ctx);
		boolean frameBufferSet = false;
		
		if (this.fbo != 0 && VulkanicAPI.isFramebuffer(ctx, this.fbo))
		{
			VulkanicAPI.bindFramebuffer(ctx, this.fbo);
			frameBufferSet = true;
		}
		
		
		if (this.blend)
		{
			VulkanicAPI.setBlendEnabled(ctx, true);
		}
		else
		{
			VulkanicAPI.setBlendEnabled(ctx, false);
		}
		
		if (this.scissor)
		{
			VulkanicAPI.setScissorTestEnabled(ctx, true);
		}
		else
		{
			VulkanicAPI.setScissorTestEnabled(ctx, false);
		}
		
		DhTextureState.setActiveTextureUnitIndex(0);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(ctx, this.texture0) ? this.texture0 : 0);
		
		DhTextureState.setActiveTextureUnitIndex(1);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(ctx, this.texture1) ? this.texture1 : 0);
		
		DhTextureState.setActiveTextureUnitIndex(2);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(ctx, this.texture2) ? this.texture2 : 0);
		
		DhTextureState.setActiveTextureUnitIndex(3);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(ctx, this.texture3) ? this.texture3 : 0);
		
		DhTextureState.setActiveTextureUnit(this.activeTextureNumber);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(ctx, this.texture2D) ? this.texture2D : 0);
		
		// attempting to set textures on the default frame buffer (ID 0) will throw errors
		if (frameBufferSet)
		{
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, this.frameBufferTexture0, 0);
			VulkanicAPI.framebufferColorAttachment1Texture2D(ctx, this.frameBufferTexture1, 0);
			VulkanicAPI.framebufferDepthAttachmentTexture2D(ctx, this.frameBufferDepthTexture, 0);
		}
		
		VulkanicAPI.bindVertexArray(ctx, VulkanicAPI.isVertexArray(ctx, this.vao) ? this.vao : 0);
		VulkanicAPI.bindBuffer(ctx, VulkanicBufferTarget.VERTEX, VulkanicAPI.isBuffer(ctx, this.vbo) ? this.vbo : 0);
		VulkanicAPI.bindBuffer(ctx, VulkanicBufferTarget.INDEX, VulkanicAPI.isBuffer(ctx, this.ebo) ? this.ebo: 0);
		VulkanicAPI.bindShaderProgram(ctx, VulkanicAPI.isProgram(ctx, this.program) ? this.program : 0);
		
		if (this.writeToDepthBuffer)
		{
			VulkanicAPI.setDepthWriteMask(ctx, true);
		}
		else
		{
			VulkanicAPI.setDepthWriteMask(ctx, false);
		}
		
		VulkanicBlendFactor.fromLegacyGlConstant(this.blendSrcColor)
			.flatMap(src -> VulkanicBlendFactor.fromLegacyGlConstant(this.blendDstColor)
				.map(dst -> new VulkanicBlendFactor[]{src, dst}))
			.ifPresentOrElse(
				factors -> VulkanicAPI.blendFunc(ctx, factors[0], factors[1]),
				() -> VulkanicAPI.blendFunc(ctx, this.blendSrcColor, this.blendDstColor)
			);

		VulkanicBlendEquation.fromLegacyGlConstant(this.blendEqRGB)
			.flatMap(rgb -> VulkanicBlendEquation.fromLegacyGlConstant(this.blendEqAlpha)
				.map(alpha -> new VulkanicBlendEquation[]{rgb, alpha}))
			.ifPresentOrElse(
				equations -> VulkanicAPI.setBlendEquationSeparate(ctx, equations[0], equations[1]),
				() -> VulkanicAPI.setBlendEquationSeparate(ctx, this.blendEqRGB, this.blendEqAlpha)
			);

		java.util.Optional<VulkanicBlendFactor> typedSrcRgb = VulkanicBlendFactor.fromLegacyGlConstant(this.blendSrcColor);
		java.util.Optional<VulkanicBlendFactor> typedDstRgb = VulkanicBlendFactor.fromLegacyGlConstant(this.blendDstColor);
		java.util.Optional<VulkanicBlendFactor> typedSrcAlpha = VulkanicBlendFactor.fromLegacyGlConstant(this.blendSrcAlpha);
		java.util.Optional<VulkanicBlendFactor> typedDstAlpha = VulkanicBlendFactor.fromLegacyGlConstant(this.blendDstAlpha);
		if (typedSrcRgb.isPresent() && typedDstRgb.isPresent() && typedSrcAlpha.isPresent() && typedDstAlpha.isPresent()) {
			VulkanicAPI.setBlendFunction(ctx, typedSrcRgb.get(), typedDstRgb.get(), typedSrcAlpha.get(), typedDstAlpha.get());
		} else {
			VulkanicAPI.setBlendFunction(ctx, this.blendSrcColor, this.blendDstColor, this.blendSrcAlpha, this.blendDstAlpha);
		}
		
		if (this.depth)
		{
			VulkanicAPI.setDepthTestEnabled(ctx, true);
		}
		else
		{
			VulkanicAPI.setDepthTestEnabled(ctx, false);
		}
		VulkanicDepthCompareOp.fromLegacyGlConstant(this.depthFunc)
			.ifPresentOrElse(
				op -> VulkanicAPI.setDepthFunc(ctx, op),
				() -> VulkanicAPI.setDepthFunc(ctx, this.depthFunc)
			);
		
		if (this.stencil)
		{
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.STENCIL_TEST, true);
		}
		else
		{
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.STENCIL_TEST, false);
		}
		VulkanicStencilCompareOp.fromLegacyGlConstant(this.stencilFunc)
			.ifPresentOrElse(
				op -> VulkanicAPI.setStencilFunc(ctx, op, this.stencilRef, this.stencilMask),
				() -> VulkanicAPI.setStencilFunc(ctx, this.stencilFunc, this.stencilRef, this.stencilMask)
			);
		java.util.Optional<VulkanicStencilOperation> typedStencilFailOp = VulkanicStencilOperation.fromLegacyGlConstant(this.stencilFailOp);
		java.util.Optional<VulkanicStencilOperation> typedStencilDepthFailOp = VulkanicStencilOperation.fromLegacyGlConstant(this.stencilDepthFailOp);
		java.util.Optional<VulkanicStencilOperation> typedStencilDepthPassOp = VulkanicStencilOperation.fromLegacyGlConstant(this.stencilDepthPassOp);
		if (typedStencilFailOp.isPresent() && typedStencilDepthFailOp.isPresent() && typedStencilDepthPassOp.isPresent()) {
			VulkanicAPI.setStencilOp(ctx, typedStencilFailOp.get(), typedStencilDepthFailOp.get(), typedStencilDepthPassOp.get());
		} else {
			VulkanicAPI.setStencilOp(ctx, this.stencilFailOp, this.stencilDepthFailOp, this.stencilDepthPassOp);
		}
		VulkanicAPI.setStencilWriteMask(ctx, this.stencilWriteMask);
		
		VulkanicAPI.setViewport(ctx, this.view[0], this.view[1], this.view[2], this.view[3]);
		if (this.cull)
		{
			VulkanicAPI.setCullFaceEnabled(ctx, true);
		}
		else
		{
			VulkanicAPI.setCullFaceEnabled(ctx, false);
		}
		VulkanicCullFaceMode.fromLegacyGlConstant(this.cullMode)
			.ifPresentOrElse(
				mode -> VulkanicAPI.setCullFaceMode(ctx, mode),
				() -> VulkanicAPI.setCullFaceMode(ctx, this.cullMode)
			);
		VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, this.polyMode);
	}
}
