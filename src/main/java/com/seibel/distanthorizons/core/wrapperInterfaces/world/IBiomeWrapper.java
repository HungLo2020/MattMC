package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * @author James Seibel
 * @version 3-5-2022
 */
public interface IBiomeWrapper extends IDhApiBiomeWrapper, IBindable
{
	String getName();
	String getSerialString();
	
}
