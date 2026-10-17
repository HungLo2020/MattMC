package net.minecraft.world.level.levelgen.synth;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/** Genuine VM effects and identity projections for the shared native Perlin graph. */
final class NativePerlin {
    private static MethodHandle handle(String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_noise_perlin_"+name,type);}
    private static final MethodHandle BUILDER=handle("builder",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle NEXT=handle("next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle BIND=handle("bind",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle TAKE=handle("take",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle BUILDER_RELEASE=handle("builder_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle READER=handle("reader",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle READER_NEXT=handle("reader_next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle READER_WRITE=handle("reader_write",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle READER_RELEASE=handle("reader_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle NONZERO=handle("nonzero",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle STATS=handle("statistics",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle AMPLITUDES=handle("amplitudes",FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle AMPLITUDES_NEXT=handle("amplitudes_next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle AMPLITUDES_RELEASE=handle("amplitudes_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private final long id;
    private final MemorySegment owner;
    final double input,value,max;
    private static final class Release implements Consumer<MemorySegment> {
        private static final VarHandle HANDLE;
        static {try{HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
        private volatile long id;
        Release(long id){this.id=id;}
        @Override public void accept(MemorySegment ignored){long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)release(RELEASE,value);}
    }
    static void bind(long driver,long seed){try{int status=(int)BIND.invokeExact(driver,seed);if(status!=0)throw new IllegalStateException("Invalid native Perlin leaf binding");}catch(Throwable failure){throw propagate(failure);}}
    private static boolean nonzero(double value){try{return (int)NONZERO.invokeExact(value)!=0;}catch(Throwable failure){throw propagate(failure);}}
    NativePerlin(RandomSource randomSource,DoubleList amplitudes,int first,boolean modern,ImprovedNoise[] projections){
        Arena arena=Arena.ofAuto();long driver=0;
        try{
            driver=(long)BUILDER.invokeExact(first,projections.length,modern?1:0);
            if(driver==0)throw new OutOfMemoryError("Native Perlin builder allocation failed");
            MemorySegment words=MemorySegment.ofAddress(driver).reinterpret(32).asReadOnly();
            PositionalRandomFactory positionalRandomFactory=null;RandomSource leafSource=randomSource;ImprovedNoise leaf=null;long response=0;
            for(;;){
                int status=(int)NEXT.invokeExact(driver,response);
                if(status==0)break;
                if(status!=1)throw new IllegalStateException("Invalid native Perlin constructor continuation");
                int command=(int)words.get(ValueLayout.JAVA_LONG,0),argument=(int)words.get(ValueLayout.JAVA_LONG,8);response=0;
                switch(command){
                    case 1:positionalRandomFactory=randomSource.forkPositional();break;
                    case 2:response=Double.doubleToRawLongBits(amplitudes.getDouble(argument));break;
                    case 3:leafSource=positionalRandomFactory.fromHashOf("octave_"+argument);break;
                    case 4:
                        leaf=new ImprovedNoise(leafSource);leaf.bindPerlin(driver);
                        if(argument>=0)projections[argument]=leaf;
                        break;
                    case 5:projections[argument]=leaf;break;
                    case 6:randomSource.consumeCount(argument);break;
                    case 7:response=amplitudes.stream().filter(NativePerlin::nonzero).count();break;
                    case 8:
                        if(argument==1)throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
                        throw new IllegalArgumentException("Positive octaves are temporarily disabled");
                    default:throw new IllegalStateException("Unknown native Perlin constructor effect "+command);
                }
            }
            input=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,0));
            value=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,8));
            max=Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,16));
            id=(long)TAKE.invokeExact(driver);
            if(id==0)throw new IllegalStateException("Native Perlin graph was not completed");
            Release cleanup;
            try{cleanup=new Release(id);}catch(Throwable failure){release(RELEASE,id);throw failure;}
            try{owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}
        }catch(Throwable failure){throw propagate(failure);}
        finally{if(driver!=0)release(BUILDER_RELEASE,driver);}
    }
    void bindNormal(long driver,int slot){try{NativeNormal.bind(driver,id,slot);}finally{Reference.reachabilityFence(this);}}
    void bindBlended(long driver,int slot){try{NativeBlended.bind(driver,id,slot);}finally{Reference.reachabilityFence(this);}}
    private long reader(int mode,double input){try{return (long)READER.invokeExact(id,mode,input);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
    double edge(DoubleList amplitudes,double input){return Double.longBitsToDouble(read(reader(0,input),amplitudes,null,null,0));}
    void write(DoubleList amplitudes,MemorySegment state,int start){read(reader(1,0),amplitudes,null,state,start);}
    boolean matches(DoubleList amplitudes,NativeNoise snapshot){return read(reader(2,0),amplitudes,snapshot,null,0)!=0;}
    static long read(long driver,DoubleList amplitudes,NativeNoise snapshot,MemorySegment state,int start){
        try{
            if(driver==0)throw new OutOfMemoryError("Native Perlin reader allocation failed");
            MemorySegment words=MemorySegment.ofAddress(driver).reinterpret(32).asReadOnly();long response=0;
            for(;;){
                int status=(int)READER_NEXT.invokeExact(driver,response);
                if(status==0)return words.get(ValueLayout.JAVA_LONG,0);
                if(status!=1)throw new IllegalStateException("Invalid native Perlin reader continuation");
                int command=(int)words.get(ValueLayout.JAVA_LONG,0),index=(int)words.get(ValueLayout.JAVA_LONG,8);response=0;
                switch(command){
                    case 2:response=Double.doubleToRawLongBits(amplitudes.getDouble(index));break;
                    case 9:
                        MemorySegment octave=state.asSlice(NativeNoise.HEADER+((long)start+index)*NativeNoise.OCTAVE,NativeNoise.OCTAVE);
                        READER_WRITE.invokeExact(driver,octave);break;
                    case 10:response=Double.doubleToRawLongBits(snapshot.amplitudes[index]);break;
                    default:throw new IllegalStateException("Unknown native Perlin reader effect "+command);
                }
            }
        }catch(Throwable failure){throw propagate(failure);}
        finally{if(driver!=0)release(READER_RELEASE,driver);}
    }
    static com.mojang.datafixers.util.Pair<Integer,DoubleList> amplitudes(it.unimi.dsi.fastutil.ints.IntSortedSet octaves){
        long driver=0;
        try{
            driver=(long)AMPLITUDES.invokeExact();
            if(driver==0)throw new OutOfMemoryError("Native Perlin octave factory allocation failed");
            MemorySegment words=MemorySegment.ofAddress(driver).reinterpret(32).asReadOnly();long response=0;
            DoubleList values=null;it.unimi.dsi.fastutil.ints.IntBidirectionalIterator iterator=null;
            for(;;){
                int status=(int)AMPLITUDES_NEXT.invokeExact(driver,response);
                if(status==0)return com.mojang.datafixers.util.Pair.of((int)words.get(ValueLayout.JAVA_LONG,0),values);
                if(status!=1)throw new IllegalStateException("Invalid native Perlin octave factory continuation");
                int command=(int)words.get(ValueLayout.JAVA_LONG,0),argument=(int)words.get(ValueLayout.JAVA_LONG,8);response=0;
                switch(command){
                    case 20:response=octaves.isEmpty()?1:0;break;
                    case 21:response=octaves.firstInt();break;
                    case 22:response=octaves.lastInt();break;
                    case 23:values=new it.unimi.dsi.fastutil.doubles.DoubleArrayList(new double[argument]);break;
                    case 24:iterator=octaves.iterator();break;
                    case 25:response=iterator.hasNext()?1:0;break;
                    case 26:response=iterator.nextInt();break;
                    case 27:values.set(argument,Double.longBitsToDouble(words.get(ValueLayout.JAVA_LONG,16)));break;
                    case 8:
                        if(argument==3)throw new IllegalArgumentException("Need some octaves!");
                        throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
                    default:throw new IllegalStateException("Unknown native Perlin octave factory effect "+command);
                }
            }
        }catch(Throwable failure){throw propagate(failure);}
        finally{if(driver!=0)release(AMPLITUDES_RELEASE,driver);}
    }
    static long[] statistics(){try(Arena arena=Arena.ofConfined()){var data=arena.allocate(9L*Long.BYTES,Long.BYTES);STATS.invokeExact(data);return data.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
    private static void release(MethodHandle handle,long id){try{handle.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
    private static RuntimeException propagate(Throwable failure){return NativePerlin.<RuntimeException,RuntimeException>raise(failure);}
    @SuppressWarnings("unchecked") private static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
