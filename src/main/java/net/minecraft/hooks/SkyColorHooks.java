package net.minecraft.hooks;

import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hook interface for sky color sampling customization.
 * Allows mods to provide optimized implementations for sky color calculations.
 */
public interface SkyColorHooks {
    /**
     * Sample sky color using a custom implementation.
     * If this returns a non-null value, it replaces the default CubicSampler.gaussianSampleVec3 call.
     *
     * @param level The client level
     * @param pos The position to sample from
     * @param rgbFetcher The RGB color fetcher function
     * @return Custom sampled color, or null to use default behavior
     */
    @Nullable
    default Vec3 sampleSkyColor(Level level, Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher) {
        return null;
    }
}
