package com.seibel.distanthorizons.api.interfaces.block;

import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;

/**
 * A Minecraft version independent way of handling Biomes.
 *
 * @author James Seibel
 * @version 3-5-2022
 * @since API 1.0.0
 */
public interface IDhApiBiomeWrapper extends IDhApiUnsafeWrapper
{
	String getName();
	
}
