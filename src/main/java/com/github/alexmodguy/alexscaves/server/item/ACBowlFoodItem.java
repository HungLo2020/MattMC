package com.github.alexmodguy.alexscaves.server.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Custom BowlFoodItem replacement for 1.21 NeoForge.
 * BowlFoodItem was removed in 1.21 - this class provides the same functionality.
 * Returns a bowl after the food is consumed.
 */
public class ACBowlFoodItem extends Item {
    public ACBowlFoodItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        return result.isEmpty() ? new ItemStack(Items.BOWL) : result;
    }
}
