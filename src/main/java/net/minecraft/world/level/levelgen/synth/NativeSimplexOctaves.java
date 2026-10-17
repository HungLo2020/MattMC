package net.minecraft.world.level.levelgen.synth;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
/** Real VM effects for the native Simplex octave producer and snapshot writer. */
final class NativeSimplexOctaves {
 private static MethodHandle handle(String suffix,FunctionDescriptor descriptor){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_noise_simplex_octaves_"+suffix,descriptor);}
 private static final MethodHandle BUILDER=handle("builder",FunctionDescriptor.of(ValueLayout.JAVA_LONG));
 private static final MethodHandle NEXT=handle("next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BIND=handle("bind",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle TAKE=handle("take",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BUILDER_RELEASE=handle("builder_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle WRITE=handle("write",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
 private static final MethodHandle STATS=handle("statistics",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
 private final long id;private final MemorySegment owner;
 final int count;final double input,value;final SimplexNoise[] projections;
 private static final class Release implements Consumer<MemorySegment>{
  private static final VarHandle HANDLE;
  static{try{HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
  private volatile long id;Release(long id){this.id=id;}
  @Override public void accept(MemorySegment ignored){long old=(long)HANDLE.getAndSet(this,0L);if(old!=0)release(RELEASE,old);}
 }
 static void bind(long driver,long leaf){try{int status=(int)BIND.invokeExact(driver,leaf);if(status!=0)throw new IllegalStateException("Invalid native Simplex octave binding");}catch(Throwable failure){throw propagate(failure);}}
 NativeSimplexOctaves(RandomSource randomSource,IntSortedSet octaves){
  Arena arena=Arena.ofAuto();long driver=0;
  try{
   driver=(long)BUILDER.invokeExact();if(driver==0)throw new OutOfMemoryError("Native Simplex octave allocation failed");MemorySegment words=MemorySegment.ofAddress(driver).reinterpret(32).asReadOnly();long response=0;RandomSource derived=null;SimplexNoise leaf=null;SimplexNoise[] actualProjections=null;
   for(;;){int status=(int)NEXT.invokeExact(driver,response);if(status==0)break;if(status!=1)throw new IllegalStateException("Invalid native Simplex octave continuation");int command=(int)words.get(ValueLayout.JAVA_LONG,0),argument=(int)words.get(ValueLayout.JAVA_LONG,8);response=0;
    switch(command){
     case 1:response=octaves.isEmpty()?1:0;break;
     case 2:response=octaves.firstInt();break;
     case 3:response=octaves.lastInt();break;
     case 4:leaf=new SimplexNoise(words.get(ValueLayout.JAVA_LONG,16)==0?randomSource:derived);leaf.bindSimplexOctaves(driver);break;
     case 5:response=octaves.contains(argument)?1:0;break;
     case 6:(words.get(ValueLayout.JAVA_LONG,16)==0?randomSource:derived).consumeCount(argument);break;
     case 7:derived=new WorldgenRandom(new LegacyRandomSource(words.get(ValueLayout.JAVA_LONG,8)));break;
     case 12:throw new IllegalArgumentException("Invalid native noise state: "+argument);
     case 9:actualProjections=new SimplexNoise[argument];break;
     case 10:actualProjections[argument]=leaf;break;
     case 8:if(argument==1)throw new IllegalArgumentException("Need some octaves!");throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
     default:throw new IllegalStateException("Unknown native Simplex octave effect "+command);
    }
   }
   projections=actualProjections;count=(int)words.get(ValueLayout.JAVA_LONG,0);input=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,8));value=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,16));id=(long)TAKE.invokeExact(driver);if(id==0)throw new IllegalStateException("Native Simplex octave graph was not completed");Release cleanup;try{cleanup=new Release(id);}catch(Throwable failure){release(RELEASE,id);throw failure;}try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}
  }catch(Throwable failure){throw propagate(failure);}finally{if(driver!=0)release(BUILDER_RELEASE,driver);}
 }
 MemorySegment allocate(){return NativeNoise.allocate(6,count,0,0,input,value);}
 void write(MemorySegment state){try{WRITE.invokeExact(id,state.asSlice(NativeNoise.HEADER));}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 static long[] statistics(){try(Arena arena=Arena.ofConfined()){var data=arena.allocate(3L*Long.BYTES,Long.BYTES);STATS.invokeExact(data);return data.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
 private static void release(MethodHandle handle,long id){try{handle.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
 private static RuntimeException propagate(Throwable failure){return NativeSimplexOctaves.<RuntimeException,RuntimeException>raise(failure);}
 @SuppressWarnings("unchecked")private static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
