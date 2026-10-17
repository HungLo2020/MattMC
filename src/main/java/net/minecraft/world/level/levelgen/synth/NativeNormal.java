package net.minecraft.world.level.levelgen.synth;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.doubles.*;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;
/** Real VM child/iterator effects for the shared native Normal producer. */
final class NativeNormal {
 private static MethodHandle handle(String suffix,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_noise_normal_"+suffix,type);}
 private static final MethodHandle BUILDER=handle("builder",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle NEXT=handle("next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BIND=handle("bind",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle TAKE=handle("take",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 private static final MethodHandle BUILDER_RELEASE=handle("builder_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 private static final MethodHandle READER=handle("reader",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 private static final MethodHandle STATS=handle("statistics",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
 private final long id;private final MemorySegment owner;
 final PerlinNoise first,second;final int n1,n2;final double input1,value1,input2,value2,factor,max;
 private static final class Release implements Consumer<MemorySegment>{
  private static final VarHandle HANDLE;
  static{try{HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
  private volatile long id;Release(long id){this.id=id;}
  @Override public void accept(MemorySegment ignored){long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)release(RELEASE,value);}
 }
 static void bind(long driver,long perlin,int slot){try{int result=(int)BIND.invokeExact(driver,perlin,slot);if(result!=0)throw new IllegalStateException("Invalid native Normal child binding");}catch(Throwable failure){throw propagate(failure);}}
 NativeNormal(RandomSource randomSource,int firstOctave,DoubleList doubleList,boolean modern){
  Arena arena=Arena.ofAuto();long driver=0;
  try{
   driver=(long)BUILDER.invokeExact(modern?1:0);if(driver==0)throw new OutOfMemoryError("Native Normal constructor allocation failed");
   MemorySegment words=MemorySegment.ofAddress(driver).reinterpret(64).asReadOnly();long response=0;PerlinNoise a=null,b=null;DoubleListIterator doubleListIterator=null;
   for(;;){
    int status=(int)NEXT.invokeExact(driver,response);if(status==0)break;if(status!=1)throw new IllegalStateException("Invalid native Normal constructor continuation");
    int command=(int)words.get(ValueLayout.JAVA_LONG,0),slot=(int)words.get(ValueLayout.JAVA_LONG,8);response=0;
    switch(command){
     case 1:
      PerlinNoise child=words.get(ValueLayout.JAVA_LONG,16)!=0?PerlinNoise.create(randomSource,firstOctave,doubleList):PerlinNoise.createLegacyForLegacyNetherBiome(randomSource,firstOctave,doubleList);
      child.bindNormal(driver,slot);if(slot==0)a=child;else b=child;break;
     case 2:doubleListIterator=doubleList.iterator();break;
     case 3:response=doubleListIterator.hasNext()?1:0;break;
     case 4:response=doubleListIterator.nextIndex();break;
     case 5:response=Double.doubleToRawLongBits(doubleListIterator.nextDouble());break;
     default:throw new IllegalStateException("Unknown native Normal constructor effect "+command);
    }
   }
   first=a;second=b;n1=(int)words.get(ValueLayout.JAVA_LONG,0);n2=(int)words.get(ValueLayout.JAVA_LONG,8);
   input1=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,16));value1=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,24));input2=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,32));value2=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,40));factor=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,48));max=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,56));
   id=(long)TAKE.invokeExact(driver);if(id==0)throw new IllegalStateException("Native Normal graph was not completed");
   Release cleanup;try{cleanup=new Release(id);}catch(Throwable failure){release(RELEASE,id);throw failure;}
   try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}
  }catch(Throwable failure){throw propagate(failure);}finally{if(driver!=0)release(BUILDER_RELEASE,driver);}
 }
 private long reader(int mode){try{return(long)READER.invokeExact(id,mode);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
 boolean matches(DoubleList amplitudes,NativeNoise snapshot){return NativePerlin.read(reader(2),amplitudes,snapshot,null,0)!=0;}
 MemorySegment allocate(){return NativeNoise.allocate(3,n1,n2,0,input1,value1,input2,value2,factor);}
 void write(DoubleList amplitudes,MemorySegment state){NativePerlin.read(reader(1),amplitudes,null,state,0);}
 static long[] statistics(){try(Arena arena=Arena.ofConfined()){var data=arena.allocate(3L*Long.BYTES,Long.BYTES);STATS.invokeExact(data);return data.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
 private static void release(MethodHandle handle,long id){try{handle.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
 private static RuntimeException propagate(Throwable failure){return NativeNormal.<RuntimeException,RuntimeException>raise(failure);}
 @SuppressWarnings("unchecked")private static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
