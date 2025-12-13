package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric API stub for ClientPlayNetworking
 */
public final class ClientPlayNetworking {
    
    public static void send(CustomPacketPayload payload) {
        // Stub implementation
    }
    
    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type,
            PlayPayloadHandler<T> handler) {
        // Stub implementation
        return true;
    }
    
    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    
    public interface Context {
        Minecraft client();
        ClientPacketListener responseSender();
    }
    
    private ClientPlayNetworking() {}
}
