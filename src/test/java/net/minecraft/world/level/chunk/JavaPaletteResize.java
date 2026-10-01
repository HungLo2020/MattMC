package net.minecraft.world.level.chunk;

import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Original Data.copyFrom body from b81c01943; only record receiver access is adapted. */
final class JavaPaletteResize<T> implements PaletteResize<T> {
    volatile PalettedContainer.Data<T> data;
    private final Strategy<T> strategy;

    JavaPaletteResize(Strategy<T> strategy, PalettedContainer.Data<T> data) {
        this.strategy = strategy;
        this.data = data;
    }

	static <T> void copyFrom(PalettedContainer.Data<T> target, Palette<T> palette, BitStorage bitStorage) {
			PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();

			for (int i = 0; i < bitStorage.getSize(); i++) {
				T object = palette.valueFor(bitStorage.get(i));
				target.storage().set(i, target.palette().idFor(object, paletteResize));
			}
		}

	private PalettedContainer.Data<T> createOrReuseData(@Nullable PalettedContainer.Data<T> data, int i) {
		Configuration configuration = this.strategy.getConfigurationForBitCount(i);
		if (data != null && configuration.equals(data.configuration())) {
			return data;
		} else {
			BitStorage bitStorage = (BitStorage)(configuration.bitsInMemory() == 0
				? new ZeroBitStorage(this.strategy.entryCount())
				: new SimpleBitStorage(configuration.bitsInMemory(), this.strategy.entryCount()));
			Palette<T> palette = configuration.createPalette(this.strategy, List.of());
			return new PalettedContainer.Data<>(configuration, bitStorage, palette);
		}
	}

	@Override
	public int onResize(int i, T object) {
		PalettedContainer.Data<T> data = this.data;
		PalettedContainer.Data<T> data2 = this.createOrReuseData(data, i);
		copyFrom(data2, data.palette(), data.storage());
		this.data = data2;
		return data2.palette().idFor(object, PaletteResize.noResizeExpected());
	}

	public T getAndSetUnchecked(int i, int j, int k, T object) {
		return this.getAndSet(this.strategy.getIndex(i, j, k), object);
	}

	private T getAndSet(int i, T object) {
		int j = this.data.palette().idFor(object, this);
		int k = this.data.storage().getAndSet(i, j);
		return this.data.palette().valueFor(k);
	}

}
