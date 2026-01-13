package com.github.alexmodguy.alexscaves.server.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import javax.annotation.Nullable;

public class MobTargetUntamedGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
    private final TamableAnimal tamableMob;

    public MobTargetUntamedGoal(TamableAnimal tamableAnimal, Class<T> clazz, int chance, boolean seeCheck, boolean reachCheck, @Nullable TargetingConditions.Selector selector) {
        super(tamableAnimal, clazz, chance, seeCheck, reachCheck, selector);
        this.tamableMob = tamableAnimal;
    }

    public boolean canUse() {
        return !this.tamableMob.isTame() && super.canUse();
    }

    public boolean canContinueToUse() {
        return this.targetConditions != null ? this.targetConditions.test(getServerLevel(this.mob), this.mob, this.target) : super.canContinueToUse();
    }
}
