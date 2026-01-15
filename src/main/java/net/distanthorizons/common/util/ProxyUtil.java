package net.distanthorizons.common.util;

import net.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import net.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

public class ProxyUtil
{
	
	public static ILevelWrapper getLevelWrapper(LevelAccessor level)
	{
		ILevelWrapper levelWrapper;
		if (level instanceof ServerLevel)
		{
			levelWrapper = ServerLevelWrapper.getWrapper((ServerLevel) level);
		}
		else
		{
			levelWrapper = ClientLevelWrapper.getWrapper((ClientLevel) level);
		}
		
		return levelWrapper;
	}
	
}
