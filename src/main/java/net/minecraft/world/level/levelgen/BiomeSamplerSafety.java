package net.minecraft.world.level.levelgen;

import java.util.IdentityHashMap;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.biome.Climate;

/** Checks whether climate sampling can precede a batch of searches without
 * reordering extension callbacks. Weak identity keys do not retain old chunks. */
public final class BiomeSamplerSafety {
    private static final java.util.concurrent.ConcurrentMap<Climate.Sampler, Boolean> CACHE =
        new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private final IdentityHashMap<DensityFunction, Boolean> visited = new IdentityHashMap<>();

    private BiomeSamplerSafety() {}

    public static boolean canBatch(Climate.Sampler sampler) {
        return CACHE.computeIfAbsent(sampler, s -> {
            var check = new BiomeSamplerSafety();
            return check.node(s.temperature(), 0) && check.node(s.humidity(), 0)
                && check.node(s.continentalness(), 0) && check.node(s.erosion(), 0)
                && check.node(s.depth(), 0) && check.node(s.weirdness(), 0);
        });
    }

    private boolean node(DensityFunction original, int depth) {
        if (depth > 128 || visited.size() > 8192) return false;
        DensityFunction f = NativeDensity.unwrap(original);
        Boolean old = visited.get(f);
        if (old != null) return old;
        visited.put(f, false); // Cycles are rejected.
        boolean result;
        if (f instanceof DensityFunctions.MarkerOrMarked marker) {
            Class<?> type = f.getClass();
            result = (type == DensityFunctions.Marker.class || type == NoiseChunk.Cache2D.class
                || type == NoiseChunk.FlatCache.class || type == NoiseChunk.CacheOnce.class
                || type == NoiseChunk.CacheAllInCell.class || type == NoiseChunk.NoiseInterpolator.class)
                && node(marker.wrapped(), depth + 1)
                && (!(f instanceof NoiseChunk.Cache2D cache) || node(cache.evaluator(), depth + 1));
        } else if (f instanceof DensityFunctions.HolderHolder holder) {
            result = holder(holder.function(), depth + 1);
        } else if (f instanceof DensityFunctions.Constant || f instanceof DensityFunctions.Noise
                || f instanceof DensityFunctions.Shift || f instanceof DensityFunctions.ShiftA
                || f instanceof DensityFunctions.ShiftB || f instanceof DensityFunctions.YClampedGradient
                || f instanceof DensityFunctions.EndIslandDensityFunction
                || f instanceof DensityFunctions.BlendAlpha || f instanceof DensityFunctions.BlendOffset) {
            result = true;
        } else if (f instanceof DensityFunctions.Spline spline) {
            result = spline(spline.spline(), depth + 1);
        } else if (f instanceof DensityFunctions.FindTopSurface surface) {
            result = node(surface.density(), depth + 1) && node(surface.upperBound(), depth + 1);
        } else if (f instanceof DensityFunctions.Ap2 binary) {
            result = node(binary.argument1(), depth + 1) && node(binary.argument2(), depth + 1);
        } else if (f instanceof DensityFunctions.MulOrAdd unary) {
            result = node(unary.input(), depth + 1);
        } else if (f instanceof DensityFunctions.Mapped mapped) {
            result = node(mapped.input(), depth + 1);
        } else if (f instanceof DensityFunctions.Clamp clamp) {
            result = node(clamp.input(), depth + 1);
        } else if (f instanceof DensityFunctions.RangeChoice choice) {
            result = node(choice.input(), depth + 1) && node(choice.whenInRange(), depth + 1)
                && node(choice.whenOutOfRange(), depth + 1);
        } else if (f instanceof DensityFunctions.ShiftedNoise noise) {
            result = node(noise.shiftX(), depth + 1) && node(noise.shiftY(), depth + 1) && node(noise.shiftZ(), depth + 1);
        } else if (f instanceof DensityFunctions.WeirdScaledSampler scaled) {
            result = node(scaled.input(), depth + 1);
        } else {
            result = false;
        }
        visited.put(f, result);
        return result;
    }

    private boolean holder(net.minecraft.core.Holder<DensityFunction> holder, int depth) {
        if (holder == null) return false;
        Class<?> type = holder.getClass();
        // Holder is an extension interface, and Reference permits subclasses.
        // Never inspect custom implementations through their virtual accessors.
        return (type == net.minecraft.core.Holder.Direct.class || type == net.minecraft.core.Holder.Reference.class)
            && holder.isBound() && node(holder.value(), depth);
    }

    private boolean spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline, int depth) {
        if (depth > 128) return false;
        if (spline instanceof CubicSpline.Constant) return true;
        if (spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multipoint) {
            if (!holder(multipoint.coordinate().function(), depth + 1)) return false;
            for (var child : multipoint.values()) if (!spline(child, depth + 1)) return false;
            return true;
        }
        return false;
    }
}
