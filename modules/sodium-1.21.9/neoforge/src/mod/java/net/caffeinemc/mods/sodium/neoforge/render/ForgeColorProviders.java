package net.caffeinemc.mods.sodium.neoforge.render;

import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.minecraft.client.renderer.sodium.model.color.ColorProvider;
import net.minecraft.client.renderer.sodium.model.quad.ModelQuadView;
import net.minecraft.client.renderer.sodium.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

import java.util.Arrays;

public class ForgeColorProviders {
    public static ColorProvider<FluidState> adapt(IClientFluidTypeExtensions handler) {
        return new ForgeFluidAdapter(handler);
    }

    private static class ForgeFluidAdapter implements ColorProvider<FluidState> {
        private final IClientFluidTypeExtensions handler;

        public ForgeFluidAdapter(IClientFluidTypeExtensions handler) {
            this.handler = handler;
        }

        @Override
        public void getColors(LevelSlice slice, BlockPos pos, BlockPos.MutableBlockPos scratchPos, FluidState state, ModelQuadView quad, int[] output, boolean smooth) {
            Arrays.fill(output,this.handler.getTintColor(state, slice, pos));
        }
    }
}
