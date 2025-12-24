package com.seibel.distanthorizons.fabric;

import net.distant_horizons.common.AbstractPluginPacketSender;
import net.distant_horizons.core.network.messages.AbstractNetworkMessage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import net.distant_horizons.common.CommonPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

public class FabricPluginPacketSender extends AbstractPluginPacketSender
{
	@Override
	public void sendToServer(AbstractNetworkMessage message)
	{
		ClientPlayNetworking.send(new CommonPacketPayload(message));
		FriendlyByteBuf buffer = PacketByteBufs.create();
		this.encodeMessage(buffer, message);
		ClientPlayNetworking.send(WRAPPER_PACKET_RESOURCE, buffer);
	}
	
	@Override
	public void sendToClient(ServerPlayer serverPlayer, AbstractNetworkMessage message)
	{
		FriendlyByteBuf buffer = PacketByteBufs.create();
		this.encodeMessage(buffer, message);
		ServerPlayNetworking.send(serverPlayer, WRAPPER_PACKET_RESOURCE, buffer);
	}
	
}