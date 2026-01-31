package net.alexsmobs.message;

import net.alexsmobs.AlexsMobs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Stub for multipart hurt messaging
 * Networking not critical for direct source integration
 */
public class MessageHurtMultipart implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageHurtMultipart> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsMobs.MODID, "hurt_multipart"));
    public static final StreamCodec<FriendlyByteBuf, MessageHurtMultipart> CODEC = StreamCodec.ofMember(MessageHurtMultipart::write, MessageHurtMultipart::read);

    public int part;
    public int parent;
    public float damage;
    public String damageType;

    public MessageHurtMultipart(int part, int parent, float damage) {
        this.part = part;
        this.parent = parent;
        this.damage = damage;
        this.damageType = "";
    }

    public MessageHurtMultipart(int part, int parent, float damage, String damageType) {
        this.part = part;
        this.parent = parent;
        this.damage = damage;
        this.damageType = damageType;
    }

    public MessageHurtMultipart() {}

    public static MessageHurtMultipart read(FriendlyByteBuf buf) {
        return new MessageHurtMultipart(buf.readInt(), buf.readInt(), buf.readFloat(), buf.readUtf());
    }

    public static void write(MessageHurtMultipart message, FriendlyByteBuf buf) {
        buf.writeInt(message.part);
        buf.writeInt(message.parent);
        buf.writeFloat(message.damage);
        buf.writeUtf(message.damageType);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Stub - networking not implemented for direct source integration
}
