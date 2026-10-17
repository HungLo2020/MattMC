package net.minecraft.world.level.material;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Small immutable compatibility views; Rust owns the palette and shading. */
final class NativeMapColors {
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_map_palette_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    // Do not construct MapColor/Brightness while their aliases are initializing.
    private static final Tables TABLES = load();
    private record Tables(int[] rgb, int[] brightness, int[] argb) { }
    private NativeMapColors() { }
    static int rgb(int id) { return TABLES.rgb[id]; }
    static int brightness(int id) { return TABLES.brightness[id]; }
    static int packedArgb(int packed) { return TABLES.argb[packed & 255]; }
    private static int[] buffer(int kind, int expected, Arena arena) throws Throwable {
        MemorySegment length = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment address = (MemorySegment) BUFFER.invokeExact(kind, length);
        if (address.address() == 0 || length.get(ValueLayout.JAVA_INT, 0) != expected)
            throw new IllegalStateException("Native map palette buffer mismatch: " + kind);
        return address.reinterpret((long)expected * Integer.BYTES).toArray(ValueLayout.JAVA_INT);
    }
    private static Tables load() {
        try (Arena arena = Arena.ofConfined()) {
            int[] header = buffer(0,4,arena);
            if (header[0] != 1 || header[1] != 62 || header[2] != 4 || header[3] != 256)
                throw new IllegalStateException("Unsupported native map palette projection");
            int[] rgb = buffer(1,header[1],arena), brightness = buffer(2,header[2],arena), argb = buffer(3,header[3],arena);
            for (int color : rgb) if ((color & 0xff000000) != 0) throw new IllegalStateException("Invalid native RGB");
            for (int shade : brightness) if (shade < 0 || shade > 255) throw new IllegalStateException("Invalid native map brightness");
            return new Tables(rgb,brightness,argb);
        } catch (Throwable error) { throw new ExceptionInInitializerError(error); }
    }
}
