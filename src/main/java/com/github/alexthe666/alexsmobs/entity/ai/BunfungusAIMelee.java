package com.github.alexthe666.alexsmobs.entity.ai;

import com.github.alexthe666.alexsmobs.entity.EntityBunfungus;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class BunfungusAIMelee extends Goal {
    private EntityBunfungus entity;
    private int attackDelay = 0;

    public BunfungusAIMelee(EntityBunfungus entity) {
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.entity = entity;
    }

    @Override
    public boolean canUse() {
        return entity.getTarget() != null && entity.getTarget().isAlive();
    }

    @Override
    public void start() {
        attackDelay = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = entity.getTarget();
        if (target != null && target.isAlive()) {
            double dist = entity.distanceTo(target);
            entity.getLookControl().setLookAt(target, 30F, 30F);
            if (attackDelay > 0) {
                attackDelay--;
            }
            if (entity.getAnimation() == IAnimatedEntity.NO_ANIMATION) {
                if (dist < 3.5D && attackDelay == 0) {
                    entity.setAnimation(EntityBunfungus.ANIMATION_BELLY);
                    attackDelay = 30;
                } else if (dist < 2.5D && attackDelay == 0) {
                    entity.setAnimation(EntityBunfungus.ANIMATION_SLAM);
                    attackDelay = 30;
                }
            }
        }
    }
}
