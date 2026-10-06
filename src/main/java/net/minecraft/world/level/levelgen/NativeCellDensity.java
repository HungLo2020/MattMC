package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Private cell evaluator: immutable programs are shared, corner snapshots belong
 * to one NoiseChunk. The canonical density/cache graph is never rewritten. */
final class NativeCellDensity implements DensityFunction {
    final DensityFunction source;
    private final NoiseChunk owner;
    private final NoiseChunk.NoiseInterpolator[] inputs;
    private final NativeDensityProgram program;
    private final double[] corners;
    private boolean loaded;
    private long epoch;
    record Key(List<NativeDensityProgram.Node> nodes,int count) {
        NativeDensityProgram create() {
            return new NativeDensityProgram(nodes.toArray(NativeDensityProgram.Node[]::new),new NormalNoise[0],0,count);
        }
    }
    private NativeCellDensity(DensityFunction source,Builder b,NativeDensityProgram program) {
        this.source=source;this.owner=b.owner;this.inputs=b.inputs.toArray(NoiseChunk.NoiseInterpolator[]::new);
        this.program=program;this.corners=new double[inputs.length*8];
    }
    static DensityFunction compile(DensityFunction source,Map<Key,NativeDensityProgram> programs) {
        if(source instanceof NativeCellDensity || source instanceof NativeDensity)return source;
        var b=new Builder(false);
        if(!b.accepts(source))return source;
        var key=new Key(List.copyOf(b.nodes),b.inputs.size());
        return new NativeCellDensity(source,b,programs.computeIfAbsent(key,Key::create));
    }
    /** The program {@link #compile} builds for a chunk's wrapped cell cache,
     * from a RandomState's unwrapped graph: its inputs are the interpolated
     * markers (in input order) the chunk's interpolators would wrap. */
    record Template(NativeDensityProgram program,List<DensityFunctions.Marker> inputs) {}
    @org.jetbrains.annotations.Nullable
    static Template template(DensityFunction source,Map<Key,NativeDensityProgram> programs) {
        var b=new Builder(true);
        if(!b.accepts(source))return null;
        var key=new Key(List.copyOf(b.nodes),b.inputs.size());
        List<DensityFunctions.Marker> inputs=new ArrayList<>();
        for(Object input:b.inputs)inputs.add((DensityFunctions.Marker)input);
        return new Template(programs.computeIfAbsent(key,Key::create),List.copyOf(inputs));
    }
    NoiseChunk owner() { return owner; }
    NoiseChunk.NoiseInterpolator[] inputs() { return inputs.clone(); }
    NativeDensityProgram program() { return program; }
    private boolean active(Object context) {
        return context==owner && owner.getClass()==NoiseChunk.class && owner.interpolating && owner.fillingCell;
    }
    private void prepare() {
        if(!loaded || epoch!=owner.arrayInterpolationCounter) {
            for(int i=0;i<inputs.length;i++)inputs[i].copyCellCorners(corners,i*8);
            epoch=owner.arrayInterpolationCounter;loaded=true;
        }
    }
    @Override public double compute(FunctionContext context) {
        if(!active(context))return source.compute(context);
        prepare();
        return program.cell(corners,(double)owner.inCellX/owner.cellWidth(),
            (double)owner.inCellY/owner.cellHeight(),(double)owner.inCellZ/owner.cellWidth());
    }
    @Override public void fillArray(double[] values,ContextProvider provider) {
        int width=owner.cellWidth(),height=owner.cellHeight();
        if(!active(provider) || width<1 || width>64 || height<1 || height>4096 || (long)width*width*height!=values.length) {
            source.fillArray(values,provider);return;
        }
        prepare();long last=program.cellArray(corners,values,width,height);
        // Known cell providers only mutate these coordinates/index while traversing
        // a graph of pure arithmetic and interpolators. Reproduce the final visit,
        // including a skipped tail and fillAllDirectly's postincremented index.
        if(last>=0) {
            owner.forIndex((int)last);
            if(((last>>>32)&1)!=0)owner.arrayIndex=(int)last+1;
        }
    }
    @Override public DensityFunction mapAll(Visitor visitor){return source.mapAll(visitor);}
    @Override public double minValue(){return source.minValue();}
    @Override public double maxValue(){return source.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return source.codec();}
    private static DensityFunction unwrap(DensityFunction f) {
        while(true) {
            if(f instanceof NativeUnary n)f=n.source;
            else if(f instanceof NativeOperands n)f=n.source;
            else if(f instanceof NativeCellDensity n)f=n.source;
            else return f;
        }
    }
    private static boolean isInterpolated(DensityFunction f) {
        return f.getClass()==DensityFunctions.Marker.class && ((DensityFunctions.Marker)f).type()==DensityFunctions.Marker.Type.Interpolated;
    }
    private static boolean stableBounds(DensityFunction f,int depth) {
        if(depth>64)return false;
        f=unwrap(f);
        if(f instanceof DensityFunctions.HolderHolder h)return h.function().isBound() && stableBounds(h.function().value(),depth+1);
        if(isInterpolated(f))return stableBounds(((DensityFunctions.Marker)f).wrapped(),depth+1);
        if(f instanceof NativeDensity n)return stableBounds(NativeDensity.unwrap(n),depth+1);
        if(f instanceof DensityFunctions.Constant || f instanceof DensityFunctions.Ap2
            || f instanceof DensityFunctions.Mapped || f instanceof DensityFunctions.MulOrAdd
            || f instanceof DensityFunctions.Clamp || f instanceof DensityFunctions.BlendDensity)return true;
        if(f.getClass()==NoiseChunk.NoiseInterpolator.class)return stableBounds(((NoiseChunk.NoiseInterpolator)f).wrapped(),depth+1);
        if(f instanceof DensityFunctions.RangeChoice n)return stableBounds(n.whenInRange(),depth+1)&&stableBounds(n.whenOutOfRange(),depth+1);
        return NativeDensity.safeInputs(f);
    }
    private static final class Builder {
        final ArrayList<NativeDensityProgram.Node> nodes=new ArrayList<>();
        // Chunk interpolators, or interpolated markers for a template.
        final ArrayList<Object> inputs=new ArrayList<>();
        final boolean template;
        NoiseChunk owner;int operations;
        Builder(boolean template) { this.template=template; }
        boolean accepts(DensityFunction source) {
            if(add(source,0)<0)return false;
            boolean unaryCell=(nodes.size()==3 || nodes.size()==5 && nodes.get(3).op()==0 && nodes.get(4).op()==8) && nodes.get(0).op()==24
                && nodes.get(1).op()==12 && nodes.get(2).op()==20;
            return unaryCell || inputs.size()>=2 && operations>=4;
        }
        int add(DensityFunction source,int depth) {
            if(depth>48 || nodes.size()>=48)return -1;
            DensityFunction f=unwrap(source);
            // A chunk-wrapped graph has no holders; a template resolves them as wrapping does.
            for(int step=0;template && f instanceof DensityFunctions.HolderHolder h && step<64;step++) {
                if(!h.function().isBound())return -1;
                f=unwrap(h.function().value());
            }
            int op,a=0,b=0,c=0;double p=0,q=0;
            if(f instanceof DensityFunctions.Constant n){op=0;p=n.value();}
            else if(f==DensityFunctions.BeardifierMarker.INSTANCE || f==Beardifier.EMPTY){op=0;}
            else if(!template && f.getClass()==NoiseChunk.NoiseInterpolator.class) {
                var n=(NoiseChunk.NoiseInterpolator)f;
                if(owner!=null && owner!=n.cellOwner())return -1;
                owner=n.cellOwner();op=24;a=inputs.indexOf(n);
                if(a<0) {if(inputs.size()==8)return -1;a=inputs.size();inputs.add(n);}
            } else if(template && isInterpolated(f)) {
                // Chunk wrapping turns equal markers into one interpolator.
                op=24;a=inputs.indexOf(f);
                if(a<0) {if(inputs.size()==8)return -1;a=inputs.size();inputs.add(f);}
            } else if(f instanceof DensityFunctions.Mapped n){op=14+n.type().ordinal();a=add(n.input(),depth+1);}
            else if(f instanceof DensityFunctions.MulOrAdd n){op=n.specificType()==DensityFunctions.MulOrAdd.Type.MUL?12:13;a=add(n.input(),depth+1);p=n.argument();}
            else if(f instanceof DensityFunctions.Clamp n){op=21;a=add(n.input(),depth+1);p=n.minValue();q=n.maxValue();}
            else if(f instanceof DensityFunctions.Ap2 n) {
                op=switch(n.type()){case ADD->8;case MUL->9;case MIN->10;case MAX->11;};
                if((op==10 || op==11) && !stableBounds(n.argument2(),0))return -1;
                a=add(n.argument1(),depth+1);b=add(n.argument2(),depth+1);
                p=op==10?n.argument2().minValue():0.;q=op==11?n.argument2().maxValue():0.;
            } else if(f instanceof DensityFunctions.RangeChoice n) {
                op=22;a=add(n.input(),depth+1);b=add(n.whenInRange(),depth+1);c=add(n.whenOutOfRange(),depth+1);
                p=n.minInclusive();q=n.maxExclusive();
            } else return -1;
            if(a<0 || b<0 || c<0 || nodes.size()>=48)return -1;
            if(op!=0 && op!=24)operations++;
            nodes.add(new NativeDensityProgram.Node(op,a,b,c,-1,p,q,0));return nodes.size()-1;
        }
    }
}
