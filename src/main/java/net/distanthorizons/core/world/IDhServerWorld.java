package net.distanthorizons.core.world;

import net.distanthorizons.core.level.IDhServerLevel;
import net.distanthorizons.core.multiplayer.server.ServerPlayerStateManager;
import net.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
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
