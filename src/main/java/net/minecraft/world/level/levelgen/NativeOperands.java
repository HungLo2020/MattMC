package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.synth.NativeDensityProgram;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Fuse arithmetic after its mandatory operands have been visited in Java order.
 * Conditional nonconstant children stay inside opaque inputs; never speculate. */
final class NativeOperands implements DensityFunction {
    final DensityFunction source;
    private final DensityFunction[] inputs;
    private final NativeDensityProgram program;
    record Key(List<NativeDensityProgram.Node> nodes,int count) {
        NativeDensityProgram create() {
            return new NativeDensityProgram(nodes.toArray(NativeDensityProgram.Node[]::new),new NormalNoise[0],count);
        }
    }
    private NativeOperands(DensityFunction source,Builder b,NativeDensityProgram program) {
        this.source=source;this.inputs=b.inputs.toArray(DensityFunction[]::new);this.program=program;
    }
    static DensityFunction compile(DensityFunction source,Map<Key,NativeDensityProgram> programs) {
        if(source instanceof NativeDensity || source instanceof NativeOperands)return source;
        var b=new Builder();
        if(b.add(source,0)<0 || b.inputs.size()<2 || b.operations<4)return source;
        var key=new Key(List.copyOf(b.nodes),b.inputs.size());
        return new NativeOperands(source,b,programs.computeIfAbsent(key,Key::create));
    }
    @Override public double compute(FunctionContext context) {
        // Locals survive reentrant child evaluations without shared scratch buffers.
        double a=inputs[0].compute(context), b=inputs[1].compute(context);
        double c=inputs.length>2?inputs[2].compute(context):0.;
        double d=inputs.length>3?inputs[3].compute(context):0.;
        return program.operands(a,b,c,d);
    }
    @Override public void fillArray(double[] values,ContextProvider provider) { source.fillArray(values,provider); }
    @Override public DensityFunction mapAll(Visitor visitor) { return source.mapAll(visitor); }
    @Override public double minValue() { return source.minValue(); }
    @Override public double maxValue() { return source.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return source.codec(); }

    private static DensityFunction unwrap(DensityFunction f) {
        while(true) {
            if(f instanceof NativeUnary n)f=n.source;
            else if(f instanceof NativeOperands n)f=n.source;
            else return f;
        }
    }
    private static boolean constant(DensityFunction f,int depth) {
        if(depth>48)return false;
        f=unwrap(f);
        if(f instanceof DensityFunctions.Constant)return true;
        if(f instanceof DensityFunctions.Mapped n)return constant(n.input(),depth+1);
        if(f instanceof DensityFunctions.MulOrAdd n)return constant(n.input(),depth+1);
        if(f instanceof DensityFunctions.Clamp n)return constant(n.input(),depth+1);
        if(f instanceof DensityFunctions.Ap2 n)return constant(n.argument1(),depth+1)&&constant(n.argument2(),depth+1);
        if(f instanceof DensityFunctions.RangeChoice n)return constant(n.input(),depth+1)&&constant(n.whenInRange(),depth+1)&&constant(n.whenOutOfRange(),depth+1);
        return false;
    }
    private static final class Builder {
        final ArrayList<NativeDensityProgram.Node> nodes=new ArrayList<>();
        final ArrayList<DensityFunction> inputs=new ArrayList<>();
        int operations;
        int add(DensityFunction original,int depth) {
            if(depth>48 || nodes.size()>=48)return -1;
            DensityFunction f=unwrap(original);
            int op,a=0,b=0,c=0;double p=0,q=0;
            if(f instanceof DensityFunctions.Constant n) {op=0;p=n.value();}
            else if(f instanceof DensityFunctions.Mapped n) {op=14+n.type().ordinal();a=add(n.input(),depth+1);}
            else if(f instanceof DensityFunctions.MulOrAdd n) {op=n.specificType()==DensityFunctions.MulOrAdd.Type.MUL?12:13;a=add(n.input(),depth+1);p=n.argument();}
            else if(f instanceof DensityFunctions.Clamp n) {op=21;a=add(n.input(),depth+1);p=n.minValue();q=n.maxValue();}
            else if(f instanceof DensityFunctions.Ap2 n && (n.type()==DensityFunctions.TwoArgumentSimpleFunction.Type.ADD || constant(n.argument2(),0))) {
                op=switch(n.type()){case ADD->8;case MUL->9;case MIN->10;case MAX->11;};
                a=add(n.argument1(),depth+1);b=add(n.argument2(),depth+1);
                p=n.type()==DensityFunctions.TwoArgumentSimpleFunction.Type.MIN?n.argument2().minValue():0.;
                q=n.type()==DensityFunctions.TwoArgumentSimpleFunction.Type.MAX?n.argument2().maxValue():0.;
            } else if(f instanceof DensityFunctions.RangeChoice n && constant(n.whenInRange(),0) && constant(n.whenOutOfRange(),0)) {
                op=22;a=add(n.input(),depth+1);b=add(n.whenInRange(),depth+1);c=add(n.whenOutOfRange(),depth+1);p=n.minInclusive();q=n.maxExclusive();
            } else {
                if(inputs.size()==4)return -1;
                op=23;a=inputs.size();inputs.add(original);
            }
            if(a<0 || b<0 || c<0 || nodes.size()>=48)return -1;
            if(op!=0 && op!=23)operations++;
            nodes.add(new NativeDensityProgram.Node(op,a,b,c,-1,p,q,0));
            return nodes.size()-1;
        }
    }
}
