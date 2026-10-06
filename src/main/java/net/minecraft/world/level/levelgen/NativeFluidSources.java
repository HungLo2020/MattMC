package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;
import org.jetbrains.annotations.Nullable;

/** Rust-owned aquifer noise sources. A {@link RandomState} compiles its router's
 * erosion, depth, fluid level floodedness, fluid level spread and lava noise
 * once into a shared native point program (see {@link NativeNoiseRouter.Compiler});
 * {@link NativeAquifer} then computes a fluid status in one native call that
 * evaluates these sources and preliminary surface levels itself, instead of
 * answering each request in Java. FlatCache markers keep their chunk semantics
 * through a per-chunk binding: inside the chunk's quart grid the input's
 * value at the quart corner with Y 0, computed once; outside it the input at
 * the point. */
final class NativeFluidSources {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_fluid_sources_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle SLOTS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_fluid_sources_slots",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    // Verification only: one source at one point with a chunk binding.
    private static final MethodHandle VALUE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_fluid_sources_value",
        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_program_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final Cleaner CLEANER = Cleaner.create();
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaFluidSources");
    // Fluid statuses computed with native sources, so tests can prove the route engaged.
    static final AtomicLong STATUSES = new AtomicLong();

    private final long handle;
    private final int slots;
    // The program references these states; they stay reachable while it lives.
    private final NativeNoiseState[] states;

    private NativeFluidSources(long handle, NativeNoiseState[] states) {
        this.handle = handle;
        this.states = states;
        CLEANER.register(this, new Release(handle));
        try {
            this.slots = (int)SLOTS.invokeExact(handle);
        } catch (Throwable error) {
            throw new IllegalStateException("Native fluid sources failed", error);
        }
    }

    private record Release(long handle) implements Runnable {
        @Override
        public void run() {
            try {
                // A point program, released like the surface programs.
                RELEASE.invokeExact(this.handle);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot release native fluid sources", error);
            }
        }
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static boolean enabled() {
        return enabled;
    }

    /** Compiles a RandomState's unwrapped sources; null keeps Java's sources. */
    @Nullable
    static NativeFluidSources compile(NoiseRouter router) {
        var compiler = new NativeNoiseRouter.Compiler(true, true);
        DensityFunction[] sources = {router.erosion(), router.depth(), router.fluidLevelFloodednessNoise(), router.fluidLevelSpreadNoise(),
            router.lavaNoise()};
        int[] roots = new int[sources.length];
        for (int index = 0; index < roots.length; index++) {
            roots[index] = compiler.addRoot(sources[index]);
            if (roots[index] < 0) return null;
        }
        // No FlatCache tables; the slice geometry is unused.
        NativeNoiseRouter.Packed packed = compiler.pack(roots, 1, 1, 1, 1, 0, 0, 0, 0, 1);
        long handle;
        try {
            handle = (long)CREATE.invokeExact(MemorySegment.ofArray(packed.ints()), packed.ints().length, MemorySegment.ofArray(packed.doubles()),
                packed.doubles().length, MemorySegment.ofArray(packed.longs()), packed.longs().length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native fluid sources", error);
        } finally {
            Reference.reachabilityFence(packed.states());
        }
        return handle == 0 ? null : new NativeFluidSources(handle, packed.states());
    }

    long handle() {
        return this.handle;
    }

    /** Verification only: source {@code root} (erosion, depth, floodedness,
     * spread, lava) at a point, through the given chunk binding. */
    double value(int root, int x, int y, int z, int[] grid, double[] memo, byte[] present) {
        try {
            return (double)VALUE.invokeExact(this.handle, root, x, y, z, MemorySegment.ofArray(grid), MemorySegment.ofArray(memo),
                MemorySegment.ofArray(present), memo.length);
        } catch (Throwable error) {
            throw new IllegalStateException("Native fluid source failed", error);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    /** FlatCache corner slots a chunk binding holds. */
    int slots() {
        return this.slots;
    }
}
