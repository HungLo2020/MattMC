package com.github.alexmodguy.alexscaves.mixin;

import com.github.alexmodguy.alexscaves.server.level.biome.ACBiomeRegistry;
import com.github.alexmodguy.alexscaves.server.level.feature.FeaturePositionValidator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LakeFeature.class)
public class LakeFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void ac_place(FeaturePlaceContext context, CallbackInfoReturnable<Boolean> cir) {
        if (FeaturePositionValidator.isBiome(context, ACBiomeRegistry.ABYSSAL_CHASM)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Fix for vanilla LakeFeature accessing chunks outside WorldGenRegion bounds.
     * In WorldGenRegion, always use getUncachedNoiseBiome() to avoid chunk access issues.
     */
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> ac_wrapGetBiome(WorldGenLevel level, BlockPos pos, Operation<Holder<Biome>> original) {
        if (level instanceof WorldGenRegion) {
            // Use noise biome directly - doesn't require chunk access and is accurate for freeze checks
            return level.getUncachedNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        }
        return original.call(level, pos);
    }
}
