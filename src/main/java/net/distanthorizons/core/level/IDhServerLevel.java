package net.distanthorizons.core.level;

import net.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

public interface IDhServerLevel extends IDhLevel
{
    IServerLevelWrapper getServerLevelWrapper();
	
}
