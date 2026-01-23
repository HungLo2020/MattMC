package com.github.alexthe666.alexsmobs.message;

import com.github.alexthe666.alexsmobs.AlexsMobs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Stub for multipart interact messaging
 * Networking not critical for direct source integration
 */
public class MessageInteractMultipart implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageInteractMultipart> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsMobs.MODID, "interact_multipart"));
    public static final StreamCodec<FriendlyByteBuf, MessageInteractMultipart> CODEC = StreamCodec.ofMember(MessageInteractMultipart::write, MessageInteractMultipart::read);

    public boolean offhand;
    public int parent;

    public MessageInteractMultipart(int parent, boolean offhand) {
        this.parent = parent;
        this.offhand = offhand;
    }

    public MessageInteractMultipart() {}

    public static MessageInteractMultipart read(FriendlyByteBuf buf) {
        return new MessageInteractMultipart(buf.readInt(), buf.readBoolean());
    }

    public static void write(MessageInteractMultipart message, FriendlyByteBuf buf) {
        buf.writeInt(message.parent);
        buf.writeBoolean(message.offhand);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Stub - networking not implemented for direct source integration
}
