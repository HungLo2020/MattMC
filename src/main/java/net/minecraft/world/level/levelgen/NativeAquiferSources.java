package net.minecraft.world.level.levelgen;

import java.util.IdentityHashMap;
import net.minecraft.util.CubicSpline;

/** Conservative, once-per-RandomState check before evaluating future cell points.
 * Unknown callbacks stay on the ordered scalar native path. No visitor is invoked. */
final class NativeAquiferSources {
    private final IdentityHashMap<DensityFunction,Boolean> visited=new IdentityHashMap<>();
    static boolean safe(NoiseRouter router) {
        var check=new NativeAquiferSources();
        return check.node(router.barrierNoise(),0) && check.node(router.erosion(),0) && check.node(router.depth(),0)
            && check.node(router.fluidLevelFloodednessNoise(),0) && check.node(router.fluidLevelSpreadNoise(),0)
            && check.node(router.lavaNoise(),0) && check.node(router.preliminarySurfaceLevel(),0)
            && check.node(router.veinToggle(),0) && check.node(router.veinRidged(),0) && check.node(router.veinGap(),0);
    }
    private boolean node(DensityFunction original,int depth) {
        if(depth>128 || visited.size()>8192)return false;
        DensityFunction f=NativeDensity.unwrap(original);
        Boolean old=visited.get(f);if(old!=null)return old;
        visited.put(f,false); // cycles are rejected
        boolean result;
        if(f instanceof DensityFunctions.Marker m)result=node(m.wrapped(),depth+1);
        else if(f instanceof DensityFunctions.HolderHolder h)result=h.function().isBound() && node(h.function().value(),depth+1);
        else if(f instanceof DensityFunctions.Constant || f instanceof DensityFunctions.Noise || f instanceof DensityFunctions.Shift || f instanceof DensityFunctions.ShiftA || f instanceof DensityFunctions.ShiftB
            || f instanceof DensityFunctions.YClampedGradient || f instanceof DensityFunctions.BlendAlpha || f instanceof DensityFunctions.BlendOffset)result=true;
        else if(f instanceof DensityFunctions.Spline s)result=spline(s.spline(),depth+1);
        else if(f instanceof DensityFunctions.FindTopSurface s)result=node(s.density(),depth+1) && node(s.upperBound(),depth+1);
        else if(f instanceof DensityFunctions.Ap2 n)result=node(n.argument1(),depth+1) && node(n.argument2(),depth+1);
        else if(f instanceof DensityFunctions.MulOrAdd n)result=node(n.input(),depth+1);
        else if(f instanceof DensityFunctions.Mapped n)result=node(n.input(),depth+1);
        else if(f instanceof DensityFunctions.Clamp n)result=node(n.input(),depth+1);
        else if(f instanceof DensityFunctions.RangeChoice n)result=node(n.input(),depth+1) && node(n.whenInRange(),depth+1) && node(n.whenOutOfRange(),depth+1);
        else if(f instanceof DensityFunctions.ShiftedNoise n)result=node(n.shiftX(),depth+1) && node(n.shiftY(),depth+1) && node(n.shiftZ(),depth+1);
        else if(f instanceof DensityFunctions.WeirdScaledSampler n)result=node(n.input(),depth+1);
        else result=false;
        visited.put(f,result);return result;
    }
    private boolean spline(CubicSpline<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate> s,int depth) {
        if(depth>128)return false;
        if(s instanceof CubicSpline.Constant)return true;
        if(s instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate> m) {
            if(!m.coordinate().function().isBound() || !node(m.coordinate().function().value(),depth+1))return false;
            for(var child:m.values())if(!spline(child,depth+1))return false;
            return true;
        }
        return false;
    }
}
