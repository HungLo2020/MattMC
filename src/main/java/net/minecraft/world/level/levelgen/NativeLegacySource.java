package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.ThreadingDetector;

/** Actual Legacy atomic state and embedded cache, retained by native consumers. */
final class NativeLegacySource {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_random_source_legacy_" + name, descriptor);
    }
    private static final MethodHandle NEW = handle("new", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle SEED = handle("seed", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle NEXT = handle("next", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle RAW = handle("raw", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle RAW_SET = handle("raw_set", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle STATS = handle("statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle LEAF = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_seed_from_legacy_source", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private volatile long id;
    private MemorySegment owner;
    private static final class Release implements Consumer<MemorySegment> {
        private static final VarHandle HANDLE;
        static {
            try { HANDLE = MethodHandles.lookup().findVarHandle(Release.class, "id", long.class); }
            catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
        private volatile long id;
        Release(long id) { this.id = id; }
        @Override public void accept(MemorySegment ignored) {
            long value = (long)HANDLE.getAndSet(this, 0L);
            if (value != 0) release(value);
        }
    }
    // WorldgenRandom overrides the constructor seed hook and delegates draws;
    // materialize its separate raw-zero/cache owner only on a genuine use.
    NativeLegacySource() {}
    /** Adopt the same pending lease; caller closes it if any projection step fails. */
    NativeLegacySource(long actual,Consumer<MemorySegment> cleanup) {
        Arena arena=Arena.ofAuto();
        owner=MemorySegment.ofAddress(actual).reinterpret(1,arena,cleanup);
        id=actual;
    }
    long handle() {
        long value = id;
        return value != 0 ? value : initialize();
    }
    long initializedHandle() { return id; }
    private synchronized long initialize() {
        if (id != 0) return id;
        Arena arena = Arena.ofAuto();
        try {
            long value = (long)NEW.invokeExact();
            if (value == 0) throw new OutOfMemoryError("Native Legacy source allocation failed");
            Release cleanup;
            try { cleanup = new Release(value); }
            catch (Throwable failure) { release(value); throw failure; }
            try { owner = MemorySegment.ofAddress(value).reinterpret(1, arena, cleanup); }
            catch (Throwable failure) { cleanup.accept(MemorySegment.NULL); throw failure; }
            id = value;
            return value;
        } catch (Throwable failure) { throw propagate(failure); }
    }
    void seed(long seed, boolean reset) {
        try { if ((int)SEED.invokeExact(handle(), seed, reset ? 1 : 0) == 0) throw collision(); }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    int next(int bits) {
        try { long result = (long)NEXT.invokeExact(handle(), bits); if ((result >>> 32) != 0) throw collision(); return (int)result; }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    long raw() {
        try { return (long)RAW.invokeExact(handle()); }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void raw(long seed) {
        try { RAW_SET.invokeExact(handle(), seed); }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    long leaf(boolean simplex) {
        try { long leaf = (long)LEAF.invokeExact(handle(), simplex ? 1 : 0); if (leaf == 0) throw collision(); return leaf; }
        catch (Throwable failure) { throw propagate(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    static RuntimeException collision() { return ThreadingDetector.makeThreadingException("LegacyRandomSource", null); }
    static long[] statistics() {
        try (Arena arena = Arena.ofConfined()) {
            var data = arena.allocate(24,8); STATS.invokeExact(data); return data.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable failure) { throw propagate(failure); }
    }
    private static void release(long id) {
        try { RELEASE.invokeExact(id); } catch (Throwable failure) { throw propagate(failure); }
    }
    private static RuntimeException propagate(Throwable failure) { return NativeLegacySource.<RuntimeException,RuntimeException>raise(failure); }
    @SuppressWarnings("unchecked") private static <E extends Throwable,T> T raise(Throwable failure) throws E { throw (E)failure; }
}
