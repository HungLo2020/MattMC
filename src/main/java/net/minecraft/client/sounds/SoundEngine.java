package net.minecraft.client.sounds;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.blaze3d.audio.Library;
import net.blaze3d.audio.ListenerTransform;
import net.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.RuledSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.SampledFloat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * The Java side of the Rust playback engine (`audio/policy`). Rust owns the
 * device thread and its recovery, voices, category volumes, the minimum
 * source lifetime, delayed and manual-loop scheduling, released-source
 * cleanup, pause/resume, the asset cache, subtitles, the once-per-event
 * warnings and the per-tick rules of the vanilla tickables. Java keeps the
 * {@link SoundInstance} objects gameplay code creates (resolution draws,
 * `canPlaySound`, entity samples, custom `tick()` bodies) and pack IO, and
 * identifies instances to Rust by numeric ID.
 */
@Environment(EnvType.CLIENT)
public class SoundEngine {
	private static final Marker MARKER = MarkerFactory.getMarker("SOUNDS");
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String MISSING_SOUND = "FOR THE DEBUG!";
	public static final String OPEN_AL_SOFT_PREFIX = "OpenAL Soft on ";
	public static final int OPEN_AL_SOFT_PREFIX_LENGTH = "OpenAL Soft on ".length();
	private static final RandomSource FACTOR_RANDOM = RandomSource.create();
	private final MusicManager musicManager;
	private final SoundManager soundManager;
	private final Options options;
	private final ResourceProvider resources;
	private boolean loaded;
	private long nextInstanceId = 1L;
	/** Instances the engine references (active or queued), by Rust ID. */
	private final Map<SoundInstance, Long> instanceIds = new IdentityHashMap<>();
	private final Long2ObjectOpenHashMap<SoundInstance> instancesById = new Long2ObjectOpenHashMap<>();
	private final Set<SoundInstance> activeInstances = Sets.newIdentityHashSet();
	private final List<TickableSoundInstance> tickingSounds = Lists.<TickableSoundInstance>newArrayList();
	private final List<TickableSoundInstance> queuedTickableSounds = Lists.<TickableSoundInstance>newArrayList();
	private final NativeSoundPolicy.TickingBatch tickingBatch = new NativeSoundPolicy.TickingBatch();
	private final RuledSoundInstance.Sample ruleSample = new RuledSoundInstance.Sample();
	private ListenerTransform listenerTransform = ListenerTransform.INITIAL;

	public SoundEngine(MusicManager musicManager, SoundManager soundManager, Options options, ResourceProvider resourceProvider) {
		this.musicManager = musicManager;
		this.soundManager = soundManager;
		this.options = options;
		this.resources = resourceProvider;
		NativeSoundPolicy.setAssetLoader(this::loadRequestedAssets);
	}

	public void reload() {
		List<String> missing = new ArrayList<>();
		for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
			if (soundEvent != SoundEvents.EMPTY) {
				ResourceLocation resourceLocation = soundEvent.location();
				if (this.soundManager.getSoundEvent(resourceLocation) == null) {
					LOGGER.warn("Missing sound for event: {}", BuiltInRegistries.SOUND_EVENT.getKey(soundEvent));
					missing.add(resourceLocation.toString());
				}
			}
		}

