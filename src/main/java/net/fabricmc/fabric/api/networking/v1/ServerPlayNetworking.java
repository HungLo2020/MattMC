package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Fabric API stub for ServerPlayNetworking
 */
public final class ServerPlayNetworking {
    
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
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
        ServerPlayer player();
        ServerGamePacketListenerImpl responseSender();
    }
    
    private ServerPlayNetworking() {}
}
