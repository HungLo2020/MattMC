package com.github.alexmodguy.alexscaves.compat.jei;

import com.github.alexmodguy.alexscaves.server.misc.ACDataComponentRegistry;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.Biome;

public class CaveTabletSubtypeInterpreter implements IIngredientSubtypeInterpreter<ItemStack> {
    public static final CaveTabletSubtypeInterpreter INSTANCE = new CaveTabletSubtypeInterpreter();

    private CaveTabletSubtypeInterpreter() {

    }

    @Override
    public String apply(ItemStack itemStack, UidContext context) {
        // First try the new CAVE_BIOME DataComponent
        if (itemStack.has(ACDataComponentRegistry.CAVE_BIOME.get())) {
            ResourceKey<Biome> biomeKey = itemStack.get(ACDataComponentRegistry.CAVE_BIOME.get());
            if (biomeKey != null) {
                return biomeKey.location().toString();
            }
        }
        // Fallback for legacy items using CustomData
        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("CaveBiome")) {
                return tag.getString("CaveBiome");
            }
        }
        return "";
    }
}
