package net.distanthorizons.util;

/** 
 * Added to MC's dynamic textures via mixins 
 * in order to denote whether a texture is a lightmap or not. <br><br>
 * 
 * If not done any dynamic texture could be used as the lightmap
 * which causes some weird rendering bugs.
 */
public interface ILightTextureMarker
{
	void markLightTexture();
}
