package net.matt.quantize.worldgen.saplinggrowers;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.matt.quantize.worldgen.QConfiguredFeatures;

public class SilverBirchTreeGrower extends AbstractTreeGrower {

   @Override
   protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
      // always grow the single configured silver‑birch tree
      return QConfiguredFeatures.SILVER_BIRCH_TREE;
   }
}
