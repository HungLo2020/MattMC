package net.minecraft.world.item;

import java.util.Map;

public final class TaczGunBurstData {
	private static final Burst DEFAULT = new Burst(3, 300L, 1000L, false);
	private static final Map<String, Burst> BURSTS = Map.ofEntries(
		Map.entry("b93r", new Burst(3, 67L, 400L, false)),
		Map.entry("db_short", new Burst(1, 100L, 500L, false)),
		Map.entry("hk_mk23", new Burst(1, 50L, 200L, false)),
		Map.entry("hk_mp5a5", new Burst(3, 67L, 300L, false)),
		Map.entry("m16a4", new Burst(3, 64L, 400L, false)),
		Map.entry("minigun", new Burst(6, 50L, 500L, true)),
		Map.entry("p90", new Burst(5, 50L, 600L, true)),
		Map.entry("qbz_95", new Burst(3, 71L, 450L, false)),
		Map.entry("scar_l", new Burst(3, 75L, 450L, true)),
		Map.entry("spas_12", new Burst(1, 333L, 300L, false)),
		Map.entry("spr15hb", new Burst(2, 80L, 500L, true)),
		Map.entry("ump45", new Burst(2, 86L, 350L, false)),
		Map.entry("vector45", new Burst(2, 67L, 300L, false))
	);

	private TaczGunBurstData() {
	}

	public static Burst burst(String gunId) {
		return BURSTS.getOrDefault(gunId, DEFAULT);
	}

	public record Burst(int count, long intervalMillis, long minIntervalMillis, boolean continuousShoot) {
		public int intervalTicks() {
			return Math.max(1, Math.round(this.intervalMillis / 50.0F));
		}

		public int minIntervalTicks() {
			return Math.max(1, Math.round(this.minIntervalMillis / 50.0F));
		}
	}
}
