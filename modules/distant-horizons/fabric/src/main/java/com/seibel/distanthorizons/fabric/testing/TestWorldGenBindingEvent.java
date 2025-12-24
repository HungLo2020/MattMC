package com.seibel.distanthorizons.fabric.testing;

import net.distant_horizons.api.DhApi;
import net.distant_horizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import net.distant_horizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import net.distant_horizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.distant_horizons.core.logging.DhLoggerBuilder;
import net.minecraft.server.level.ServerLevel;
import net.distant_horizons.core.logging.DhLogger;

// TODO add to API example once Builderb0y has given the all-clear
public class TestWorldGenBindingEvent extends DhApiLevelLoadEvent
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	static
	{
		//LOGGER.info("[DH-WORLDGEN-BINDING] ########## TestWorldGenBindingEvent CLASS LOADED ##########");
	}
	
	@Override
	public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> event)
	{
		//LOGGER.info("[DH-WORLDGEN-BINDING] ========== onLevelLoad() CALLED ==========");
		//LOGGER.info("[DH-WORLDGEN-BINDING] Level: " + event.value.levelWrapper);
		//LOGGER.info("[DH-WORLDGEN-BINDING] Dimension: " + event.value.levelWrapper.getDimensionType());
		//LOGGER.info("[DH-WORLDGEN-BINDING] Thread: " + Thread.currentThread().getName());
		LOGGER.info("DH Level: ["+event.value.levelWrapper.getDimensionType()+"] loaded.");
		
		try
		{
			//LOGGER.info("[DH-WORLDGEN-BINDING] Attempting to cast level wrapper to ServerLevel");
			// Note: whenever you use a wrapper method on a new Minecraft version it is recommended that you
			// call wrapper.getClass() to determine which object the API will return before you try casting it.
			ServerLevel level = (ServerLevel) event.value.levelWrapper.getWrappedMcObject();
			//LOGGER.info("[DH-WORLDGEN-BINDING] Successfully cast to ServerLevel: " + level);
			
			// override the core DH world generator for this level
			//IDhApiWorldGenerator exampleWorldGen = new TestChunkWorldGenerator(level); // TODO biomes are broken for some reason
			//LOGGER.info("[DH-WORLDGEN-BINDING] Creating TestGenericWorldGenerator");
			IDhApiWorldGenerator exampleWorldGen = new TestGenericWorldGenerator(event.value.levelWrapper);
			//LOGGER.info("[DH-WORLDGEN-BINDING] TestGenericWorldGenerator created: " + exampleWorldGen);
			
			//LOGGER.info("[DH-WORLDGEN-BINDING] Registering world generator override");
			DhApi.worldGenOverrides.registerWorldGeneratorOverride(event.value.levelWrapper, exampleWorldGen);
			//LOGGER.info("[DH-WORLDGEN-BINDING] ========== WORLD GENERATOR REGISTERED SUCCESSFULLY ==========");
		}
		catch (ClassCastException e)
		{
			//LOGGER.warn("[DH-WORLDGEN-BINDING] Unable to add world generator to level wrapper ["+event.value.levelWrapper.getClass()+"] - ["+event.value.levelWrapper.getDimensionType()+"].", e);
		}
		catch (Exception e)
		{
			//LOGGER.error("[DH-WORLDGEN-BINDING] Error registering world generator: " + e.getMessage(), e);
		}
		//LOGGER.info("[DH-WORLDGEN-BINDING] ========== onLevelLoad() COMPLETE ==========");
	}
}
