package net.fabricmc.fabric.api.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Fabric API stub for ServerPlayConnectionEvents
 */
public final class ServerPlayConnectionEvents {
    public static final Event<Join> JOIN = Event.create(Join.class, callbacks -> (handler, sender, server) -> {
        for (Join callback : callbacks) {
            callback.onPlayReady(handler, sender, server);
        }
    });
    
    public static final Event<Disconnect> DISCONNECT = Event.create(Disconnect.class, callbacks -> (handler, server) -> {
        for (Disconnect callback : callbacks) {
            callback.onPlayDisconnect(handler, server);
        }
    });
    
    @FunctionalInterface
    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
    }
    
    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
    
    private ServerPlayConnectionEvents() {}
}
