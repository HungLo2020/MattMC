package com.seibel.distanthorizons.core.network.messages.fullData;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.network.messages.ILevelRelatedMessage;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import io.netty.buffer.ByteBuf;

public class FullDataPartialUpdateMessage extends AbstractNetworkMessage implements ILevelRelatedMessage
{
	public FullDataPayload payload;
	
	private String levelName;
	@Override
	public String getLevelName() { return this.levelName; }
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public FullDataPartialUpdateMessage() { }
	public FullDataPartialUpdateMessage(IServerLevelWrapper level, FullDataPayload payload)
	{
		this.levelName = level.getKeyedLevelDimensionName();
		this.payload = payload;
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out)
	{
		this.writeString(this.levelName, out);
		this.payload.encode(out);
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.levelName = this.readString(in);
		this.payload = INetworkObject.decodeToInstance(new FullDataPayload(), in);
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("levelName", this.levelName)
				.add("payload", this.payload);
	}
	
}