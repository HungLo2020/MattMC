package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

// Stub class for EntityAlligatorSnappingTurtle - not yet implemented
public class EntityAlligatorSnappingTurtle extends Animal {
    public EntityAlligatorSnappingTurtle(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public Animal getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableEntity) {
        return null;
    }
}
