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

/** Rust-owned preliminary surface levels. A {@link RandomState} compiles its
 * router's preliminarySurfaceLevel once into a shared native point program
 * (see {@link NativeNoiseRouter.Compiler}); NoiseChunks with the empty blender
 * then compute every surface level natively instead of evaluating the
 * chunk-wrapped function in Java, filling the same per-chunk cache. A
 * FindTopSurface root runs its whole downward search in Rust. */
final class NativeSurfaceLevel {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_program_create",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    // One column of an unbounded downward search: an ordinary downcall.
    private static final MethodHandle LEVEL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_level",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    // Borrows heap arrays; bounded to BATCH columns per call.
    private static final MethodHandle LEVELS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_levels",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_surface_program_release",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final Cleaner CLEANER = Cleaner.create();
    static final int BATCH = 32;
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaSurfaceLevel");
    // Natively computed columns, so tests can prove the route engaged.
    static final AtomicLong COLUMNS = new AtomicLong();

    private final long handle;
    // The program references these states; they stay reachable while it lives.
    private final NativeNoiseState[] states;
    // A program of one constant node has one level, computed natively once.
    private final boolean constant;
    private final int constantLevel;

    private NativeSurfaceLevel(long handle, NativeNoiseState[] states, boolean constant) {
        this.handle = handle;
        this.states = states;
        CLEANER.register(this, new Release(handle));
        this.constantLevel = constant ? this.call(0, 0) : 0;
        this.constant = constant;
    }

    private record Release(long handle) implements Runnable {
        @Override
        public void run() {
            try {
                RELEASE.invokeExact(this.handle);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot release native surface program", error);
            }
        }
    }

    long handle() {
        return this.handle;
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    static boolean enabled() {
        return enabled;
    }

    /** Compiles a RandomState's unwrapped preliminarySurfaceLevel; null keeps Java. */
    @Nullable
    static NativeSurfaceLevel compile(DensityFunction preliminarySurfaceLevel) {
        var compiler = new NativeNoiseRouter.Compiler(true);
        int root = compiler.addRoot(preliminarySurfaceLevel);
        if (root < 0 || root != compiler.nodes.size() / 5 - 1) return null;
        // One root, no FlatCache slots; the slice geometry is unused.
        NativeNoiseRouter.Packed packed = compiler.pack(new int[]{root}, 1, 1, 1, 1, 0, 0, 0, 0, 1);
        long handle;
        try {
            handle = (long)CREATE.invokeExact(MemorySegment.ofArray(packed.ints()), packed.ints().length, MemorySegment.ofArray(packed.doubles()),
                packed.doubles().length, MemorySegment.ofArray(packed.longs()), packed.longs().length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot create native surface program", error);
        } finally {
            Reference.reachabilityFence(packed.states());
        }
        // Rust also proves FlatCache and Cache2D inputs Y-independent; a rejected program keeps Java.
        boolean constant = packed.ints()[0] == 1 && compiler.nodes.getInt(0) == 0;
        return handle == 0 ? null : new NativeSurfaceLevel(handle, packed.states(), constant);
    }

    /** {@code Mth.floor(preliminarySurfaceLevel.compute((x, 0, z)))} at a quart-aligned column. */
    int level(int x, int z) {
        COLUMNS.incrementAndGet();
        return this.constant ? this.constantLevel : this.call(x, z);
    }

    private int call(int x, int z) {
        try {
            return (int)LEVEL.invokeExact(this.handle, x, z);
        } catch (Throwable error) {
            throw new IllegalStateException("Native surface level failed", error);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    /** Levels of {@code count} quart-aligned columns, written to {@code out}. */
    void levels(int[] xs, int[] zs, int[] out, int count) {
        if (count > xs.length || count > zs.length || count > out.length) throw new IllegalArgumentException("Surface level buffers");
        if (this.constant) {
            java.util.Arrays.fill(out, 0, count, this.constantLevel);
            COLUMNS.addAndGet(count);
            return;
        }
        MemorySegment x = MemorySegment.ofArray(xs), z = MemorySegment.ofArray(zs), result = MemorySegment.ofArray(out);
        try {
            for (int start = 0; start < count; start += BATCH) {
                int size = Math.min(BATCH, count - start);
                int status = (int)LEVELS.invokeExact(this.handle, x.asSlice(start * 4L, size * 4L), z.asSlice(start * 4L, size * 4L),
                    result.asSlice(start * 4L, size * 4L), size);
                if (status != 0) throw new IllegalStateException("Native surface levels failed: " + status);
            }
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native surface levels failed", error);
        } finally {
            COLUMNS.addAndGet(count);
            Reference.reachabilityFence(this);
        }
    }
}
