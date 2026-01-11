package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class UpdateBossBarMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateBossBarMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "update_boss_bar"));

    public static final StreamCodec<FriendlyByteBuf, UpdateBossBarMessage> CODEC =
        StreamCodec.ofMember(UpdateBossBarMessage::write, UpdateBossBarMessage::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private UUID bossBar;
    private int renderType;

    public UpdateBossBarMessage(UUID bossBar, int renderType) {
        this.bossBar = bossBar;
        this.renderType = renderType;
    }


    public static UpdateBossBarMessage read(FriendlyByteBuf buf) {
        return new UpdateBossBarMessage(buf.readUUID(), buf.readInt());
    }

    public static void write(UpdateBossBarMessage message, FriendlyByteBuf buf) {
        buf.writeUUID(message.bossBar);
        buf.writeInt(message.renderType);
    }

    public static void handle(UpdateBossBarMessage message, IPayloadContext context) {
        // This packet is sent from server to client
        if (!context.flow().isClientbound()) {
            return;
        }
        if (message.renderType == -1) {
            AlexsCaves.PROXY.removeBossBarRender(message.bossBar);
        } else {
            AlexsCaves.PROXY.setBossBarRender(message.bossBar, message.renderType);
        }
    }

}
