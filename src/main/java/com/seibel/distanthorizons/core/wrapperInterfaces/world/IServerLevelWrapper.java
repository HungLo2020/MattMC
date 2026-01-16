package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;

import java.io.File;

public interface IServerLevelWrapper extends ILevelWrapper
{
	File getMcSaveFolder();
	
	String getKeyedLevelDimensionName();
	
}
