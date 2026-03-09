package com.seibel.distanthorizons.core.render.glObject;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

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
	public int[] view;
	public boolean cull;
	public int cullMode;
	public int polyMode;
	
	
	
	public GLState() { this.saveState(); }
	
	public void saveState()
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		this.program = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_CURRENT_PROGRAM);
		this.vao = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_VERTEX_ARRAY_BINDING);
		this.vbo = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_ARRAY_BUFFER_BINDING);
		this.ebo = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER_BINDING);
		
		this.fbo = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_FRAMEBUFFER_BINDING);
		
		this.texture2D = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_TEXTURE_BINDING_2D);
		this.activeTextureNumber = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_ACTIVE_TEXTURE);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0);
		this.texture0 = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE1);
		this.texture1 = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE2); // problem with Iris
		this.texture2 = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE3);
		this.texture3 = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_TEXTURE_BINDING_2D);
		
		DhTextureState.setActiveTextureUnit(this.activeTextureNumber);
		
		if (this.fbo != 0)
		{
			this.frameBufferTexture0 = VulkanicAPI.getFramebufferAttachmentParameteri(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
			this.frameBufferTexture1 = VulkanicAPI.getFramebufferAttachmentParameteri(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT1, VulkanicAPI.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
			this.frameBufferDepthTexture = VulkanicAPI.getFramebufferAttachmentParameteri(ctx, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_DEPTH_ATTACHMENT, VulkanicAPI.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
		}
		else
		{
			// attempting to get values from the default framebuffer can throw errors on Linux
			this.frameBufferTexture0 = 0;
			this.frameBufferTexture1 = 0;
			this.frameBufferDepthTexture = 0;
		}
		
		this.blend = VulkanicAPI.isEnabled(ctx, VulkanicAPI.GL_BLEND);
		this.scissor = VulkanicAPI.isEnabled(ctx, VulkanicAPI.GL_SCISSOR_TEST);
		this.blendEqRGB = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_BLEND_EQUATION_RGB);
		this.blendEqAlpha = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_BLEND_EQUATION_ALPHA);
		this.blendSrcColor = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_BLEND_SRC_RGB);
		this.blendSrcAlpha = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_BLEND_SRC_ALPHA);
		this.blendDstColor = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_BLEND_DST_RGB);
		this.blendDstAlpha = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_BLEND_DST_ALPHA);
		this.depth = VulkanicAPI.isEnabled(ctx, VulkanicAPI.GL_DEPTH_TEST);
		this.writeToDepthBuffer = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_DEPTH_WRITEMASK) == VulkanicAPI.GL_TRUE;
		this.depthFunc = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_DEPTH_FUNC);
		this.stencil = VulkanicAPI.isEnabled(ctx, VulkanicAPI.GL_STENCIL_TEST);
		this.stencilFunc = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_STENCIL_FUNC);
		this.stencilRef = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_STENCIL_REF);
		this.stencilMask = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_STENCIL_VALUE_MASK);
		this.view = new int[4];
		VulkanicAPI.getIntegerv(ctx, VulkanicAPI.GL_VIEWPORT, this.view);
		this.cull = VulkanicAPI.isEnabled(ctx, VulkanicAPI.GL_CULL_FACE);
		this.cullMode = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_CULL_FACE_MODE);
		this.polyMode = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_POLYGON_MODE);
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
				", view={x:" + this.view[0] + ", y:" + this.view[1] +
				", w:" + this.view[2] + ", h:" + this.view[3] + "}" + ", cull=" + this.cull +
				", cullMode=" + GLEnums.getString(this.cullMode) + ", polyMode=" + GLEnums.getString(this.polyMode) +
				'}';
	}
	
	public void restore()
	{
		// explicitly unbinding the frame buffer is necessary to prevent GL_CLEAR calls from hitting the wrong buffer
		VulkanicAPI.bindDefaultFramebuffer(VulkanicAPI.getImmediateContext());
		boolean frameBufferSet = false;
		
		if (this.fbo != 0 && VulkanicAPI.isFramebuffer(VulkanicAPI.getImmediateContext(), this.fbo))
		{
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getImmediateContext(), this.fbo);
			frameBufferSet = true;
		}
		
		
		if (this.blend)
		{
			VulkanicAPI.setBlendEnabled(VulkanicAPI.getImmediateContext(), true);
		}
		else
		{
			VulkanicAPI.setBlendEnabled(VulkanicAPI.getImmediateContext(), false);
		}
		
		if (this.scissor)
		{
			VulkanicAPI.setScissorTestEnabled(VulkanicAPI.getImmediateContext(), true);
		}
		else
		{
			VulkanicAPI.setScissorTestEnabled(VulkanicAPI.getImmediateContext(), false);
		}
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(VulkanicAPI.getImmediateContext(), this.texture0) ? this.texture0 : 0);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE1);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(VulkanicAPI.getImmediateContext(), this.texture1) ? this.texture1 : 0);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE2);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(VulkanicAPI.getImmediateContext(), this.texture2) ? this.texture2 : 0);
		
		DhTextureState.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE3);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(VulkanicAPI.getImmediateContext(), this.texture3) ? this.texture3 : 0);
		
		DhTextureState.setActiveTextureUnit(this.activeTextureNumber);
		DhTextureState.bindTexture2D(VulkanicAPI.isTexture(VulkanicAPI.getImmediateContext(), this.texture2D) ? this.texture2D : 0);
		
		// attempting to set textures on the default frame buffer (ID 0) will throw errors
		if (frameBufferSet)
		{
			VulkanicAPI.framebufferTexture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D, this.frameBufferTexture0, 0);
			VulkanicAPI.framebufferTexture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT1, VulkanicAPI.GL_TEXTURE_2D, this.frameBufferTexture1, 0);
			VulkanicAPI.framebufferTexture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_DEPTH_ATTACHMENT, VulkanicAPI.GL_TEXTURE_2D, this.frameBufferDepthTexture, 0);
		}
		
		VulkanicAPI.bindVertexArray(VulkanicAPI.getImmediateContext(), VulkanicAPI.isVertexArray(VulkanicAPI.getImmediateContext(), this.vao) ? this.vao : 0);
		VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_ARRAY_BUFFER, VulkanicAPI.isBuffer(VulkanicAPI.getImmediateContext(), this.vbo) ? this.vbo : 0);
		VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, VulkanicAPI.isBuffer(VulkanicAPI.getImmediateContext(), this.ebo) ? this.ebo: 0);
		VulkanicAPI.bindShaderProgram(VulkanicAPI.getImmediateContext(), VulkanicAPI.isProgram(VulkanicAPI.getImmediateContext(), this.program) ? this.program : 0);
		
		if (this.writeToDepthBuffer)
		{
			VulkanicAPI.setDepthWriteMask(VulkanicAPI.getImmediateContext(), true);
		}
		else
		{
			VulkanicAPI.setDepthWriteMask(VulkanicAPI.getImmediateContext(), false);
		}
		
		VulkanicAPI.blendFunc(VulkanicAPI.getImmediateContext(), this.blendSrcColor, this.blendDstColor);
		VulkanicAPI.setBlendEquationSeparate(VulkanicAPI.getImmediateContext(), this.blendEqRGB, this.blendEqAlpha);
		VulkanicAPI.setBlendFunction(VulkanicAPI.getImmediateContext(), this.blendSrcColor, this.blendDstColor, this.blendSrcAlpha, this.blendDstAlpha);
		
		if (this.depth)
		{
			VulkanicAPI.setDepthTestEnabled(VulkanicAPI.getImmediateContext(), true);
		}
		else
		{
			VulkanicAPI.setDepthTestEnabled(VulkanicAPI.getImmediateContext(), false);
		}
		VulkanicAPI.setDepthFunc(VulkanicAPI.getImmediateContext(), this.depthFunc);
		
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		if (this.stencil)
		{
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_STENCIL_TEST, true);
		}
		else
		{
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_STENCIL_TEST, false);
		}
		VulkanicAPI.setStencilFunc(ctx, this.stencilFunc, this.stencilRef, this.stencilMask);
		
		VulkanicAPI.setViewport(VulkanicAPI.getImmediateContext(), this.view[0], this.view[1], this.view[2], this.view[3]);
		if (this.cull)
		{
			VulkanicAPI.setCullFaceEnabled(VulkanicAPI.getImmediateContext(), true);
		}
		else
		{
			VulkanicAPI.setCullFaceEnabled(VulkanicAPI.getImmediateContext(), false);
		}
		VulkanicAPI.setCullFaceMode(VulkanicAPI.getImmediateContext(), this.cullMode);
		VulkanicAPI.setPolygonMode(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRONT_AND_BACK, this.polyMode);
	}
}
