package net.minecraft.client.tacz;

import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczAttachmentItem;
import net.minecraft.world.item.TaczAttachmentType;
import net.minecraft.world.item.TaczFireMode;
import net.minecraft.world.item.TaczMvpGunItem;
import net.minecraft.world.item.TaczRefitGun;

public final class TaczGunAnimationTimings {
	private static final float FALLBACK_DRAW_SECONDS = 0.7083F;
	private static final float FALLBACK_PUT_AWAY_SECONDS = 0.4F;
	private static final float FALLBACK_SHOOT_SECONDS = 0.6F;
	private static final float FALLBACK_RELOAD_TACTICAL_SECONDS = 1.9333F;
	private static final float FALLBACK_RELOAD_EMPTY_SECONDS = 2.2833F;
	private static final float FALLBACK_INSPECT_SECONDS = 11.25F;
	private static final float FALLBACK_INSPECT_EMPTY_SECONDS = 12.2833F;
	private static final Map<String, AnimationInfo> ANIMATION_INFO = new ConcurrentHashMap<>();

	private static final Map<String, Map<String, Float>> DURATIONS = Map.ofEntries(
		entry("mp40", Map.of("draw", 0.8F, "put_away", 0.2667F, "shoot", 0.5F, "reload_tactical", 2.5333F, "reload_empty", 2.5333F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("m1a1", Map.of("draw", 0.95F, "put_away", 0.6F, "shoot", 0.75F, "reload_tactical", 3.25F, "reload_empty", 4.05F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("m1897", Map.of("put_away", 0.4667F, "shoot", 1.0333F, "reload_intro_empty", 2.13F, "reload_intro", 0.37F, "reload_loop", 0.67F, "reload_end", 0.17F, "inspect", 11.5F, "inspect_empty", 10.5F)),
		entry("g43", Map.of("draw", 0.7333F, "put_away", 0.4333F, "shoot", 0.6667F, "reload_tactical", 2.3333F, "reload_empty", 2.8667F, "inspect", 6.6667F, "inspect_empty", 4.65F)),
		entry("m1", Map.of("draw", 0.85F, "put_away", 0.5667F, "shoot", 0.6333F, "reload_tactical", 2.4F, "reload_empty", 3.15F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("stg44", Map.of("draw", 0.7333F, "put_away", 0.4333F, "shoot", 0.6667F, "reload_tactical", 2.3333F, "reload_empty", 2.8667F, "inspect", 4.65F, "inspect_empty", 4.65F)),
		entry("m1_garand", Map.of("draw", 0.66F, "put_away", 0.64F, "shoot", 0.36F, "shoot_last", 0.48F, "reload_tactical", 3.4583F, "reload_empty", 1.5833F, "inspect", 6.0417F, "inspect_empty", 6.0417F)),
		entry("kar98", Map.ofEntries(
			Map.entry("draw", 0.7833F),
			Map.entry("put_away", 0.75F),
			Map.entry("shoot", 1.1833F),
			Map.entry("shoot_scope", 1.1833F),
			Map.entry("bolt", 1.2667F),
			Map.entry("bolt_scope", 1.2667F),
			Map.entry("reload_empty_clip", 3.45F),
			Map.entry("reload_intro_empty", 1.6F),
			Map.entry("reload_intro", 0.75F),
			Map.entry("reload_loop", 0.6833F),
			Map.entry("reload_end", 0.9833F),
			Map.entry("inspect", 7.6333F),
			Map.entry("inspect_empty", 5.9333F)
		)),
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
		if ("spas_12".equals(gunId) && TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST) {
			return ammo == 1 ? firstExisting(gunId, "shoot_semi_last", "shoot_semi", "shoot") : firstExisting(gunId, "shoot_semi", "shoot");
		}
		if (("m1_garand".equals(gunId) || "trs_bull".equals(gunId)) && ammo == 1) {
			return firstExisting(gunId, "shoot_last", "shoot_pump_last", "shoot");
		}
		if (ammo == 1) {
			String last = firstExisting(gunId, "shoot_last", "shoot_semi_last", "shoot_pump_last");
			if (last != null) {
				return last;
			}
		}
		if ("kar98".equals(gunId) && hasScope(itemStack)) {
			return "shoot_scope";
		}
		if (TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST) {
			String burst = firstExisting(gunId, "shoot_burst");
			if (burst != null) {
				return burst;
			}
		}
		return "shoot";
	}

	public static String drawAnimation(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		if ("spas_12".equals(gunId) && TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST) {
			return firstExisting(gunId, isEmpty(itemStack) ? "draw_semi_caught" : null, "draw_semi", "draw");
		}
		return firstExisting(gunId, "draw", "draw_semi");
	}

	public static String putAwayAnimation(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		if ("spas_12".equals(gunId) && TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST) {
			return firstExisting(gunId, isEmpty(itemStack) ? "put_away_semi_caught" : null, "put_away_semi", "put_away");
		}
		return firstExisting(gunId, "put_away", "put_away_semi");
	}

	public static List<String> idleAnimations(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		java.util.ArrayList<String> names = new java.util.ArrayList<>();
		if ("spas_12".equals(gunId) && TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST) {
			String idle = firstExisting(gunId, isEmpty(itemStack) ? "static_idle_semi_caught" : null, "static_idle_semi", "static_idle");
			if (idle != null) {
				names.add(idle);
			}
			return List.copyOf(names);
		}

		String idle = firstExisting(gunId, "static_idle");
		if (idle != null) {
			names.add(idle);
		}
		if (isEmpty(itemStack)) {
			String boltCaught = firstExisting(gunId, "static_bolt_caught");
			if (boltCaught != null) {
				names.add(boltCaught);
			}
		}
		return List.copyOf(names);
	}

	public static List<String> followUpShootAnimations(ItemStack itemStack) {
		if (!"kar98".equals(gunId(itemStack))) {
			return List.of();
		}
		return List.of(hasScope(itemStack) ? "bolt_scope" : "bolt");
	}

	public static float followUpShootDelaySeconds(ItemStack itemStack) {
		return "kar98".equals(gunId(itemStack)) ? 0.2F : 0.0F;
	}

	public static List<String> reloadSequence(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		int ammo = TaczMvpGunItem.getAmmo(itemStack);
		if ("m1897".equals(gunId) || "m870".equals(gunId)) {
			int needed = Math.max(1, TaczMvpGunItem.getMagazineSize(itemStack) - ammo);
			return manualActionReloadSequence(gunId, needed, ammo <= 0, false);
		}
		if ("kar98".equals(gunId)) {
			int needed = Math.max(1, TaczMvpGunItem.getMagazineSize(itemStack) - ammo);
			if (ammo <= 0 && !hasScope(itemStack)) {
				return List.of("reload_empty_clip");
			}

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
		if ("m1014".equals(gunId)) {
			int needed = Math.max(1, TaczMvpGunItem.getMagazineSize(itemStack) - ammo);
			return m1014ReloadSequence(gunId, needed, ammo <= 0);
		}
		if ("spas_12".equals(gunId)) {
			int needed = Math.max(1, TaczMvpGunItem.getMagazineSize(itemStack) - ammo);
			return manualActionReloadSequence(gunId, needed, ammo <= 0, TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST);
		}
		String selected = reloadVariant(gunId, itemStack, ammo <= 0);
		return selected == null ? List.of() : List.of(selected);
	}

	public static String inspectAnimation(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		boolean empty = TaczMvpGunItem.getAmmo(itemStack) <= 0;
		if ("spas_12".equals(gunId)) {
			boolean semi = TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST;
			if (semi && empty) {
				return firstExisting(gunId, "inspect_empty_semi", "inspect_1_semi", "inspect_semi", "inspect_empty", "inspect");
			}
			if (semi) {
				return firstExisting(gunId, "inspect_semi", "inspect_1_semi", "inspect", "inspect_1");
			}
			if (empty) {
				return firstExisting(gunId, "inspect_empty", "inspect_1", "inspect");
			}
		}
		if (("m1897".equals(gunId) || "m870".equals(gunId)) && empty) {
			return firstExisting(gunId, "inspect_empty", "inspect_01", "inspect");
		}
		int extendedMagLevel = extendedMagLevel(itemStack);
		if (extendedMagLevel > 0) {
			String xmag = firstExisting(
				gunId,
				(empty ? "inspect_empty_xmag_" : "inspect_xmag_") + extendedMagLevel,
				(empty ? "inspect_empty_xmag_" : "inspect_xmag_") + groupedXmagSuffix(extendedMagLevel),
				empty ? "inspect_empty_xmag" : "inspect_xmag"
			);
			if (xmag != null) {
				return xmag;
			}
		}
		return firstExisting(gunId, empty ? "inspect_empty" : "inspect", empty ? "inspect_empty_pump" : "inspect_pump", empty ? "inspect_empty_semi" : "inspect_semi", "inspect");
	}

	public static String fireSelectAnimation(ItemStack itemStack) {
		String gunId = gunId(itemStack);
		if ("spas_12".equals(gunId)) {
			boolean empty = isEmpty(itemStack);
			return TaczMvpGunItem.getFireMode(itemStack) == TaczFireMode.BURST
				? firstExisting(gunId, empty ? "switch_semi_empty" : "switch_semi", "switch_semi")
				: firstExisting(gunId, empty ? "switch_pump_empty" : "switch_pump", "switch_pump");
		}
		String mode = TaczMvpGunItem.getFireMode(itemStack).getSerializedName();
		return firstExisting(gunId, "switch_" + mode, "switch_" + mode + "_empty");
	}

	public static float duration(ItemStack itemStack, String animationName) {
		return duration(gunId(itemStack), animationName);
	}

	public static float duration(String gunId, String animationName) {
		Float parsedDuration = animationInfo(gunId).durations().get(animationName);
		if (parsedDuration != null) {
			return parsedDuration;
		}
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

	private static boolean hasScope(ItemStack itemStack) {
		ItemStack scope = TaczRefitGun.getStoredAttachment(itemStack, TaczAttachmentType.SCOPE);
		return scope.getItem() instanceof TaczAttachmentItem;
	}

	private static boolean isEmpty(ItemStack itemStack) {
		return TaczMvpGunItem.getAmmo(itemStack) <= 0;
	}

	private static int extendedMagLevel(ItemStack itemStack) {
		ItemStack extendedMag = TaczRefitGun.getStoredAttachment(itemStack, TaczAttachmentType.EXTENDED_MAG);
		return extendedMag.getItem() instanceof TaczAttachmentItem attachment ? Math.max(0, attachment.getAttachmentLevel()) : 0;
	}

	private static String reloadVariant(String gunId, ItemStack itemStack, boolean empty) {
		int level = extendedMagLevel(itemStack);
		if (level > 0) {
			String prefix = empty ? "reload_empty_xmag" : "reload_tactical_xmag";
			String xmag = firstExisting(gunId, prefix + "_" + level, prefix + "_" + groupedXmagSuffix(level), prefix);
			if (xmag != null) {
				return xmag;
			}
		}
		return empty
			? firstExisting(gunId, "reload_empty", "reload_empty_pump", "reload_tactical")
			: firstExisting(gunId, "reload_tactical", "reload_empty");
	}

	private static List<String> manualActionReloadSequence(String gunId, int needed, boolean empty, boolean semi) {
		java.util.ArrayList<String> segments = new java.util.ArrayList<>();
		String intro = firstExisting(
			gunId,
			semi && empty ? "reload_empty_intro_semi" : null,
			semi ? "reload_intro_semi" : null,
			empty ? "reload_empty_intro" : null,
			empty ? "reload_intro_empty" : null,
			"reload_intro"
		);
		String loop = firstExisting(gunId, semi ? "reload_loop_semi" : null, "reload_loop");
		String ending = firstExisting(gunId, semi ? "reload_end_semi" : null, "reload_end");
		if (intro != null) {
			segments.add(intro);
		}
		if (loop != null) {
			for (int i = 0; i < needed; i++) {
				segments.add(loop);
			}
		}
		if (ending != null) {
			segments.add(ending);
		}
		return List.copyOf(segments);
	}

	private static List<String> m1014ReloadSequence(String gunId, int needed, boolean empty) {
		java.util.ArrayList<String> segments = new java.util.ArrayList<>();
		String intro = firstExisting(gunId, empty ? "reload_intro_empty" : null, "reload_intro");
		String single = firstExisting(gunId, "reload_loop");
		String pair = firstExisting(gunId, "reload_loop_2");
		String ending = firstExisting(gunId, "reload_end");
		if (intro != null) {
			segments.add(intro);
		}
		int remaining = needed;
		while (remaining > 0) {
			if (remaining > 1 && pair != null) {
				segments.add(pair);
				remaining -= 2;
			} else if (single != null) {
				segments.add(single);
				remaining--;
			} else {
				break;
			}
		}
		if (ending != null) {
			segments.add(ending);
		}
		return List.copyOf(segments);
	}

	private static String groupedXmagSuffix(int level) {
		return level <= 2 ? "12" : "23";
	}

	private static String firstExisting(String gunId, String... candidates) {
		Set<String> names = animationInfo(gunId).names();
		for (String candidate : candidates) {
			if (candidate != null && names.contains(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static AnimationInfo animationInfo(String gunId) {
		return ANIMATION_INFO.computeIfAbsent(gunId, TaczGunAnimationTimings::loadAnimationInfo);
	}

	private static AnimationInfo loadAnimationInfo(String gunId) {
		ResourceLocation location = ResourceLocation.withDefaultNamespace("animations/" + gunId + ".animation.json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
			JsonObject root = GsonHelper.parse(reader);
			JsonObject animations = GsonHelper.getAsJsonObject(root, "animations");
			Map<String, Float> durations = new HashMap<>();
			for (Map.Entry<String, com.google.gson.JsonElement> entry : animations.entrySet()) {
				durations.put(entry.getKey(), GsonHelper.getAsFloat(entry.getValue().getAsJsonObject(), "animation_length", 0.0F));
			}
			return new AnimationInfo(Set.copyOf(durations.keySet()), Map.copyOf(durations));
		} catch (Exception exception) {
			return new AnimationInfo(Set.of(), Map.of());
		}
	}

	private static Map.Entry<String, Map<String, Float>> entry(String gunId, Map<String, Float> durations) {
		return Map.entry(gunId, durations);
	}

	private record AnimationInfo(Set<String> names, Map<String, Float> durations) {
	}
}
