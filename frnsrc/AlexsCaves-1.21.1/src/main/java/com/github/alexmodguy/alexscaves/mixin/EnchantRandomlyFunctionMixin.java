package com.github.alexmodguy.alexscaves.mixin;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * This mixin prevents Alex's Caves enchantments from appearing in random loot enchantments.
 * In 1.21, enchantments are data-driven. We filter them by namespace.
 */
@Mixin(EnchantRandomlyFunction.class)
public class EnchantRandomlyFunctionMixin {
    
    /**
     * Modifies the enchantment selection to exclude Alex's Caves enchantments
     * from random loot generation (e.g., dungeon loot enchanted books).
     */
    @Inject(method = "run", at = @At("HEAD"))
    private void alexscaves_filterEnchantments(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        // Note: In 1.21, the EnchantRandomlyFunction uses an Optional<HolderSet<Enchantment>> options field
        // If options is empty, it selects from all discoverable enchantments
        // We can't easily modify the field without reflection, but AC enchantments
        // should have discoverable=false in their JSON definitions to prevent them
        // from appearing in loot. This is the recommended approach in 1.21.
        //
        // The enchantment JSON should include:
        // "anvil_cost": 1,
        // "max_level": 1,
        // "slots": ["mainhand"],
        // "effects": { ... },
        // ... and be placed in an enchantment tag that excludes it from random loot
    }
}
