package net.distanthorizons.core.network.messages.requests;

import com.google.common.base.MoreObjects;
import net.distanthorizons.core.network.exceptions.RateLimitedException;
import net.distanthorizons.core.network.exceptions.RequestOutOfRangeException;
import net.distanthorizons.core.network.exceptions.RequestRejectedException;
import net.distanthorizons.core.network.exceptions.SectionRequiresSplittingException;
import net.distanthorizons.core.network.messages.AbstractTrackableMessage;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

public class ExceptionMessage extends AbstractTrackableMessage
{
	private static final List<Class<? extends Exception>> EXCEPTION_LIST = new ArrayList<Class<? extends Exception>>()
	{{
		// All exceptions here must include constructor: (String)
		this.add(RateLimitedException.class);
		this.add(RequestOutOfRangeException.class);
		this.add(RequestRejectedException.class);
		this.add(SectionRequiresSplittingException.class);
	}};
	
	public Exception exception;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public ExceptionMessage() { }
	public ExceptionMessage(Exception exception) { this.exception = exception; }
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override 
	protected void encodeInternal(ByteBuf out)
	{
		out.writeInt(EXCEPTION_LIST.indexOf(this.exception.getClass()));
		this.writeString(this.exception.getMessage(), out);
	}
	
	@Override 
	protected void decodeInternal(ByteBuf in) throws Exception
	{
		int id = in.readInt();
		String message = this.readString(in);
		this.exception = EXCEPTION_LIST.get(id).getDeclaredConstructor(String.class).newInstance(message);
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("exception", this.exception);
	}
	
}