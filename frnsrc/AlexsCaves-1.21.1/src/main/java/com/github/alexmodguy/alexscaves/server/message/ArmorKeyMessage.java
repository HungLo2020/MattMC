package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.item.KeybindUsingArmor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ArmorKeyMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ArmorKeyMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "armor_key"));

    public static final StreamCodec<FriendlyByteBuf, ArmorKeyMessage> CODEC =
        StreamCodec.ofMember(ArmorKeyMessage::write, ArmorKeyMessage::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int equipmentSlot;
    public int playerId;
    public int type;

    public ArmorKeyMessage(int equipmentSlot, int playerId, int type) {
        this.equipmentSlot = equipmentSlot;
        this.playerId = playerId;
        this.type = type;
    }


    public ArmorKeyMessage() {
    }

    public static ArmorKeyMessage read(FriendlyByteBuf buf) {
        return new ArmorKeyMessage(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void write(ArmorKeyMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.equipmentSlot);
        buf.writeInt(message.playerId);
        buf.writeInt(message.type);
    }

    public static void handle(ArmorKeyMessage message, IPayloadContext context) {
        // This packet is sent from client to server
        context.enqueueWork(() -> {
            Player playerSided = context.player();
            if (playerSided != null) {
                Entity keyPresser = playerSided.level().getEntity(message.playerId);
                EquipmentSlot equipmentSlot1 = EquipmentSlot.values()[Mth.clamp(message.equipmentSlot, 0, EquipmentSlot.values().length - 1)];
                if (keyPresser != null && keyPresser instanceof Player player) {
                    ItemStack stack = player.getItemBySlot(equipmentSlot1);
                    if (stack.getItem() instanceof KeybindUsingArmor armor) {
                        armor.onKeyPacket(keyPresser, stack, message.type);
                    }
                }
            }
        });
    }
}
