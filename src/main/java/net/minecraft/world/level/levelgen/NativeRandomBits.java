package net.minecraft.world.level.levelgen;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
/** Canonical native two-word RNG state, retained by real native consumers. */
final class NativeRandomBits {
 private static MethodHandle handle(String suffix,FunctionDescriptor descriptor,boolean critical){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_random_xoroshiro_"+suffix,descriptor,critical?new Linker.Option[]{Linker.Option.critical(false)}:new Linker.Option[0]);}
 private static final MethodHandle NEW=handle("new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),false);
 private static final MethodHandle NEXT=handle("next",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),true);
 private static final MethodHandle GET=handle("get",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT),true);
 private static final MethodHandle SET=handle("set",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),true);
 private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG),false);
 private static final MethodHandle STATS=handle("statistics",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),false);
 private static final MethodHandle LEAF=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_noise_seed_from_xoroshiro",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle NEW_SEEDED=handle("new_seeded",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),false);
 private static final MethodHandle BOUNDED=handle("bounded",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT),false);
 private static final MethodHandle DOUBLE=handle("double",FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_LONG),true);
 private static final MethodHandle FLOAT=handle("float",FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,ValueLayout.JAVA_LONG),true);
 private static final MethodHandle CONSUME=handle("consume",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT),false);
 private final long id;private final MemorySegment owner;
 private static final class Release implements Consumer<MemorySegment>{
  private static final VarHandle HANDLE;static{try{HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
  private volatile long id;Release(long id){this.id=id;}
  @Override public void accept(MemorySegment ignored){long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)release(value);}
 }
 NativeRandomBits(long lo,long hi){this(lo,hi,false);}
 NativeRandomBits(long seed){this(seed,0,true);}
 private NativeRandomBits(long lo,long hi,boolean seeded){Arena arena=Arena.ofAuto();try{id=seeded?(long)NEW_SEEDED.invokeExact(lo):(long)NEW.invokeExact(lo,hi);if(id==0)throw new OutOfMemoryError("Native RNG allocation failed");Release cleanup;try{cleanup=new Release(id);}catch(Throwable failure){release(id);throw failure;}try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}}catch(Throwable failure){throw propagate(failure);}}
 long handle(){return id;}
 NativeXoroshiroSource sourceOwner(){try{return new NativeXoroshiroSource(this);}finally{Reference.reachabilityFence(this);}}
 void bind(NativeXoroshiroSource source){try{source.bind(id);}finally{Reference.reachabilityFence(this);}}
 NativeRandomBits(NativeXoroshiroSource source){Arena arena=Arena.ofAuto();try{id=source.root();if(id==0)throw new OutOfMemoryError("Native RNG projection failed");Release cleanup;try{cleanup=new Release(id);}catch(Throwable failure){release(id);throw failure;}try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}}catch(Throwable failure){throw propagate(failure);}}
 long noiseLeaf(boolean simplex){try{return(long)LEAF.invokeExact(id,simplex?1:0);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 int bounded(int bound){try{int value=(int)BOUNDED.invokeExact(id,bound);if(value<0)throw new IllegalArgumentException("Bound must be positive");return value;}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 double nextDouble(){try{return(double)DOUBLE.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 float nextFloat(){try{return(float)FLOAT.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 void consume(int count){try{CONSUME.invokeExact(id,count);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 long next(){try{return(long)NEXT.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 long get(int slot){try{return(long)GET.invokeExact(id,slot);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 void set(long lo,long hi){try{SET.invokeExact(id,lo,hi);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 static long[] statistics(){try(Arena arena=Arena.ofConfined()){var out=arena.allocate(3L*Long.BYTES,Long.BYTES);STATS.invokeExact(out);return out.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
 private static void release(long id){try{RELEASE.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
 private static RuntimeException propagate(Throwable failure){return NativeRandomBits.<RuntimeException,RuntimeException>raise(failure);}
 @SuppressWarnings("unchecked")private static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
