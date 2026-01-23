package com.github.alexthe666.alexsmobs.entity.ai;

import com.github.alexthe666.alexsmobs.entity.EntityCapuchinMonkey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class CapuchinAIMelee extends MeleeAttackGoal {

    private final EntityCapuchinMonkey monkey;

    public CapuchinAIMelee(EntityCapuchinMonkey monkey, double speedIn, boolean useLongMemory) {
        super(monkey, speedIn, useLongMemory);
        this.monkey = monkey;
    }

    public boolean canUse() {
        return super.canUse() && !monkey.attackDecision;
    }

    public boolean canContinueToUse() {
        return super.canContinueToUse() && !monkey.attackDecision;
    }

    protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
        double d0 = getAttackReachSqr(enemy);
        if (distToEnemySqr <= d0) {
            this.resetAttackCooldown();
            this.mob.swing(InteractionHand.MAIN_HAND);
            if (this.mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                this.mob.doHurtTarget(serverLevel, enemy);
            }
        }

    }

    protected double getAttackReachSqr(LivingEntity target) {
        return (double)(this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + target.getBbWidth());
    }

}
