package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric API stub for ServerEntityWorldChangeEvents
 */
public final class ServerEntityWorldChangeEvents {
    public static final Event<AfterPlayerChange> AFTER_PLAYER_CHANGE_WORLD = Event.create(AfterPlayerChange.class, callbacks -> (player, origin, destination) -> {
        for (AfterPlayerChange callback : callbacks) {
            callback.afterChangeWorld(player, origin, destination);
        }
    });
    
    @FunctionalInterface
    public interface AfterPlayerChange {
        void afterChangeWorld(ServerPlayer player, ServerLevel origin, ServerLevel destination);
    }
    
    private ServerEntityWorldChangeEvents() {}
}
