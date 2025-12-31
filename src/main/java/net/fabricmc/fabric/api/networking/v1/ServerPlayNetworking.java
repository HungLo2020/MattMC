package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side networking functionality for play stage.
 * Simplified stub for Distant Horizons compatibility.
 */
public final class ServerPlayNetworking {
	private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> GLOBAL_RECEIVERS = new HashMap<>();
	
	private ServerPlayNetworking() {}
	
	/**
	 * Registers a global receiver for a custom payload type.
	 */
	public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
		CustomPacketPayload.Type<T> type, 
		PlayPayloadHandler<T> handler) {
		GLOBAL_RECEIVERS.put(type, handler);
		return true;
	}
	
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
	
	@FunctionalInterface
	public interface PlayPayloadHandler<T extends CustomPacketPayload> {
		void receive(T payload, Context context);
	}
	
	public interface Context {
		ServerPlayer player();
		ServerGamePacketListenerImpl listener();
		void execute(Runnable task);
	}
}
