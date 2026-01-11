package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class WorldEventMessage implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorldEventMessage> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "world_event"));
    public static final StreamCodec<FriendlyByteBuf, WorldEventMessage> CODEC = 
        StreamCodec.ofMember(WorldEventMessage::write, WorldEventMessage::read);

    public int messageId;
    public int blockX;
    public int blockY;
    public int blockZ;

    public WorldEventMessage(int messageId, int blockX, int blockY, int blockZ) {
        this.messageId = messageId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    public WorldEventMessage() {}

    public static WorldEventMessage read(FriendlyByteBuf buf) {
        return new WorldEventMessage(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void write(WorldEventMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.messageId);
        buf.writeInt(message.blockX);
        buf.writeInt(message.blockY);
        buf.writeInt(message.blockZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WorldEventMessage message, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player playerSided = context.player();
            if (context.flow().isClientbound()) {
                playerSided = AlexsCaves.PROXY.getClientSidePlayer();
            }
            if (playerSided != null && playerSided.level() != null) {
                BlockPos blockPos = new BlockPos(message.blockX, message.blockY, message.blockZ);
                AlexsCaves.PROXY.playWorldEvent(message.messageId, playerSided.level(), blockPos);
            }
        });
    }
}
