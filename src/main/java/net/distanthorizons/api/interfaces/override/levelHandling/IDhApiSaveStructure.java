package net.distanthorizons.api.interfaces.override.levelHandling;

import net.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import net.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;

import java.io.File;

/**
 * Used to override which folder DH uses when loading a level.
 * Can be used to redirect LOD data saving into a more manageable location
 * or for replays/local-servers that are running out of a different folder
 * than where the DH data is normally saved.
 * 
 * @author James Seibel
 * @version 2024-9-28
 * @since API 4.0.0
 */
public interface IDhApiSaveStructure extends IDhApiOverrideable
{
	/**
	 * Called when DH first loads a level to determine which folder it should use
	 * for file handling.
	 * 
	 * @param currentFilePath the file path DH is planning to use. If this method returns null this is the file path that will be used.
	 * @param levelWrapper the level this file path is used for.
	 * @return null if you don't want to override the file path. Non-null if you want to change the file path.
	 */
	File overrideFilePath(File currentFilePath, IDhApiLevelWrapper levelWrapper);
	
}
