package net.minecraft.hooks;

import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hook interface for fog color sampling customization.
 * Allows mods to provide optimized implementations for fog color calculations.
 */
public interface FogColorHooks {
    /**
     * Sample fog color using a custom implementation.
     * If this returns a non-null value, it replaces the default CubicSampler.gaussianSampleVec3 call.
     *
     * @param biomeManager The biome manager
     * @param pos The position to sample from (in quart coordinates)
     * @param rgbFetcher The RGB color fetcher function
     * @return Custom sampled color, or null to use default behavior
     */
    @Nullable
    default Vec3 sampleFogColor(BiomeManager biomeManager, Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher) {
        return null;
    }
}
