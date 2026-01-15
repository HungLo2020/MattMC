package net.distanthorizons.core.file.structure;

import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import java.io.File;

/** Used to determining where LOD data should be saved to. */
public interface ISaveStructure extends AutoCloseable
{
	String DATABASE_NAME = "DistantHorizons.sqlite";
	
	/** 
	 * Returns the folder that contains LOD data for the given {@link ILevelWrapper}.
	 * If no appropriate folder exists, one will be created. 
	 */
	File getSaveFolder(ILevelWrapper levelWrapper);
	
	File getPre23SaveFolder(ILevelWrapper levelWrapper);
	
}

