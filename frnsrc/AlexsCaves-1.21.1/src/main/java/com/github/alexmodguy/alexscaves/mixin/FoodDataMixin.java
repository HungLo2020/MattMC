package com.github.alexmodguy.alexscaves.mixin;

import com.github.alexmodguy.alexscaves.server.item.PrimordialArmorItem;
import com.github.alexmodguy.alexscaves.server.misc.ACTagRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to modify food eating behavior for Primordial Armor bonus.
 * In 1.21, food consumption happens in Player.eat() instead of FoodData.eat().
 */
@Mixin(Player.class)
public abstract class FoodDataMixin {

    @Inject(
            method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V")
    )
    public void ac_eat(Level level, ItemStack food, FoodProperties foodProperties, CallbackInfoReturnable<ItemStack> cir) {
        Player player = (Player) (Object) this;
        if (food.is(ACTagRegistry.RAW_MEATS)) {
            int extraShanksFromArmor = PrimordialArmorItem.getExtraSaturationFromArmor(player);
            if (extraShanksFromArmor != 0) {
                // Add extra nutrition and saturation from Primordial Armor when eating raw meat
                player.getFoodData().eat(extraShanksFromArmor, extraShanksFromArmor * 0.125F);
            }
        }
    }
}
