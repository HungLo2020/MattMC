package net.minecraft.world.level.levelgen;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
/** Actual native source and current generator, with escaped-root projection. */
final class NativeXoroshiroSource {
 private static MethodHandle handle(String suffix,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_random_source_xoroshiro_"+suffix,type);}
 private static final MethodHandle NEW=handle("new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle ROOT=handle("root",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BIND=handle("bind",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle SEED=handle("seed",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle NEXT=handle("next",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BOUNDED=handle("bounded",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle DOUBLE=handle("double",FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_LONG));
 private static final MethodHandle FLOAT=handle("float",FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,ValueLayout.JAVA_LONG));
 private static final MethodHandle CONSUME=handle("consume",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle STATS=handle("statistics",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
 private static final MethodHandle LEAF=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_noise_seed_from_xoroshiro_source",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private final long id;private final MemorySegment owner;
 private static final class Release implements Consumer<MemorySegment>{private static final VarHandle HANDLE;static{try{HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}private volatile long id;Release(long id){this.id=id;}@Override public void accept(MemorySegment ignored){long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)release(value);}}
 NativeXoroshiroSource(long seed){this(seed,0,1,null);}
 NativeXoroshiroSource(long lo,long hi){this(lo,hi,0,null);}
 NativeXoroshiroSource(NativeRandomBits bits){this(bits.handle(),0,2,bits);}
 private NativeXoroshiroSource(long a,long b,int kind,Object keepAlive){Arena arena=Arena.ofAuto();try{id=(long)NEW.invokeExact(a,b,kind);if(id==0)throw new OutOfMemoryError("Native source allocation failed");Release cleanup;try{cleanup=new Release(id);}catch(Throwable failure){release(id);throw failure;}try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(keepAlive);}}
 long root(){try{return(long)ROOT.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 void bind(long root){try{BIND.invokeExact(id,root);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 void seed(long seed){try{SEED.invokeExact(id,seed);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 long next(){try{return(long)NEXT.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 int bounded(int bound){try{int value=(int)BOUNDED.invokeExact(id,bound);if(value<0)throw new IllegalArgumentException("Bound must be positive");return value;}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 double nextDouble(){try{return(double)DOUBLE.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 float nextFloat(){try{return(float)FLOAT.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 void consume(int count){try{CONSUME.invokeExact(id,count);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 long leaf(boolean simplex){try{return(long)LEAF.invokeExact(id,simplex?1:0);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 static long[] statistics(){try(Arena arena=Arena.ofConfined()){var data=arena.allocate(24,8);STATS.invokeExact(data);return data.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
 private static void release(long id){try{RELEASE.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
 private static RuntimeException propagate(Throwable failure){return NativeXoroshiroSource.<RuntimeException,RuntimeException>raise(failure);}
 @SuppressWarnings("unchecked")private static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
