package com.github.alexmodguy.alexscaves.mixin;

import com.github.alexmodguy.alexscaves.server.potion.ACEffectRegistry;
import com.github.alexmodguy.alexscaves.server.potion.IrradiatedEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionContents.class)
public class PotionUtilsMixin {

    @Inject(
            method = {"Lnet/minecraft/world/item/alchemy/PotionContents;getColor(Ljava/lang/Iterable;)I"},
            remap = true,
            cancellable = true,
            at = @At(value = "HEAD")
    )
    private static void ac_getColor(Iterable<MobEffectInstance> effects, CallbackInfoReturnable<Integer> cir) {
        for (MobEffectInstance mobEffectInstance : effects) {
            if (mobEffectInstance.getEffect().is(ACEffectRegistry.IRRADIATED) && mobEffectInstance.getAmplifier() >= IrradiatedEffect.BLUE_LEVEL) {
                cir.setReturnValue(0X00FFFF);
                return;
            }
        }
    }
}
