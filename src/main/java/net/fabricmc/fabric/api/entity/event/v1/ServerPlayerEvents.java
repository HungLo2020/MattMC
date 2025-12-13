package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric API stub for ServerPlayerEvents
 */
public final class ServerPlayerEvents {
    public static final Event<CopyFrom> COPY_FROM = Event.create(CopyFrom.class, callbacks -> (oldPlayer, newPlayer, alive) -> {
        for (CopyFrom callback : callbacks) {
            callback.copyFromPlayer(oldPlayer, newPlayer, alive);
        }
    });
    
    @FunctionalInterface
    public interface CopyFrom {
        void copyFromPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }
    
    private ServerPlayerEvents() {}
}
