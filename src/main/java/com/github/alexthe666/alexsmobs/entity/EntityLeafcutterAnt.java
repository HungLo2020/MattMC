package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Stub implementation of EntityLeafcutterAnt for Anteater mob functionality
 * This is a minimal implementation to support Anteater's interactions with ants
 */
public class EntityLeafcutterAnt extends Animal implements NeutralMob {
    
    @Nullable
    private UUID lastHurtBy;
    private int angerTime;
    private int stayOutOfHiveCountdown;
    private boolean isQueen = false;
    
    protected EntityLeafcutterAnt(EntityType<? extends Animal> type, Level world) {
        super(type, world);
    }
    
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }
    
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level, AgeableMob parent) {
        return null;
    }
    
    public boolean isQueen() {
        return isQueen;
    }
    
    public void setQueen(boolean queen) {
        isQueen = queen;
    }
    
    @Override
    public int getRemainingPersistentAngerTime() {
        return angerTime;
    }
    
    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.angerTime = time;
    }
    
    @Override
    public UUID getPersistentAngerTarget() {
        return lastHurtBy;
    }
    
    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.lastHurtBy = target;
    }
    
    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(200);
    }
    
    public void setStayOutOfHiveCountdown(int countdown) {
        this.stayOutOfHiveCountdown = countdown;
    }
}
