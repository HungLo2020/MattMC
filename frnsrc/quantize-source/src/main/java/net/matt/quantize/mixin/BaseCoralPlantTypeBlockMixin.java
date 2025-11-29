package net.matt.quantize.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CoralFanBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseCoralPlantTypeBlock.class)
public class BaseCoralPlantTypeBlockMixin {

    private static boolean staticEnabled = true; // Default value

    @Inject(method = "scanForWater", at = @At("RETURN"), cancellable = true)
    private static void injectScanForWater(BlockState state, BlockGetter getter, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        boolean original = cir.getReturnValue();
        if (!original && staticEnabled && state.getBlock() instanceof CoralFanBlock) {
            cir.setReturnValue(getter.getBlockState(pos.below()).getBlock() == Blocks.CACTUS);
        }
    }

    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
    private void injectCanSurvive(BlockState state, LevelReader getter, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        boolean original = cir.getReturnValue();
        if (!original && staticEnabled && state.getBlock() instanceof CoralFanBlock) {
            cir.setReturnValue(getter.getBlockState(pos.below()).getBlock() == Blocks.CACTUS);
        }
    }
}