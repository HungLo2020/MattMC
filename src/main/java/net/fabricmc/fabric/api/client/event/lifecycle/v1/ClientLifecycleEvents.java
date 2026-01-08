package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class ClientLifecycleEvents {
	private ClientLifecycleEvents() {
	}

	/**
	 * Called when Minecraft has started and it's client about to tick for the first time.
	 *
	 * <p>This occurs while the splash screen is displayed.
	 */
	public static final Event<ClientStarted> CLIENT_STARTED = EventFactory.createArrayBacked(ClientStarted.class, callbacks -> client -> {
		for (ClientStarted callback : callbacks) {
			callback.onClientStarted(client);
		}
	});

	/**
	 * Called when Minecraft's client begins to stop.
	 * This is caused by quitting while in game, or closing the game window.
	 *
	 * <p>This will be called before the integrated server is stopped if it is running.
	 */
	public static final Event<ClientStopping> CLIENT_STOPPING = EventFactory.createArrayBacked(ClientStopping.class, callbacks -> client -> {
		for (ClientStopping callback : callbacks) {
			callback.onClientStopping(client);
		}
	});

	@FunctionalInterface
	public interface ClientStarted {
		void onClientStarted(Minecraft client);
	}

	@FunctionalInterface
	public interface ClientStopping {
		void onClientStopping(Minecraft client);
	}
}
