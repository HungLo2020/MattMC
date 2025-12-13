package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * Fabric API stub for ClientLifecycleEvents
 */
public final class ClientLifecycleEvents {
    public static final Event<ClientStarted> CLIENT_STARTED = Event.create(ClientStarted.class, callbacks -> client -> {
        for (ClientStarted callback : callbacks) {
            callback.onClientStarted(client);
        }
    });
    
    public static final Event<ClientStopping> CLIENT_STOPPING = Event.create(ClientStopping.class, callbacks -> client -> {
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
    
    private ClientLifecycleEvents() {}
}
