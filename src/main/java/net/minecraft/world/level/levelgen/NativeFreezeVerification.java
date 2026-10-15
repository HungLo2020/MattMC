package net.minecraft.world.level.levelgen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * Verification only ({@code -Dmattmc.worldgen.verifyDecoration=true}): Java's
 * per-thread biome temperature caches around a native {@code SnowAndFreezeFeature}
 * placement. Snapshots read each biome's cache map on the current thread
 * directly (never through a temperature call). The caches before the native
 * placement are restored for Java's replay, so both routes start from the same
 * history; their resulting caches (entries and insertion order, which is the
 * eviction order) and temperature call counts must match. Afterwards the native
 * post-state is restored, as the native placement is the one that stands, even
 * if the replay or comparison throws. A native failure skips all of this: the
 * native exception stands with the cache state of its completed prefix. {@code -Dmattmc.worldgen.verifyFreezeSeed=true} first
 * seeds every column's positions under a different sea level and fills each
 * biome's cache to two below its 1024-entry boundary, so the history and FIFO
 * eviction are exercised (this changes later Java answers, so seeded runs are
 * not parity runs).
 */
final class NativeFreezeVerification {
	private static final Field CACHE;
	private static final boolean SEED = Boolean.getBoolean("mattmc.worldgen.verifyFreezeSeed");
	static final LongAdder VERIFIED = new LongAdder(), MISMATCHED = new LongAdder();

	static {
		try {
			CACHE = Biome.class.getDeclaredField("temperatureCache");
			CACHE.setAccessible(true);
		} catch (ReflectiveOperationException error) {
			throw new ExceptionInInitializerError(error);
		}
	}

	private final List<Biome> biomes = new ArrayList<>();
	private final List<long[]> before;
	private List<long[]> nativeState;
	private long nativeCalls;

	private NativeFreezeVerification(List<Holder<Biome>> values, WorldGenLevel level, BlockPos origin) {
		var seen = new IdentityHashMap<Biome, Boolean>();
		for (var holder : values) if (seen.put(holder.value(), Boolean.TRUE) == null) this.biomes.add(holder.value());
		if (SEED) this.seed(level, origin);
		this.before = this.capture();
	}

	static NativeFreezeVerification begin(List<Holder<Biome>> values, WorldGenLevel level, BlockPos origin) {
		return new NativeFreezeVerification(values, level, origin);
	}

	/** The feature's own column positions under another sea level, then filler to the FIFO boundary. */
	private void seed(WorldGenLevel level, BlockPos origin) {
		int other = level.getSeaLevel() + 200;
		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				int x = origin.getX() + i, z = origin.getZ() + j, y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				for (BlockPos pos : List.of(new BlockPos(x, y, z), new BlockPos(x, y - 1, z))) level.getBiome(pos).value().warmEnoughToRain(pos, other);
			}
		}
		for (Biome biome : this.biomes) {
			var map = cache(biome);
			for (int i = 0; map.size() < 1022; i++) biome.warmEnoughToRain(new BlockPos(6_000_000 + i, 70, 0), other);
		}
	}

	private static Long2FloatLinkedOpenHashMap cache(Biome biome) {
		try {
			return (Long2FloatLinkedOpenHashMap)((ThreadLocal<?>)CACHE.get(biome)).get();
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException(error);
		}
	}

	/** Each biome's cache as [key, float bits] pairs in insertion order. */
	private List<long[]> capture() {
		List<long[]> out = new ArrayList<>();
		for (Biome biome : this.biomes) {
			var map = cache(biome);
			long[] entries = new long[map.size() * 2];
			int at = 0;
			for (var entry : map.long2FloatEntrySet()) {
				entries[at++] = entry.getLongKey();
				entries[at++] = Float.floatToRawIntBits(entry.getFloatValue());
			}
			out.add(entries);
		}
		return out;
	}

	private void restore(List<long[]> state) {
		for (int b = 0; b < this.biomes.size(); b++) {
			var map = cache(this.biomes.get(b));
			map.clear();
			long[] entries = state.get(b);
			for (int at = 0; at < entries.length; at += 2) map.put(entries[at], Float.intBitsToFloat((int)entries[at + 1]));
		}
	}

	/**
	 * After the native placement: keep its result and restore the history Java's
	 * replay starts from. {@code calls} is this placement's own OP_WARM count (per
	 * call, not a delta of the global aggregate, which other threads also advance).
	 */
	void afterNative(long calls) {
		this.nativeState = this.capture();
		this.nativeCalls = calls;
		this.restore(this.before);
	}

	/**
	 * The temperature calls Java's replay makes from the restored blocks when the
	 * feature runs (its biome filter passes): shouldFreeze's always, shouldSnow's
	 * when the biome has precipitation.
	 */
	static long expectedCalls(WorldGenLevel level, BlockPos origin) {
		long calls = 0;
		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				int x = origin.getX() + i, z = origin.getZ() + j, y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				calls += 1 + (level.getBiome(new BlockPos(x, y, z)).value().hasPrecipitation() ? 1 : 0);
			}
		}
		return calls;
	}

	/** The last mismatch's description, for the verification log. */
	String difference = "";

	/** The first biome whose cache differs: sizes and the first differing entry. */
	private String describe(List<long[]> replay, List<long[]> nat) {
		for (int b = 0; b < Math.min(replay.size(), nat.size()); b++) {
			long[] j = replay.get(b), n = nat.get(b);
			if (java.util.Arrays.equals(j, n)) continue;
			int at = 0;
			while (at < Math.min(j.length, n.length) && j[at] == n[at]) at++;
			return "; biome " + b + " sizes java " + j.length / 2 + " native " + n.length / 2 + " first difference at entry " + at / 2
				+ (at + 1 < j.length ? " java " + BlockPos.of(j[at - at % 2]) + "=" + Float.intBitsToFloat((int)j[at - at % 2 + 1]) : " java end")
				+ (at + 1 < n.length ? " native " + BlockPos.of(n[at - at % 2]) + "=" + Float.intBitsToFloat((int)n[at - at % 2 + 1]) : " native end");
		}
		return "; caches equal";
	}

	/** Leaves the native post-state in place (after the replay, or when it threw). */
	void finish() {
		if (this.nativeState != null) this.restore(this.nativeState);
	}

	/** After Java's replay: both routes must leave identical caches and call counts. */
	boolean afterJava(long expectedCalls) {
		List<long[]> replayed = this.capture();
		boolean same = this.nativeCalls == expectedCalls && replayed.size() == this.nativeState.size();
		for (int b = 0; same && b < replayed.size(); b++) same = java.util.Arrays.equals(replayed.get(b), this.nativeState.get(b));
		VERIFIED.increment();
		if (!same) {
			MISMATCHED.increment();
			this.difference = "calls native " + this.nativeCalls + " expected " + expectedCalls + describe(replayed, this.nativeState);
		}
		return same;
	}
}