		NativeSoundPolicy.resetWarnings(missing);
		this.destroy();
		this.loadLibrary();
	}

	private synchronized void loadLibrary() {
		if (!this.loaded) {
			try {
				String string = this.options.soundDevice().get();
				String preferred = "".equals(string) ? null : string;
				boolean directionalAudio = this.options.directionalAudio().get();
				net.blaze3d.audio.DevAudioValidation.maybeRunIsolated(preferred, directionalAudio);
				String failure = NativeSoundPolicy.engineInit(preferred, directionalAudio);
				if (failure != null) {
					throw new IllegalStateException(failure);
				}
				this.pushCategoryVolumes(false);
				this.loaded = true;
				this.loadRequestedAssets();
				LOGGER.info(MARKER, "Sound engine started");
			} catch (RuntimeException var2) {
				LOGGER.error(MARKER, "Error starting SoundSystem. Turning off sounds & music", (Throwable)var2);
			}
		}
	}

	private void pushCategoryVolumes(boolean refresh) {
		SoundSource[] sources = SoundSource.values();
		float[] volumes = new float[sources.length];
		for (SoundSource source : sources) {
			volumes[source.ordinal()] = this.options.getFinalSoundSourceVolume(source);
		}
		NativeSoundPolicy.setCategoryVolumes(volumes, refresh);
	}

	/** Reads the asset files Rust requested off-thread and hands their bytes back. */
	private void loadRequestedAssets() {
		NativeSoundPolicy.AssetRequests requests = NativeSoundPolicy.takeAssetRequests();
		for (String path : requests.paths()) {
			long generation = requests.generation();
			CompletableFuture.runAsync(() -> {
				try (InputStream inputStream = this.resources.open(ResourceLocation.parse(path))) {
					NativeSoundPolicy.provideAsset(path, generation, inputStream.readAllBytes());
				} catch (IOException | RuntimeException exception) {
					LOGGER.warn(MARKER, "Failed to load sound {}", path, exception);
					NativeSoundPolicy.failAsset(path, generation);
				}
			}, Util.nonCriticalIoPool());
		}
	}

	public void updateCategoryVolume(SoundSource soundSource) {
		if (this.loaded) {
			if ((soundSource == SoundSource.MASTER || soundSource == SoundSource.MUSIC) && this.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0.0F) {
				this.musicManager.showNowPlayingToastIfNeeded();
			}

			this.pushCategoryVolumes(true);
		}
	}

	public void destroy() {
		if (this.loaded) {
			this.stopAll();
			NativeSoundPolicy.engineDestroy();
			this.loaded = false;
		}
	}

	public void emergencyShutdown() {
		if (this.loaded) {
			NativeSoundPolicy.engineEmergencyShutdown();
		}
	}

	public void stop(SoundInstance soundInstance) {
		if (this.loaded && this.activeInstances.contains(soundInstance)) {
			NativeSoundPolicy.stop(this.instanceIds.get(soundInstance));
		}
	}

	public void setVolume(SoundInstance soundInstance, float f) {
		if (this.loaded && this.activeInstances.contains(soundInstance)) {
			NativeSoundPolicy.setVolume(this.instanceIds.get(soundInstance), f, soundInstance.getVolume());
		}
	}

	public void stopAll() {
		if (this.loaded) {
			NativeSoundPolicy.stopAll();
			this.forgetAll();
		}
	}

	private void forgetAll() {
		this.instanceIds.clear();
		this.instancesById.clear();
		this.activeInstances.clear();
		this.tickingSounds.clear();
		this.queuedTickableSounds.clear();
	}

	public void tick(boolean bl) {
		int begin = NativeSoundPolicy.tickBegin(bl);
		if ((begin & NativeSoundPolicy.TICK_REOPENED) != 0) {
			// Rust reopened the output device: every instance was released.
			this.forgetAll();
			if ((begin & NativeSoundPolicy.TICK_STOPPED) != 0) {
				this.loaded = false;
			}
		}

		NativeSoundPolicy.loadAssetsIfRequested(begin);
		if (!bl) {
			this.tickInGameSound();
		}

		NativeSoundPolicy.loadAssetsIfRequested(NativeSoundPolicy.tickEnd(bl));
		this.forgetReleased();
		if (!bl) {
			for (long id : NativeSoundPolicy.takeDue()) {
				SoundInstance soundInstance = this.instancesById.get(id);
				if (soundInstance != null) {
					if (soundInstance instanceof TickableSoundInstance tickable) {
						tickable.tick();
					}

					this.play(soundInstance);
				}
			}
		}

		NativeSoundPolicy.deviceTick();
		this.logRustLines();
	}

	private void logRustLines() {
		for (String line : NativeSoundPolicy.takeLogs()) {
			String message = line.length() > 2 ? line.substring(2) : line;
			switch (line.charAt(0)) {
				case 'E' -> LOGGER.error(MARKER, message);
				case 'W' -> LOGGER.warn(MARKER, message);
				default -> LOGGER.info(MARKER, message);
			}
		}
	}

	private void tickInGameSound() {
		this.queuedTickableSounds.stream().filter(SoundInstance::canPlaySound).forEach(this::play);
		this.queuedTickableSounds.clear();

		for (TickableSoundInstance tickableSoundInstance : this.tickingSounds) {
			if (!tickableSoundInstance.canPlaySound()) {
				this.stop(tickableSoundInstance);
			}

			long id = this.instanceIds.get(tickableSoundInstance);
			if (tickableSoundInstance instanceof RuledSoundInstance ruled) {
				RuledSoundInstance.Sample sample = this.ruleSample;
				sample.clear();
				ruled.sampleSoundRule(sample);
				this.tickingBatch.addSample(id, sample.flags, sample.value, sample.x, sample.y, sample.z);
				continue;
			}

			tickableSoundInstance.tick();
			if (tickableSoundInstance.isStopped()) {
				this.stop(tickableSoundInstance);
			} else {
				this.tickingBatch.addPlain(
					id, tickableSoundInstance.getVolume(), tickableSoundInstance.getPitch(),
					tickableSoundInstance.getX(), tickableSoundInstance.getY(), tickableSoundInstance.getZ()
				);
			}
		}

		for (long outcome : this.tickingBatch.flush()) {
			long id = outcome & ~(NativeSoundPolicy.OUTCOME_STOPPED | NativeSoundPolicy.OUTCOME_SWITCHED);
			if (this.instancesById.get(id) instanceof RuledSoundInstance ruled) {
				if ((outcome & NativeSoundPolicy.OUTCOME_SWITCHED) != 0L) {
					ruled.onSoundRuleSwitched();
				}

				if ((outcome & NativeSoundPolicy.OUTCOME_STOPPED) != 0L) {
					ruled.markSoundRuleStopped();
				}
			}
		}
	}

	/** Drops released instances; requeued manual loops keep their object. */
	private void forgetReleased() {
		for (long released : NativeSoundPolicy.takeReleased()) {
			long id = released & ~NativeSoundPolicy.RELEASED_REQUEUED;
			SoundInstance soundInstance = this.instancesById.get(id);
			if (soundInstance == null) {
				continue;
			}

			this.activeInstances.remove(soundInstance);
			if (soundInstance instanceof TickableSoundInstance) {
				this.tickingSounds.remove(soundInstance);
			}

			if ((released & NativeSoundPolicy.RELEASED_REQUEUED) == 0L) {
				this.forget(id, soundInstance);
			}
		}
	}

	private long idFor(SoundInstance soundInstance) {
		Long existing = this.instanceIds.get(soundInstance);
		if (existing != null) {
			return existing;
		}

		long id = this.nextInstanceId++;
		this.instanceIds.put(soundInstance, id);
		this.instancesById.put(id, soundInstance);
		return id;
	}

	private void forget(long id, SoundInstance soundInstance) {
		this.instancesById.remove(id);
		this.instanceIds.remove(soundInstance);
	}

	public boolean isActive(SoundInstance soundInstance) {
		return this.loaded && this.activeInstances.contains(soundInstance);
	}

	public SoundEngine.PlayResult play(SoundInstance soundInstance) {
		if (!this.loaded) {
			return SoundEngine.PlayResult.NOT_STARTED;
		} else if (!soundInstance.canPlaySound()) {
			return SoundEngine.PlayResult.NOT_STARTED;
		}

		WeighedSoundEvents weighedSoundEvents = soundInstance.resolve(this.soundManager);
		ResourceLocation resourceLocation = soundInstance.getLocation();
		if (weighedSoundEvents == null) {
			if (NativeSoundPolicy.warnOnce(resourceLocation.toString())) {
				LOGGER.warn(MARKER, "Unable to play unknown soundEvent: {}", resourceLocation);
			}

			// DEBUG_SUBTITLES keeps going with a placeholder event, whose empty
			// sound then stops the play below.
			if (!SharedConstants.DEBUG_SUBTITLES) {
				return SoundEngine.PlayResult.NOT_STARTED;
			}
		}

		Sound sound = soundInstance.getSound();
		if (sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
			return SoundEngine.PlayResult.NOT_STARTED;
		} else if (sound == SoundManager.EMPTY_SOUND) {
			if (NativeSoundPolicy.warnOnce(resourceLocation.toString())) {
				LOGGER.warn(MARKER, "Unable to play empty soundEvent: {}", resourceLocation);
			}

			return SoundEngine.PlayResult.NOT_STARTED;
		}

		SoundInstance.Attenuation attenuation = soundInstance.getAttenuation();
		int flags = (attenuation == SoundInstance.Attenuation.LINEAR ? NativeSoundPolicy.PLAY_FLAG_LINEAR : 0)
			| (soundInstance.isRelative() ? NativeSoundPolicy.PLAY_FLAG_RELATIVE : 0)
			| (soundInstance.isLooping() ? NativeSoundPolicy.PLAY_FLAG_LOOPING : 0)
			| (sound.shouldStream() ? NativeSoundPolicy.PLAY_FLAG_STREAM : 0)
			| (soundInstance.canStartSilent() ? NativeSoundPolicy.PLAY_FLAG_CAN_START_SILENT : 0);
		NativeSoundPolicy.Rule rule = soundInstance instanceof RuledSoundInstance ruled
			? new NativeSoundPolicy.Rule(ruled.soundRule(), ruled.soundRuleParameter(), ruled.ruleBaseVolume(), ruled.ruleBasePitch(),
				factor(sound.getVolume()), factor(sound.getPitch()))
			: NativeSoundPolicy.NO_RULE;
		long id = this.idFor(soundInstance);
		int result = NativeSoundPolicy.loadAssetsIfRequested(NativeSoundPolicy.play(
			id, soundInstance.getX(), soundInstance.getY(), soundInstance.getZ(), soundInstance.getVolume(), soundInstance.getPitch(),
			soundInstance.getDelay(), sound.getAttenuationDistance(), soundInstance.getSource().ordinal(), flags, rule,
			resourceLocation.toString(), sound.getPath().toString()
		));
		SoundEngine.PlayResult playResult = SoundEngine.PlayResult.values()[result & 0xFF];
		if (playResult == SoundEngine.PlayResult.NOT_STARTED) {
			if (!NativeSoundPolicy.knows(id)) {
				this.forget(id, soundInstance);
			}

			return playResult;
		}

		this.activeInstances.add(soundInstance);
		if (soundInstance instanceof TickableSoundInstance tickable) {
			this.tickingSounds.add(tickable);
		}

		return playResult;
	}

	/** A resolved sound's volume or pitch factor (sounds.json values are constant). */
	private static float factor(SampledFloat value) {
		return value instanceof ConstantFloat constant ? constant.getValue() : value.sample(FACTOR_RANDOM);
	}

	/**
	 * A seeded world sound event (`ClientLevel.playSound`) that Rust resolves
	 * and plays, after `delay` ticks when positive.
	 */
	public void playEvent(ResourceLocation event, SoundSource source, float volume, float pitch, long seed, double x, double y, double z, int delay) {
		NativeSoundClient.playEvent(event.toString(), source.ordinal(), volume, pitch, seed, x, y, z, delay, NativeSoundClient.EVENT_FLAG_LINEAR);
	}

	public void queueTickingSound(TickableSoundInstance tickableSoundInstance) {
		this.queuedTickableSounds.add(tickableSoundInstance);
	}

	public void pauseAllExcept(SoundSource... soundSources) {
		if (this.loaded) {
			int kept = 0;
			for (SoundSource soundSource : soundSources) {
				kept |= 1 << soundSource.ordinal();
			}

			NativeSoundPolicy.pauseAllExcept(kept);
		}
	}

	public void resume() {
		if (this.loaded) {
			NativeSoundPolicy.resume();
		}
	}

	public void playDelayed(SoundInstance soundInstance, int i) {
		NativeSoundPolicy.playDelayed(this.idFor(soundInstance), i);
	}

	public void updateSource(Camera camera) {
		if (this.loaded && camera.isInitialized()) {
			Vec3 position = camera.getPosition();
			Vec3 forward = new Vec3(camera.getLookVector());
			Vec3 up = new Vec3(camera.getUpVector());
			this.listenerTransform = new ListenerTransform(position, forward, up);
			NativeSoundPolicy.listener(position.x, position.y, position.z, forward.x, forward.y, forward.z, up.x, up.y, up.z);
		}
	}

	public void stop(@Nullable ResourceLocation resourceLocation, @Nullable SoundSource soundSource) {
		if (soundSource == null && resourceLocation == null) {
			this.stopAll();
		} else {
			NativeSoundPolicy.stopMatching(resourceLocation == null ? null : resourceLocation.toString(), soundSource == null ? -1 : soundSource.ordinal());
		}
	}

	public String getDebugString() {
		int[] counts = NativeSoundPolicy.poolCounts();
		return String.format(Locale.ROOT, "Sounds: %d/%d + %d/%d", counts[0], counts[1], counts[2], counts[3]);
	}

	public List<String> getAvailableSoundDevices() {
		return Library.availableSoundDevices();
	}

	public ListenerTransform getListenerTransform() {
		return this.listenerTransform;
	}

	@Environment(EnvType.CLIENT)
	public static enum PlayResult {
		STARTED,
		STARTED_SILENTLY,
		NOT_STARTED;
	}
}
