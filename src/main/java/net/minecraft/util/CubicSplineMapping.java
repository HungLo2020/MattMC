package net.minecraft.util;

import java.util.Arrays;
import java.util.Collections;

/** Allocation-light mapping for built-in immutable terrain graphs. Bounds and
 * coordinate/child visit order are identical to CubicSpline.mapAll. */
public final class CubicSplineMapping {
    private CubicSplineMapping() {}

    @SuppressWarnings("unchecked")
    public static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> mapTerrain(
            CubicSpline<C, I> source, CubicSpline.CoordinateVisitor<I> visitor) {
        if (!(source instanceof CubicSpline.Multipoint<C, I> multi)) return source.mapAll(visitor);
        I coordinate = visitor.visit(multi.coordinate());
        CubicSpline<C, I>[] children = new CubicSpline[multi.values().size()];
        for (int i = 0; i < children.length; i++) children[i] = mapTerrain(multi.values().get(i), visitor);
        return CubicSpline.Multipoint.create(coordinate, multi.locations(),
            Collections.unmodifiableList(Arrays.asList(children)), multi.derivatives());
    }
}
