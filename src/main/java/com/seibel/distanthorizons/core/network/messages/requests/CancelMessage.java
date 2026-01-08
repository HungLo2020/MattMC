package com.seibel.distanthorizons.core.network.messages.requests;

import com.seibel.distanthorizons.core.network.messages.AbstractTrackableMessage;
import io.netty.buffer.ByteBuf;

public class CancelMessage extends AbstractTrackableMessage
{
	public CancelMessage() { }
	
	
	
	@Override
	public void encodeInternal(ByteBuf out) { }
	@Override
	public void decodeInternal(ByteBuf in) { }
	
}