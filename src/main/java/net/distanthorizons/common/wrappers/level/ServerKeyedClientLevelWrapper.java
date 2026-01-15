package net.distanthorizons.common.wrappers.level;

import net.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import net.distanthorizons.core.level.IServerKeyedClientLevel;
import net.minecraft.client.multiplayer.ClientLevel;

public class ServerKeyedClientLevelWrapper extends ClientLevelWrapper implements IServerKeyedClientLevel
{
	/** Returns the folder name the server wants the client to use. */
	private final String serverKey;
	
	/** A unique identifier (generally the level's name) for differentiating multiverse levels */
	private final String serverLevelKey;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ServerKeyedClientLevelWrapper(ClientLevel level, String serverKey, String serverLevelKey)
	{
		super(level);
		this.serverKey = serverKey;
		this.serverLevelKey = serverLevelKey;
	}
	
	
	@Override
	public String getServerKey() { return this.serverKey; }
	
	//======================//
	// level identification //
	//======================//
	
	@Override
	public String getServerLevelKey() { return this.serverLevelKey; }
	
	@Override
	public String getDhIdentifier() { return this.getServerLevelKey(); }
	
	
	
}
