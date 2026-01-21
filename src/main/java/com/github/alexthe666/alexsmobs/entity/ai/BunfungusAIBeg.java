package com.github.alexthe666.alexsmobs.entity.ai;

import com.github.alexthe666.alexsmobs.entity.EntityBunfungus;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

public class BunfungusAIBeg extends Goal {
    private EntityBunfungus entity;
    private Player begTarget;
    private double speed;
    private int begTime = 0;

    public BunfungusAIBeg(EntityBunfungus entity, double speed) {
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.entity = entity;
        this.speed = speed;
    }

    @Override
    public boolean canUse() {
        if (!entity.isCarroted() || entity.isSleeping()) {
            return false;
        }
        List<Player> players = entity.level().getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(8D));
        for (Player player : players) {
            if (entity.isCarrot(player.getMainHandItem()) || entity.isCarrot(player.getOffhandItem())) {
                begTarget = player;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (begTarget == null || !begTarget.isAlive() || entity.distanceTo(begTarget) > 10D) {
            return false;
        }
        return entity.isCarrot(begTarget.getMainHandItem()) || entity.isCarrot(begTarget.getOffhandItem());
    }

    @Override
    public void start() {
        begTime = 0;
        entity.setBegging(true);
    }

    @Override
    public void stop() {
        begTarget = null;
        entity.setBegging(false);
    }

    @Override
    public void tick() {
        if (begTarget != null) {
            entity.getLookControl().setLookAt(begTarget, 30F, 30F);
            if (entity.distanceTo(begTarget) > 3D) {
                entity.getNavigation().moveTo(begTarget, speed);
            }
            begTime++;
        }
    }
}
