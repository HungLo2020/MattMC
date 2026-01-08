package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

public interface IDhServerLevel extends IDhLevel
{
    IServerLevelWrapper getServerLevelWrapper();
	
}
