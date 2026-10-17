package net.minecraft.world.level.levelgen;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;

/** Canonical native cache and control; performs only requested genuine VM effects. */
final class NativeGaussian {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_gaussian_" + name, descriptor);
    }
    private static MethodHandle fixed(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_gaussian_" + name, descriptor, Linker.Option.critical(false));
    }
    private static final MethodHandle NEW = handle("new", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle HAS = fixed("has", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle VALUE = fixed("value", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RESET = fixed("reset", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle SET = fixed("set", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle START = handle("draw_new_source", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle WORLDGEN = handle("draw_bind_worldgen", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle NEXT = handle("draw_next", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle END = handle("draw_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
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
        Release(long id) { this.id = id; }
        @Override public void accept(MemorySegment ignored) {
            long value = (long) HANDLE.getAndSet(this, 0L);
            if (value != 0) release(value);
        }
    }

    NativeGaussian() {
        Arena arena = Arena.ofAuto();
        try {
            id = (long) NEW.invokeExact();
            if (id == 0) throw new OutOfMemoryError("Native Gaussian allocation failed");
            Release cleanup;
            try { cleanup = new Release(id); }
            catch (Throwable failure) { release(id); throw failure; }
            try { owner = MemorySegment.ofAddress(id).reinterpret(1, arena, cleanup); }
            catch (Throwable failure) { cleanup.accept(MemorySegment.NULL); throw failure; }
        } catch (Throwable failure) { throw propagate(failure); }
    }

    static boolean has(NativeLegacySource source) { return has(source.handle(), 2, source); }
    static double value(NativeLegacySource source) { return value(source.handle(), 2, source); }
    static void reset(NativeLegacySource source) { reset(source.handle(), 2, source); }
    static void set(NativeLegacySource source, boolean has, double value) { set(source.handle(), 2, source, has, value); }
    static double draw(NativeLegacySource cache, MarsagliaPolarGaussian caller, NativeXoroshiroSource producer) { return draw(cache.handle(), 2, cache, caller, producer); }

    boolean has() { return has(id, 0, this); }
    static boolean has(NativeXoroshiroSource source) { return has(source.handle(), 1, source); }
    private static boolean has(long id, int kind, Object keepAlive) {
        try { return (int) HAS.invokeExact(id, kind) != 0; }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(keepAlive); }
    }
    double value() { return value(id, 0, this); }
    static double value(NativeXoroshiroSource source) { return value(source.handle(), 1, source); }
    private static double value(long id, int kind, Object keepAlive) {
        try { return Double.longBitsToDouble((long) VALUE.invokeExact(id, kind)); }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(keepAlive); }
    }
    void reset() { reset(id, 0, this); }
    static void reset(NativeXoroshiroSource source) { reset(source.handle(), 1, source); }
    private static void reset(long id, int kind, Object keepAlive) {
        try { RESET.invokeExact(id, kind); }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(keepAlive); }
    }
    void set(boolean has, double value) { set(id, 0, this, has, value); }
    static void set(NativeXoroshiroSource source, boolean has, double value) {
        set(source.handle(), 1, source, has, value);
    }
    private static void set(long id, int kind, Object keepAlive, boolean has, double value) {
        try { SET.invokeExact(id, kind, has ? 1 : 0, Double.doubleToRawLongBits(value)); }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(keepAlive); }
    }
    double draw(MarsagliaPolarGaussian caller, NativeXoroshiroSource producer) {
        return draw(id, 0, this, caller, producer);
    }
    static double draw(NativeXoroshiroSource cache, MarsagliaPolarGaussian caller, NativeXoroshiroSource producer) {
        return draw(cache.handle(), 1, cache, caller, producer);
    }
    private static double draw(long id, int kind, Object cache, MarsagliaPolarGaussian caller, NativeXoroshiroSource producer) {
        long frame = 0;
        NativeWorldgenSource.GaussianTarget worldgenProducer=null;
        NativeLegacySource legacyProducer = LegacyRandomSource.nativeGaussianProducer(caller.randomSource);
        try {
            long source = legacyProducer != null ? legacyProducer.handle() : producer == null ? 0 : producer.handle();
            frame = (long) START.invokeExact(id, kind, source, legacyProducer != null ? 2 : 1);
            if (frame == 0) return Double.longBitsToDouble((long) VALUE.invokeExact(id, kind));
            // Inspect real source facts only after the committed cache miss. Native
            // control requests the first actual count resource when it needs one.
            worldgenProducer=NativeWorldgenSource.gaussianTarget(caller.randomSource);
            if(worldgenProducer!=null&&(int)WORLDGEN.invokeExact(frame,worldgenProducer.countHandle(),worldgenProducer.backing().kind(),worldgenProducer.backing().id())==0)worldgenProducer=null;
            MemorySegment words = MemorySegment.ofAddress(frame).reinterpret(16).asReadOnly();
            long response = 0;
            while ((int) NEXT.invokeExact(frame, response) != 0) {
                long op = words.getAtIndex(ValueLayout.JAVA_LONG, 0);
                response = switch ((int) op) {
                    case 1 -> Double.doubleToRawLongBits(caller.nativeGaussianNextDouble());
                    case 2 -> Double.doubleToRawLongBits(Math.log(Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG, 1))));
                    case 3 -> throw NativeLegacySource.collision();
                    case 4 -> worldgenProducer.materializeCount();
                    default -> throw new IllegalStateException("Unknown Gaussian effect " + op);
                };
            }
            return Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG, 1));
        } catch (Throwable failure) { throw propagate(failure); }
        finally {
            try { if (frame != 0) END.invokeExact(frame); }
            catch (Throwable failure) { throw propagate(failure); }
            finally {
                Reference.reachabilityFence(worldgenProducer);
                Reference.reachabilityFence(legacyProducer);
                Reference.reachabilityFence(producer);
                Reference.reachabilityFence(caller);
                Reference.reachabilityFence(cache);
            }
        }
    }
    static long[] statistics() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(48, 8);
            STATS.invokeExact(data);
            return data.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable failure) { throw propagate(failure); }
    }
    private static void release(long id) {
        try { RELEASE.invokeExact(id); }
        catch (Throwable failure) { throw propagate(failure); }
    }
    private static RuntimeException propagate(Throwable failure) {
        return NativeGaussian.<RuntimeException, RuntimeException>raise(failure);
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable, T> T raise(Throwable failure) throws E { throw (E) failure; }
}
