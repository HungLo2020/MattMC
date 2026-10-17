package net.minecraft.world.level.chunk;

import java.util.function.Consumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntArraySet;

/** Literal original getAll body from 7af3a1594, with receiver accessor adaptation. */
final class JavaPaletteDistinct {
	static <T> void getAll(PalettedContainer<T> source, Consumer<T> consumer) {
		Palette<T> palette = source.dataForNativeScan().palette();
		IntSet intSet = new IntArraySet();
		source.dataForNativeScan().storage().getAll(intSet::add);
		intSet.forEach(i -> consumer.accept(palette.valueFor(i)));
	}

}
