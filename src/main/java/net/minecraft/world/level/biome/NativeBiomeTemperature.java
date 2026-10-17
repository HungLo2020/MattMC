package net.minecraft.world.level.biome;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;

/** Shared native cache and temperature policy; VM effects keep actual callers. */
final class NativeBiomeTemperature {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_biome_temperature_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle LOOKUP = handle("lookup", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle FRAME = handle("frame", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle MODIFIER = handle("modifier", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle NEXT = handle("next", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle NOISE = handle("noise", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle FRAME_RELEASE = handle("frame_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle STATS = handle("statistics", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle SNAPSHOT = handle("snapshot", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle RESTORE = handle("restore", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private final long id;
    private final MemorySegment owner;

    /** Handle-only idempotent adoption; JDK may register cleanup before failing. */
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
            if (value != 0) release(RELEASE, value);
        }
    }
    NativeBiomeTemperature() {
        Arena arena = Arena.ofAuto();
        try { id = (long)CREATE.invokeExact(); }
        catch (Throwable failure) { throw propagate(failure); }
        if (id == 0) throw new OutOfMemoryError("Native biome temperature cache allocation failed");
        Release cleanup;
        try { cleanup = new Release(id); }
        catch (Throwable failure) { release(RELEASE, id); throw propagate(failure); }
        try { owner = MemorySegment.ofAddress(id).reinterpret(1, arena, cleanup); }
        catch (Throwable failure) { cleanup.accept(MemorySegment.NULL); throw propagate(failure); }
    }
    boolean test(Biome biome, BlockPos blockPos, long key, int seaLevel, int mode) {
        long driver = 0;
        try {
            int cached = (int)LOOKUP.invokeExact(id, key, mode);
            if (cached != 0) return cached == 2;
            Biome.TemperatureModifier modifier = biome.climateSettings.temperatureModifier();
            int kind = modifier == null ? -1 : modifier.ordinal();
            driver = (long)FRAME.invokeExact(id, key, seaLevel, mode, kind);
            if (driver == 0) throw new OutOfMemoryError("Native biome temperature invocation allocation failed");
            return run(driver, biome, blockPos) != 0;
        } catch (Throwable failure) { return raise(failure); }
        finally { if (driver != 0) release(FRAME_RELEASE, driver); Reference.reachabilityFence(this); }
    }
    static float modifier(int kind, BlockPos blockPos, float base) {
        long driver = 0;
        try {
            driver = (long)MODIFIER.invokeExact(kind, Float.floatToRawIntBits(base));
            if (driver == 0) throw new OutOfMemoryError("Native biome modifier invocation allocation failed");
            return Float.intBitsToFloat((int)run(driver, null, blockPos));
        } catch (Throwable failure) { return raise(failure); }
        finally { if (driver != 0) release(FRAME_RELEASE, driver); }
    }
    private static long run(long driver, Biome biome, BlockPos blockPos) throws Throwable {
        MemorySegment words = MemorySegment.ofAddress(driver).reinterpret(32).asReadOnly();
        long response = 0;
        int failed = 0;
        Throwable original = null;
        float base = 0;
        for (;;) {
            int status = (int)NEXT.invokeExact(driver, response, failed);
            if (status != 1) {
                if (original != null) throw original;
                if (status != 0) throw new IllegalStateException("Invalid native biome temperature continuation");
                return words.get(ValueLayout.JAVA_LONG, 0);
            }
            int command = (int)words.get(ValueLayout.JAVA_LONG, 0);
            int argument = (int)words.get(ValueLayout.JAVA_LONG, 8);
            response = 0;
            try {
                switch (command) {
                    case 1: base = biome.getBaseTemperature(); response = Float.floatToRawIntBits(base) & 0xffffffffL; break;
                    case 2: response = blockPos.getY(); break;
                    case 3: response = blockPos.getX(); break;
                    case 4: response = blockPos.getZ(); break;
                    case 5: {
                        NativeNoiseState snapshot = Biome.temperatureNoise(argument);
                        MemorySegment state = snapshot.state();
                        int result;
                        try { result = (int)NOISE.invokeExact(driver, state, state.byteSize()); }
                        finally { Reference.reachabilityFence(snapshot); }
                        if (result != 0) throw new IllegalStateException("Invalid native temperature noise binding: " + result);
                        break;
                    }
                    case 6: biome.invokeTemperatureModifier(blockPos, base); break;
                    default: throw new IllegalStateException("Unknown native biome temperature effect " + command);
                }
            } catch (Throwable failure) { original = failure; failed = 1; }
        }
    }
    /** The weather parent's actual child adopts this cache before this VM lease ends. */
    void bindWeather(long driver, int modifier) {
        try { NativeBiomeWeather.bind(driver, id, modifier); }
        finally { Reference.reachabilityFence(this); }
    }
    /** Opt-in verifier only; copies the actual current-thread cache in FIFO order. */
    long[] snapshot() {
        try (Arena arena = Arena.ofConfined()) {
            long count = (long)SNAPSHOT.invokeExact(id, MemorySegment.NULL, 0L);
            MemorySegment data = arena.allocate(Math.max(1, count * 16), 8);
            long actual = (long)SNAPSHOT.invokeExact(id, data, count);
            if (actual != count) throw new IllegalStateException("Concurrent temperature verification");
            return data.asSlice(0, count * 16).toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable failure) { return raise(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    void restore(long[] pairs) {
        if ((pairs.length & 1) != 0 || pairs.length > 2048) throw new IllegalArgumentException("Invalid temperature cache snapshot");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocateFrom(ValueLayout.JAVA_LONG, pairs);
            int result = (int)RESTORE.invokeExact(id, data, (long)pairs.length / 2);
            if (result == -2) throw new OutOfMemoryError("Native temperature verification restore allocation failed");
            if (result != 0) throw new IllegalArgumentException("Invalid temperature cache snapshot");
        } catch (Throwable failure) { NativeBiomeTemperature.<RuntimeException,Object>raise(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    static long[] statistics() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(7L * Long.BYTES, Long.BYTES);
            STATS.invokeExact(data);
            return data.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable failure) { return raise(failure); }
    }
    private static void release(MethodHandle function, long id) {
        try { function.invokeExact(id); }
        catch (Throwable failure) { NativeBiomeTemperature.<RuntimeException,Object>raise(failure); }
    }
    private static RuntimeException propagate(Throwable failure) { return NativeBiomeTemperature.<RuntimeException,RuntimeException>raise(failure); }
    @SuppressWarnings("unchecked") private static <E extends Throwable,T> T raise(Throwable failure) throws E { throw (E)failure; }
}
