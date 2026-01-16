package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class ClientChunkEvents {
	private ClientChunkEvents() {
	}

	/**
	 * Called when a chunk is loaded into a ClientLevel.
	 *
	 * <p>When this event is called, the chunk is already in the world.
	 */
	public static final Event<ClientChunkEvents.Load> CHUNK_LOAD = EventFactory.createArrayBacked(ClientChunkEvents.Load.class, callbacks -> (clientWorld, chunk) -> {
		for (Load callback : callbacks) {
			callback.onChunkLoad(clientWorld, chunk);
		}
	});

	/**
	 * Called when a chunk is about to be unloaded from a ClientLevel.
	 *
	 * <p>When this event is called, the chunk is still present in the world.
	 */
	public static final Event<ClientChunkEvents.Unload> CHUNK_UNLOAD = EventFactory.createArrayBacked(ClientChunkEvents.Unload.class, callbacks -> (clientWorld, chunk) -> {
		for (Unload callback : callbacks) {
			callback.onChunkUnload(clientWorld, chunk);
		}
	});

	@FunctionalInterface
	public interface Load {
		void onChunkLoad(ClientLevel world, LevelChunk chunk);
	}

	@FunctionalInterface
	public interface Unload {
		void onChunkUnload(ClientLevel world, LevelChunk chunk);
	}
}
