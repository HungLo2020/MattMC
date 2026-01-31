package net.alexsmobs.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public class TameableAITempt extends TemptGoal {

    private final Animal tameable;
    private int calmDown;
    private final Predicate<ItemStack> items;

    public TameableAITempt(Animal tameable, double speedIn, Predicate<ItemStack> temptItemsIn, boolean scaredByPlayerMovementIn) {
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
