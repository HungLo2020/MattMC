package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.core.util.math.Vec3d;

import java.net.SocketAddress;

public interface IServerPlayerWrapper extends IDhApiUnsafeWrapper
{
	String getName();
	
	IServerLevelWrapper getLevel();
	
	Vec3d getPosition();
	
}
