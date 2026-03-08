package com.seibel.distanthorizons.core.wrapperInterfaces.minecraft;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * Used to sync GL state changes between DH and MC.
 * This is specifically important for other mods that change MC's rendering like Iris.
 */
public interface IMinecraftGLWrapper extends IBindable
{
	
	// scissor //
	
	/** Enables scissor testing */
	void enableScissorTest();
	/** Disables scissor testing */
	void disableScissorTest();
	
	
	// stencil //
	
	///** Enables scissor testing */
	//void enableScissorTest() { GlStateManager._enableScissorTest(); }
	///** Disables scissor testing */
	//void disableScissorTest() { GlStateManager._disableScissorTest(); }
	
	
	// depth //
	
	/** Enables depth testing */
	void enableDepthTest();
	/** Disables depth testing */
	void disableDepthTest();
	
	/** Sets depth comparison function */
	void glDepthFunc(int func);
	
	/** Enables depth buffer writing */
	void enableDepthMask();
	/** Disables depth buffer writing */
	void disableDepthMask();
	
	
	
	// blending //
	
	/** Enables blending */
	void enableBlend();
	/** Disables blending */
	void disableBlend();
	
	/** Sets blend function */
	void glBlendFunc(int sfactor, int dfactor);
	/** Sets separate blend functions for RGB and alpha */
	void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha);
	
	
	// buffers //
	
	/** Generates a buffer object */
	int glGenBuffers();
	
	/** Deletes a buffer object */
	void glDeleteBuffers(int buffer);
	
	
	
	// culling //
	
	/** Enables face culling */
	void enableFaceCulling();
	/** Disables face culling */
	void disableFaceCulling();
	
	
	// textures //
	
	/** Generates a texture object */
	int glGenTextures();
	/** Deletes a texture object */
	void glDeleteTextures(int texture);
	
	/** Sets the active texture unit */
	void glActiveTexture(int textureId);
	/** 
	 * Only works for textures bound via this system. <br> 
	 * Returns the currently bound 2D texture
	 */
	int getActiveTexture();
	
	/**
	 * Always binds to 2D texture target
	 */
	void glBindTexture(int texture);
	
}
