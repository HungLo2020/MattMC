package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * @author James Seibel
 * @version 3-5-2022
 */
public interface ILightMapWrapper extends IBindable
{
	
	/** Returns the bound texture position */
	void bind();
	void unbind();
	
}
