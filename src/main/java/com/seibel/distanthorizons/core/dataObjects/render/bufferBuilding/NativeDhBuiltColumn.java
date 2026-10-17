package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.ArrayList;
import net.minecraft.util.NativeLibraryLoader;

/** Scoped immutable CPU build. Publication moves its bytes into Rust's ledger; no GPU handles. */
public final class NativeDhBuiltColumn implements AutoCloseable {
    private static final Cleaner CLEANER=Cleaner.create();
    private static MethodHandle bind(String name, MemoryLayout result, MemoryLayout... args) {
        var descriptor = result == null ? FunctionDescriptor.ofVoid(args) : FunctionDescriptor.of(result,args);
        return NativeLibraryLoader.downcallHandle("mattmc_rust",name,descriptor,Linker.Option.critical(true));
    }
    private static final MethodHandle CREATE=bind("mattmc_dh_built_column_create",ValueLayout.ADDRESS);
    private static final MethodHandle RELEASE=bind("mattmc_dh_built_column_release",null,ValueLayout.ADDRESS);
    private static final MethodHandle APPEND=bind("mattmc_dh_built_column_append",ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT);
    private static final MethodHandle VALIDATE=bind("mattmc_dh_built_column_validate",ValueLayout.JAVA_INT,ValueLayout.ADDRESS);
    private static final MethodHandle COUNT=bind("mattmc_dh_built_column_count",ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT);
    private static final MethodHandle COPY=bind("mattmc_dh_built_column_copy",ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT);
    private static final MethodHandle COVERAGE=bind("mattmc_dh_built_column_coverage",ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS);
    private static final MethodHandle SAMPLES=bind("mattmc_dh_built_column_water_samples",ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT);
    private static final MethodHandle RECORD=bind("mattmc_dh_collector_record_native",ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS);
    private static final class Lease implements Runnable {
        MemorySegment pointer;
        Lease(MemorySegment pointer){this.pointer=pointer;}
        @Override public synchronized void run() {
            if(pointer.address()==0)return;
            try {RELEASE.invokeExact(pointer);}catch(Throwable e){throw failure(e);}
            finally {pointer=MemorySegment.NULL;}
        }
    }
    private final Lease lease;
    private final Cleaner.Cleanable cleanable;
    NativeDhBuiltColumn() {
        try {
            var pointer=(MemorySegment)CREATE.invokeExact();if(pointer.address()==0)throw new IllegalStateException("Cannot allocate DH column build");
            lease=new Lease(pointer);
            try {cleanable=CLEANER.register(this,lease);}catch(Throwable e){lease.run();throw e;}
        }
        catch(Throwable e){throw failure(e);}
    }
    private static RuntimeException failure(Throwable e) {
        if(e instanceof Error error)throw error;
        return e instanceof RuntimeException error?error:new IllegalStateException("Native DH column operation failed",e);
    }
    private MemorySegment pointer() {
        if(lease.pointer.address()==0)throw new IllegalStateException("DH column build closed");return lease.pointer;
    }
    synchronized void append(MemorySegment plan,int route,int[] colors,float[] shades) {
        if(colors.length!=shades.length)throw new IllegalArgumentException("DH callback extents differ");
        try {
            var c=colors.length==0?MemorySegment.NULL:MemorySegment.ofArray(colors);
            var s=shades.length==0?MemorySegment.NULL:MemorySegment.ofArray(shades);
            if((int)APPEND.invokeExact(pointer(),plan,route,c,s,colors.length)!=1)throw new IllegalStateException("Cannot append DH packet layer");
        }catch(Throwable e){throw failure(e);}finally{Reference.reachabilityFence(this);}
    }
    private int count(int layer,int index) throws Throwable {
        int count=(int)COUNT.invokeExact(pointer(),layer,index);
        if(count<0)throw new IllegalStateException("Invalid DH column projection");return count;
    }
    /** Copies requested immutable bytes/companions for diagnostics; ordinary publication does not use this. */
    public synchronized LodQuadBuilder.SemanticVertexBufferBuild project(int layer) {
        try {
            int segments=count(layer,-1);
            var vertices=new ArrayList<byte[]>(segments);var ids=new ArrayList<int[]>(segments);
            var variants=new ArrayList<byte[]>(segments);var positions=new ArrayList<long[]>(segments);
            for(int index=0;index<segments;index++) {
                int quads=count(layer,index);byte[] bytes=new byte[Math.multiplyExact(quads,64)];int[] material=new int[quads];byte[] states=new byte[quads];long[] seeds=new long[quads];
                if((int)COPY.invokeExact(pointer(),layer,index,MemorySegment.ofArray(bytes),MemorySegment.ofArray(material),MemorySegment.ofArray(states),MemorySegment.ofArray(seeds),quads)!=1)throw new IllegalStateException("Cannot project DH column segment");
                vertices.add(bytes);ids.add(material);variants.add(states);positions.add(seeds);
            }
            return new LodQuadBuilder.SemanticVertexBufferBuild(vertices,ids,variants,positions);
        }catch(Throwable e){throw failure(e);}finally{Reference.reachabilityFence(this);}
    }
    public synchronized LodQuadBuilder.SemanticQuadCoverage coverage() {
        try {
            int[] values=new int[6];if((int)COVERAGE.invokeExact(pointer(),MemorySegment.ofArray(values))!=1)throw new IllegalStateException("Cannot project DH column coverage");
            return new LodQuadBuilder.SemanticQuadCoverage(values[0],values[1],values[2],values[3],values[4],values[5]);
        }catch(Throwable e){throw failure(e);}finally{Reference.reachabilityFence(this);}
    }
    /** Scalar CPU extent for diagnostics and consumers that do not need a packet projection. */
    public synchronized long byteSize() {
        try {
            long bytes=0;
            for(int layer=0;layer<4;layer++)for(int index=0,segments=count(layer,-1);index<segments;index++)
                bytes=Math.addExact(bytes,(long)count(layer,index)*64);
            return bytes;
        }catch(Throwable e){throw failure(e);}finally{Reference.reachabilityFence(this);}
    }
    /** Bounded (layer, ARGB) pairs in original stream order, only requested with auditing enabled. */
    public synchronized int[] waterSamples() {
        try {
            int count=(int)SAMPLES.invokeExact(pointer(),MemorySegment.NULL,0);if(count<0)throw new IllegalStateException("Invalid DH water samples");
            int[] values=new int[count];if(count>0&&(int)SAMPLES.invokeExact(pointer(),MemorySegment.ofArray(values),count)!=count)throw new IllegalStateException("Cannot project DH water samples");
            return values;
        }catch(Throwable e){throw failure(e);}finally{Reference.reachabilityFence(this);}
    }
    /** Same snapshot admission checks, after generation allocation and before collector mutation. */
    public synchronized void validatePublication(long generation) {
        if(generation<=0)throw new IllegalArgumentException("generation must be positive");
        try {
            int status=(int)VALIDATE.invokeExact(pointer());
            if(status==-2)throw new IllegalArgumentException("Distant Horizons LOD segment count must be <= 512");
            if(status==-3)throw new IllegalArgumentException("Distant Horizons LOD vertex is outside Rust semantic bounds");
            if(status!=0)throw new IllegalStateException("DH column build incomplete or consumed");
        }catch(Throwable e){throw failure(e);}finally{Reference.reachabilityFence(this);}
    }
    /** Called under the Java collector lock. Moves CPU bytes once, returning ledger effects. */
    public synchronized int record(int config,long key,long generation,int x,int y,int z,long provenanceBytes,boolean sameProvenance,boolean retainOwner,long[] result) {
        if(result.length!=2)throw new IllegalArgumentException("DH ledger result requires two longs");
        try {return (int)RECORD.invokeExact(pointer(),config,key,generation,x,y,z,provenanceBytes,sameProvenance?1:0,retainOwner?1:0,MemorySegment.ofArray(result));}
        catch(Throwable e){throw failure(e);}finally{Reference.reachabilityFence(this);}
    }
    @Override public synchronized void close() { cleanable.clean(); }
}
