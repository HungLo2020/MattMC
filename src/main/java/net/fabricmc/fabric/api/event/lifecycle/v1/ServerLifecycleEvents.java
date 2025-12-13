package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

/**
 * Fabric API stub for ServerLifecycleEvents
 */
public final class ServerLifecycleEvents {
    public static final Event<ServerStarting> SERVER_STARTING = Event.create(ServerStarting.class, callbacks -> server -> {
        for (ServerStarting callback : callbacks) {
            callback.onServerStarting(server);
        }
    });
    
    public static final Event<ServerStarted> SERVER_STARTED = Event.create(ServerStarted.class, callbacks -> server -> {
        for (ServerStarted callback : callbacks) {
            callback.onServerStarted(server);
        }
    });
    
    public static final Event<ServerStopping> SERVER_STOPPING = Event.create(ServerStopping.class, callbacks -> server -> {
        for (ServerStopping callback : callbacks) {
            callback.onServerStopping(server);
        }
    });
    
    public static final Event<ServerStopped> SERVER_STOPPED = Event.create(ServerStopped.class, callbacks -> server -> {
        for (ServerStopped callback : callbacks) {
            callback.onServerStopped(server);
        }
    });
    
    @FunctionalInterface
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }
    
    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }
    
    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }
    
    @FunctionalInterface
    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }
    
    private ServerLifecycleEvents() {}
}
