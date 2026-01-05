package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Events for client configuration connection lifecycle.
 * Stub for VoxelMap compatibility.
 */
public final class ClientConfigurationConnectionEvents {
    private ClientConfigurationConnectionEvents() {}

    /**
     * Event fired when configuration connection initializes.
     */
    public static final Event<Init> INIT = new Event<>();

    public interface Init {
        void onConfigurationInit(ClientConfigurationPacketListenerImpl handler, Minecraft client);
    }

    /**
     * Simple event implementation.
     */
    public static class Event<T> {
        private final List<T> listeners = new ArrayList<>();

        public void register(T listener) {
            listeners.add(listener);
        }

        public List<T> getListeners() {
            return listeners;
        }

        public void invokeAll(ClientConfigurationPacketListenerImpl handler, Minecraft client) {
            for (Object listener : listeners) {
                if (listener instanceof Init) {
                    ((Init) listener).onConfigurationInit(handler, client);
                }
            }
        }
    }
}
