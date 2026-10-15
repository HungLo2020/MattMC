package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The native level view against the real fastutil {@code Long2ByteOpenHashMap} it
 * replaces, under the tracker rule (levels at or above the bound removed). The same
 * histories run on both, including the zero key, colliding keys, growth, shrink and
 * mutation while a walk is in progress (as a Java callback could), comparing every
 * visited key and level, point reads, sizes and the exception (type and message) a
 * walk ends with.
 */
class NativeLevelViewTest {
	static final int ABSENT = 33;
	static final int REMOVE_FROM = 33;

	/** One history applied to both. */
	static final class Pair {
		final Long2ByteOpenHashMap original = new Long2ByteOpenHashMap();
		final NativeLevelView candidate = new NativeLevelView(ABSENT, REMOVE_FROM);

		Pair() {
			this.original.defaultReturnValue((byte)ABSENT);
		}

		void set(long key, int level) {
			if (level >= REMOVE_FROM) this.original.remove(key);
			else this.original.put(key, (byte)level);
			this.candidate.set(key, level);
		}

		void compare(long[] probes, String context) {
			assertEquals(this.original.size(), this.candidate.size(), () -> context + " size");
			for (long probe : probes) assertEquals(this.original.get(probe), this.candidate.get(probe), () -> context + " get " + probe);
			assertEquals(walkOriginal(this.original, Integer.MAX_VALUE, l -> {}), walkCandidate(this.candidate, Integer.MAX_VALUE, l -> {}), () -> context + " order");
		}
	}

	/** Visited keys (with levels) and the walk's ending: the fastIterable loop with a filter and a per-entry callback. */
	static List<String> walkOriginal(Long2ByteOpenHashMap map, int maxLevel, LongConsumer callback) {
		List<String> out = new ArrayList<>();
		try {
			for (Long2ByteMap.Entry entry : Long2ByteMaps.fastIterable(map)) {
				byte b = entry.getByteValue();
				long l = entry.getLongKey();
				if (b <= maxLevel) {
					out.add(l + "=" + b);
					callback.accept(l);
				}
			}
			out.add("end");
		} catch (RuntimeException e) {
			out.add(e.getClass().getName() + ": " + e.getMessage());
		}
		return out;
	}

	static List<String> walkCandidate(NativeLevelView view, int maxLevel, LongConsumer callback) {
		List<String> out = new ArrayList<>();
		try {
			view.forEachAtMost(maxLevel, l -> {
				out.add(l + "=" + view.get(l));
				callback.accept(l);
			});
			out.add("end");
		} catch (RuntimeException e) {
			out.add(e.getClass().getName() + ": " + e.getMessage());
		}
		return out;
	}

	static long[] probes(Random random, long[] pool) {
		long[] probes = new long[24];
		for (int i = 0; i < probes.length; i++) probes[i] = i < 8 ? pool[random.nextInt(pool.length)] : random.nextLong();
		probes[0] = 0;
		return probes;
	}

	@Test
	void zeroKeyCollisionsGrowthAndShrinkMatchFastutil() {
		Random random = new Random(7);
		for (int round = 0; round < 40; round++) {
			Pair pair = new Pair();
			// Keys that collide in the mixed hash, the zero key and ordinary chunk keys.
			long[] pool = new long[300];
			for (int i = 0; i < pool.length; i++) pool[i] = i % 7 == 0 ? (long)i << 32 : random.nextInt(4096) - 2048L;
			pool[0] = 0;
			int ops = 50 + random.nextInt(800);
			for (int op = 0; op < ops; op++) {
				long key = pool[random.nextInt(pool.length)];
				pair.set(key, random.nextInt(4) == 0 ? 33 + random.nextInt(3) : random.nextInt(33));
				if (op % 37 == 0) pair.compare(probes(random, pool), "round " + round + " op " + op);
			}
			pair.compare(probes(random, pool), "round " + round + " end");
			// Shrink back down through the minimum.
			for (long key : pool) pair.set(key, 33);
			pair.compare(probes(random, pool), "round " + round + " emptied");
		}
	}

