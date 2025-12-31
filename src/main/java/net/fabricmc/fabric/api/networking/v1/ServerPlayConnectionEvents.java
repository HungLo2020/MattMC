package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Events related to the connection to a client on a logical server while a client is in game.
 * Simplified stub for Distant Horizons compatibility.
 */
public final class ServerPlayConnectionEvents {
	
	/**
	 * An event for when a player has just joined the server (after login).
	 */
	public static final Event<Join> JOIN = EventFactory.createArrayBacked(Join.class, callbacks -> (handler, sender, server) -> {
		for (Join callback : callbacks) {
			callback.onPlayReady(handler, sender, server);
		}
	});
	
	/**
	 * An event for when a player is disconnecting.
	 */
	public static final Event<Disconnect> DISCONNECT = EventFactory.createArrayBacked(Disconnect.class, callbacks -> (handler, server) -> {
		for (Disconnect callback : callbacks) {
			callback.onPlayDisconnect(handler, server);
		}
	});
	
	@FunctionalInterface
	public interface Join {
		void onPlayReady(ServerPlayer handler, PacketSender sender, MinecraftServer server);
	}
	
	@FunctionalInterface
	public interface Disconnect {
		void onPlayDisconnect(ServerPlayer handler, MinecraftServer server);
	}
}
