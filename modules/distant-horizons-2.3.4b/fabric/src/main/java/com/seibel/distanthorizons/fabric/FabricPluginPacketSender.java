package com.seibel.distanthorizons.fabric;

import com.seibel.distanthorizons.common.AbstractPluginPacketSender;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import com.seibel.distanthorizons.common.CommonPacketPayload;

public class FabricPluginPacketSender extends AbstractPluginPacketSender
{
	@Override
	public void sendToServer(AbstractNetworkMessage message)
	{
				ClientPlayNetworking.send(new CommonPacketPayload(message));
			}
	
	@Override
	public void sendToClient(ServerPlayer serverPlayer, AbstractNetworkMessage message)
	{
				ServerPlayNetworking.send(serverPlayer, new CommonPacketPayload(message));
			}
	
}