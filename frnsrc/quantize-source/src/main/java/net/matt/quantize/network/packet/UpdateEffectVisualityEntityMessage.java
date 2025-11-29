package net.matt.quantize.network.packet;

import net.matt.quantize.Quantize;
import net.matt.quantize.effects.QEffects;
//import com.github.alexmodguy.alexscaves.server.potion.IrradiatedEffect;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateEffectVisualityEntityMessage {

    private int entityID;
    private int fromEntityID;
    private int potionType;
    private int duration;

    private boolean remove;

    public UpdateEffectVisualityEntityMessage(int entityID, int fromEntityID, int potionType, int duration) {
        this(entityID, fromEntityID, potionType, duration, false);
    }

    public UpdateEffectVisualityEntityMessage(int entityID, int fromEntityID, int potionType, int duration, boolean remove) {
        this.entityID = entityID;
        this.fromEntityID = fromEntityID;
        this.potionType = potionType;
        this.duration = duration;
        this.remove = remove;
    }


    public static UpdateEffectVisualityEntityMessage read(FriendlyByteBuf buf) {
        return new UpdateEffectVisualityEntityMessage(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void write(UpdateEffectVisualityEntityMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.entityID);
        buf.writeInt(message.fromEntityID);
        buf.writeInt(message.potionType);
        buf.writeInt(message.duration);
        buf.writeBoolean(message.remove);
    }

    public static void handle(UpdateEffectVisualityEntityMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        Player playerSided = context.get().getSender();
        if (context.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
            playerSided = Quantize.PROXY.getClientSidePlayer();
        }
        if (playerSided != null) {
            Entity entity = playerSided.level().getEntity(message.entityID);
            Entity senderEntity = playerSided.level().getEntity(message.fromEntityID);
            if (entity instanceof LivingEntity living && senderEntity != null && senderEntity.distanceTo(living) < 32) {
                MobEffect mobEffect = null;
                int level = 0;
                switch (message.potionType) {
                    case 0:
                        //mobEffect = QEffects.IRRADIATED.get();
                        break;
                    case 1:
                        //mobEffect = QEffects.BUBBLED.get();
                        //entity.playSound(QSounds.SEA_STAFF_BUBBLE.get());
                        break;
                    case 2:
                        //mobEffect = QEffects.MAGNETIZING.get();
                        break;
                    case 3:
                        mobEffect = QEffects.STUNNED.get();
                        break;
                    case 4:
                        //mobEffect = QEffects.IRRADIATED.get();
                        //level = IrradiatedEffect.BLUE_LEVEL;
                        break;
                }
                if (mobEffect != null) {
                    if (message.remove) {
                        living.removeEffectNoUpdate(mobEffect);
                    } else {
                        living.addEffect(new MobEffectInstance(mobEffect, message.duration, level));
                    }
                }
            }
        }
    }

}
