package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric API stub for PayloadTypeRegistry
 */
public final class PayloadTypeRegistry<B extends FriendlyByteBuf> {
    private static final PayloadTypeRegistry<FriendlyByteBuf> PLAY_C2S = new PayloadTypeRegistry<>();
    private static final PayloadTypeRegistry<FriendlyByteBuf> PLAY_S2C = new PayloadTypeRegistry<>();
    private static final PayloadTypeRegistry<FriendlyByteBuf> CONFIG_C2S = new PayloadTypeRegistry<>();
    private static final PayloadTypeRegistry<FriendlyByteBuf> CONFIG_S2C = new PayloadTypeRegistry<>();
    
    public static PayloadTypeRegistry<FriendlyByteBuf> playC2S() {
        return PLAY_C2S;
    }
    
    public static PayloadTypeRegistry<FriendlyByteBuf> playS2C() {
        return PLAY_S2C;
    }
    
    public static PayloadTypeRegistry<FriendlyByteBuf> configurationC2S() {
        return CONFIG_C2S;
    }
    
    public static PayloadTypeRegistry<FriendlyByteBuf> configurationS2C() {
        return CONFIG_S2C;
    }
    
    public <T extends CustomPacketPayload> PayloadTypeRegistry<B> register(
            CustomPacketPayload.Type<T> type, 
            StreamCodec<? super B, T> codec) {
        // Stub - no actual registration in this simplified implementation
        return this;
    }
    
    private PayloadTypeRegistry() {}
}
