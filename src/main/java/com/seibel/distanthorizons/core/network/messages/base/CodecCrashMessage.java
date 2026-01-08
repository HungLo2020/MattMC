package com.seibel.distanthorizons.core.network.messages.base;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import io.netty.buffer.ByteBuf;

public class CodecCrashMessage extends AbstractNetworkMessage
{
	public ECrashPhase crashPhase;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public CodecCrashMessage() { }
	public CodecCrashMessage(ECrashPhase crashPhase) { this.crashPhase = crashPhase; }
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out)
	{
		if (this.crashPhase == ECrashPhase.ENCODE)
		{
			throw new RuntimeException("encode force crash");
		}
	}

	@Override
	public void decode(ByteBuf in) { throw new RuntimeException("decode force crash"); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("crashPhase", this.crashPhase);
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/**
	 * ENCODE, <br>
	 * DECODE, <br>
	 */
	public enum ECrashPhase
	{
		ENCODE,
		DECODE
	}
	
}