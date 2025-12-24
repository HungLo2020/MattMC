package com.seibel.distanthorizons.api.interfaces.render;

import net.distant_horizons.api.DhApi;
import net.distant_horizons.api.interfaces.world.IDhApiLevelWrapper;
import net.distant_horizons.api.interfaces.world.IDhApiWorldProxy;

/**
 * Handles adding and removing
 * {@link IDhApiRenderableBoxGroup} objects,
 * from DH's renderer. <br><br>
 * 
 * Can be accessed in 
 * {@link DhApi.Delayed#worldProxy} -> {@link IDhApiLevelWrapper}.
 *
 * @see IDhApiCustomRenderObjectFactory
 * @see IDhApiRenderableBoxGroup
 * @see IDhApiWorldProxy
 * @see IDhApiLevelWrapper
 *
 * @author James Seibel
 * @version 2024-7-3
 * @since API 3.0.0
 */
public interface IDhApiCustomRenderRegister
{
	void add(IDhApiRenderableBoxGroup cubeGroup) throws IllegalArgumentException;
	
	IDhApiRenderableBoxGroup remove(long id);
	
}
