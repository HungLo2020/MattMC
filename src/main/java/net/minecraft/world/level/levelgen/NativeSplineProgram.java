package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable, validated spline tape. Java owns the allocation for its full lifetime. */
final class NativeSplineProgram {
    record Node(int axis, int start, int count, float value) {}
    record Knot(float location, float derivative, int child) {}
    private static final MethodHandle VALIDATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_spline_validate",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle EVAL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_spline_eval",
        FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
    private static final MethodHandle SMALL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_spline_eval",
        FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT), Linker.Option.critical(false));
    private final boolean small;
    private static final FunctionDescriptor CACHED_TYPE = FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
    private static final MethodHandle CACHED = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_spline_cached",CACHED_TYPE);
    private static final MethodHandle SMALL_CACHED = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_spline_cached",CACHED_TYPE,Linker.Option.critical(false));
    private static final MethodHandle VALIDATE_COORDINATES = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_spline_coordinates_validate",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private final java.util.concurrent.ConcurrentMap<List<List<NativeUnaryProgram.Step>>,MemorySegment> coordinatePrograms = new java.util.concurrent.ConcurrentHashMap<>();
    private final MemorySegment nodes, knots;
    private final int count, nknots;
    NativeSplineProgram(List<Node> ns, List<Knot> ks) {
        count = ns.size(); nknots = ks.size();
        if (count < 1 || count > 4096 || nknots > 16384) throw new IllegalArgumentException("Spline program size");
        Arena arena = Arena.ofAuto();
        var n = arena.allocate(count * 16L, 4); var k = arena.allocate(Math.max(1, nknots) * 12L, 4);
        for (int i = 0; i < count; i++) {
            Node node = ns.get(i); long at = i * 16L;
            n.set(ValueLayout.JAVA_INT, at, node.axis()); n.set(ValueLayout.JAVA_INT, at + 4, node.start());
            n.set(ValueLayout.JAVA_INT, at + 8, node.count()); n.set(ValueLayout.JAVA_FLOAT, at + 12, node.value());
        }
        for (int i = 0; i < nknots; i++) {
            Knot knot = ks.get(i); long at = i * 12L;
            k.set(ValueLayout.JAVA_FLOAT, at, knot.location()); k.set(ValueLayout.JAVA_FLOAT, at + 4, knot.derivative());
            k.set(ValueLayout.JAVA_INT, at + 8, knot.child());
        }
        nodes = n.asReadOnly(); knots = k.asReadOnly();
        try { if ((int)VALIDATE.invokeExact(nodes, count, knots, nknots) != 0) throw new IllegalArgumentException("Invalid spline program"); }
        catch (Throwable t) { throw failure(t); }
        int[] work = new int[count];
        for (int i=0;i<count;i++) {
            Node node=ns.get(i);int first=0,second=0;
            for(int j=0;j<node.count();j++) {
                int v=work[ks.get(node.start()+j).child()];
                if(v>=first){second=first;first=v;}else second=Math.max(second,v);
            }
            work[i]=1+first+second;
        }
        small=work[count-1]<=128;
    }
    float sample(float a, float b, float c, float d) {
        try { return small ? (float)SMALL.invokeExact(nodes, count, knots, nknots, a, b, c, d)
                           : (float)EVAL.invokeExact(nodes, count, knots, nknots, a, b, c, d); }
        catch (Throwable t) { throw failure(t); }
    }
    MemorySegment coordinates(List<List<NativeUnaryProgram.Step>> steps) {
        if (steps.size() > 4) throw new IllegalArgumentException("Too many spline coordinates");
        return coordinatePrograms.computeIfAbsent(steps, key -> {
            var data=Arena.ofAuto().allocate(4*392L,8);
            for(int axis=0;axis<key.size();axis++) {
                var operations=key.get(axis);if(operations.size()>16)throw new IllegalArgumentException("Spline coordinate complexity");
                long base=axis*392L;data.set(ValueLayout.JAVA_INT,base,operations.size());
                for(int i=0;i<operations.size();i++) {
                    var op=operations.get(i);long at=base+8+i*24L;
                    data.set(ValueLayout.JAVA_INT,at,op.op());data.set(ValueLayout.JAVA_DOUBLE,at+8,op.p());data.set(ValueLayout.JAVA_DOUBLE,at+16,op.q());
                }
            }
            try {if((int)VALIDATE_COORDINATES.invokeExact(data,data.byteSize())!=0)throw new IllegalArgumentException("Invalid spline coordinates");}
            catch(Throwable t){throw failure(t);}
            return data.asReadOnly();
        });
    }
    float cached(MemorySegment coordinates,double a,double b,double c,double d) {
        try {return small?(float)SMALL_CACHED.invokeExact(nodes,count,knots,nknots,coordinates,a,b,c,d)
                         :(float)CACHED.invokeExact(nodes,count,knots,nknots,coordinates,a,b,c,d);}
        catch(Throwable t){throw failure(t);}
    }
    private static RuntimeException failure(Throwable t) {
        if (t instanceof Error e) throw e;
        if (t instanceof RuntimeException e) return e;
        return new IllegalStateException("Native spline evaluation failed", t);
    }
}
