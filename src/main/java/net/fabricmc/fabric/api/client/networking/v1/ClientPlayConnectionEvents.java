package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.PacketFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * Events for client play connection lifecycle.
 * Stub for VoxelMap compatibility.
 */
public final class ClientPlayConnectionEvents {
    private ClientPlayConnectionEvents() {}

    /**
     * Event fired when connection is initialized.
     */
    public static final InitEvent INIT = new InitEvent();

    /**
     * Event fired when player joins.
     */
    public static final JoinEvent JOIN = new JoinEvent();

    /**
     * Event fired when disconnecting.
     */
    public static final DisconnectEvent DISCONNECT = new DisconnectEvent();

    public interface Init {
        void onPlayInit(ClientPacketListener handler, Minecraft client);
    }

    public interface Join {
        void onPlayJoin(ClientPacketListener handler, PacketFlow sender, Minecraft client);
    }

    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }

    /**
     * Event implementation for Init.
     */
    public static class InitEvent {
        private final List<Init> listeners = new ArrayList<>();

        public void register(Init listener) {
            listeners.add(listener);
        }

        public void invokeAll(ClientPacketListener handler, Minecraft client) {
            for (Init listener : listeners) {
                listener.onPlayInit(handler, client);
            }
        }
    }

    /**
     * Event implementation for Join.
     */
    public static class JoinEvent {
        private final List<Join> listeners = new ArrayList<>();

        public void register(Join listener) {
            listeners.add(listener);
        }

        public void invokeAll(ClientPacketListener handler, PacketFlow sender, Minecraft client) {
            for (Join listener : listeners) {
                listener.onPlayJoin(handler, sender, client);
            }
        }
    }

    /**
     * Event implementation for Disconnect.
     */
    public static class DisconnectEvent {
        private final List<Disconnect> listeners = new ArrayList<>();

        public void register(Disconnect listener) {
            listeners.add(listener);
        }

        public void invokeAll(ClientPacketListener handler, Minecraft client) {
            for (Disconnect listener : listeners) {
                listener.onPlayDisconnect(handler, client);
            }
        }
    }
}
