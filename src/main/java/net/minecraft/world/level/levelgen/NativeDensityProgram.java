package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;

/** Internal compiled density ABI; all native pointers are offsets within one GC-owned segment. */
public final class NativeDensityProgram {
    public record Node(int op, int a, int b, int c, int noise, double p, double q, double r) {}
    private static final MethodHandle EVAL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_density_eval",
        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE), Linker.Option.critical(false));
    private static final MethodHandle VALIDATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_density_validate",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle OPERANDS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_density_operands",
        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE), Linker.Option.critical(false));
    private static final MethodHandle CELL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_density_cell",
        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,ValueLayout.ADDRESS,ValueLayout.ADDRESS,
            ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE),Linker.Option.critical(true));
    private static final MethodHandle CELL_ARRAY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_density_cell_array",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT),Linker.Option.critical(true));
    private final int cells;
    private final Node[] nodes;
    private final NormalNoise[] noises;
    private final int inputs;
    private record Snapshot(MemorySegment state, NativeNoiseState[] noises) {}
    private volatile Snapshot snapshot;
    public NativeDensityProgram(Node[] nodes, NormalNoise[] noises) {
        this(nodes,noises,0);
    }
    public NativeDensityProgram(Node[] nodes, NormalNoise[] noises,int inputs) {
        this(nodes,noises,inputs,0);
    }
    public NativeDensityProgram(Node[] nodes,NormalNoise[] noises,int inputs,int cells) {
        if(cells<0 || cells>8 || cells>0 && (inputs!=0 || noises.length!=0))throw new IllegalArgumentException("Density cell inputs");
        this.cells=cells;
        this.nodes=nodes.clone(); this.noises=noises.clone();
        if(inputs<0 || inputs>4)throw new IllegalArgumentException("Density operand count");
        this.inputs=inputs;
        if(nodes.length==0 || nodes.length>48) throw new IllegalArgumentException("Density node count");
    }
    private Snapshot state() {
        Snapshot s=snapshot;
        if (s!=null) {
            boolean fresh=true;
            for(int i=0;i<noises.length;i++) if(noises[i].nativeState()!=s.noises[i]) {fresh=false;break;}
            if(fresh) return s;
        }
        return rebuild();
    }
    private synchronized Snapshot rebuild() {
        NativeNoiseState[] current=new NativeNoiseState[noises.length];
        long size=16L+64L*nodes.length; int octaves=0;
        long[] offsets=new long[noises.length];
        for(int i=0;i<current.length;i++) {
            current[i]=noises[i].nativeState(); offsets[i]=size; size+=current[i].state().byteSize();
            octaves+=current[i].octaveCount();
        }
        if(octaves>128) throw new IllegalArgumentException("Density critical-call work limit");
        MemorySegment data=Arena.ofAuto().allocate(size,8);
        data.set(ValueLayout.JAVA_INT,0,nodes.length);
        data.set(ValueLayout.JAVA_INT,4,inputs);
        data.set(ValueLayout.JAVA_INT,8,cells);
        for(int i=0;i<nodes.length;i++) {
            Node n=nodes[i]; long at=16L+i*64L;
            data.set(ValueLayout.JAVA_INT,at,n.op);data.set(ValueLayout.JAVA_INT,at+4,n.a);data.set(ValueLayout.JAVA_INT,at+8,n.b);data.set(ValueLayout.JAVA_INT,at+12,n.c);
            if(n.noise>=0) {
                data.set(ValueLayout.JAVA_LONG,at+16,offsets[n.noise]);
                data.set(ValueLayout.JAVA_LONG,at+24,current[n.noise].state().byteSize());
            }
            data.set(ValueLayout.JAVA_DOUBLE,at+32,n.p);data.set(ValueLayout.JAVA_DOUBLE,at+40,n.q);data.set(ValueLayout.JAVA_DOUBLE,at+48,n.r);
        }
        for(int i=0;i<current.length;i++) MemorySegment.copy(current[i].state(),0,data,offsets[i],current[i].state().byteSize());
        try { int status=(int)VALIDATE.invokeExact(data,size); if(status!=0)throw new IllegalArgumentException("Invalid density ABI: "+status); }
        catch(Throwable t){throw failure(t);}
        Snapshot s=new Snapshot(data.asReadOnly(),current);snapshot=s;return s;
    }
    /** The validated program memory; it stays valid while it is reachable. */
    MemorySegment memory() { return state().state; }
    public double sample(double x,double y,double z) {
        Snapshot s=state();
        try{return (double)EVAL.invokeExact(s.state,x,y,z);}catch(Throwable t){throw failure(t);}
    }
    public double operands(double a,double b,double c,double d) {
        Snapshot s=state();
        try{return (double)OPERANDS.invokeExact(s.state,a,b,c,d);}catch(Throwable t){throw failure(t);}
    }
    public double cell(double[] corners,double x,double y,double z) {
        if(cells==0 || corners.length!=cells*8)throw new IllegalArgumentException("Cell corners");
        Snapshot s=state();MemorySegment frame=MemorySegment.ofArray(corners);
        try{return (double)CELL.invokeExact(s.state,frame,x,y,z);}catch(Throwable t){throw failure(t);}
    }
    /** Returns the last observable provider visit, encoded as phase/kind/index. */
    public long cellArray(double[] corners,double[] output,int width,int height) {
        if(cells==0 || corners==output || corners.length!=cells*8 || width<1 || height<1
            || width>64 || height>4096 || (long)width*width*height!=output.length)throw new IllegalArgumentException("Cell dimensions");
        Snapshot s=state();MemorySegment frame=MemorySegment.ofArray(corners),out=MemorySegment.ofArray(output);
        long last=-1;
        for(int start=0;start<output.length;start+=256) {
            int count=Math.min(256,output.length-start);long visit;
            try {visit=(long)CELL_ARRAY.invokeExact(s.state,frame,out.asSlice(start*8L,count*8L),start,count,width,height);}
            catch(Throwable t){throw failure(t);}
            if(visit < -1)throw new IllegalStateException("Native cell fill failed: "+visit);
            last=Math.max(last,visit);
        }
        return last;
    }
    private static RuntimeException failure(Throwable t) {
        if(t instanceof Error e)throw e;
        if(t instanceof RuntimeException e)return e;
        return new IllegalStateException("Native density evaluation failed",t);
    }
}
