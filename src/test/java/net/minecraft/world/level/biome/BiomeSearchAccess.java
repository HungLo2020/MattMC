package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Pair;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

/** The protected spiral search of {@link BiomeSource#findClosestBiome3d}, for tests. */
public final class BiomeSearchAccess {
    private BiomeSearchAccess() {}

    public static Pair<BlockPos, Holder<Biome>> closest(BiomeSource source, BlockPos center, int radius, int step, Set<Holder<Biome>> set, int[] ys,
                                                        Climate.Sampler sampler) {
        return source.findClosestBiome3d(center, radius, step, set, ys, sampler);
    }
}
