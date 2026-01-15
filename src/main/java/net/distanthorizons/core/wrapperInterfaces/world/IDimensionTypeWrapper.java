package net.distanthorizons.core.wrapperInterfaces.world;

import net.distanthorizons.api.interfaces.world.IDhApiDimensionTypeWrapper;
import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

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
