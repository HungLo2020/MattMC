package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.entity.item.BeholderEyeEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class BeholderSyncMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BeholderSyncMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "beholder_sync"));

    public static final StreamCodec<FriendlyByteBuf, BeholderSyncMessage> CODEC =
        StreamCodec.ofMember(BeholderSyncMessage::write, BeholderSyncMessage::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int beholderId;
    public boolean active;

    public BeholderSyncMessage(int beholderId, boolean active) {
        this.beholderId = beholderId;
        this.active = active;
    }


    public BeholderSyncMessage() {
    }

    public static BeholderSyncMessage read(FriendlyByteBuf buf) {
        return new BeholderSyncMessage(buf.readInt(), buf.readBoolean());
    }

    public static void write(BeholderSyncMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.beholderId);
        buf.writeBoolean(message.active);
    }

    public static void handle(BeholderSyncMessage message, IPayloadContext context) {
        // This packet is sent from server to client
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> {
                Player playerSided = AlexsCaves.PROXY.getClientSidePlayer();
                if (playerSided != null) {
                    Entity watcher = playerSided.level().getEntity(message.beholderId);
                    if (watcher instanceof BeholderEyeEntity beholderEye) {
                        Entity beholderEyePlayer = beholderEye.getUsingPlayer();
                        beholderEye.hasTakenFullControlOfCamera = true;
                        if (beholderEyePlayer != null && beholderEyePlayer instanceof Player && beholderEyePlayer.equals(playerSided)) {
                            if (message.active) {
                                AlexsCaves.PROXY.setRenderViewEntity(playerSided, beholderEye);
                            } else {
                                AlexsCaves.PROXY.resetRenderViewEntity(playerSided);
                            }
                        }
                    }
                }
            });
        }
    }
}
