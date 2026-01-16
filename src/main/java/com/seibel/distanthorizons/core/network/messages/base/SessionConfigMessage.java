package com.seibel.distanthorizons.core.network.messages.base;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.multiplayer.config.SessionConfig;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import io.netty.buffer.ByteBuf;

/** used for full DH support */
public class SessionConfigMessage extends AbstractNetworkMessage
{
	public SessionConfig config;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public SessionConfigMessage() { }
	public SessionConfigMessage(SessionConfig config) { this.config = config; }
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out) { this.config.encode(out); }
	
	@Override
	public void decode(ByteBuf in) { this.config = INetworkObject.decodeToInstance(new SessionConfig(), in); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("config", this.config);
	}
	
}