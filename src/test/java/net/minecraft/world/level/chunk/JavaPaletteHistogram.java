package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/** Original count body from fffe4a073, with inlining receiver accessor adaptation. */
final class JavaPaletteHistogram {
	static <T> void count(PalettedContainer<T> source, PalettedContainer.CountConsumer<T> countConsumer) {
		if (source.dataForNativeScan().palette().getSize() == 1) {
			countConsumer.accept(source.dataForNativeScan().palette().valueFor(0), source.dataForNativeScan().storage().getSize());
		} else {
			Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
			source.dataForNativeScan().storage().getAll(i -> int2IntOpenHashMap.addTo(i, 1));
			int2IntOpenHashMap.int2IntEntrySet().forEach(entry -> countConsumer.accept(source.dataForNativeScan().palette().valueFor(entry.getIntKey()), entry.getIntValue()));
		}
	}

}
