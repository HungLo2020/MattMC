package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

/**
 * Fabric API stub for ServerTickEvents
 */
public final class ServerTickEvents {
    public static final Event<StartTick> START_SERVER_TICK = Event.create(StartTick.class, callbacks -> server -> {
        for (StartTick callback : callbacks) {
            callback.onStartTick(server);
        }
    });
    
    public static final Event<EndTick> END_SERVER_TICK = Event.create(EndTick.class, callbacks -> server -> {
        for (EndTick callback : callbacks) {
            callback.onEndTick(server);
        }
    });
    
    @FunctionalInterface
    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }
    
    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }
    
    private ServerTickEvents() {}
}
