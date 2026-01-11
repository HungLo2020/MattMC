package com.github.alexmodguy.alexscaves.server.block.grower;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.Optional;

public class AncientTreeGrower {

    public static final ResourceKey<ConfiguredFeature<?, ?>> ANCIENT_TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "ancient_tree"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> GIANT_ANCIENT_TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "giant_ancient_tree"));

    // TreeGrower is a record in 1.21, we create an instance with mega tree support
    public static final TreeGrower GROWER = new TreeGrower(
            "ancient",
            0.1F, // secondaryChance - 10% chance for mega tree when 2x2 saplings
            Optional.empty(), // megaTree - no 2x2 mega tree (we use 3x3)
            Optional.empty(), // secondaryMegaTree
            Optional.of(ANCIENT_TREE), // tree - normal tree
            Optional.empty(), // secondaryTree
            Optional.empty(), // flowers
            Optional.empty()  // floweringTree
    );
    
    // For 3x3 mega tree, we provide a separate grower
    public static final TreeGrower MEGA_GROWER = new TreeGrower(
            "ancient_mega",
            Optional.empty(),
            Optional.of(GIANT_ANCIENT_TREE),
            Optional.empty()
    );

    public static boolean isThreeByThreeSapling(BlockState state, BlockGetter level, BlockPos pos, int x, int z) {
        Block block = state.getBlock();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mutableBlockPos.set(pos.getX() + x + i, pos.getY(), pos.getZ() + z + j);
                if (!level.getBlockState(mutableBlockPos).is(block)) {
                    return false;
                }
            }
        }
        return true;
    }
}
