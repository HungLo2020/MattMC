package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class ServerWorldEvents {
	/**
	 * Called just after a world is loaded by a Minecraft server.
	 *
	 * <p>This can be used to load world specific metadata or initialize a {@link PersistentState} on a server world.
	 */
	public static final Event<Load> LOAD = EventFactory.createArrayBacked(Load.class, callbacks -> (server, world) -> {
		for (Load callback : callbacks) {
			callback.onWorldLoad(server, world);
		}
	});

	/**
	 * Called before a world is unloaded by a Minecraft server.
	 *
	 * <p>This typically occurs after a server has {@link ServerLifecycleEvents#SERVER_STOPPING started shutting down}.
	 * Mods which allow dynamic world (un)registration should call this event so mods can let go of world handles when a world is removed.
	 */
	public static final Event<Unload> UNLOAD = EventFactory.createArrayBacked(Unload.class, callbacks -> (server, world) -> {
		for (Unload callback : callbacks) {
			callback.onWorldUnload(server, world);
		}
	});

	@FunctionalInterface
	public interface Load {
		void onWorldLoad(MinecraftServer server, ServerLevel world);
	}

	@FunctionalInterface
	public interface Unload {
		void onWorldUnload(MinecraftServer server, ServerLevel world);
	}

	private ServerWorldEvents() {
	}
}
