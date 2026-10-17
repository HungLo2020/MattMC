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
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;

/** Owns a compact immutable native leaf; RNG callbacks retain their real VM identity. */
final class NativeNoiseSeed {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_seed_" + name, descriptor);
    }
    private static final MethodHandle BUILDER = handle("builder", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle NEXT = handle("next", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle TAKE = handle("take", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle BUILDER_RELEASE = handle("builder_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle OCTAVE = handle("octave", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle STATS = handle("statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final long id;
    private final MemorySegment owner;
    private static final class Release implements Consumer<MemorySegment> {
        private static final VarHandle HANDLE;
        static {
            try { HANDLE = MethodHandles.lookup().findVarHandle(Release.class, "id", long.class); }
            catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
        private volatile long id;
        Release(long id) { this.id=id; }
        @Override public void accept(MemorySegment ignored) {
            long value=(long)HANDLE.getAndSet(this,0L);
            if(value!=0) release(RELEASE,value);
        }
    }
    NativeNoiseSeed(RandomSource randomSource, boolean simplex) {
        Arena arena=Arena.ofAuto();
        long builder=0;
        try {
            builder=(long)BUILDER.invokeExact(simplex?1:0);
            if(builder==0)throw new OutOfMemoryError("Native noise seed builder allocation failed");
            MemorySegment words=MemorySegment.ofAddress(builder).reinterpret(32).asReadOnly();
            long response=0;
            for(;;) {
                int status=(int)NEXT.invokeExact(builder,response);
                if(status==0)break;
                if(status==-1)throw new ArrayIndexOutOfBoundsException("Index " + (int)words.get(ValueLayout.JAVA_LONG,0) + " out of bounds for length " + words.get(ValueLayout.JAVA_LONG,8));
                if(status!=1)throw new IllegalStateException("Invalid native noise seed continuation");
                int command=(int)words.get(ValueLayout.JAVA_LONG,0);
                response=switch(command) {
                    case 1 -> Double.doubleToRawLongBits(randomSource.nextDouble());
                    case 2 -> randomSource.nextInt((int)words.get(ValueLayout.JAVA_LONG,8));
                    default -> throw new IllegalStateException("Unknown native noise seed effect " + command);
                };
            }
            id=(long)TAKE.invokeExact(builder);
            if(id==0)throw new IllegalStateException("Native noise seed was not completed");
            Release cleanup;
            try { cleanup=new Release(id); }
            catch(Throwable failure) { release(RELEASE,id); throw failure; }
            try { owner=MemorySegment.ofAddress(id).reinterpret(280,arena,cleanup).asReadOnly(); }
            catch(Throwable failure) { cleanup.accept(MemorySegment.NULL); throw failure; }
        } catch(Throwable failure) { throw propagate(failure); }
        finally { if(builder!=0)release(BUILDER_RELEASE,builder); }
    }
    double offset(int index) { return owner.get(ValueLayout.JAVA_DOUBLE,index*8L); }
    byte[] diagnosticPermutation() { return owner.asSlice(24,256).toArray(ValueLayout.JAVA_BYTE); }
    void write(MemorySegment state,int index,double amplitude) {
        MemorySegment octave=state.asSlice(NativeNoise.HEADER+(long)index*NativeNoise.OCTAVE,NativeNoise.OCTAVE);
        try { OCTAVE.invokeExact(id,octave,amplitude); }
        catch(Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void bindPerlin(long driver) {
        try { NativePerlin.bind(driver,id); }
        finally { Reference.reachabilityFence(this); }
    }
    static long[] statistics() {
        try(Arena arena=Arena.ofConfined()) {
            var words=arena.allocate(6L*Long.BYTES,Long.BYTES);
            STATS.invokeExact(words);return words.toArray(ValueLayout.JAVA_LONG);
        }catch(Throwable failure){throw propagate(failure);}
    }
    private static void release(MethodHandle handle,long id) {
        try { handle.invokeExact(id); }catch(Throwable failure){throw propagate(failure);}
    }
    private static RuntimeException propagate(Throwable failure) { return NativeNoiseSeed.<RuntimeException,RuntimeException>raise(failure); }
    @SuppressWarnings("unchecked") private static <E extends Throwable,T> T raise(Throwable failure)throws E {throw(E)failure;}
}
