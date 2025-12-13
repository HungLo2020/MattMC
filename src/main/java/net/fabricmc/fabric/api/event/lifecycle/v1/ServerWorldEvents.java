package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Fabric API stub for ServerWorldEvents
 */
public final class ServerWorldEvents {
    public static final Event<Load> LOAD = Event.create(Load.class, callbacks -> (server, world) -> {
        for (Load callback : callbacks) {
            callback.onWorldLoad(server, world);
        }
    });
    
    public static final Event<Unload> UNLOAD = Event.create(Unload.class, callbacks -> (server, world) -> {
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
    
    private ServerWorldEvents() {}
}
