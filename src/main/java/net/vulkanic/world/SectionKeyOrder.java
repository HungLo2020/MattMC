package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.LongArrays;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Canonical terrain submission order: ascending section key. Collections are
 * gathered from a hash map whose iteration order shifts as sections stream in
 * and out, so an identical section set reached Rust in a different order and
 * missed its cached batch plan. Opaque and shadow terrain do not depend on this
 * order; translucent terrain is sorted separately by camera distance.
 * Not thread-safe; reuse one instance per producer thread.
 */
final class SectionKeyOrder {
	private long[] keys = new long[0];
	private long[] positions = new long[0];
	private Object[] items = new Object[0];

	@SuppressWarnings("unchecked")
	<T> void sort(List<T> list, ToLongFunction<T> key) {
		int count = list.size();
		if (count < 2) return;
		if (keys.length < count) {
			int capacity = Math.max(count, keys.length * 2);
			keys = new long[capacity];
			positions = new long[capacity];
			items = new Object[capacity];
		}
		for (int index = 0; index < count; index++) {
			T item = list.get(index);
			items[index] = item;
			keys[index] = key.applyAsLong(item);
			positions[index] = index;
		}
		// Pairs sort by key, then original position, so equal keys stay stable.
		LongArrays.radixSort(keys, positions, 0, count);
		for (int index = 0; index < count; index++) {
			list.set(index, (T) items[(int) positions[index]]);
		}
		java.util.Arrays.fill(items, 0, count, null);
	}
}
