package net.matt.quantize.worldgen.saplinggrowers;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.matt.quantize.worldgen.QConfiguredFeatures;

public class PalmTreeGrower extends AbstractTreeGrower {
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean bool) {
        if (random.nextInt(10) == 0) {
            return QConfiguredFeatures.TALL_PALM_TREE;
        } else {
            return QConfiguredFeatures.PALM_TREE;
        }
   }
}
