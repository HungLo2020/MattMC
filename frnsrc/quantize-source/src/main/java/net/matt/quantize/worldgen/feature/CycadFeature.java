package net.matt.quantize.worldgen.feature;

import net.matt.quantize.block.QBlocks;
import net.matt.quantize.block.block.CycadBlock;
import net.matt.quantize.tags.QTags;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class CycadFeature extends Feature<NoneFeatureConfiguration> {

    public CycadFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource randomsource = context.random();
        WorldGenLevel level = context.level();
        BlockPos treeBottom = context.origin();
        if (!level.getBlockState(treeBottom.below()).is(BlockTags.DIRT)) {
            return false;
        }
        int height = 1 + (int) Math.ceil(randomsource.nextFloat() * 2.5F);
        for (int i = 0; i <= height; i++) {
            BlockPos trunk = treeBottom.above(i);
            if (canReplace(level.getBlockState(trunk))) {
                level.setBlock(trunk, QBlocks.CYCAD.get().defaultBlockState().setValue(CycadBlock.TOP, i == height), 2);
            }
        }
        return true;
    }

    private static boolean canReplace(BlockState state) {
        return (state.isAir() || state.canBeReplaced()) && !state.is(QTags.UNMOVEABLE) && state.getFluidState().isEmpty();
    }
}
