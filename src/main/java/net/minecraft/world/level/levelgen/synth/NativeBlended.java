package net.minecraft.world.level.levelgen.synth;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;
/** Real VM effects and native ownership for Blended construction and snapshots. */
final class NativeBlended {
 private static MethodHandle handle(String family,String suffix,FunctionDescriptor descriptor){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_noise_"+family+"_"+suffix,descriptor);}
 private static MethodHandle handle(String suffix,FunctionDescriptor descriptor){return handle("blended",suffix,descriptor);}
 private static final MethodHandle BUILDER=handle("builder",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE));
 private static final MethodHandle NEXT=handle("next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BIND=handle("bind",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle TAKE=handle("take",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BUILDER_RELEASE=handle("builder_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle READER=handle("reader",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle READER_NEXT=handle("perlin","reader_next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle READER_WRITE=handle("perlin","reader_write",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
 private static final MethodHandle READER_RELEASE=handle("perlin","reader_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle STATS=handle("statistics",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
 private final long id;private final MemorySegment owner;
 final PerlinNoise[] children;final int n1,n2,n3;final double xz,y,xzf,yf,smear,max;
 private static final class Release implements Consumer<MemorySegment>{
  private static final VarHandle HANDLE;
  static{try{HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
  private volatile long id;Release(long id){this.id=id;}
  @Override public void accept(MemorySegment ignored){long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)release(RELEASE,value);}
 }
 static void bind(long driver,long perlin,int slot){try{int status=(int)BIND.invokeExact(driver,perlin,slot);if(status!=0)throw new IllegalStateException("Invalid native Blended child binding");}catch(Throwable failure){throw propagate(failure);}}
 NativeBlended(RandomSource randomSource,double xzScale,double yScale,double xzFactor,double yFactor,double smearScale){
  Arena arena=Arena.ofAuto();long driver=0;
  try{
   driver=(long)BUILDER.invokeExact(xzScale,yScale,xzFactor,yFactor,smearScale);if(driver==0)throw new OutOfMemoryError("Native Blended constructor allocation failed");
   MemorySegment words=MemorySegment.ofAddress(driver).reinterpret(80).asReadOnly();long response=0;children=new PerlinNoise[3];
   for(;;){int status=(int)NEXT.invokeExact(driver,response);if(status==0)break;if(status!=1)throw new IllegalStateException("Invalid native Blended constructor continuation");int command=(int)words.get(ValueLayout.JAVA_LONG,0),slot=(int)words.get(ValueLayout.JAVA_LONG,8);response=0;
    switch(command){
     case 1:var child=PerlinNoise.createLegacyForBlendedNoise(randomSource,IntStream.rangeClosed((int)words.get(ValueLayout.JAVA_LONG,16),(int)words.get(ValueLayout.JAVA_LONG,24)));child.bindBlended(driver,slot);children[slot]=child;break;
     case 2:response=Double.doubleToRawLongBits(children[slot].maxBrokenValue(Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,16))));break;
     default:throw new IllegalStateException("Unknown native Blended constructor effect "+command);
    }
   }
   n1=(int)words.get(ValueLayout.JAVA_LONG,0);n2=(int)words.get(ValueLayout.JAVA_LONG,8);n3=(int)words.get(ValueLayout.JAVA_LONG,16);xz=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,24));y=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,32));xzf=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,40));yf=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,48));smear=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,56));max=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,64));
   id=(long)TAKE.invokeExact(driver);if(id==0)throw new IllegalStateException("Native Blended graph was not completed");Release cleanup;try{cleanup=new Release(id);}catch(Throwable failure){release(RELEASE,id);throw failure;}try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}
  }catch(Throwable failure){throw propagate(failure);}finally{if(driver!=0)release(BUILDER_RELEASE,driver);}
 }
 private long reader(){try{return(long)READER.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 MemorySegment allocate(){return NativeNoise.allocate(4,n1,n2,n3,xz,y,xzf,yf,smear);}
 void write(MemorySegment state){DoubleList[] amplitudes=new DoubleList[]{children[0].nativeAmplitudes(),children[1].nativeAmplitudes(),children[2].nativeAmplitudes()};try{read(reader(),amplitudes,state);}finally{Reference.reachabilityFence(this);}}
 static void read(long driver,DoubleList[] amplitudes,MemorySegment state){try{if(driver==0)throw new OutOfMemoryError("Native Blended reader allocation failed");MemorySegment words=MemorySegment.ofAddress(driver).reinterpret(32).asReadOnly();long response=0;for(;;){int status=(int)READER_NEXT.invokeExact(driver,response);if(status==0)return;if(status!=1)throw new IllegalStateException("Invalid native Blended reader continuation");int command=(int)words.get(ValueLayout.JAVA_LONG,0),index=(int)words.get(ValueLayout.JAVA_LONG,8);response=0;switch(command){case 2:response=Double.doubleToRawLongBits(amplitudes[(int)words.get(ValueLayout.JAVA_LONG,16)].getDouble(index));break;case 9:READER_WRITE.invokeExact(driver,state.asSlice(NativeNoise.HEADER+(long)index*NativeNoise.OCTAVE,NativeNoise.OCTAVE));break;default:throw new IllegalStateException("Unknown native Blended reader effect "+command);}}}catch(Throwable failure){throw propagate(failure);}finally{if(driver!=0)release(READER_RELEASE,driver);}}

 static long[] statistics(){try(Arena arena=Arena.ofConfined()){var data=arena.allocate(3L*Long.BYTES,Long.BYTES);STATS.invokeExact(data);return data.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
 private static void release(MethodHandle handle,long id){try{handle.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
 private static RuntimeException propagate(Throwable failure){return NativeBlended.<RuntimeException,RuntimeException>raise(failure);}
 @SuppressWarnings("unchecked")private static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
