package net.distanthorizons.core.network.messages.fullData;

import com.google.common.base.MoreObjects;
import net.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import net.distanthorizons.core.network.INetworkObject;
import net.distanthorizons.core.network.messages.AbstractTrackableMessage;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * Response message, containing the requested full data source,
 * or null if requested in updates-only mode and the data was not updated.
 */
public class FullDataSourceResponseMessage extends AbstractTrackableMessage
{
	@Nullable
	public FullDataPayload payload;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceResponseMessage() { }
	public FullDataSourceResponseMessage(@Nullable FullDataPayload payload)
	{
		if (payload != null)
		{
			this.payload = payload;
		}
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encodeInternal(ByteBuf out)
	{
		if (this.writeOptional(out, this.payload))
		{
			this.payload.encode(out);
		}
	}
	
	@Override
	public void decodeInternal(ByteBuf in) { this.payload = this.readOptional(in, () -> INetworkObject.decodeToInstance(new FullDataPayload(), in)); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("payload", this.payload);
	}
	
}