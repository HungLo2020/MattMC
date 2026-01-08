package com.seibel.distanthorizons.core.network.messages.base;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import io.netty.buffer.ByteBuf;

/**
 * When the communication is about to be stopped, either side can send this message
 * There may be messages after this, but they should be ignored if possible.
 */
public class CloseReasonMessage extends AbstractNetworkMessage
{
	public String reason;

	
	
	//==============//
	// constructors //
	//==============//
	
	public CloseReasonMessage() { }
	public CloseReasonMessage(String reason) { this.reason = reason; }
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out) { this.writeString(this.reason, out); }
	@Override
	public void decode(ByteBuf in) { this.reason = this.readString(in); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("reason", this.reason);
	}
	
}