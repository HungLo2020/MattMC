package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Fuses known pure expressions. Other paths use the same Rust operations via Java traversal. */
final class NativeDensity implements DensityFunction {
    /** Opt-in for contexts whose coordinate accessors are stable and have no side effects. */
    interface StableContext extends DensityFunction.FunctionContext {}
    private final DensityFunction source;
    private final NativeDensityProgram program;
    private NativeDensity(DensityFunction source, NativeDensityProgram program) { this.source = source; this.program = program; }
    static DensityFunction unwrap(DensityFunction f) {
        while(true) {
            if(f instanceof NativeDensity n)f=n.source;
            else if(f instanceof NativeUnary n)f=n.source;
            else if(f instanceof NativeOperands n)f=n.source;
            else if(f instanceof NativeCellDensity n)f=n.source;
            else if(f instanceof NativeSpline n)f=n.source;
            else return f;
        }
    }
    static boolean candidate(DensityFunction f) {
        return f instanceof NativeDensity || f instanceof NativeUnary || f instanceof DensityFunctions.Ap2 || f instanceof DensityFunctions.MulOrAdd
            || f instanceof DensityFunctions.Mapped || f instanceof DensityFunctions.Clamp || f instanceof DensityFunctions.RangeChoice
            || f instanceof DensityFunctions.ShiftedNoise || f instanceof DensityFunctions.WeirdScaledSampler;
    }
    static DensityFunction compile(DensityFunction source) {
        if (source instanceof NativeDensity || !candidate(source)) return source;
        var builder = new Builder();
        int root = builder.add(source);
        // Small programs do not amortize interpreter overhead. Keep fusion for
        // expressions that combine at least two live noise evaluations.
        if (root < 0 || builder.noiseCalls < 2 || builder.octaves > 128) return source;
        return new NativeDensity(source, new NativeDensityProgram(builder.nodes.toArray(NativeDensityProgram.Node[]::new), builder.noises.toArray(NormalNoise[]::new)));
    }
    @Override public double compute(FunctionContext c) {
        if (!(c instanceof NoiseChunk || c instanceof DensityFunction.SinglePointContext || c instanceof StableContext))
            return source.compute(c);
        return program.sample(c.blockX(), c.blockY(), c.blockZ());
    }
    @Override public void fillArray(double[] values, ContextProvider provider) {
        // Preserve traversal, short circuits and provider counters. Source nodes delegate
        // migrated math to NativeDensityMath; this is not an alternate Java evaluator.
        source.fillArray(values, provider);
    }
    @Override public DensityFunction mapAll(Visitor visitor) { return source.mapAll(visitor); }
    @Override public double minValue() { return source.minValue(); }
    @Override public double maxValue() { return source.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return source.codec(); }

    static boolean safeInputs(DensityFunction f) { return safeInputs(f, 0); }
    private static boolean safeInputs(DensityFunction f, int depth) {
        if (depth > 64) return false;
        f = unwrap(f);
        if (f instanceof DensityFunctions.MarkerOrMarked m) {
            Class<?> type=f.getClass();
            if (type!=DensityFunctions.Marker.class && type!=NoiseChunk.CacheOnce.class && type!=NoiseChunk.Cache2D.class
                && type!=NoiseChunk.FlatCache.class && type!=NoiseChunk.CacheAllInCell.class && type!=NoiseChunk.NoiseInterpolator.class) return false;
            return safeInputs(m.wrapped(), depth+1);
        }
        if (f instanceof DensityFunctions.HolderHolder h) return h.function().isBound() && safeInputs(h.function().value(), depth+1);
        if (f instanceof DensityFunctions.Constant || f instanceof DensityFunctions.Noise || f instanceof DensityFunctions.ShiftNoise || f instanceof DensityFunctions.YClampedGradient) return true;
        if (f instanceof DensityFunctions.ShiftedNoise n) return safeInputs(n.shiftX(),depth+1) && safeInputs(n.shiftY(),depth+1) && safeInputs(n.shiftZ(),depth+1);
        if (f instanceof DensityFunctions.WeirdScaledSampler t) return safeInputs(t.input(),depth+1);
        if (f instanceof DensityFunctions.PureTransformer t && (f instanceof DensityFunctions.Mapped || f instanceof DensityFunctions.MulOrAdd || f instanceof DensityFunctions.Clamp)) return safeInputs(t.input(),depth+1);
        if (f instanceof DensityFunctions.Ap2 n) return safeInputs(n.argument1(),depth+1) && safeInputs(n.argument2(),depth+1);
        if (f instanceof DensityFunctions.RangeChoice n) return safeInputs(n.input(),depth+1) && safeInputs(n.whenInRange(),depth+1) && safeInputs(n.whenOutOfRange(),depth+1);
        return false;
    }

