package net.minecraft.world.level.levelgen.synth;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Scoped weak observations of this real constructor; holds no graph/leaf data. */
final class NoiseConstructionProbe implements AutoCloseable {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_simplex_octaves_" + name, descriptor);
    }
    private static final MethodHandle NEW = handle("probe_new", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle READ = handle("probe_read", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("probe_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    final long id;
    NoiseConstructionProbe() {
        try { id = (long) NEW.invokeExact(); }
        catch (Throwable failure) { throw propagate(failure); }
    }
    long[] read() {
        try (var arena = Arena.ofConfined()) {
            var data = arena.allocate(48, 8);
            READ.invokeExact(id, data);
            return data.toArray(ValueLayout.JAVA_LONG);
        } catch (Throwable failure) { throw propagate(failure); }
    }
    @Override public void close() {
        try { RELEASE.invokeExact(id); }
        catch (Throwable failure) { throw propagate(failure); }
    }
    private static RuntimeException propagate(Throwable failure) {
        return NoiseConstructionProbe.<RuntimeException, RuntimeException>raise(failure);
    }
    @SuppressWarnings("unchecked") private static <E extends Throwable, T> T raise(Throwable failure) throws E { throw (E) failure; }
}
