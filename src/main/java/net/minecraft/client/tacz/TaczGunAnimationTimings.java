package net.minecraft.client.tacz;

import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczMvpGunItem;

public final class TaczGunAnimationTimings {
	private static final float FALLBACK_DRAW_SECONDS = 0.7083F;
	private static final float FALLBACK_PUT_AWAY_SECONDS = 0.4F;
	private static final float FALLBACK_SHOOT_SECONDS = 0.6F;
	private static final float FALLBACK_RELOAD_TACTICAL_SECONDS = 1.9333F;
	private static final float FALLBACK_RELOAD_EMPTY_SECONDS = 2.2833F;
	private static final float FALLBACK_INSPECT_SECONDS = 11.25F;
	private static final float FALLBACK_INSPECT_EMPTY_SECONDS = 12.2833F;

	private static final Map<String, Map<String, Float>> DURATIONS = Map.ofEntries(
		entry("mp40", Map.of("draw", 0.8F, "put_away", 0.2667F, "shoot", 0.5F, "reload_tactical", 2.5333F, "reload_empty", 2.5333F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("m1a1", Map.of("draw", 0.95F, "put_away", 0.6F, "shoot", 0.75F, "reload_tactical", 3.25F, "reload_empty", 4.05F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("m1897", Map.of("put_away", 0.4667F, "shoot", 1.0333F, "reload_intro_empty", 2.13F, "reload_intro", 0.37F, "reload_loop", 0.67F, "reload_end", 0.17F, "inspect", 11.5F, "inspect_empty", 10.5F)),
		entry("g43", Map.of("draw", 0.7333F, "put_away", 0.4333F, "shoot", 0.6667F, "reload_tactical", 2.3333F, "reload_empty", 2.8667F, "inspect", 6.6667F, "inspect_empty", 4.65F)),
		entry("m1", Map.of("draw", 0.85F, "put_away", 0.5667F, "shoot", 0.6333F, "reload_tactical", 2.4F, "reload_empty", 3.15F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("stg44", Map.of("draw", 0.7333F, "put_away", 0.4333F, "shoot", 0.6667F, "reload_tactical", 2.3333F, "reload_empty", 2.8667F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("m1_garand", Map.of("draw", 0.66F, "put_away", 0.64F, "shoot", 0.36F, "shoot_last", 0.48F, "reload_tactical", 3.4583F, "reload_empty", 1.5833F, "inspect", 6.0417F, "inspect_empty", 6.0417F)),
		entry("kar98k", Map.of("draw", 0.6667F, "put_away", 0.5833F, "shoot", 0.65F, "reload_tactical", 3.6333F, "reload_empty", 3.35F, "inspect", 6.5F, "inspect_empty", 4.3833F)),
		entry("trs_bull", Map.ofEntries(
			Map.entry("draw", 1.06F),
			Map.entry("put_away", 0.48F),
			Map.entry("shoot", 1.08F),
			Map.entry("shoot_last", 1.08F),
			Map.entry("reload_tactical", 3.64F),
			Map.entry("reload_empty", 3.06F),
			Map.entry("reload_1", 3.72F),
			Map.entry("reload_2", 3.6F),
			Map.entry("reload_3", 4.86F),
			Map.entry("reload_4", 3.94F),
			Map.entry("inspect", 11.62F),
			Map.entry("inspect_empty", 11.62F)
		)),
		entry("raygun_bo6", Map.of("draw", 0.9167F, "put_away", 0.5833F, "shoot", 0.62F, "reload_tactical", 3.38F, "reload_empty", 3.38F, "inspect", 7.7F, "inspect_empty", 7.7F))
	);

	private TaczGunAnimationTimings() {
	}

	public static String shootAnimation(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		int ammo = TaczMvpGunItem.getAmmo(itemStack);
		if (("m1_garand".equals(gunId) || "trs_bull".equals(gunId)) && ammo == 1) {
			return "shoot_last";
		}
		return "shoot";
	}

	public static List<String> reloadSequence(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		int ammo = TaczMvpGunItem.getAmmo(itemStack);
		if ("m1897".equals(gunId)) {
			int needed = Math.max(1, TaczMvpGunItem.getMagazineSize(itemStack) - ammo);
			java.util.ArrayList<String> segments = new java.util.ArrayList<>();
			segments.add(ammo <= 0 ? "reload_intro_empty" : "reload_intro");
			for (int i = 0; i < needed; i++) {
				segments.add("reload_loop");
			}
			segments.add("reload_end");
			return List.copyOf(segments);
		}
		if ("trs_bull".equals(gunId)) {
			return List.of(ragingBullReloadAnimation(ammo));
		}
		return List.of(ammo <= 0 ? "reload_empty" : "reload_tactical");
	}

	public static float duration(ItemStack itemStack, String animationName) {
		return duration(gunId(itemStack), animationName);
	}

	public static float duration(String gunId, String animationName) {
		Float duration = DURATIONS.getOrDefault(gunId, Map.of()).get(animationName);
		if (duration != null) {
			return duration;
		}
		return switch (animationName) {
			case "draw" -> FALLBACK_DRAW_SECONDS;
			case "put_away" -> FALLBACK_PUT_AWAY_SECONDS;
			case "shoot" -> FALLBACK_SHOOT_SECONDS;
			case "reload_tactical" -> FALLBACK_RELOAD_TACTICAL_SECONDS;
			case "reload_empty" -> FALLBACK_RELOAD_EMPTY_SECONDS;
			case "inspect" -> FALLBACK_INSPECT_SECONDS;
			case "inspect_empty" -> FALLBACK_INSPECT_EMPTY_SECONDS;
			default -> 0.0F;
		};
	}

	private static String ragingBullReloadAnimation(int ammo) {
		return switch (ammo) {
			case 0 -> "reload_empty";
			case 1 -> "reload_4";
			case 2 -> "reload_3";
			case 3 -> "reload_2";
			case 4 -> "reload_1";
			default -> "gun_check";
		};
	}

	private static String gunId(ItemStack itemStack) {
		return itemStack.getItem() instanceof TaczMvpGunItem gunItem ? gunItem.gunId() : "glock_17";
	}

	private static Map.Entry<String, Map<String, Float>> entry(String gunId, Map<String, Float> durations) {
		return Map.entry(gunId, durations);
	}
}
