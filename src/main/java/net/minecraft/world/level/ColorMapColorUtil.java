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
	private static final MethodHandle INIT_DRY_FOLIAGE = NativeLibraryLoader.downcallHandle("mattmc_rust",
			"mattmc_world_level_color_map_color_util_init_dry_foliage",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle GET_DRY_FOLIAGE = NativeLibraryLoader.downcallHandle("mattmc_rust",
			"mattmc_world_level_color_map_color_util_get_dry_foliage",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));

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

	public static void initDryFoliage(int[] is) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pixels = arena.allocate(ValueLayout.JAVA_INT, is.length);
			for (int index = 0; index < is.length; index++) {
				pixels.setAtIndex(ValueLayout.JAVA_INT, index, is[index]);
			}

			int status = (int)INIT_DRY_FOLIAGE.invokeExact(pixels, is.length);
			if (status == 0) {
				throw new IllegalStateException("Rust dry foliage color map initialization failed");
			}
		} catch (Throwable throwable) {
			throw new IllegalStateException("Rust dry foliage color map initialization failed", throwable);
		}
	}

	public static int getDryFoliage(double d, double e) {
		try {
			return (int)GET_DRY_FOLIAGE.invokeExact(d, e);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Rust dry foliage color map lookup failed", throwable);
		}
	}
}
