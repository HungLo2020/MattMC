package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.entity.util.PossessesCamera;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PossessionKeyMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PossessionKeyMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "possession_key"));

    public static final StreamCodec<FriendlyByteBuf, PossessionKeyMessage> CODEC =
        StreamCodec.ofMember(PossessionKeyMessage::write, PossessionKeyMessage::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int watcher;
    public int playerId;
    public int type;

    public PossessionKeyMessage(int watcher, int playerId, int type) {
        this.watcher = watcher;
        this.playerId = playerId;
        this.type = type;
    }


    public PossessionKeyMessage() {
    }

    public static PossessionKeyMessage read(FriendlyByteBuf buf) {
        return new PossessionKeyMessage(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void write(PossessionKeyMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.watcher);
        buf.writeInt(message.playerId);
        buf.writeInt(message.type);
    }

    public static void handle(PossessionKeyMessage message, IPayloadContext context) {
        // This packet is sent from client to server
        context.enqueueWork(() -> {
            Player playerSided = context.player();
            if (playerSided != null) {
                Entity watcher = playerSided.level().getEntity(message.watcher);
                Entity keyPresser = playerSided.level().getEntity(message.playerId);
                if (watcher instanceof PossessesCamera watcherEntity && keyPresser instanceof Player) {
                    watcherEntity.onPossessionKeyPacket(keyPresser, message.type);
                }
            }
        });
    }
}
