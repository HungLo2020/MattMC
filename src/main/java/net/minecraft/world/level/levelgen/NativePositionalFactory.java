package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;

/** Actual immutable factory data; returned source facades retain the same native source. */
final class NativePositionalFactory {
    private static MethodHandle h(String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_positional_"+name,type);}
    private static final MethodHandle NEW=h("new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE=h("release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle SEED=h("seed",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    private static final MethodHandle AT=h("at",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle FROM_SEED=h("from_seed",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle FROM_HASH=h("from_hash",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle SOURCE_RELEASE=h("source_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle STATS=h("statistics",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final int kind; // Actual VM class discriminator, not factory seed state.
    private final long id;
    private final MemorySegment owner;
    private static final class Release implements Consumer<MemorySegment> {
        private static final VarHandle HANDLE;
        static {try{HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
        private volatile long id;
        Release(long id){this.id=id;}
        @Override public void accept(MemorySegment ignored){long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)release(value);}
    }
    NativePositionalFactory(int kind,long a,long b) {
        this.kind=kind;Arena arena=Arena.ofAuto();
        try {
            id=(long)NEW.invokeExact(kind,a,b);if(id==0)throw new OutOfMemoryError("Native positional factory allocation failed");
            Release cleanup;try{cleanup=new Release(id);}catch(Throwable failure){release(id);throw failure;}
            try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}
        }catch(Throwable failure){throw propagate(failure);}
    }
    long handle(){return id;}
    long seed(int index){try{return(long)SEED.invokeExact(id,index);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
    /** One actual source reference, transferred from the pending return to its VM anchor. */
    static final class SourceLease implements Consumer<MemorySegment> {
        private static final VarHandle HANDLE;
        static {try{HANDLE=MethodHandles.lookup().findVarHandle(SourceLease.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
        private final int kind;
        private volatile long id;
        SourceLease(int kind,long id){this.kind=kind;this.id=id;}
        @Override public void accept(MemorySegment ignored){long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)releaseSource(kind,value);}
    }
    private static void releaseSource(int kind,long source){try{SOURCE_RELEASE.invokeExact(kind,source);}catch(Throwable failure){throw propagate(failure);}}
    private RandomSource project(long source) {
        if(source==0)throw new OutOfMemoryError("Native derived random source allocation failed");
        SourceLease lease;
        try{lease=new SourceLease(kind,source);}catch(Throwable failure){releaseSource(kind,source);throw failure;}
        boolean projected=false;
        try {
            RandomSource value=kind==2?new LegacyRandomSource(new NativeLegacySource(source,lease)):new XoroshiroRandomSource(NativeXoroshiroSource.adoptSource(source,lease),true);
            projected=true;
            return value;
        }finally{
            if(!projected)lease.accept(MemorySegment.NULL);
            Reference.reachabilityFence(this);
        }
    }
    RandomSource at(int x,int y,int z){try{return project((long)AT.invokeExact(id,x,y,z));}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
    RandomSource fromSeed(long seed){try{return project((long)FROM_SEED.invokeExact(id,seed));}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
    RandomSource fromHashOf(String string) {
        if(string==null) {
            // Perform the genuine original VM null effect before touching native data.
            if(kind==2)string.hashCode();else RandomSupport.seedFromHashOf(string);
        }
        char[] units=string.toCharArray();
        try(Arena arena=Arena.ofConfined()){
            var data=arena.allocate(Math.max(1,units.length)*2L,2);
            MemorySegment.copy(MemorySegment.ofArray(units),0,data,0,units.length*2L);
            return project((long)FROM_HASH.invokeExact(id,data,(long)units.length));
        }catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}
    }
    static long[] statistics(){try(Arena arena=Arena.ofConfined()){var out=arena.allocate(24,8);STATS.invokeExact(out);return out.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
    private static void release(long id){try{RELEASE.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
    private static RuntimeException propagate(Throwable failure){return NativePositionalFactory.<RuntimeException,RuntimeException>raise(failure);}
    @SuppressWarnings("unchecked")private static <E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
