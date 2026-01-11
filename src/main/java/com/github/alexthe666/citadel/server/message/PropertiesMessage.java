package com.github.alexthe666.citadel.server.message;

import com.github.alexthe666.citadel.Citadel;
import com.github.alexthe666.citadel.server.entity.CitadelEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
// TODO: Replace with Fabric Networking API
// import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PropertiesMessage implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PropertiesMessage> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("citadel", "properties"));
    public static final StreamCodec<FriendlyByteBuf, PropertiesMessage> CODEC = StreamCodec.ofMember(PropertiesMessage::write, PropertiesMessage::read);
    private String propertyID;
    private CompoundTag compound;
    private int entityID;

    public PropertiesMessage(String propertyID, CompoundTag compound, int entityID) {
        this.propertyID = propertyID;
        this.compound = compound;
        this.entityID = entityID;
    }

    public static void write(PropertiesMessage message, FriendlyByteBuf packetBuffer) {
        PacketBufferUtils.writeUTF8String(packetBuffer, message.propertyID);
        PacketBufferUtils.writeTag(packetBuffer, message.compound);
        packetBuffer.writeInt(message.entityID);
    }

    public static PropertiesMessage read(FriendlyByteBuf packetBuffer) {
        return new PropertiesMessage(PacketBufferUtils.readUTF8String(packetBuffer), PacketBufferUtils.readTag(packetBuffer), packetBuffer.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Temporary context interface - TODO: Replace with Fabric networking
    public interface PayloadContext {
        Player player();
        boolean isClientbound();
        void enqueueWork(Runnable task);
    }

    // TODO: Replace with Fabric ServerPlayNetworking handler
    public static void handle(final PropertiesMessage message, PayloadContext context) {
        context.enqueueWork(() -> {
            if (context.isClientbound()) {
                Citadel.PROXY.handlePropertiesPacket(message.propertyID, message.compound, message.entityID);
            } else {
                Entity e = context.player().level().getEntity(message.entityID);
                if (e instanceof LivingEntity && (message.propertyID.equals("CitadelPatreonConfig") || message.propertyID.equals("CitadelTagUpdate"))) {
                    CitadelEntityData.setCitadelTag((LivingEntity) e, message.compound);
                }
            }
        });
    }
}