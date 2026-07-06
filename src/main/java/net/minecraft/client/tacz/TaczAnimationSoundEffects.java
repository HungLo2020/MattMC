package net.minecraft.client.tacz;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		schedule(gunId(itemStack), animationName, offsetSeconds);
	}

	private static void schedule(String gunId, String animationName, float offsetSeconds) {
		if (animationName == null || animationName.isEmpty()) {
			return;
		}
		for (SoundKeyframe keyframe : animationSounds(gunId).getOrDefault(animationName, List.of())) {
			long delayMillis = Math.max(0L, Math.round((offsetSeconds + keyframe.timeSeconds()) * 1000.0F));
			if (delayMillis == 0L) {
				Minecraft.getInstance().execute(() -> playIfStillHolding(gunId, keyframe));
			} else {
				EXECUTOR.schedule(() -> Minecraft.getInstance().execute(() -> playIfStillHolding(gunId, keyframe)), delayMillis, TimeUnit.MILLISECONDS);
			}
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
