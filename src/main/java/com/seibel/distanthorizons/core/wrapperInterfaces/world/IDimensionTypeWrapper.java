package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiDimensionTypeWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

public interface IDimensionTypeWrapper extends IDhApiDimensionTypeWrapper, IBindable
{
	@Override
	boolean hasCeiling();
	
	String getName();
	
	@Override
	boolean hasSkyLight();
	
	boolean isTheEnd();
	
	double getCoordinateScale();
	
}
