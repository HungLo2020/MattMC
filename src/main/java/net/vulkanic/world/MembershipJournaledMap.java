package net.vulkanic.world;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Insertion-ordered registry that journals key membership changes, so a
 * rollback checkpoint records a journal position instead of copying every key.
 * Only {@link #put}, {@link #remove(Object)}, {@link #putAll} and {@link #clear}
 * may change membership; the other bulk mutators are rejected, and callers must
 * not remove through the collection views.
 */
final class MembershipJournaledMap<K, V> extends LinkedHashMap<K, V> {
	/** Bound on journal events between resets; older marks then expire. */
	private static final int MAX_JOURNAL_EVENTS = 1 << 18;

	/** A journal position; valid until the next {@link #resetJournal()}. */
	record Mark(long epoch, int position) {}

	private final ArrayList<K> journalKeys = new ArrayList<>();
	/** Bit {@code i} set: event {@code i} added its key; clear: removed it. */
	private final BitSet journalAdded = new BitSet();
	private long epoch;

	@Override
	public V put(K key, V value) {
		boolean absent = !containsKey(key);
		V previous = super.put(key, value);
		if (absent) record(key, true);
		return previous;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V remove(Object key) {
		if (!containsKey(key)) return null;
		V previous = super.remove(key);
		record((K) key, false);
		return previous;
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException("journaled registry membership changes through remove(key)");
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> entries) {
		for (Map.Entry<? extends K, ? extends V> entry : entries.entrySet()) put(entry.getKey(), entry.getValue());
	}

	@Override
	public void clear() {
		for (K key : keySet()) record(key, false);
		super.clear();
	}

	@Override
	public V putIfAbsent(K key, V value) {
		throw new UnsupportedOperationException("journaled registry membership changes through put");
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mapping) {
		throw new UnsupportedOperationException("journaled registry membership changes through put");
	}

	@Override
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
		throw new UnsupportedOperationException("journaled registry membership changes through put");
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
		throw new UnsupportedOperationException("journaled registry membership changes through put");
	}

	@Override
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
		throw new UnsupportedOperationException("journaled registry membership changes through put");
	}

	Mark mark() {
		return new Mark(epoch, journalKeys.size());
	}

	/**
	 * Keys absent at {@code mark}: exactly those whose first membership event
	 * after the mark is an addition. They may have been removed again since.
	 */
	List<K> keysAddedSince(Mark mark) {
		if (mark.epoch() != epoch || mark.position() > journalKeys.size()) {
			throw new IllegalStateException("registry checkpoint predates the current membership journal");
		}
		Set<K> seen = new LinkedHashSet<>();
		List<K> added = new ArrayList<>();
		for (int index = mark.position(); index < journalKeys.size(); index++) {
			K key = journalKeys.get(index);
			if (seen.add(key) && journalAdded.get(index)) added.add(key);
		}
		return added;
	}

	/** Drops the journal; every earlier mark expires. Caller owns the frame boundary. */
	void resetJournal() {
		journalKeys.clear();
		journalAdded.clear();
		epoch++;
	}

	private void record(K key, boolean added) {
		if (journalKeys.size() >= MAX_JOURNAL_EVENTS) resetJournal();
		journalAdded.set(journalKeys.size(), added);
		journalKeys.add(key);
	}
}
