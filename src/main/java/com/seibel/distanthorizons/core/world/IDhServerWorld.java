package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerStateManager;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.jetbrains.annotations.Nullable;

/** Used both for dedicated server and singleplayer worlds */
public interface IDhServerWorld extends IDhWorld
{
	ServerPlayerStateManager getServerPlayerStateManager();
	void addPlayer(IServerPlayerWrapper serverPlayer);
	void removePlayer(IServerPlayerWrapper serverPlayer);
	void changePlayerLevel(IServerPlayerWrapper player, IServerLevelWrapper originLevel, IServerLevelWrapper destinationLevel);
	
	@Nullable
	default IDhServerLevel getOrLoadServerLevel(ILevelWrapper levelWrapper) { return (IDhServerLevel) this.getOrLoadLevel(levelWrapper); }
	
}
