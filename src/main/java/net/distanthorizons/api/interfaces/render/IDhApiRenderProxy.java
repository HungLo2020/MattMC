package net.distanthorizons.api.interfaces.render;

import net.distanthorizons.api.objects.DhApiResult;


/**
 * Used to interact with Distant Horizons' rendering system.
 *
 * @author James Seibel
 * @version 2024-7-27
 * @since API 1.0.0
 */
public interface IDhApiRenderProxy
{
	/**
	 * Forces any cached render data to be deleted and regenerated.
	 * This is generally called whenever resource packs are changed or specific
	 * rendering settings are changed in Distant Horizon's config. <Br><Br>
	 *
	 * If this is called on a dedicated server it won't do anything and will return {@link DhApiResult#success} = false <Br><Br>
	 *
	 * Background: <Br>
	 * When rendering Distant Horizons bakes each block's color into the geometry that's rendered. <Br>
	 * This improves rendering speed and VRAM size, but prevents dynamically changing LOD colors. <Br>
	 */
	DhApiResult<Boolean> clearRenderDataCache();
	
	
	
	//=======================//
	// OpenGL object getters //
	//=======================//
	
	/**
	 * Returns the name of Distant Horizons' depth texture. <br>
	 * Will return {@link DhApiResult#success} = false and {@link DhApiResult#payload} = -1 if the texture hasn't been created yet.
	 */
	DhApiResult<Integer> getDhDepthTextureId();
	
	/**
	 * Returns the name of Distant Horizons' color texture. <br>
	 * Will return {@link DhApiResult#success} = false and {@link DhApiResult#payload} = -1 if the texture hasn't been created yet.
	 */
	DhApiResult<Integer> getDhColorTextureId();
	
	
	
	//======================//
	// Shader compatibility //
	//======================//
	
	/**
	 * If set to true DH won't render opaque and transparent LODs in the same pass.
	 * Instead, opaque objects will be rendered at the normal time, but 
	 * transparent objects will only be rendered in a second pass during Minecraft's
	 * own transparent rendering pass.
	 */
	void setDeferTransparentRendering(boolean deferTransparentRendering);
	/** @return If DH should defer transparent rendering or not. */
	boolean getDeferTransparentRendering();
	
	/** This may change based on FOV, player speed, and other factors. */
	float getNearClipPlaneDistanceInBlocks(float partialTicks);
	
	
}
