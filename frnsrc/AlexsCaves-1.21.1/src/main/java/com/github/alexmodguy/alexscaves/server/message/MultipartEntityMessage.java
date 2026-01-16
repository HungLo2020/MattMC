package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MultipartEntityMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MultipartEntityMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "multipart_entity"));

    public static final StreamCodec<FriendlyByteBuf, MultipartEntityMessage> CODEC =
        StreamCodec.ofMember(MultipartEntityMessage::write, MultipartEntityMessage::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int parentId;
    public int playerId;
    public int type;

    public MultipartEntityMessage(int parentId, int playerId, int type) {
        this.parentId = parentId;
        this.playerId = playerId;
        this.type = type;
    }


    public MultipartEntityMessage() {
    }

    public static MultipartEntityMessage read(FriendlyByteBuf buf) {
        return new MultipartEntityMessage(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void write(MultipartEntityMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.parentId);
        buf.writeInt(message.playerId);
        buf.writeInt(message.type);
    }

    public static void handle(MultipartEntityMessage message, IPayloadContext context) {
        // This packet is sent from client to server
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null && !player.level().isClientSide) {
                Entity parent = player.level().getEntity(message.parentId);
                if (parent != null && parent.isMultipartEntity() && player.distanceTo(parent) < 16) {
                    if (message.type == 0) {
                        parent.interact(player, player.getUsedItemHand());
                    } else if (message.type == 1) {
                        // Use player's attack method to properly calculate damage with weapons, enchantments, crits, etc.
                        player.attack(parent);
                    }
                }
            }
        });
    }
}
