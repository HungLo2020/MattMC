package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.KeyDispatchDataCodec;

/** Fuses consecutive transforms without speculating about child evaluation. */
final class NativeUnary implements DensityFunction {
    final DensityFunction source;
    private final DensityFunction input;
    private final NativeUnaryProgram program;
    private final boolean conditional;
    private NativeUnary(DensityFunction source, DensityFunction input, NativeUnaryProgram program, boolean conditional) {
        this.source=source;this.input=input;this.program=program;this.conditional=conditional;
    }
    static DensityFunction compile(DensityFunction source, Map<List<NativeUnaryProgram.Step>,NativeUnaryProgram> programs) {
        if(source instanceof NativeDensity || source instanceof NativeUnary)return source;
        var steps=new ArrayList<NativeUnaryProgram.Step>();
        DensityFunction input=source;
        while(steps.size()<16) {
            if(input instanceof NativeUnary n)input=n.source;
            if(input instanceof DensityFunctions.Mapped n) {
                steps.add(new NativeUnaryProgram.Step(14+n.type().ordinal(),0,0));input=n.input();
            } else if(input instanceof DensityFunctions.MulOrAdd n) {
                steps.add(new NativeUnaryProgram.Step(n.specificType()==DensityFunctions.MulOrAdd.Type.MUL?12:13,n.argument(),0));input=n.input();
            } else if(input instanceof DensityFunctions.Clamp n) {
                steps.add(new NativeUnaryProgram.Step(21,n.minValue(),n.maxValue()));input=n.input();
            } else if(input instanceof DensityFunctions.Ap2 n && n.argument2() instanceof DensityFunctions.Constant c
                && (n.type()==DensityFunctions.TwoArgumentSimpleFunction.Type.MIN || n.type()==DensityFunctions.TwoArgumentSimpleFunction.Type.MAX)) {
                steps.add(new NativeUnaryProgram.Step(n.type()==DensityFunctions.TwoArgumentSimpleFunction.Type.MIN?10:11,c.value(),0));input=n.argument1();
            } else break;
        }
        if(steps.size()<2)return source;
        var key=List.copyOf(steps.reversed());
        return new NativeUnary(source,input,programs.computeIfAbsent(key,NativeUnaryProgram::new),key.stream().anyMatch(step->step.op()==10 || step.op()==11));
    }
    /** Build a private evaluation tree. Cache nodes remain leaves; their wrapped
     * source graph must stay unchanged for NoiseChunk's structural deduplication. */
    static DensityFunction tree(DensityFunction f, Map<List<NativeUnaryProgram.Step>,NativeUnaryProgram> programs) {
        return tree(f,programs,new java.util.HashMap<>());
    }
    static DensityFunction tree(DensityFunction f, Map<List<NativeUnaryProgram.Step>,NativeUnaryProgram> programs,
                               Map<NativeOperands.Key,NativeDensityProgram> operands) {
        return tree(f,programs,operands,new java.util.HashMap<>());
    }
    static DensityFunction tree(DensityFunction f, Map<List<NativeUnaryProgram.Step>,NativeUnaryProgram> programs,
        Map<NativeOperands.Key,NativeDensityProgram> operands,
        Map<NativeCellDensity.Key,NativeDensityProgram> cells) {
        return tree(f,programs,operands,cells,new java.util.IdentityHashMap<>(),0);
    }
    private static DensityFunction tree(DensityFunction f, Map<List<NativeUnaryProgram.Step>,NativeUnaryProgram> programs,
                                       Map<NativeOperands.Key,NativeDensityProgram> operands,
                                       Map<NativeCellDensity.Key,NativeDensityProgram> cells,
                                       java.util.IdentityHashMap<DensityFunction,DensityFunction> seen,int depth) {
        if(depth>64 || f instanceof NativeDensity || f instanceof NativeUnary)return f;
        DensityFunction cached=seen.get(f);if(cached!=null)return cached;
        // Compile a complete cell expression before building transforms for its
        // children; nested private wrappers would only add construction work.
        DensityFunction cell=NativeCellDensity.compile(f,cells);
        if(cell!=f){seen.put(f,cell);return cell;}
        DensityFunction result=f;
        if(f instanceof DensityFunctions.Spline n) {
            result=NativeSpline.compile(n);
        } else if(f instanceof DensityFunctions.Mapped n) {
            result=new DensityFunctions.Mapped(n.type(),tree(n.input(),programs,operands,cells,seen,depth+1),n.minValue(),n.maxValue());
        } else if(f instanceof DensityFunctions.MulOrAdd n) {
            result=new DensityFunctions.MulOrAdd(n.specificType(),tree(n.input(),programs,operands,cells,seen,depth+1),n.minValue(),n.maxValue(),n.argument());
        } else if(f instanceof DensityFunctions.Clamp n) {
            result=new DensityFunctions.Clamp(tree(n.input(),programs,operands,cells,seen,depth+1),n.minValue(),n.maxValue());
        } else if(f instanceof DensityFunctions.Ap2 n) {
            result=new DensityFunctions.Ap2(n.type(),tree(n.argument1(),programs,operands,cells,seen,depth+1),tree(n.argument2(),programs,operands,cells,seen,depth+1),n.minValue(),n.maxValue());
        } else if(f instanceof DensityFunctions.RangeChoice n) {
            result=new DensityFunctions.RangeChoice(tree(n.input(),programs,operands,cells,seen,depth+1),n.minInclusive(),n.maxExclusive(),
                tree(n.whenInRange(),programs,operands,cells,seen,depth+1),tree(n.whenOutOfRange(),programs,operands,cells,seen,depth+1));
        } else if(f instanceof DensityFunctions.ShiftedNoise n) {
            result=new DensityFunctions.ShiftedNoise(tree(n.shiftX(),programs,operands,cells,seen,depth+1),tree(n.shiftY(),programs,operands,cells,seen,depth+1),
                tree(n.shiftZ(),programs,operands,cells,seen,depth+1),n.xzScale(),n.yScale(),n.noise());
        } else if(f instanceof DensityFunctions.WeirdScaledSampler n) {
            result=new DensityFunctions.WeirdScaledSampler(tree(n.input(),programs,operands,cells,seen,depth+1),n.noise(),n.rarityValueMapper());
        } else if(f instanceof DensityFunctions.BlendDensity n) {
            result=new DensityFunctions.BlendDensity(tree(n.input(),programs,operands,cells,seen,depth+1));
        } else if(f instanceof DensityFunctions.FindTopSurface n) {
            result=new DensityFunctions.FindTopSurface(tree(n.density(),programs,operands,cells,seen,depth+1),
                tree(n.upperBound(),programs,operands,cells,seen,depth+1),n.lowerBound(),n.cellHeight());
        }
        result=NativeOperands.compile(compile(result,programs),operands);seen.put(f,result);return result;
    }
    @Override public double compute(FunctionContext c) { return program.apply(input.compute(c)); }
    @Override public void fillArray(double[] values,ContextProvider provider) {
        if(conditional)source.fillArray(values,provider);
        else {input.fillArray(values,provider);program.applyArray(values);}
    }
    @Override public DensityFunction mapAll(Visitor visitor) { return source.mapAll(visitor); }
    @Override public double minValue() { return source.minValue(); }
    @Override public double maxValue() { return source.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return source.codec(); }
    // NoiseChunk deduplicates structurally equal marker graphs. A wrapper must
    // retain that equality or shared interpolators get created more than once.
    @Override public boolean equals(Object other) {
        return this==other || other instanceof NativeUnary n && source.equals(n.source);
    }
    @Override public int hashCode() { return source.hashCode(); }
}
