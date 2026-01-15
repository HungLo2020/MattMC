package net.distanthorizons.core.wrapperInterfaces.minecraft;

import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import org.lwjgl.opengl.GL32;

/**
 * Used to sync GL state changes between DH and MC.
 * This is specifically important for other mods that change MC's rendering like Iris.
 */
public interface IMinecraftGLWrapper extends IBindable
{
	
	// scissor //
	
	/** @see GL32#GL_SCISSOR_TEST */
	void enableScissorTest();
	/** @see GL32#GL_SCISSOR_TEST */
	void disableScissorTest();
	
	
	// stencil //
	
	///** @see GL32#GL_SCISSOR_TEST */
	//void enableScissorTest() { GlStateManager._enableScissorTest(); }
	///** @see GL32#GL_SCISSOR_TEST */
	//void disableScissorTest() { GlStateManager._disableScissorTest(); }
	
	
	// depth //
	
	/** @see GL32#GL_DEPTH_TEST */
	void enableDepthTest();
	/** @see GL32#GL_DEPTH_TEST */
	void disableDepthTest();
	
	/** @see GL32#glDepthFunc(int)  */
	void glDepthFunc(int func);
	
	/** @see GL32#glDepthMask(boolean) */
	void enableDepthMask();
	/** @see GL32#glDepthMask(boolean) */
	void disableDepthMask();
	
	
	
	// blending //
	
	/** @see GL32#GL_BLEND */
	void enableBlend();
	/** @see GL32#GL_BLEND */
	void disableBlend();
	
	/** @see GL32#glBlendFunc */
	void glBlendFunc(int sfactor, int dfactor);
	/** @see GL32#glBlendFuncSeparate */
	void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha);
	
	
	// frame buffers //
	
	/** @see GL32#glBindFramebuffer */
	void glBindFramebuffer(int target, int framebuffer);
	
	
	// buffers //
	
	/** @see GL32#glGenBuffers() */
	int glGenBuffers();
	
	/** @see GL32#glDeleteBuffers(int)  */
	void glDeleteBuffers(int buffer);
	
	
	
	// culling //
	
	/** @see GL32#GL_CULL_FACE */
	void enableFaceCulling();
	/** @see GL32#GL_CULL_FACE */
	void disableFaceCulling();
	
	
	// textures //
	
	/** @see GL32#glGenTextures() */
	int glGenTextures();
	/** @see GL32#glDeleteTextures(int) */
	void glDeleteTextures(int texture);
	
	/** @see GL32#glActiveTexture(int) */
	void glActiveTexture(int textureId);
	/** 
	 * Only works for textures bound via this system. <br> 
	 * Returns the bound {@link GL32#GL_TEXTURE_BINDING_2D} 
	 */
	int getActiveTexture();
	
	/**
	 * Always binds to {@link GL32#GL_TEXTURE_2D}
	 * @see GL32#glBindTexture(int, int)
	 */
	void glBindTexture(int texture);
	
}
