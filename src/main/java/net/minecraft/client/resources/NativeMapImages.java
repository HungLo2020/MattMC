package net.minecraft.client.resources;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Temporary whole-image CPU bridge. Rust encodes indexed map pixels directly. */
public final class NativeMapImages {
    private static final int MAP_PIXELS = 128 * 128;
    private static final int MAX_PNG_BYTES = 20 * 1024;
    private static final MethodHandle ENCODE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_map_image_png",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private NativeMapImages() { }
    public static byte[] encodePng(byte[] colors) {
        if (colors == null || colors.length != MAP_PIXELS) throw new IllegalArgumentException("Map image requires exactly 128x128 indexed colors");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, colors);
            MemorySegment output = arena.allocate(MAX_PNG_BYTES);
            int count = (int) ENCODE.invokeExact(input, MAP_PIXELS, output, MAX_PNG_BYTES);
            if (count <= 0 || count > MAX_PNG_BYTES) throw new IllegalStateException("Native map PNG encoding failed: " + count);
            return output.asSlice(0,count).toArray(ValueLayout.JAVA_BYTE);
        } catch (RuntimeException error) { throw error; }
        catch (Throwable error) { throw new IllegalStateException("Native map PNG bridge failed",error); }
    }
}
