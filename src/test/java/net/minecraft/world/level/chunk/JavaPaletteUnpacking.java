package net.minecraft.world.level.chunk;

import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;

/** Original complete saved-data unpack caller, pinned to Git by the verification driver. */
final class JavaPaletteUnpacking {
	public static <T> DataResult<PalettedContainer<T>> unpack(Strategy<T> strategy, PalettedContainerRO.PackedData<T> packedData) {
		List<T> list = packedData.paletteEntries();
		int i = strategy.entryCount();
		Configuration configuration = strategy.getConfigurationForPaletteSize(list.size());
		int j = configuration.bitsInStorage();
		if (packedData.bitsPerEntry() != -1 && j != packedData.bitsPerEntry()) {
			return DataResult.error(() -> "Invalid bit count, calculated " + j + ", but container declared " + packedData.bitsPerEntry());
		} else {
			BitStorage bitStorage;
			Palette<T> palette;
			if (configuration.bitsInMemory() == 0) {
				palette = configuration.createPalette(strategy, list);
				bitStorage = new ZeroBitStorage(i);
			} else {
				Optional<LongStream> optional = packedData.storage();
				if (optional.isEmpty()) {
					return DataResult.error(() -> "Missing values for non-zero storage");
				}

				long[] ls = ((LongStream)optional.get()).toArray();

				try {
					if (!configuration.alwaysRepack() && configuration.bitsInMemory() == j) {
						palette = configuration.createPalette(strategy, list);
						bitStorage = new SimpleBitStorage(configuration.bitsInMemory(), i, ls);
					} else {
						Palette<T> palette2 = new HashMapPalette<>(j, list);
						SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, ls);
						Palette<T> palette3 = configuration.createPalette(strategy, list);
						int[] is = reencodeContents(simpleBitStorage, palette2, palette3);
						palette = palette3;
						bitStorage = new SimpleBitStorage(configuration.bitsInMemory(), i, is);
					}
				} catch (SimpleBitStorage.InitializationException var14) {
					return DataResult.error(() -> "Failed to read PalettedContainer: " + var14.getMessage());
				}
			}

			return DataResult.success(new PalettedContainer<>(strategy, configuration, bitStorage, palette));
		}
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
