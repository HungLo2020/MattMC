package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;

/** Native wrapper state and producer control; only genuine VM effects run here. */
final class NativeWorldgenSource {
    private static MethodHandle handle(String name, FunctionDescriptor type) { return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_worldgen_" + name, type); }
    private static final MethodHandle NEW = handle("new", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle COUNT = handle("count", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle SET = handle("set_count", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    private static final MethodHandle NEXT = handle("next", FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle WORD = handle("word", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle SEED_VALUE = handle("seed_value", FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle SEED = handle("seed_new", FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle STEP = handle("seed_next", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle END = handle("seed_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle SLIME = handle("slime", FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle STATS = handle("statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle LEAF = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_seed_from_worldgen", FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    private final long id;
    private final MemorySegment owner;
    private static final class Release implements Consumer<MemorySegment> {
        private static final VarHandle HANDLE;
        static { try { HANDLE=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class); } catch(ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); } }
        private volatile long id;
        Release(long id) { this.id=id; }
        @Override public void accept(MemorySegment ignored) { long value=(long)HANDLE.getAndSet(this,0L);if(value!=0)release(value); }
    }
    NativeWorldgenSource() {
        Arena arena=Arena.ofAuto();
        try {
            id=(long)NEW.invokeExact();if(id==0)throw new OutOfMemoryError("Native worldgen state allocation failed");
            Release cleanup;try {cleanup=new Release(id);}catch(Throwable failure){release(id);throw failure;}
            try {owner=MemorySegment.ofAddress(id).reinterpret(1,arena,cleanup);}catch(Throwable failure){cleanup.accept(MemorySegment.NULL);throw failure;}
        }catch(Throwable failure){throw propagate(failure);}
    }
    long handle(){return id;}
    int count(){try{return(int)COUNT.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
    void count(int value){try{SET.invokeExact(id,value);}catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(this);}}
    record Target(int kind,long id,RandomSource facade) {}
    static Target target(RandomSource source) {
        if(source!=null&&source.getClass()==LegacyRandomSource.class){
            var value=LegacyRandomSource.nativeGaussianProducer(source);long id=value==null?0:value.initializedHandle();
            return new Target(id==0?3:1,id,source);
        }
        if(source!=null&&source.getClass()==XoroshiroRandomSource.class){
            var xoro=XoroshiroRandomSource.nativeWorldgenProducer(source);
            if(xoro!=null)return new Target(2,xoro.handle(),source);
        }
        return new Target(source instanceof LegacyRandomSource?3:4,0,source);
    }
    int next(WorldgenRandom caller,int bits) {
        Target target=target(caller.nativeTarget());
        try {
            long result=(long)NEXT.invokeExact(id,bits,target.kind(),target.id());
            return switch((int)(result>>>32)) {
                case 0 -> (int)result;
                case 1 -> caller.nativeNextLegacy(bits);
                case 2 -> (int)WORD.invokeExact(bits,caller.nativeNextWord());
                case 3 -> throw NativeLegacySource.collision();
                default -> throw new IllegalStateException("Unknown native worldgen draw effect");
            };
        }catch(Throwable failure){throw propagate(failure);}
        finally{Reference.reachabilityFence(target);Reference.reachabilityFence(caller);Reference.reachabilityFence(this);}
    }
    long leaf(Target target,boolean simplex) {
        try {long value=(long)LEAF.invokeExact(id,target.kind(),target.id(),simplex?1:0);if(value==0)throw NativeLegacySource.collision();return value;}
        catch(Throwable failure){throw propagate(failure);}finally{Reference.reachabilityFence(target);Reference.reachabilityFence(this);}
    }
    static long seed(WorldgenRandom caller,int kind,long seed,int x,int z,int salt) {
        long frame=0;
        try {
            if(kind==3||kind==4){long value=(long)SEED_VALUE.invokeExact(kind,seed,x,z,salt);caller.setSeed(value);return value;}
            frame=(long)SEED.invokeExact(kind,seed,x,z,salt);if(frame==0)throw new OutOfMemoryError("Native worldgen seed producer allocation failed");
            var words=MemorySegment.ofAddress(frame).reinterpret(16).asReadOnly();long response=0;
            while((int)STEP.invokeExact(frame,response)!=0){
                int op=(int)words.getAtIndex(ValueLayout.JAVA_LONG,0);
                if(op==1){caller.setSeed(words.getAtIndex(ValueLayout.JAVA_LONG,1));response=0;}
                else if(op==2)response=caller.nextLong();
                else throw new IllegalStateException("Unknown native worldgen seed effect "+op);
            }
            return words.getAtIndex(ValueLayout.JAVA_LONG,1);
        }catch(Throwable failure){throw propagate(failure);}
        finally {
            try{if(frame!=0)END.invokeExact(frame);}catch(Throwable failure){throw propagate(failure);}
            finally{Reference.reachabilityFence(caller);}
        }
    }
    static long slime(long seed,int x,int z,long salt){try{return(long)SLIME.invokeExact(seed,x,z,salt);}catch(Throwable failure){throw propagate(failure);}}
    static long[] statistics(){try(Arena arena=Arena.ofConfined()){var data=arena.allocate(48,8);STATS.invokeExact(data);return data.toArray(ValueLayout.JAVA_LONG);}catch(Throwable failure){throw propagate(failure);}}
    private static void release(long id){try{RELEASE.invokeExact(id);}catch(Throwable failure){throw propagate(failure);}}
    private static RuntimeException propagate(Throwable failure){return NativeWorldgenSource.<RuntimeException,RuntimeException>raise(failure);}
    @SuppressWarnings("unchecked")private static<E extends Throwable,T>T raise(Throwable failure)throws E{throw(E)failure;}
}
