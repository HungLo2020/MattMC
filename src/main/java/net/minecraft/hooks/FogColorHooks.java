package net.minecraft.hooks;

import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Hook interface for fog color sampling customization.
 * Allows mods to provide optimized implementations for fog color calculations.
 */
public interface FogColorHooks {
    /** World context for retained consumers; existing hooks keep their dispatch. */
    default Vec3 sampleFogColor(Level level, BiomeManager biomeManager, Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher) {
        return sampleFogColor(biomeManager, pos, rgbFetcher);
    }
    /**
     * Sample fog color using a custom implementation.
     * If this returns a non-null value, it replaces the default CubicSampler.gaussianSampleVec3 call.
     *
     * @param biomeManager The biome manager
     * @param pos The position to sample from (in quart coordinates)
     * @param rgbFetcher The RGB color fetcher function
     * @return Custom sampled color, or null to use default behavior
     */
    default Vec3 sampleFogColor(BiomeManager biomeManager, Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher) {
        return null;
    }
}
