package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;

/**
 * Stub class for EntityRaccoon - Blue Jay can ride on raccoons
 * This is a minimal implementation to satisfy compilation requirements
 */
public class EntityRaccoon extends TamableAnimal {
    
    public float prevBegProgress;
    public float begProgress;
    public float prevStandProgress;
    public float standProgress;
    public float prevSitProgress;
    public float sitProgress;

    protected EntityRaccoon(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean isFood(net.minecraft.world.item.ItemStack itemStack) {
        return false;
    }

    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel serverLevel, net.minecraft.world.entity.AgeableMob ageableMob) {
        return null;
    }
}
