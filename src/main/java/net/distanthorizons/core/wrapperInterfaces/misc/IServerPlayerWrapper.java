package net.distanthorizons.core.wrapperInterfaces.misc;

import net.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.distanthorizons.core.util.math.Vec3d;

public interface IServerPlayerWrapper extends IDhApiUnsafeWrapper
{
	String getName();
	
	IServerLevelWrapper getLevel();
	
	Vec3d getPosition();
	
}
