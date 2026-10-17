package net.minecraft.world.level.biome;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Scalar compatibility source adapter; selection policy lives in Rust. */
final class NativeBiomeQuery {
    private static final MethodHandle CORNER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_biome_corner",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    static int corner(long seed, int x, int y, int z) {
        try { return (int) CORNER.invokeExact(seed, x, y, z); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native biome selection failed", failure); }
    }
}
