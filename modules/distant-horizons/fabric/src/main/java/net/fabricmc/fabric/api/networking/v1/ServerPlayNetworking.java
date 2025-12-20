package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side networking functionality for play stage.
 * Simplified stub for Distant Horizons compatibility.
 */
public final class ServerPlayNetworking {
	
	private ServerPlayNetworking() {}
	
	/**
	 * Sends a packet to a player via a channel.
	 */
	public static void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
		// For compatibility - in real usage, should wrap in CustomPacketPayload
	}
	
	/**
	 * Sends a custom payload to a player.
	 */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && player.connection != null) {
			player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload));
		}
	}
}
