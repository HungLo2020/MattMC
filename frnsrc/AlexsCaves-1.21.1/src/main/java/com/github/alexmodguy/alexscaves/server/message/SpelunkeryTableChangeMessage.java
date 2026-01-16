package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.inventory.SpelunkeryTableMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SpelunkeryTableChangeMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpelunkeryTableChangeMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "spelunkery_table_change"));

    public static final StreamCodec<FriendlyByteBuf, SpelunkeryTableChangeMessage> CODEC =
        StreamCodec.ofMember(SpelunkeryTableChangeMessage::write, SpelunkeryTableChangeMessage::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public boolean pass;

    public SpelunkeryTableChangeMessage(boolean pass) {
        this.pass = pass;
    }


    public SpelunkeryTableChangeMessage() {
    }

    public static SpelunkeryTableChangeMessage read(FriendlyByteBuf buf) {
        return new SpelunkeryTableChangeMessage(buf.readBoolean());
    }

    public static void write(SpelunkeryTableChangeMessage message, FriendlyByteBuf buf) {
        buf.writeBoolean(message.pass);
    }

    public static void handle(SpelunkeryTableChangeMessage message, IPayloadContext context) {
        // This packet is sent from client to server
        Player player = context.player();
        if (player != null) {
            if (player.containerMenu instanceof SpelunkeryTableMenu tableMenu) {
                tableMenu.onMessageFromScreen(player, message.pass);
            }
        }
    }
}
