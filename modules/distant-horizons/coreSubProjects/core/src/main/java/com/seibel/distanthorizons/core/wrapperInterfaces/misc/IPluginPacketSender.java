package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import net.distant_horizons.coreapi.interfaces.dependencyInjection.IBindable;

public interface IPluginPacketSender extends IBindable
{
	/** Sends a packet from the client */
	void sendToServer(AbstractNetworkMessage message);
	/** Sends a packet from the server */
	void sendToClient(IServerPlayerWrapper serverPlayer, AbstractNetworkMessage message);
	
}