package net.distanthorizons.api.methods.override;

import net.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import net.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGeneratorOverrideRegister;
import net.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import net.distanthorizons.api.objects.DhApiResult;
import net.distanthorizons.coreapi.DependencyInjection.WorldGeneratorInjector;

/**
 * Handles adding world generator overrides.
 *
 * @author James Seibel
 * @version 2022-12-10
 * @since API 1.0.0
 */
public class DhApiWorldGeneratorOverrideRegister implements IDhApiWorldGeneratorOverrideRegister
{
	public static DhApiWorldGeneratorOverrideRegister INSTANCE = new DhApiWorldGeneratorOverrideRegister();
	
	private DhApiWorldGeneratorOverrideRegister() { }
	
	
	
	@Override
	public DhApiResult<Void> registerWorldGeneratorOverride(IDhApiLevelWrapper levelWrapper, IDhApiWorldGenerator worldGenerator)
	{
		try
		{
			WorldGeneratorInjector.INSTANCE.bind(levelWrapper, worldGenerator);
			return DhApiResult.createSuccess();
		}
		catch (Exception e)
		{
			return DhApiResult.createFail(e.getMessage());
		}
	}
	
}
