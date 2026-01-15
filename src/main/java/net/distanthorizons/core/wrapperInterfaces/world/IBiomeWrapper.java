package net.distanthorizons.core.wrapperInterfaces.world;

import net.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * @author James Seibel
 * @version 3-5-2022
 */
public interface IBiomeWrapper extends IDhApiBiomeWrapper, IBindable
{
	String getName();
	String getSerialString();
	
}
