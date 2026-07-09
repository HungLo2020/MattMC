package net.minecraft.world.level;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public final class ColorMapColorUtil {
	private static final MethodHandle GET = NativeLibraryLoader.downcallHandle("mattmc_rust",
			"mattmc_world_level_color_map_color_util_get",
			FunctionDescriptor.of(ValueLayout.JAVA_INT,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.ADDRESS,
					ValueLayout.JAVA_INT,
					ValueLayout.JAVA_INT));

	private ColorMapColorUtil() {
	}

	static int get(double d, double e, int[] is, int i) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pixels = arena.allocate(ValueLayout.JAVA_INT, is.length);
			for (int index = 0; index < is.length; index++) {
				pixels.setAtIndex(ValueLayout.JAVA_INT, index, is[index]);
			}

			return (int) GET.invokeExact(d, e, pixels, is.length, i);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Rust color map lookup failed", throwable);
		}
	}
}
