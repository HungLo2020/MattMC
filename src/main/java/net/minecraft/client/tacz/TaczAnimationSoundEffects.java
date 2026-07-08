package net.minecraft.client.tacz;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczMvpGunItem;
import org.slf4j.Logger;

public final class TaczAnimationSoundEffects {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<String, Map<String, List<SoundKeyframe>>> CACHE = new ConcurrentHashMap<>();
	private static final Map<ResourceLocation, Set<ResourceLocation>> SOUND_EVENT_TARGETS = new ConcurrentHashMap<>();
	private static final Map<String, Optional<ResourceLocation>> DISPLAY_SOUND_CACHE = new ConcurrentHashMap<>();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TACZ Animation Sounds");
		thread.setDaemon(true);
		return thread;
	});

	private TaczAnimationSoundEffects() {
	}

	public static void schedule(ItemStack itemStack, String animationName) {
		schedule(itemStack, animationName, 0.0F);
	}

	public static void scheduleShoot(ItemStack itemStack, String animationName) {
		schedule(gunId(itemStack), animationName, 0.0F, true);
	}

	public static boolean playReload(ItemStack itemStack, boolean noAmmo) {
		String gunId = gunId(itemStack);
		Optional<ResourceLocation> sound = displaySound(gunId, noAmmo ? "reload_empty" : "reload_tactical");
		if (sound.isEmpty()) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return true;
		}

		minecraft.level
			.playLocalSound(
				minecraft.player,
				SoundEvent.createVariableRangeEvent(sound.get()),
				SoundSource.PLAYERS,
				1.0F,
				1.0F
			);
		return true;
	}

	public static void scheduleSequence(ItemStack itemStack, List<String> animationNames) {
		String gunId = gunId(itemStack);
		float offsetSeconds = 0.0F;
		for (String animationName : animationNames) {
			if (animationName == null || animationName.isEmpty()) {
				continue;
			}
			schedule(gunId, animationName, offsetSeconds);
			offsetSeconds += TaczGunAnimationTimings.duration(gunId, animationName);
		}
	}

	private static void schedule(ItemStack itemStack, String animationName, float offsetSeconds) {
		schedule(gunId(itemStack), animationName, offsetSeconds, false);
	}

	private static void schedule(String gunId, String animationName, float offsetSeconds) {
		schedule(gunId, animationName, offsetSeconds, false);
	}

	private static void schedule(String gunId, String animationName, float offsetSeconds, boolean suppressDuplicateShootSound) {
		if (animationName == null || animationName.isEmpty()) {
			return;
		}
		for (SoundKeyframe keyframe : animationSounds(gunId).getOrDefault(animationName, List.of())) {
			if (suppressDuplicateShootSound && duplicatesGunShootSound(gunId, keyframe)) {
				continue;
			}
			long delayMillis = Math.max(0L, Math.round((offsetSeconds + keyframe.timeSeconds()) * 1000.0F));
			if (delayMillis == 0L) {
				Minecraft.getInstance().execute(() -> playIfStillHolding(gunId, keyframe));
			} else {
				EXECUTOR.schedule(() -> Minecraft.getInstance().execute(() -> playIfStillHolding(gunId, keyframe)), delayMillis, TimeUnit.MILLISECONDS);
			}
		}
	}

	private static boolean duplicatesGunShootSound(String gunId, SoundKeyframe keyframe) {
		return resolvesToSameSound(keyframe.sound(), ResourceLocation.withDefaultNamespace(gunId + ".shoot"));
	}

	private static boolean resolvesToSameSound(ResourceLocation left, ResourceLocation right) {
		if (left.equals(right)) {
			return true;
		}

		Set<ResourceLocation> leftTargets = soundEventTargets(left);
		Set<ResourceLocation> rightTargets = soundEventTargets(right);
		for (ResourceLocation leftTarget : leftTargets) {
			if (rightTargets.contains(leftTarget)) {
				return true;
			}
		}
		return false;
	}

	private static Set<ResourceLocation> soundEventTargets(ResourceLocation soundEvent) {
		return SOUND_EVENT_TARGETS.computeIfAbsent(soundEvent, TaczAnimationSoundEffects::loadSoundEventTargets);
	}

	private static Optional<ResourceLocation> displaySound(String gunId, String soundName) {
		return DISPLAY_SOUND_CACHE.computeIfAbsent(gunId + ":" + soundName, key -> loadDisplaySound(gunId, soundName));
	}

	private static Optional<ResourceLocation> loadDisplaySound(String gunId, String soundName) {
		ResourceLocation location = ResourceLocation.withDefaultNamespace("display/guns/" + gunId + "_display.json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
			JsonObject root = GsonHelper.parse(reader);
			JsonObject sounds = GsonHelper.getAsJsonObject(root, "sounds", null);
			if (sounds == null) {
				return Optional.empty();
			}

			String sound = GsonHelper.getAsString(sounds, soundName, "");
			if (sound.isEmpty()) {
				return Optional.empty();
			}

			ResourceLocation displaySound = ResourceLocation.parse(sound);
			ResourceLocation normalizedDisplaySound = ResourceLocation.withDefaultNamespace(displaySound.getPath());
			if (!soundEventTargets(normalizedDisplaySound).isEmpty()) {
				return Optional.of(normalizedDisplaySound);
			}
			return importedSoundEventForDisplayTarget(gunId, normalizedDisplaySound);
		} catch (Exception exception) {
			LOGGER.warn("Failed to load TACZ display sound {} from {}", soundName, location, exception);
			return Optional.empty();
		}
	}

	private static Optional<ResourceLocation> importedSoundEventForDisplayTarget(String gunId, ResourceLocation displaySound) {
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.withDefaultNamespace("sounds.json"))) {
			JsonObject root = GsonHelper.parse(reader);
			ResourceLocation best = null;
			int bestScore = Integer.MAX_VALUE;
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				ResourceLocation event = ResourceLocation.withDefaultNamespace(entry.getKey());
				if (!soundEventMatchesDisplayTarget(entry.getValue().getAsJsonObject(), displaySound)) {
					continue;
				}

				int score = importedSoundEventScore(gunId, event, displaySound);
				if (score < bestScore) {
					best = event;
					bestScore = score;
				}
			}
			return Optional.ofNullable(best);
		} catch (Exception exception) {
			LOGGER.warn("Failed to resolve imported TACZ display sound event for {}", displaySound, exception);
			return Optional.empty();
		}
	}

	private static boolean soundEventMatchesDisplayTarget(JsonObject event, ResourceLocation displaySound) {
		JsonArray sounds = GsonHelper.getAsJsonArray(event, "sounds", null);
		if (sounds == null) {
			return false;
		}

		for (JsonElement sound : sounds) {
			ResourceLocation target = soundTarget(sound);
			if (target != null && matchesDisplaySound(target, displaySound)) {
				return true;
			}
		}
		return false;
	}

	private static ResourceLocation soundTarget(JsonElement sound) {
		if (sound.isJsonPrimitive()) {
			return ResourceLocation.parse(sound.getAsString());
		}
		if (sound.isJsonObject()) {
			String name = GsonHelper.getAsString(sound.getAsJsonObject(), "name", "");
			if (!name.isEmpty()) {
				return ResourceLocation.parse(name);
			}
		}
		return null;
	}

	private static boolean matchesDisplaySound(ResourceLocation target, ResourceLocation displaySound) {
		String targetPath = target.getPath();
		String displayPath = displaySound.getPath();
		return target.equals(displaySound) || targetPath.equals(displayPath) || targetPath.endsWith("/" + displayPath);
	}

	private static int importedSoundEventScore(String gunId, ResourceLocation event, ResourceLocation displaySound) {
		String eventPath = event.getPath();
		if (eventPath.equals(displaySound.getPath())) {
			return 0;
		}
		if (eventPath.startsWith(gunId + ".")) {
			return 1;
		}
		if (eventPath.startsWith(gunId + "/")) {
			return 2;
		}
		return 3;
	}

	private static Set<ResourceLocation> loadSoundEventTargets(ResourceLocation soundEvent) {
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.withDefaultNamespace("sounds.json"))) {
			JsonObject root = GsonHelper.parse(reader);
			JsonObject event = GsonHelper.getAsJsonObject(root, soundEvent.getPath(), null);
			if (event == null) {
				return Set.of();
			}

			JsonArray sounds = GsonHelper.getAsJsonArray(event, "sounds", null);
			if (sounds == null) {
				return Set.of();
			}

			Set<ResourceLocation> targets = new HashSet<>();
			for (JsonElement sound : sounds) {
				ResourceLocation target = soundTarget(sound);
				if (target != null) {
					targets.add(target);
				}
			}
			return Set.copyOf(targets);
		} catch (Exception exception) {
			LOGGER.warn("Failed to resolve TACZ sound event targets for {}", soundEvent, exception);
			return Set.of();
		}
	}

	private static void playIfStillHolding(String gunId, SoundKeyframe keyframe) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		if (!(minecraft.player.getMainHandItem().getItem() instanceof TaczMvpGunItem gunItem) || !gunId.equals(gunItem.gunId())) {
			return;
		}

		minecraft.level
			.playLocalSound(
				minecraft.player,
				SoundEvent.createVariableRangeEvent(keyframe.sound()),
				SoundSource.PLAYERS,
				keyframe.volume(),
				keyframe.pitch()
			);
	}

	private static Map<String, List<SoundKeyframe>> animationSounds(String gunId) {
		return CACHE.computeIfAbsent(gunId, TaczAnimationSoundEffects::loadAnimationSounds);
	}

	private static Map<String, List<SoundKeyframe>> loadAnimationSounds(String gunId) {
		ResourceLocation location = ResourceLocation.withDefaultNamespace("animations/" + gunId + ".animation.json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
			JsonObject root = GsonHelper.parse(reader);
			JsonObject animations = GsonHelper.getAsJsonObject(root, "animations");
			Map<String, List<SoundKeyframe>> result = new HashMap<>();
			for (Map.Entry<String, JsonElement> animationEntry : animations.entrySet()) {
				JsonObject animation = animationEntry.getValue().getAsJsonObject();
				JsonObject effects = GsonHelper.getAsJsonObject(animation, "sound_effects", null);
				if (effects == null) {
					continue;
				}

				List<SoundKeyframe> keyframes = new ArrayList<>();
				for (Map.Entry<String, JsonElement> effectEntry : effects.entrySet()) {
					JsonObject effect = effectEntry.getValue().getAsJsonObject();
					String effectId = GsonHelper.getAsString(effect, "effect", "");
					if (effectId.isEmpty()) {
						continue;
					}

					ResourceLocation parsed = ResourceLocation.parse(effectId);
					keyframes.add(
						new SoundKeyframe(
							Float.parseFloat(effectEntry.getKey()),
							ResourceLocation.withDefaultNamespace(parsed.getPath()),
							GsonHelper.getAsFloat(effect, "volume", 1.0F),
							GsonHelper.getAsFloat(effect, "pitch", 1.0F)
						)
					);
				}
				keyframes.sort(Comparator.comparing(SoundKeyframe::timeSeconds));
				result.put(animationEntry.getKey(), List.copyOf(keyframes));
			}
			return Map.copyOf(result);
		} catch (Exception exception) {
			LOGGER.warn("Failed to load TACZ animation sound effects {}", location, exception);
			return Map.of();
		}
	}

	private static String gunId(ItemStack itemStack) {
		return itemStack.getItem() instanceof TaczMvpGunItem gunItem ? gunItem.gunId() : "glock_17";
	}

	private record SoundKeyframe(float timeSeconds, ResourceLocation sound, float volume, float pitch) {
	}
}
