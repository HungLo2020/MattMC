package com.github.alexmodguy.alexscaves.server.message;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.entity.util.MagnetUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Field;

public class PlayerJumpFromMagnetMessage implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerJumpFromMagnetMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "player_jump_from_magnet"));

    public static final StreamCodec<FriendlyByteBuf, PlayerJumpFromMagnetMessage> CODEC =
        StreamCodec.ofMember(PlayerJumpFromMagnetMessage::write, PlayerJumpFromMagnetMessage::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private int entityID;
    private boolean jumping;
    
    private static Field jumpingField;

    public PlayerJumpFromMagnetMessage(int entityID, boolean jumping) {
        this.entityID = entityID;
        this.jumping = jumping;
    }


    public static PlayerJumpFromMagnetMessage read(FriendlyByteBuf buf) {
        return new PlayerJumpFromMagnetMessage(buf.readInt(), buf.readBoolean());
    }

    public static void write(PlayerJumpFromMagnetMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.entityID);
        buf.writeBoolean(message.jumping);
    }

    public static void handle(PlayerJumpFromMagnetMessage message, IPayloadContext context) {
        // Packet handling is automatic in NeoForge;
        Player player = context.player();
        if (player != null) {
            Entity entity = player.level().getEntity(message.entityID);
            if (MagnetUtil.isPulledByMagnets(entity) && entity instanceof LivingEntity living) {
                setJumping(living, message.jumping);
            }
        }
    }
    
    private static void setJumping(LivingEntity living, boolean jumping) {
        try {
            if (jumpingField == null) {
                jumpingField = ObfuscationReflectionHelper.findField(LivingEntity.class, "jumping");
            }
            jumpingField.setBoolean(living, jumping);
        } catch (Exception e) {
            AlexsCaves.LOGGER.error("Failed to set jumping field on LivingEntity", e);
        }
    }

}
