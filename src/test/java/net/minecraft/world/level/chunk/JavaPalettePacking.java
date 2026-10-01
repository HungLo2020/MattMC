package net.minecraft.world.level.chunk;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.LongStream;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;

/** Pinned original pack and reencode bodies; receiver accessors inline to their original reads. */
final class JavaPalettePacking {
	static <T> PalettedContainerRO.PackedData<T> pack(PalettedContainer<T> source, Strategy<T> strategy) {
		source.acquire();

		PalettedContainerRO.PackedData var14;
		try {
			BitStorage bitStorage = source.dataForNativeScan().storage();
			Palette<T> palette = source.dataForNativeScan().palette();
			HashMapPalette<T> hashMapPalette = new HashMapPalette<>(bitStorage.getBits());
			int i = strategy.entryCount();
			int[] is = reencodeContents(bitStorage, palette, hashMapPalette);
			Configuration configuration = strategy.getConfigurationForPaletteSize(hashMapPalette.getSize());
			int j = configuration.bitsInStorage();
			Optional<LongStream> optional;
			if (j != 0) {
				SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, is);
				optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
			} else {
				optional = Optional.empty();
			}

			var14 = new PalettedContainerRO.PackedData(hashMapPalette.getEntries(), optional, j);
		} finally {
			source.release();
		}

		return var14;
	}

	private static <T> int[] reencodeContents(BitStorage bitStorage, Palette<T> palette, Palette<T> palette2) {
		int[] is = new int[bitStorage.getSize()];
		bitStorage.unpack(is);
		PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();
		int i = -1;
		int j = -1;

		for (int k = 0; k < is.length; k++) {
			int l = is[k];
			if (l != i) {
				i = l;
				j = palette2.idFor(palette.valueFor(l), paletteResize);
			}

			is[k] = j;
		}

		return is;
	}

}
