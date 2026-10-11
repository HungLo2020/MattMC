package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Retained CPU visibility topology; Java emits mutations, not per-frame rows. */
public final class NativeDhVisibility implements AutoCloseable {
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_visibility_"+name, descriptor);
    }
    private static final MethodHandle CREATE=bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE=bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle EVENT=bind("event", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle STATE=bind("state", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle INVALIDATE=bind("invalidate",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle WALK=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_dh_visibility_walk",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_FLOAT,ValueLayout.JAVA_FLOAT),Linker.Option.critical(true));
    private final MemorySegment owner;
    private volatile boolean compatible=true;
    public NativeDhVisibility(int root,int leaf,int diameter,int x,int z) {
        try {
            MemorySegment pointer=(MemorySegment)CREATE.invokeExact(root,leaf,diameter,x,z);
            if(pointer.address()==0) throw new IllegalArgumentException("Invalid DH visibility bounds");
            boolean adopted=false;
            try { owner=pointer.reinterpret(1,Arena.ofAuto(),NativeDhVisibility::release);adopted=true; }
            finally {if(!adopted) release(pointer);}
        } catch(Throwable t) {throw failure(t);}
    }
    public boolean compatible() {return compatible;}
    MemorySegment owner() {return owner;}
    private static void release(MemorySegment p) {try {RELEASE.invokeExact(p);} catch(Throwable t){throw failure(t);}}
    public long event(int kind,long key,int a,int b) {
        try {return (long)EVENT.invokeExact(owner,kind,key,a,b);} catch(Throwable t){throw failure(t);}
    }
    public void state(long key,long token,boolean enabled,long generation,long epoch) {
        try {STATE.invokeExact(owner,key,token,enabled?1:0,generation,epoch);} catch(Throwable t){throw failure(t);}
    }
    public void invalidate(long key,long token,long epoch) {
        try {INVALIDATE.invokeExact(owner,key,token,epoch);} catch(Throwable t){throw failure(t);}
    }
    /** Explicit diagnostic projection: counters and (key,generation) pairs. */
    public long[] inspect(float[] planes,float minY,float maxY) {
        if(planes!=null && planes.length!=24) throw new IllegalArgumentException("DH frustum requires24 plane floats");
        try {
            var input=planes==null?MemorySegment.NULL:MemorySegment.ofArray(planes);
            return DhCollectorLedger.takeOutput((int)WALK.invokeExact(owner,input,minY,maxY));
        } catch(Throwable t){throw failure(t);}
    }
    /** Unsupported semantic producers preserve their original callback route. */
    public void requireCompatibility() {compatible=false;close();}
    @Override public void close() {event(5,0L,0,0);}
    private static RuntimeException failure(Throwable t) {
        if(t instanceof RuntimeException e)return e;
        if(t instanceof Error e)throw e;
        return new IllegalStateException("Rust DH visibility failed",t);
    }
}
