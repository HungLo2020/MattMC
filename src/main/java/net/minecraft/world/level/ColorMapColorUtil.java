package net.minecraft.world.level;

import com.sun.jna.Library;
import net.minecraft.util.NativeLibraryLoader;

public final class ColorMapColorUtil {
	private static final NativeMethods NATIVE = NativeLibraryLoader.loadRustLibrary("mattmc_rust", NativeMethods.class);

	private ColorMapColorUtil() {
	}

	static int get(double d, double e, int[] is, int i) {
		return NATIVE.mattmc_world_level_color_map_color_util_get(d, e, is, is.length, i);
	}

	private interface NativeMethods extends Library {
		int mattmc_world_level_color_map_color_util_get(double d, double e, int[] is, int length, int fallback);
	}
}
