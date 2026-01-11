package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class UpdateBossEruptionStatus implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateBossEruptionStatus> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "update_boss_eruption_status"));

    public static final StreamCodec<FriendlyByteBuf, UpdateBossEruptionStatus> CODEC =
        StreamCodec.ofMember(UpdateBossEruptionStatus::write, UpdateBossEruptionStatus::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private int entityId;
    private boolean erupting;

    public UpdateBossEruptionStatus(int entityId, boolean erupting) {
        this.entityId = entityId;
        this.erupting = erupting;
    }


    public static UpdateBossEruptionStatus read(FriendlyByteBuf buf) {
        return new UpdateBossEruptionStatus(buf.readInt(), buf.readBoolean());
    }

    public static void write(UpdateBossEruptionStatus message, FriendlyByteBuf buf) {
        buf.writeInt(message.entityId);
        buf.writeBoolean(message.erupting);
    }

    public static void handle(UpdateBossEruptionStatus message, IPayloadContext context) {
        // This packet is sent from server to client
        if (!context.flow().isClientbound()) {
            return;
        }
        Player playerSided = AlexsCaves.PROXY.getClientSidePlayer();
        if (playerSided != null) {
            AlexsCaves.PROXY.setPrimordialBossActive(playerSided.level(), message.entityId, message.erupting);
        }
    }

}
