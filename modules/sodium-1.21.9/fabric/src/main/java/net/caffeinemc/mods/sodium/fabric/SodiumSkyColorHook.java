package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.util.color.FastCubicSampler;
import net.minecraft.hooks.SkyColorHooks;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Sodium implementation of SkyColorHooks.
 * Provides optimized sky color sampling using FastCubicSampler.
 */
public class SodiumSkyColorHook implements SkyColorHooks {
    @Override
    public Vec3 sampleSkyColor(Level level, Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher) {
        // Use Sodium's optimized fast cubic sampler for sky color sampling
        return FastCubicSampler.sampleColor(
            pos,
            (x, y, z) -> level.getNoiseBiome(x, y, z).value().getSkyColor(),
            Function.identity()
        );
    }
}
