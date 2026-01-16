package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class ClientTickEvents {
	private ClientTickEvents() {
	}

	/**
	 * Called at the start of the client tick.
	 */
	public static final Event<StartTick> START_CLIENT_TICK = EventFactory.createArrayBacked(StartTick.class, callbacks -> client -> {
		for (StartTick event : callbacks) {
			event.onStartTick(client);
		}
	});

	/**
	 * Called at the end of the client tick.
	 */
	public static final Event<EndTick> END_CLIENT_TICK = EventFactory.createArrayBacked(EndTick.class, callbacks -> client -> {
		for (EndTick event : callbacks) {
			event.onEndTick(client);
		}
	});

	/**
	 * Called at the start of a ClientLevel's tick.
	 */
	public static final Event<StartWorldTick> START_WORLD_TICK = EventFactory.createArrayBacked(StartWorldTick.class, callbacks -> world -> {
		for (StartWorldTick callback : callbacks) {
			callback.onStartTick(world);
		}
	});

	/**
	 * Called at the end of a ClientLevel's tick.
	 *
	 * <p>End of world tick may be used to start async computations for the next tick.
	 */
	public static final Event<EndWorldTick> END_WORLD_TICK = EventFactory.createArrayBacked(EndWorldTick.class, callbacks -> world -> {
		for (EndWorldTick callback : callbacks) {
			callback.onEndTick(world);
		}
	});

	@FunctionalInterface
	public interface StartTick {
		void onStartTick(Minecraft client);
	}

	@FunctionalInterface
	public interface EndTick {
		void onEndTick(Minecraft client);
	}

	@FunctionalInterface
	public interface StartWorldTick {
		void onStartTick(ClientLevel world);
	}

	@FunctionalInterface
	public interface EndWorldTick {
		void onEndTick(ClientLevel world);
	}
}
