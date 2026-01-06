package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side networking functionality for configuration stage.
 * Stub for VoxelMap compatibility.
 */
public final class ClientConfigurationNetworking {
    private static final Map<CustomPacketPayload.Type<?>, ConfigurationPayloadHandler<?>> GLOBAL_RECEIVERS = new HashMap<>();

    private ClientConfigurationNetworking() {}

    /**
     * Registers a global receiver for a custom payload type during configuration.
     */
    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type,
            ConfigurationPayloadHandler<T> handler) {
        GLOBAL_RECEIVERS.put(type, handler);
        return true;
    }

    /**
     * Handler for configuration payloads.
     */
    public interface ConfigurationPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    /**
     * Context for configuration networking.
     */
    public interface Context {
        Minecraft client();
        ClientConfigurationPacketListenerImpl handler();
        void responseSender(); // Simplified
    }

    /**
     * Called by Minecraft's packet handler to dispatch to registered handlers.
     */
    public static <T extends CustomPacketPayload> boolean handlePayload(T payload, Context context) {
        @SuppressWarnings("unchecked")
        ConfigurationPayloadHandler<T> handler = (ConfigurationPayloadHandler<T>) GLOBAL_RECEIVERS.get(payload.type());
        if (handler != null) {
            handler.receive(payload, context);
            return true;
        }
        return false;
    }
}
