package com.github.alexthe666.citadel.server.message;

import com.github.alexthe666.citadel.Citadel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
// TODO: Replace with Fabric Networking API
// import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AnimationMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnimationMessage> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("citadel", "animation"));
    public static final StreamCodec<FriendlyByteBuf, AnimationMessage> CODEC = StreamCodec.ofMember(AnimationMessage::write, AnimationMessage::read);

    private int entityID;
    private int index;

    public AnimationMessage(int entityID, int index) {
        this.entityID = entityID;
        this.index = index;
    }

    public static AnimationMessage read(FriendlyByteBuf buf) {
        return new AnimationMessage(buf.readInt(), buf.readInt());
    }

    public static void write(AnimationMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.entityID);
        buf.writeInt(message.index);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // TODO: Replace with Fabric ServerPlayNetworking handler
    public static void handle(final AnimationMessage message, PropertiesMessage.PayloadContext context) {
        context.enqueueWork(() -> Citadel.PROXY.handleAnimationPacket(message.entityID, message.index));
    }
}
