package com.seibel.distanthorizons.common.wrappers.minecraft;

import net.blaze3d.opengl.GlStateManager;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;

import com.seibel.distanthorizons.core.logging.DhLogger;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;


/**
 * <b>Why does DH often call GL methods twice? </b><br> 
 * Once using the base GL function and a second time using
 * Minecraft's {@link GlStateManager}?<br><br>
 * 
 * <b>Answer: </b><br>
 * Compatibility and robustness<br>
 * In general all MC rendering should go through MC's {@link GlStateManager},
 * however that isn't always the case.
 * So, to prevent issues if a mod (or MC itself) calls a direct GL function
 * instead of the {@link GlStateManager} wrapper, we need to be sure about what the actual
 * set value is (whether setting or getting) and that MC knows what DH has done.
 * This way whether a mod (or MC) is using the {@link GlStateManager} or direct GL calls,
 * they should always have the correct value for anything DH has modified.
 * <br><br>
 * This may slow down some low end GPUs that are driver limited,
 * however James would rather have slow correct rendering vs fast broken rendering.
 */
public class MinecraftGLWrapper implements IMinecraftGLWrapper
{
	public static final MinecraftGLWrapper INSTANCE = new MinecraftGLWrapper();
	
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	
	/*
    private static final StencilState STENCIL;
	 */
	
	
	// scissor //
	
	/** Enables scissor testing */
	@Override
	public void enableScissorTest() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setScissorTestEnabled(ctx, true);
	}
	/** Disables scissor testing */
	@Override
	public void disableScissorTest() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setScissorTestEnabled(ctx, false);
	}
	
	
	// stencil //
//	
//	/** Enables stencil testing */
//	public void enableScissorTest() { GlStateManager._stencilFunc(); }
//	/** Disables stencil testing */
//	public void disableScissorTest() { /* stencil disable */ }
	
	
	// depth //
	
	/** Enables depth testing */
	@Override
	public void enableDepthTest() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setDepthTestEnabled(ctx, true);
	}
	/** Disables depth testing */
	@Override
	public void disableDepthTest() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setDepthTestEnabled(ctx, false);
	}
	
	/** Sets depth comparison function */
	@Override
	public void glDepthFunc(int func) 
	{ 
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setDepthFunc(ctx, func);
	}
	
	/** Enables depth buffer writing */
	@Override
	public void enableDepthMask() 
	{
		net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
	}
	/** Disables depth buffer writing */
	@Override
	public void disableDepthMask() 
	{
		net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(false);
	}
	
	
	// blending //
	
	/** Enables blending */
	@Override
	public void enableBlend() 
	{
		net.irisshaders.iris.gl.blending.BlendModeStorage.setBlendEnabled(true);
	}
	/** Disables blending */
	@Override
	public void disableBlend() 
	{
		net.irisshaders.iris.gl.blending.BlendModeStorage.setBlendEnabled(false);
	}
	
	/** Sets blend function */
	@Override
	public void glBlendFunc(int sfactor, int dfactor) 
	{
		VulkanicAPI.blendFunc(VulkanicAPI.getImmediateContext(), sfactor, dfactor);
		
	}
	/** Sets separate blend functions for RGB and alpha */
	@Override
	public void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) 
	{
		net.irisshaders.iris.gl.blending.BlendModeStorage.setBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
	}
	
	
	// frame buffers //
	
	/** Binds a framebuffer */
	@Override
	public void glBindFramebuffer(int target, int framebuffer) 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.bindFramebuffer(ctx, target, framebuffer);
		net.irisshaders.iris.gl.IrisRenderSystem.bindFramebuffer(target, framebuffer);
	}
	
	
	// buffers //
	
	/** Generates a buffer object */
	@Override
	public int glGenBuffers()
	{
		GlStateManager.incrementTrackedBuffers();
		return VulkanicAPI.createBuffer(VulkanicAPI.getImmediateContext());
	}
	
	/** Deletes a buffer object */
	@Override
	public void glDeleteBuffers(int buffer)
	{
		GlStateManager.decrementTrackedBuffers();
		VulkanicAPI.deleteBuffer(VulkanicAPI.getImmediateContext(), buffer);
		
		// MC's implementation has a bug where it will throw:
		// GL_INVALID_OPERATION in glBufferData(immutable)
		// when attempting to delete Storage Buffers
		// So we need to manually delete the buffers ourselves
		//GlStateManager._glDeleteBuffers(buffer); 
	}
	
	
	// culling //
	
	/** Enables face culling */
	@Override
	public void enableFaceCulling() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCullFaceEnabled(ctx, true);
	}
	/** Disables face culling */
	@Override
	public void disableFaceCulling() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCullFaceEnabled(ctx, false);
	}
	
	
	// textures //
	
	/** Generates a texture object */
	@Override
	public int glGenTextures() { return net.irisshaders.iris.gl.IrisRenderSystem.createTextureId(); }
	/** Deletes a texture object */
	@Override
	public void glDeleteTextures(int texture) { net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(texture); }
	
	/** Sets the active texture unit */
	@Override
	public void glActiveTexture(int textureId) 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setActiveTextureUnit(ctx, textureId);
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTexture(textureId);
	}
	@Override
	public int getActiveTexture() { 
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		return VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_TEXTURE_BINDING_2D); 
	}
	
	/**
	 * Always binds to 2D texture target
	 */
	@Override
	public void glBindTexture(int texture) 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.bindTexture2D(ctx, texture);
	}
	
	
	
	
}
