package com.github.alexthe666.alexsmobs.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.crafting.Ingredient;

public class TameableAITempt extends TemptGoal {

    private final Animal tameable;
    private int calmDown;
    private final Ingredient items;

    public TameableAITempt(Animal tameable, double speedIn, Ingredient temptItemsIn, boolean scaredByPlayerMovementIn) {
        super(tameable, speedIn, temptItemsIn, scaredByPlayerMovementIn);
        this.tameable = tameable;
        this.items = temptItemsIn;
    }


    public boolean shouldFollowAM(LivingEntity p_148139_, ServerLevel level) {
        return this.items.test(p_148139_.getMainHandItem()) || this.items.test(p_148139_.getOffhandItem());
    }

    public boolean canUse() {
        if (this.calmDown > 0) {
            --this.calmDown;
            return false;
        } else {
            return  (!(tameable instanceof TamableAnimal) || !((TamableAnimal)tameable).isTame()) && super.canUse();
        }
    }


    public void stop() {
        super.stop();
        this.calmDown = reducedTickDelay(100);
    }

}
