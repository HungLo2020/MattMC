package com.github.alexthe666.alexsmobs.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class EffectDebilitatingSting extends MobEffect {

    public EffectDebilitatingSting() {
        super(MobEffectCategory.HARMFUL, 0xFF9400);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration > 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Debilitating effect - prevents movement and attack
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        return true;
    }

    public String getDescriptionId() {
        return "minecraft.potion.debilitating_sting";
    }
}
