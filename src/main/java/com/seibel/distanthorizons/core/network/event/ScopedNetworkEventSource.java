package com.seibel.distanthorizons.core.network.event;

import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;

import java.util.function.Consumer;

/** 
 * Provides a way to register network message handlers which are expected to be removed later. <br><br>
 * 
 * In other words, listeners can be added to this {@link AbstractNetworkEventSource} and when
 * you no longer need any of those listeners you can {@link ScopedNetworkEventSource#close()}
 * this handler to remove all of them.
 */
public final class ScopedNetworkEventSource extends AbstractNetworkEventSource
{
	public final AbstractNetworkEventSource parent;
	private boolean isClosed = false;
	
	private final Consumer<AbstractNetworkMessage> actualHandleMessageStable = this::handleMessage;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ScopedNetworkEventSource(AbstractNetworkEventSource parent) { this.parent = parent; }
	
	
	
	//==================//
	// message handlers //
	//==================//
	
	@Override
	public <T extends AbstractNetworkMessage> void registerHandler(Class<T> handlerClass, Consumer<T> handlerImplementation)
	{
		if (this.isClosed)
		{
			return;
		}
		
		//noinspection unchecked
		this.parent.registerHandler(this, handlerClass, (Consumer<T>) this.actualHandleMessageStable);
		
		super.registerHandler(this, handlerClass, handlerImplementation);
	}
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	@Override
	public void close()
	{
		this.isClosed = true;
		this.parent.removeAllHandlers(this);
	}
	
}