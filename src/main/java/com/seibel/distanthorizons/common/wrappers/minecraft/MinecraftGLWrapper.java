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
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_SCISSOR_TEST, true);
		GlStateManager._enableScissorTest(); 
	}
	/** Disables scissor testing */
	@Override
	public void disableScissorTest() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_SCISSOR_TEST, false);
		GlStateManager._disableScissorTest(); 
	}
	
	
	// stencil //
//	
//	/** Enables stencil testing */
//	public void enableScissorTest() { GlStateManager._stencilFunc(); }
//	/** Disables stencil testing */
//	public void disableScissorTest() { GlStateManager._disableScissorTest(); }
	
	
	// depth //
	
	/** Enables depth testing */
	@Override
	public void enableDepthTest() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_DEPTH_TEST, true);
		GlStateManager._enableDepthTest(); 
	}
	/** Disables depth testing */
	@Override
	public void disableDepthTest() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_DEPTH_TEST, false);
		GlStateManager._disableDepthTest(); 
	}
	
	/** Sets depth comparison function */
	@Override
	public void glDepthFunc(int func) 
	{ 
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setDepthFunc(ctx, func);
		GlStateManager._depthFunc(func); 
	}
	
	/** Enables depth buffer writing */
	@Override
	public void enableDepthMask() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setDepthWriteMask(ctx, true);
		GlStateManager._depthMask(true); 
	}
	/** Disables depth buffer writing */
	@Override
	public void disableDepthMask() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setDepthWriteMask(ctx, false);
		GlStateManager._depthMask(false); 
	}
	
	
	// blending //
	
	/** Enables blending */
	@Override
	public void enableBlend() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_BLEND, true);
		GlStateManager._enableBlend();
	}
	/** Disables blending */
	@Override
	public void disableBlend() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_BLEND, false);
		GlStateManager._disableBlend(); 
	}
	
	/** Sets blend function */
	@Override
	public void glBlendFunc(int sfactor, int dfactor) 
	{
		VulkanicAPI.glBlendFunc(sfactor, dfactor);
		
	}
	/** Sets separate blend functions for RGB and alpha */
	@Override
	public void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) 
	{
		VulkanicAPI.setBlendFunction(net.vulkanic.VulkanicAPI.getImmediateContext(), sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
		GlStateManager._blendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha); 
	}
	
	
	// frame buffers //
	
	/** Binds a framebuffer */
	@Override
	public void glBindFramebuffer(int target, int framebuffer) 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.bindFramebuffer(ctx, target, framebuffer);
		GlStateManager._glBindFramebuffer(target, framebuffer);
	}
	
	
	// buffers //
	
	/** Generates a buffer object */
	@Override
	public int glGenBuffers()
	{ return GlStateManager._glGenBuffers(); }
	
	/** Deletes a buffer object */
	@Override
	public void glDeleteBuffers(int buffer)
	{
		VulkanicAPI.glDeleteBuffers(buffer);
		
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
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_CULL_FACE, true);
		GlStateManager._enableCull(); 
	}
	/** Disables face culling */
	@Override
	public void disableFaceCulling() 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_CULL_FACE, false);
		GlStateManager._disableCull(); 
	}
	
	
	// textures //
	
	/** Generates a texture object */
	@Override
	public int glGenTextures() { return GlStateManager._genTexture(); }
	/** Deletes a texture object */
	@Override
	public void glDeleteTextures(int texture) { GlStateManager._deleteTexture(texture); }
	
	/** Sets the active texture unit */
	@Override
	public void glActiveTexture(int textureId) 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setActiveTextureUnit(ctx, textureId);
		GlStateManager._activeTexture(textureId);
	}
	@Override
	public int getActiveTexture() { return VulkanicAPI.glGetInteger(VulkanicAPI.GL_TEXTURE_BINDING_2D); }
	
	/**
	 * Always binds to 2D texture target
	 */
	@Override
	public void glBindTexture(int texture) 
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.bindTexture2D(ctx, texture);
		GlStateManager._bindTexture(texture);
	}
	
	
	
	
}
