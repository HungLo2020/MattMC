package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * Fabric API stub for ClientTickEvents
 */
public final class ClientTickEvents {
    public static final Event<StartTick> START_CLIENT_TICK = Event.create(StartTick.class, callbacks -> client -> {
        for (StartTick callback : callbacks) {
            callback.onStartTick(client);
        }
    });
    
    public static final Event<EndTick> END_CLIENT_TICK = Event.create(EndTick.class, callbacks -> client -> {
        for (EndTick callback : callbacks) {
            callback.onEndTick(client);
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
    
    private ClientTickEvents() {}
}
