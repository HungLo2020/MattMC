package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.NativeLibraryLoader;
import org.junit.jupiter.api.Test;

class ColorMapColorUtilRustTest {
	@Test
	void grassColorUsesRustColorMapLookup() {
		int[] originalPixels = GrassColor.pixels;
		int[] pixels = new int[65536];
		int index = 223 << 8 | 191;
		pixels[index] = 0x12345678;

		try {
			GrassColor.init(pixels);
			assertEquals(0x12345678, GrassColor.get(0.25, 0.5));
		} finally {
			GrassColor.init(originalPixels);
		}
	}

	@Test
	void rustColorMapLookupReturnsFallbackForShortMaps() {
		assertEquals(-65281, ColorMapColorUtil.get(0.25, 0.5, new int[]{1, 2, 3}, -65281));
	}

	@Test
	void dryFoliageColorStateLivesInRust() {
		int[] pixels = new int[65536];
		int index = 223 << 8 | 191;
		pixels[index] = 0x23456789;

		try {
			ColorMapColorUtil.initDryFoliage(pixels);
			assertEquals(0x23456789, ColorMapColorUtil.getDryFoliage(0.25, 0.5));
		} finally {
			ColorMapColorUtil.initDryFoliage(new int[65536]);
		}
	}

	@Test
	void nativeLibraryNameIncludesPlatformDetails() {
		String fileName = NativeLibraryLoader.platformLibraryFileName("mattmc_rust");

		assertTrue(fileName.startsWith("mattmc_rust-"));
		assertTrue(fileName.endsWith(".so") || fileName.endsWith(".dll") || fileName.endsWith(".dylib"));
	}
}
