package net.minecraft.world.item;

import java.util.Map;

public final class TaczGunBurstData {
	private static final Burst DEFAULT = new Burst(3, 200, 1000L, false);
	private static final Map<String, Burst> BURSTS = Map.ofEntries(
		Map.entry("b93r", new Burst(3, 900, 400L, false)),
		Map.entry("db_short", new Burst(1, 600, 500L, false)),
		Map.entry("hk_mk23", new Burst(1, 1200, 200L, false)),
		Map.entry("hk_mp5a5", new Burst(3, 900, 300L, false)),
		Map.entry("m16a1", new Burst(3, 900, 300L, false)),
		Map.entry("m16a4", new Burst(3, 937, 400L, false)),
		Map.entry("minigun", new Burst(6, 1200, 500L, true)),
		Map.entry("p90", new Burst(5, 1200, 600L, true)),
		Map.entry("qbz_95", new Burst(3, 850, 450L, false)),
		Map.entry("scar_l", new Burst(3, 800, 450L, true)),
		Map.entry("spas_12", new Burst(1, 180, 300L, false)),
		Map.entry("spr15hb", new Burst(2, 750, 500L, true)),
		Map.entry("type_81", new Burst(3, 900, 300L, false)),
		Map.entry("ump45", new Burst(2, 700, 350L, false)),
		Map.entry("vector45", new Burst(2, 900, 300L, false))
	);

	private TaczGunBurstData() {
	}

	public static Burst burst(String gunId) {
		return BURSTS.getOrDefault(gunId, DEFAULT);
	}

	public record Burst(int count, int bpm, long minIntervalMillis, boolean continuousShoot) {
		public long intervalMillis() {
			return this.bpm <= 0 ? 300L : 60000L / this.bpm;
		}

		public int intervalTicks() {
			return Math.max(1, Math.round(this.intervalMillis() / 50.0F));
		}

		public int minIntervalTicks() {
			return Math.max(1, Math.round(this.minIntervalMillis / 50.0F));
		}
	}
}
