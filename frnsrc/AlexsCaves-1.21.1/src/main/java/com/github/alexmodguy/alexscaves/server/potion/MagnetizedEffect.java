package com.github.alexmodguy.alexscaves.server.potion;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.message.UpdateEffectVisualityEntityMessage;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class MagnetizedEffect extends MobEffect {

    protected MagnetizedEffect() {
        super(MobEffectCategory.NEUTRAL, 0X53556C);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int tick) {
        if (!entity.level().isClientSide && entity.tickCount % 20 == 0) {
            MobEffectInstance instance = entity.getEffect(ACEffectRegistry.MAGNETIZING);
            if (instance != null) {
                AlexsCaves.sendMSGToAll(new UpdateEffectVisualityEntityMessage(entity.getId(), entity.getId(), 2, instance.getDuration()));
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration > 0;
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            MobEffectInstance instance = entity.getEffect(ACEffectRegistry.MAGNETIZING);
            if (instance != null) {
                AlexsCaves.sendMSGToAll(new UpdateEffectVisualityEntityMessage(entity.getId(), entity.getId(), 2, instance.getDuration()));
            }
        }
    }

}
