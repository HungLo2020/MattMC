package net.distanthorizons.api.interfaces.world;

import net.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;

/**
 * @author James Seibel
 * @version 2022-7-14
 * @since API 1.0.0
 */
public interface IDhApiDimensionTypeWrapper extends IDhApiUnsafeWrapper
{
	boolean hasCeiling();
	
	boolean hasSkyLight();
	
}
