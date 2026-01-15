package net.distanthorizons.core.level;

import net.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import net.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import net.distanthorizons.core.file.structure.ISaveStructure;
import net.distanthorizons.core.generation.BatchGenerator;
import net.distanthorizons.core.generation.WorldGenerationQueue;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.coreapi.DependencyInjection.WorldGeneratorInjector;
import net.distanthorizons.core.logging.DhLogger;

import java.io.IOException;
import java.sql.SQLException;

public class ServerLevelModule implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private final IDhServerLevel parentServerLevel;
	public final ISaveStructure saveStructure;
	public final GeneratedFullDataSourceProvider fullDataFileHandler;
	
	public final LodRequestModule lodRequestModule;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ServerLevelModule(IDhServerLevel parentServerLevel, ISaveStructure saveStructure) throws SQLException, IOException
	{
		this.parentServerLevel = parentServerLevel;
		this.saveStructure = saveStructure;
		this.fullDataFileHandler = new GeneratedFullDataSourceProvider(parentServerLevel, saveStructure);
		this.lodRequestModule = new LodRequestModule(this.parentServerLevel, this.parentServerLevel, this.fullDataFileHandler, () -> new LodRequestState(this.parentServerLevel));
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public void close()
	{
		// shutdown the world-gen
		this.lodRequestModule.close();
		this.fullDataFileHandler.close();
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class LodRequestState extends LodRequestModule.AbstractLodRequestState
	{
		LodRequestState(IDhServerLevel level)
		{
			//LOGGER.info("[DH-WORLDGEN-QUEUE] ========== CREATING WorldGenerationQueue ==========");
			//LOGGER.info("[DH-WORLDGEN-QUEUE] Level: " + level.getLevelWrapper());
			//LOGGER.info("[DH-WORLDGEN-QUEUE] Thread: " + Thread.currentThread().getName());
			
			IDhApiWorldGenerator worldGenerator = WorldGeneratorInjector.INSTANCE.get(level.getLevelWrapper());
			//LOGGER.info("[DH-WORLDGEN-QUEUE] WorldGenerator from injector: " + worldGenerator);
			
			if (worldGenerator == null)
			{
				//LOGGER.info("[DH-WORLDGEN-QUEUE] No override generator found, creating BatchGenerator");
				// no override generator is bound, use the Core world generator
				worldGenerator = new BatchGenerator(level);
				// binding the core generator won't prevent other mods from binding their own generators
				// since core world generator's should have the lowest override priority
				WorldGeneratorInjector.INSTANCE.bind(level.getLevelWrapper(), worldGenerator);
				//LOGGER.info("[DH-WORLDGEN-QUEUE] BatchGenerator created and bound: " + worldGenerator);
			}
			else
			{
				//LOGGER.info("[DH-WORLDGEN-QUEUE] Using override generator: " + worldGenerator);
			}
			
			//LOGGER.info("[DH-WORLDGEN-QUEUE] Creating WorldGenerationQueue with generator: " + worldGenerator.getClass().getName());
			this.retrievalQueue = new WorldGenerationQueue(worldGenerator, level);
			//LOGGER.info("[DH-WORLDGEN-QUEUE] ========== WorldGenerationQueue CREATED ==========");
		}
		
	}
	
}
