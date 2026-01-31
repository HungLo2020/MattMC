package net.alexsmobs.entity.ai;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AnimalAILeapRandomly extends Goal {
    private PathfinderMob entity;
    private int cooldown;
    private int leapChance;

    public AnimalAILeapRandomly(PathfinderMob entity, int cooldown, int leapChance) {
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        this.entity = entity;
        this.cooldown = cooldown;
        this.leapChance = leapChance;
    }

    @Override
    public boolean canUse() {
        if (entity.isVehicle() || !entity.onGround()) {
            return false;
        }
        return entity.getRandom().nextInt(cooldown) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        float f = (float) Math.toRadians(entity.getYRot());
        float xMod = -1F + entity.getRandom().nextFloat() * 2F;
        float zMod = -1F + entity.getRandom().nextFloat() * 2F;
        Vec3 motion = new Vec3(
            Math.sin(f) * leapChance * 0.1F * xMod,
            0.4F + entity.getRandom().nextFloat() * 0.3F,
            -Math.cos(f) * leapChance * 0.1F * zMod
        );
        entity.setDeltaMovement(motion);
    }
}
