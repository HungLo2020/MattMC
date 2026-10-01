package net.minecraft.world.level.levelgen;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.KeyDispatchDataCodec;

/** Private evaluator for immutable built-in spline graphs. Canonical graphs,
 * serialization, mapping and chunk cache ownership remain in Java. */
final class NativeSpline implements DensityFunction {
    /** Engine visitors with stable structural deduplication; never extension visitors. */
    interface StableVisitor extends DensityFunction.Visitor {}
    // Values retain only numeric data and paths, never their weak graph keys or chunks.
    private static final java.util.concurrent.ConcurrentMap<DensityFunctions.Spline, Template> TEMPLATES =
        new com.google.common.collect.MapMaker().weakKeys().makeMap();
    final DensityFunctions.Spline source;
    private final DensityFunction[] coordinates;
    private final NoiseChunk.FlatCache[] flatCaches;
    private final java.lang.foreign.MemorySegment coordinateProgram;
    private final NativeSplineProgram program;
    private NativeSpline(DensityFunctions.Spline source, Template template) {
        this.source = source;
        coordinates = new DensityFunction[template.paths.size()];
        for (int i = 0; i < coordinates.length; i++) {
            var spline = source.spline();
            for (int child : template.paths.get(i)) spline = ((CubicSpline.Multipoint<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate>)spline).values().get(child);
            coordinates[i] = ((CubicSpline.Multipoint<DensityFunctions.Spline.Point,DensityFunctions.Spline.Coordinate>)spline).coordinate().function().value();
        }
        program = template.program();
        var binding = flatCaches(coordinates);
        flatCaches = binding == null ? null : binding.caches();
        coordinateProgram = binding == null ? null : program.coordinates(binding.steps());
    }
    private record CacheBinding(NoiseChunk.FlatCache[] caches,List<List<NativeUnaryProgram.Step>> steps) {}
    private static CacheBinding flatCaches(DensityFunction[] coordinates) {
        if (coordinates.length == 0) return null;
        var result = new NoiseChunk.FlatCache[coordinates.length];
        var transforms = new ArrayList<List<NativeUnaryProgram.Step>>();
        for (int i=0;i<result.length;i++) {
            DensityFunction f=coordinates[i];
            var operations = new ArrayList<NativeUnaryProgram.Step>();
            for(int depth=0;depth<32;depth++) {
                f=NativeDensity.unwrap(f);
                if(f instanceof DensityFunctions.HolderHolder h) {
                    var holder=h.function();
                    if((holder.getClass()!=Holder.Direct.class && holder.getClass()!=Holder.Reference.class) || !holder.isBound())return null;
                    f=holder.value();
                } else if(f instanceof DensityFunctions.Mapped m) {
                    operations.add(new NativeUnaryProgram.Step(14+m.type().ordinal(),0,0));f=m.input();
                } else if(f instanceof DensityFunctions.MulOrAdd m) {
                    operations.add(new NativeUnaryProgram.Step(m.specificType()==DensityFunctions.MulOrAdd.Type.MUL?12:13,m.argument(),0));f=m.input();
                } else if(f instanceof DensityFunctions.Clamp m) {
                    operations.add(new NativeUnaryProgram.Step(21,m.minValue(),m.maxValue()));f=m.input();
                } else break;
            }
            if(f==null || f.getClass()!=NoiseChunk.FlatCache.class || operations.size()>16)return null;
            result[i]=(NoiseChunk.FlatCache)f;
            if(i>0 && !result[0].sameSplineGrid(result[i]))return null;
            transforms.add(List.copyOf(operations.reversed()));
        }
        return new CacheBinding(result,List.copyOf(transforms));
    }
    static DensityFunction compile(DensityFunctions.Spline source) {
        Template template = template(source);
        return template == null ? source : new NativeSpline(source, template);
    }
    static DensityFunction mapSpline(DensityFunctions.Spline source, Visitor visitor) {
        Template template = template(source);
        if (visitor instanceof StableVisitor && template != null && template.stableCoordinates) {
            // The engine visitor already deduplicates these pure coordinate
            // graphs. Map each shared axis once instead of rebuilding it at
            // every knot. Extension visitors always use the original path below.
            var axes = new IdentityHashMap<DensityFunction, DensityFunctions.Spline.Coordinate>(4);
            var mapped = new DensityFunctions.Spline(net.minecraft.util.CubicSplineMapping.mapTerrain(source.spline(), coordinate -> {
                var function = coordinate.function().value();
                var binding = axes.get(function);
                if (binding == null) {
                    binding = coordinate.mapAll(visitor);
                    axes.put(function, binding);
                }
                return binding;
            }));
            var result = visitor.apply(mapped);
            // NoiseChunk deduplicates equal graphs. Cache only the surviving
            // graph, not every temporary graph created while visiting it.
            if (result instanceof DensityFunctions.Spline canonical) TEMPLATES.putIfAbsent(canonical, template);
            return result;
        }
        var bindings = new IdentityHashMap<DensityFunction,DensityFunction>();
        boolean[] coherent = {true};
        var mapped = new DensityFunctions.Spline(source.spline().mapAll(coordinate -> {
            var result = coordinate.mapAll(visitor);
            var holder = coordinate.function();
            if ((holder.getClass() == Holder.Direct.class || holder.getClass() == Holder.Reference.class) && holder.isBound()) {
                var before = holder.value(); var after = result.function().value();
                if (bindings.containsKey(before) && bindings.get(before) != after) coherent[0] = false;
                bindings.put(before, after);
            } else coherent[0] = false;
            return result;
        }));
        // Built-in mapAll preserves locations/derivatives/constants exactly. Bind
        // the mapped coordinates separately; do not share mutable chunk caches.
        if (template != null && coherent[0]) TEMPLATES.put(mapped, template);
        return visitor.apply(mapped);
    }
    private static Template template(DensityFunctions.Spline source) {
        Template existing = TEMPLATES.get(source);
        if (existing != null) return existing;
        var b = new Builder();
        if (b.add(source.spline(), new int[0]) < 0 || b.nodes.size() < 4) return null;
        var created = new Template(List.copyOf(b.nodes), List.copyOf(b.knots), List.copyOf(b.paths),
            b.coordinates.stream().allMatch(f -> stableMapping(f,0)));
        var previous = TEMPLATES.putIfAbsent(source, created);
        return previous == null ? created : previous;
    }
    private static final class Template {
        final List<NativeSplineProgram.Node> nodes;
        final List<NativeSplineProgram.Knot> knots;
        final List<int[]> paths;
        final boolean stableCoordinates;
        private volatile NativeSplineProgram program;
        Template(List<NativeSplineProgram.Node> nodes, List<NativeSplineProgram.Knot> knots, List<int[]> paths, boolean stableCoordinates) {
            this.nodes=nodes;this.knots=knots;this.paths=paths;this.stableCoordinates=stableCoordinates;
        }
        NativeSplineProgram program() {
            var p = program;
            if (p == null) synchronized (this) {
                p = program;
                if (p == null) program = p = new NativeSplineProgram(nodes, knots);
            }
            return p;
        }
    }
    @Override public double compute(FunctionContext context) {
        // Unknown contexts and callbacks must retain their original lazy visits.
        if (context == null) return source.compute(null);
        if (flatCaches != null && (context.getClass() == NoiseChunk.class || context instanceof SinglePointContext)) {
            int index=flatCaches[0].splineCacheIndex(context);
            if(index<0)return source.compute(context);
            return program.cached(coordinateProgram,flatCaches[0].values[index],
                flatCaches.length>1?flatCaches[1].values[index]:0,
                flatCaches.length>2?flatCaches[2].values[index]:0,
                flatCaches.length>3?flatCaches[3].values[index]:0);
        }
        if (!canEvaluate(context)) return source.compute(context);
        float a = coordinates.length > 0 ? (float)coordinates[0].compute(context) : 0;
        float b = coordinates.length > 1 ? (float)coordinates[1].compute(context) : 0;
        float c = coordinates.length > 2 ? (float)coordinates[2].compute(context) : 0;
        float d = coordinates.length > 3 ? (float)coordinates[3].compute(context) : 0;
        return program.sample(a, b, c, d);
    }
    boolean canEvaluate(FunctionContext context) {
        if (context == null) return false;
        if (context.getClass() != NoiseChunk.class && !(context instanceof SinglePointContext)) return false;
        for (var coordinate : coordinates) if (!available(coordinate, context, 0)) return false;
        return true;
    }
    private static boolean stableMapping(DensityFunction original, int depth) {
        if(original==null || depth>64)return false;
        var f=NativeDensity.unwrap(original);
        if(f instanceof DensityFunctions.HolderHolder h) {
            var holder=h.function();
            return (holder.getClass()==Holder.Direct.class || holder.getClass()==Holder.Reference.class)
                && holder.isBound() && stableMapping(holder.value(),depth+1);
        }
        if(f instanceof DensityFunctions.MarkerOrMarked m) {
            var type=f.getClass();
            return (type==DensityFunctions.Marker.class || type==NoiseChunk.FlatCache.class || type==NoiseChunk.Cache2D.class)
                && stableMapping(m.wrapped(),depth+1);
        }
        if(f instanceof DensityFunctions.Constant || f instanceof DensityFunctions.YClampedGradient || f instanceof DensityFunctions.Noise
            || f instanceof DensityFunctions.Shift || f instanceof DensityFunctions.ShiftA || f instanceof DensityFunctions.ShiftB)return true;
        if(f instanceof DensityFunctions.Mapped m)return stableMapping(m.input(),depth+1);
        if(f instanceof DensityFunctions.MulOrAdd m)return stableMapping(m.input(),depth+1);
        if(f instanceof DensityFunctions.Clamp m)return stableMapping(m.input(),depth+1);
        if(f instanceof DensityFunctions.Ap2 m)return stableMapping(m.argument1(),depth+1)&&stableMapping(m.argument2(),depth+1);
        if(f instanceof DensityFunctions.ShiftedNoise m)return stableMapping(m.shiftX(),depth+1)&&stableMapping(m.shiftY(),depth+1)&&stableMapping(m.shiftZ(),depth+1);
        return false;
    }
    private static boolean available(DensityFunction f, FunctionContext c, int depth) {
        if (f == null || depth > 32) return false;
        f = NativeDensity.unwrap(f);
        if (f.getClass() == NoiseChunk.FlatCache.class) return ((NoiseChunk.FlatCache)f).containsSplinePoint(c);
        if (f instanceof DensityFunctions.Constant || f instanceof DensityFunctions.YClampedGradient) return true;
        if (f instanceof DensityFunctions.HolderHolder h) {
            var holder = h.function();
            return (holder.getClass() == Holder.Direct.class || holder.getClass() == Holder.Reference.class)
                && holder.isBound() && available(holder.value(), c, depth + 1);
        }
        if (f instanceof DensityFunctions.Mapped m) return available(m.input(), c, depth + 1);
        if (f instanceof DensityFunctions.MulOrAdd m) return available(m.input(), c, depth + 1);
        if (f instanceof DensityFunctions.Clamp m) return available(m.input(), c, depth + 1);
        return false;
    }
    @Override public void fillArray(double[] values, ContextProvider provider) { source.fillArray(values, provider); }
    @Override public DensityFunction mapAll(Visitor visitor) { return source.mapAll(visitor); }
    @Override public double minValue() { return source.minValue(); }
    @Override public double maxValue() { return source.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return source.codec(); }
    private static final class Builder {
        final List<NativeSplineProgram.Node> nodes = new ArrayList<>();
        final List<NativeSplineProgram.Knot> knots = new ArrayList<>();
        final List<DensityFunction> coordinates = new ArrayList<>();
        final List<int[]> paths = new ArrayList<>();
        final List<Integer> work = new ArrayList<>();
        final IdentityHashMap<CubicSpline<?,?>, Integer> seen = new IdentityHashMap<>();
        int add(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline, int[] path) {
            if (path.length > 32 || nodes.size() >= 4096 || knots.size() >= 16384) return -1;
            var cached = seen.get(spline); if (cached != null) return cached;
            int axis = -1, start = 0, count = 0; float value = 0;
            if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> constant) value = constant.value();
            else if (spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multi) {
                var holder = multi.coordinate().function();
                if ((holder.getClass() != Holder.Direct.class && holder.getClass() != Holder.Reference.class) || !holder.isBound()) return -1;
                var function = holder.value();
                axis = -1;
                for (int i = 0; i < coordinates.size(); i++) if (coordinates.get(i) == function) { axis = i; break; }
                if (axis < 0) { axis = coordinates.size(); if (axis == 4) return -1; coordinates.add(function); paths.add(path); }
                count = multi.locations().length;
                if (count == 0 || count != multi.derivatives().length || count != multi.values().size() || count > 16384) return -1;
                int[] children = new int[count];
                for (int i = 0; i < count; i++) {
                    int[] childPath=Arrays.copyOf(path,path.length+1);childPath[path.length]=i;
                    children[i] = add(multi.values().get(i), childPath); if (children[i] < 0) return -1;
                }
                start = knots.size();
                if (start + count > 16384 || nodes.size() >= 4096) return -1;
                for (int i = 0; i < count; i++) knots.add(new NativeSplineProgram.Knot(multi.locations()[i], multi.derivatives()[i], children[i]));
            } else return -1;
            int first=0,second=0;
            for(int i=0;i<count;i++) {
                int v=work.get(knots.get(start+i).child());
                if(v>=first){second=first;first=v;}else second=Math.max(second,v);
            }
            if(1+first+second>4096)return -1;
            work.add(1+first+second);
            int id = nodes.size(); nodes.add(new NativeSplineProgram.Node(axis, start, count, value)); seen.put(spline, id); return id;
        }
    }
}
