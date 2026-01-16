package com.seibel.distanthorizons.api.interfaces.override.worldGenerator;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;

/**
 * Handles adding world generator overrides.
 *
 * @author James Seibel
 * @version 2022-12-10
 * @since API 1.0.0
 */
public interface IDhApiWorldGeneratorOverrideRegister
{
	/**
	 * Registers the given world generator for the given level. <Br> <Br>
	 *
	 * Only one world generator can be registered for a specific level at a given time. <Br>
	 * If another world generator has already been registered, DhApiResult will return
	 * the name of the previously registered generator and success = false.
	 */
	DhApiResult<Void> registerWorldGeneratorOverride(IDhApiLevelWrapper levelWrapper, IDhApiWorldGenerator worldGenerator);
	
}
