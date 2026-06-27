package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * @author James Seibel
 * @version 3-5-2022
 */
public interface ILightMapWrapper extends IBindable
{
	int OPENGL_LIGHTMAP_TEXTURE_UNIT = 0;
	int VULKAN_LIGHTMAP_TEXTURE_UNIT = 2;
	
	/** Returns the bound texture position */
	void bind();
	void unbind();
	
}