    private static final class Builder {
        final ArrayList<NativeDensityProgram.Node> nodes = new ArrayList<>();
        final ArrayList<NormalNoise> noises = new ArrayList<>();
        final IdentityHashMap<NormalNoise,Integer> ids = new IdentityHashMap<>();
        int noiseCalls, octaves, depth;
        int noise(DensityFunction.NoiseHolder holder) {
            if (holder.noise() == null) return -1;
            noiseCalls++;
            octaves += 2*holder.noise().parameters().amplitudes().size();
            return ids.computeIfAbsent(holder.noise(), n -> { noises.add(n); return noises.size()-1; });
        }
        int add(DensityFunction f) {
            if (++depth > 48 || nodes.size() >= 48) { depth--; return -1; }
            try { return addNode(unwrap(f)); } finally { depth--; }
        }
        int addNode(DensityFunction f) {
            int op,a=0,b=0,c=0,noise=-1; double p=0,q=0,r=0;
            if (f instanceof DensityFunctions.Constant n) { op=0;p=n.value(); }
            else if (f instanceof DensityFunctions.Noise n) { op=1;noise=noise(n.noise());p=n.xzScale();q=n.yScale(); }
            else if (f instanceof DensityFunctions.ShiftNoise n) { op=f instanceof DensityFunctions.ShiftA?3:f instanceof DensityFunctions.ShiftB?4:2;noise=noise(n.offsetNoise()); }
            else if (f instanceof DensityFunctions.ShiftedNoise n) { op=5;a=add(n.shiftX());b=add(n.shiftY());c=add(n.shiftZ());noise=noise(n.noise());p=n.xzScale();q=n.yScale(); }
            else if (f instanceof DensityFunctions.WeirdScaledSampler n) { op=n.rarityValueMapper()==DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1?6:7;a=add(n.input());noise=noise(n.noise()); }
            else if (f instanceof DensityFunctions.Ap2 n) { op=switch(n.type()){case ADD->8;case MUL->9;case MIN->10;case MAX->11;};a=add(n.argument1());b=add(n.argument2());p=n.argument2().minValue();q=n.argument2().maxValue(); }
            else if (f instanceof DensityFunctions.MulOrAdd n) { op=n.specificType()==DensityFunctions.MulOrAdd.Type.MUL?12:13;a=add(n.input());p=n.argument(); }
            else if (f instanceof DensityFunctions.Mapped n) { op=switch(n.type()){case ABS->14;case SQUARE->15;case CUBE->16;case HALF_NEGATIVE->17;case QUARTER_NEGATIVE->18;case INVERT->19;case SQUEEZE->20;};a=add(n.input()); }
            else if (f instanceof DensityFunctions.Clamp n) { op=21;a=add(n.input());p=n.minValue();q=n.maxValue(); }
            else if (f instanceof DensityFunctions.RangeChoice n) { op=22;a=add(n.input());b=add(n.whenInRange());c=add(n.whenOutOfRange());p=n.minInclusive();q=n.maxExclusive(); }
            else return -1;
            if (a<0 || b<0 || c<0 || nodes.size()>=48) return -1;
            nodes.add(new NativeDensityProgram.Node(op,a,b,c,noise,p,q,r));
            return nodes.size()-1;
        }
    }
}
