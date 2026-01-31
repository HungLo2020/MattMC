package net.sodium.fabric;

import net.sodium.client.util.color.FastCubicSampler;
import net.minecraft.hooks.FogColorHooks;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;

/**
 * Sodium implementation of FogColorHooks.
 * Provides optimized fog color sampling using FastCubicSampler.
 */
public class SodiumFogColorHook implements FogColorHooks {
    @Override
    public Vec3 sampleFogColor(BiomeManager biomeManager, Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher) {
        // Use Sodium's optimized fast cubic sampler for fog color sampling
        // The position (pos) is already in quart coordinates (scaled by 0.25 in getBaseColor)
        // FastCubicSampler will floor the position and add offsets, maintaining quart coordinates
        return FastCubicSampler.sampleColor(
            pos,
            (i, j, k) -> biomeManager.getNoiseBiomeAtQuart(i, j, k).value().getFogColor(),
            (v) -> v
        );
    }
}
