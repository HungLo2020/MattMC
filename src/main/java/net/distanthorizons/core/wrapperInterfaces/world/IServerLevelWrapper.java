package net.distanthorizons.core.wrapperInterfaces.world;

import java.io.File;

public interface IServerLevelWrapper extends ILevelWrapper
{
	File getMcSaveFolder();
	
	String getKeyedLevelDimensionName();
	
}
