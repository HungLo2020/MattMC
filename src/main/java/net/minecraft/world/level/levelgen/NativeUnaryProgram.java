package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable, bounded arithmetic pipeline; no context, cache or noise references. */
public final class NativeUnaryProgram {
    public record Step(int op,double p,double q) {}
    private static final MethodHandle APPLY=bind("unary",FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
        ValueLayout.ADDRESS,ValueLayout.JAVA_DOUBLE),false);
    private static final MethodHandle ARRAY=bind("unary_array",FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT),true);
    private static final MethodHandle VALIDATE=bind("unary_validate",FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_LONG),false);
    private final MemorySegment state;
    private static MethodHandle bind(String name,FunctionDescriptor descriptor,boolean heap) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_density_"+name,descriptor,Linker.Option.critical(heap));
    }
    public NativeUnaryProgram(List<Step> steps) {
        if(steps.isEmpty() || steps.size()>16)throw new IllegalArgumentException("Unary step count");
        MemorySegment data=Arena.ofAuto().allocate(8L+24L*steps.size(),8);
        data.set(ValueLayout.JAVA_INT,0,steps.size());
        for(int i=0;i<steps.size();i++) {
            Step step=steps.get(i);long at=8L+24L*i;
            data.set(ValueLayout.JAVA_INT,at,step.op);
            data.set(ValueLayout.JAVA_DOUBLE,at+8,step.p);
            data.set(ValueLayout.JAVA_DOUBLE,at+16,step.q);
        }
        try {
            int status=(int)VALIDATE.invokeExact(data,data.byteSize());
            if(status!=0)throw new IllegalArgumentException("Invalid unary program: "+status);
        } catch(Throwable t) { throw failure(t); }
        state=data.asReadOnly();
    }
    public double apply(double value) {
        try { return (double)APPLY.invokeExact(state,value); }
        catch(Throwable t) { throw failure(t); }
    }
    public void applyArray(double[] values) {
        MemorySegment out=MemorySegment.ofArray(values);
        try {
            for(int i=0;i<values.length;i+=256) {
                int count=Math.min(256,values.length-i);
                int status=(int)ARRAY.invokeExact(state,out.asSlice(i*8L,count*8L),count);
                if(status!=0)throw new IllegalStateException("Native unary array: "+status);
            }
        } catch(Throwable t) { throw failure(t); }
    }
    private static RuntimeException failure(Throwable t) {
        if(t instanceof Error e)throw e;
        if(t instanceof RuntimeException e)return e;
        return new IllegalStateException("Native unary evaluation failed",t);
    }
}
