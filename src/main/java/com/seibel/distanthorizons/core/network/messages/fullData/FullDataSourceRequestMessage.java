package com.seibel.distanthorizons.core.network.messages.fullData;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.messages.ILevelRelatedMessage;
import com.seibel.distanthorizons.core.network.messages.AbstractTrackableMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;

public class FullDataSourceRequestMessage extends AbstractTrackableMessage implements ILevelRelatedMessage
{
	public long sectionPos;
	
	/** Only present when requesting changes. */
	@Nullable
	public Long clientTimestamp;
	
	private String levelName;
	@Override
	public String getLevelName() { return this.levelName; }
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public FullDataSourceRequestMessage() {}
	public FullDataSourceRequestMessage(ILevelWrapper levelWrapper, long sectionPos, @Nullable Long clientTimestamp)
	{
		this.levelName = levelWrapper.getDhIdentifier();
		this.sectionPos = sectionPos;
		this.clientTimestamp = clientTimestamp;
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
    public void encodeInternal(ByteBuf out)
	{
		this.writeString(this.levelName, out);
		out.writeLong(this.sectionPos);
		if (this.writeOptional(out, this.clientTimestamp))
		{
			out.writeLong(this.clientTimestamp);
		}
    }

    @Override
    public void decodeInternal(ByteBuf in)
	{
		this.levelName = this.readString(in);
		this.sectionPos = in.readLong();
		this.clientTimestamp = this.readOptional(in, in::readLong);
    }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("levelName", this.levelName)
				.add("sectionPos", this.sectionPos)
				.add("clientTimestamp", this.clientTimestamp);
	}
	
}