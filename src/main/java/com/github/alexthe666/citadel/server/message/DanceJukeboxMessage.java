package com.github.alexthe666.citadel.server.message;

import com.github.alexthe666.citadel.Citadel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
// TODO: Replace with Fabric Networking API
// import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DanceJukeboxMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DanceJukeboxMessage> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("citadel", "dance_jukebox"));
    public static final StreamCodec<FriendlyByteBuf, DanceJukeboxMessage> CODEC = StreamCodec.ofMember(DanceJukeboxMessage::write, DanceJukeboxMessage::read);

    public int entityID;
    public boolean dance;
    public BlockPos jukeBox;

    public DanceJukeboxMessage(int entityID, boolean dance, BlockPos jukeBox) {
        this.entityID = entityID;
        this.dance = dance;
        this.jukeBox = jukeBox;
    }

    public DanceJukeboxMessage() {
    }

    public static DanceJukeboxMessage read(FriendlyByteBuf buf) {
        return new DanceJukeboxMessage(buf.readInt(), buf.readBoolean(), buf.readBlockPos());
    }

    public static void write(DanceJukeboxMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.entityID);
        buf.writeBoolean(message.dance);
        buf.writeBlockPos(message.jukeBox);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // TODO: Replace with Fabric ServerPlayNetworking handler
    public static void handle(final DanceJukeboxMessage message, PropertiesMessage.PayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (context.isClientbound()) {
                player = Citadel.PROXY.getClientSidePlayer();
            }
            if (player != null) {
                Citadel.PROXY.handleJukeboxPacket(player.level(), message.entityID, message.jukeBox, message.dance);

            }
        });
    }
}