	@Test
	void mutationDuringAWalkMatchesFastutilIncludingItsExceptions() {
		Random random = new Random(11);
		int exceptions = 0;
		for (int round = 0; round < 300; round++) {
			Pair pair = new Pair();
			long[] pool = new long[200];
			for (int i = 0; i < pool.length; i++) pool[i] = random.nextInt(2048) - 1024L;
			pool[0] = 0;
			int size = 1 + random.nextInt(150);
			for (int i = 0; i < size; i++) pair.set(pool[random.nextInt(pool.length)], random.nextInt(33));
			// The same mutation script runs inside both walks: grow, shrink or churn at chosen steps.
			long seed = random.nextLong();
			int filter = random.nextBoolean() ? Integer.MAX_VALUE : 31;
			List<long[]> script = new ArrayList<>();
			Random scripted = new Random(seed);
			for (int step = 0; step < 200; step++) {
				if (scripted.nextInt(5) != 0) {
					script.add(null);
					continue;
				}
				int count = 1 + scripted.nextInt(scripted.nextBoolean() ? 3 : 120);
				long[] ops = new long[count * 2];
				boolean remove = scripted.nextBoolean();
				for (int i = 0; i < count; i++) {
					ops[i * 2] = scripted.nextInt(4) == 0 ? scripted.nextInt(100000) : pool[scripted.nextInt(pool.length)];
					ops[i * 2 + 1] = remove ? 33 : scripted.nextInt(33);
				}
				script.add(ops);
			}
			Long2ByteOpenHashMap original = pair.original;
			NativeLevelView candidate = pair.candidate;
			int[] originalStep = {0};
			List<String> expected = walkOriginal(original, filter, l -> {
				long[] ops = originalStep[0] < script.size() ? script.get(originalStep[0]++) : null;
				if (ops != null) for (int i = 0; i < ops.length; i += 2) {
					if (ops[i + 1] >= REMOVE_FROM) original.remove(ops[i]);
					else original.put(ops[i], (byte)ops[i + 1]);
				}
			});
			int[] candidateStep = {0};
			List<String> actual = walkCandidate(candidate, filter, l -> {
				long[] ops = candidateStep[0] < script.size() ? script.get(candidateStep[0]++) : null;
				if (ops != null) for (int i = 0; i < ops.length; i += 2) candidate.set(ops[i], (int)ops[i + 1]);
			});
			assertEquals(expected, fastThrow(expected, actual), "round " + round);
			if (!expected.getLast().equals("end")) exceptions++;
			pair.compare(probes(random, pool), "round " + round + " after walk");
		}
		assertTrue(exceptions > 0, "no walk reached an iterator exception");
	}

	/**
	 * Once the JIT compiles fastutil's iterator hot, it may throw a preallocated
	 * NullPointerException or ArrayIndexOutOfBoundsException with no message
	 * (OmitStackTraceInFastThrow). When the original ended that way, the candidate's
	 * ending of the same class counts as equal; earlier rounds compare exact messages.
	 */
	static List<String> fastThrow(List<String> expected, List<String> actual) {
		String end = expected.getLast();
		if (!end.endsWith(": null") || actual.isEmpty()) return actual;
		String type = end.substring(0, end.indexOf(':'));
		String last = actual.getLast();
		if (!last.startsWith(type + ":")) return actual;
		List<String> out = new ArrayList<>(actual);
		out.set(out.size() - 1, end);
		return out;
	}

	static List<String> keys(List<String> walk) {
		List<String> out = new ArrayList<>();
		for (String entry : walk) out.add(entry.contains("=") ? entry.substring(0, entry.indexOf('=')) : entry);
		return out;
	}

	@Test
	void keyIteratorMatchesKeySetIterator() {
		Random random = new Random(3);
		Pair pair = new Pair();
		for (int i = 0; i < 500; i++) pair.set(random.nextInt(4000) - 2000L, random.nextInt(36));
		List<Long> expected = new ArrayList<>();
		for (Iterator<Long> it = pair.original.keySet().iterator(); it.hasNext(); ) expected.add(it.next());
		List<Long> actual = new ArrayList<>();
		for (LongIterator it = pair.candidate.keys(); it.hasNext(); ) actual.add(it.nextLong());
		assertEquals(expected, actual);
	}

	@Test
	void aWalkKeepsItsViewAliveAfterTheWrapperIsReleased() throws Exception {
		NativeLevelView view = new NativeLevelView(ABSENT, REMOVE_FROM);
		for (long key = 1; key <= 50; key++) view.set(key, 30);
		LongIterator walk = view.keys();
		WeakReference<NativeLevelView> wrapper = new WeakReference<>(view);
		view = null;
		for (int i = 0; i < 20 && wrapper.get() != null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		assertNull(wrapper.get(), "the view wrapper was not collected");
		int count = 0;
		while (walk.hasNext()) {
			walk.nextLong();
			count++;
		}
		assertEquals(50, count);
	}
}
