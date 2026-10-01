package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

/** The generator's immutable global fluid policy, also recognized by cell batches. */
final class AquiferFluidPicker implements Aquifer.FluidPicker {
    final Aquifer.FluidStatus lava=new Aquifer.FluidStatus(-54,Blocks.LAVA.defaultBlockState());
    final Aquifer.FluidStatus fluid;
    final Aquifer.FluidStatus disabled=new Aquifer.FluidStatus(DimensionType.MIN_Y*2,Blocks.AIR.defaultBlockState());
    AquiferFluidPicker(int seaLevel,BlockState fluid) {this.fluid=new Aquifer.FluidStatus(seaLevel,fluid);}
    @Override public Aquifer.FluidStatus computeFluid(int x,int y,int z) {
        return SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? disabled : y<Math.min(-54,fluid.fluidLevel())?lava:fluid;
    }
}
