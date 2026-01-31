package net.alexsmobs.entity.ai;

import net.alexsmobs.entity.EntityCrocodile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class CrocodileAIMelee extends MeleeAttackGoal {

    private final EntityCrocodile crocodile;

    public CrocodileAIMelee(EntityCrocodile crocodile, double speedIn, boolean useLongMemory) {
        super(crocodile, speedIn, useLongMemory);
        this.crocodile = crocodile;
    }

    public boolean canUse() {

        return super.canUse() && crocodile.getPassengers().isEmpty();
    }

    public boolean canContinueToUse() {
        return super.canContinueToUse() && crocodile.getPassengers().isEmpty();
    }

    protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
        double d0 = getAttackReachSqr(enemy);
        if (distToEnemySqr <= d0) {
            this.resetAttackCooldown();
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget((ServerLevel)this.mob.level(), enemy);
        }

    }

    protected double getAttackReachSqr(LivingEntity target) {
        return (double)(this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + target.getBbWidth());
    }

}
