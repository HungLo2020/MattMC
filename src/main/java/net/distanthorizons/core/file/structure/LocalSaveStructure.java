package net.distanthorizons.core.file.structure;

import net.distanthorizons.api.interfaces.override.levelHandling.IDhApiSaveStructure;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.world.EWorldEnvironment;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import net.distanthorizons.coreapi.util.StringUtil;
import net.distanthorizons.core.logging.DhLogger;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Designed for {@link EWorldEnvironment#CLIENT_SERVER} & {@link EWorldEnvironment#SERVER_ONLY} environments.
 */
public class LocalSaveStructure implements ISaveStructure
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private final ConcurrentHashMap<ILevelWrapper, File> levelWrapperToFileMap = new ConcurrentHashMap<>();
	
	
	
	public LocalSaveStructure() { }
	
	
	
	//================//
	// folder methods //
	//================//
	
	@Override
	public File getSaveFolder(ILevelWrapper levelWrapper)
	{
		return this.levelWrapperToFileMap.computeIfAbsent(levelWrapper, (newLevelWrapper) ->
		{
			IServerLevelWrapper serverLevelWrapper = (IServerLevelWrapper) levelWrapper;
			File saveFolder = serverLevelWrapper.getMcSaveFolder();
			
			
			// Allow API users to override the save folder
			IDhApiSaveStructure saveStructureOverride = OverrideInjector.INSTANCE.get(IDhApiSaveStructure.class);
			if (saveStructureOverride != null)
			{
				File overrideFile = saveStructureOverride.overrideFilePath(saveFolder, levelWrapper);
				if (overrideFile != null)
				{
					LOGGER.info("Save folder overridden from [" + saveFolder.getPath() + "] -> [" + overrideFile.getPath() + "].");
					saveFolder = overrideFile;
				}
			}
			
			return saveFolder;
		});
	}
	
	@Override
	public File getPre23SaveFolder(ILevelWrapper levelWrapper) { return this.getSaveFolder(levelWrapper); }
	
	
	//==================//
	// override methods //
	//==================//
	
	@Override
	public void close() throws Exception { }
	
	@Override
	public String toString() 
	{ return "[" + this.getClass().getSimpleName() + "@(" + StringUtil.join(";", this.levelWrapperToFileMap.values()) + ")]"; }
	
}
