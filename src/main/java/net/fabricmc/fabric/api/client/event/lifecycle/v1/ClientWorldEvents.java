package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric API events for client world lifecycle.
 */
public final class ClientWorldEvents {
    /**
     * Called when a client world is loaded and set in the game engines.
     * This is called from Minecraft.updateLevelInEngines() when a world is set.
     */
    public static final Event<Load> WORLD_LOAD = Event.create(Load.class, callbacks -> (client, world) -> {
        for (Load callback : callbacks) {
            callback.onWorldLoad(client, world);
        }
    });
    
    /**
     * Called when a client world is about to be unloaded.
     * This is called from Minecraft.updateLevelInEngines() when a world is being cleared.
     */
    public static final Event<Unload> WORLD_UNLOAD = Event.create(Unload.class, callbacks -> (client, world) -> {
        for (Unload callback : callbacks) {
            callback.onWorldUnload(client, world);
        }
    });
    
    @FunctionalInterface
    public interface Load {
        void onWorldLoad(Minecraft client, ClientLevel world);
    }
    
    @FunctionalInterface
    public interface Unload {
        void onWorldUnload(Minecraft client, @Nullable ClientLevel world);
    }
    
    private ClientWorldEvents() {}
}
