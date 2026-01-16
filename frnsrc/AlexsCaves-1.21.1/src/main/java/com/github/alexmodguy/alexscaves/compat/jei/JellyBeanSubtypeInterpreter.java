package com.github.alexmodguy.alexscaves.compat.jei;

import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

public class JellyBeanSubtypeInterpreter implements IIngredientSubtypeInterpreter<ItemStack> {
    public static final JellyBeanSubtypeInterpreter INSTANCE = new JellyBeanSubtypeInterpreter();

    private JellyBeanSubtypeInterpreter() {
    }

    @Override
    public String apply(ItemStack itemStack, UidContext context) {
        PotionContents potionContents = itemStack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null && potionContents.potion().isPresent()) {
            return potionContents.potion().get().getRegisteredName();
        }
        return "";
    }
}
