package net.minecraft.client.sounds;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.blaze3d.audio.Library;
import net.blaze3d.audio.ListenerTransform;
import net.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
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
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * The Java side of the Rust playback engine (`audio/policy`). Rust owns the
 * device thread, voices, category volumes, the minimum source lifetime,
 * delayed and manual-loop scheduling, released-source cleanup, pause/resume,
 * device-change detection and the asset cache. Java keeps the
 * {@link SoundInstance} objects (resolution draws, per-tick behavior),
 * listeners and pack IO, and identifies instances to Rust by numeric ID.
 */
@Environment(EnvType.CLIENT)
public class SoundEngine {
	private static final Marker MARKER = MarkerFactory.getMarker("SOUNDS");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<ResourceLocation> ONLY_WARN_ONCE = Sets.<ResourceLocation>newHashSet();
	public static final String MISSING_SOUND = "FOR THE DEBUG!";
	public static final String OPEN_AL_SOFT_PREFIX = "OpenAL Soft on ";
	public static final int OPEN_AL_SOFT_PREFIX_LENGTH = "OpenAL Soft on ".length();
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
	private final List<SoundEventListener> listeners = Lists.<SoundEventListener>newArrayList();
	private final List<TickableSoundInstance> queuedTickableSounds = Lists.<TickableSoundInstance>newArrayList();
	private final NativeSoundPolicy.TickingBatch tickingBatch = new NativeSoundPolicy.TickingBatch();
	private ListenerTransform listenerTransform = ListenerTransform.INITIAL;

	public SoundEngine(MusicManager musicManager, SoundManager soundManager, Options options, ResourceProvider resourceProvider) {
		this.musicManager = musicManager;
		this.soundManager = soundManager;
		this.options = options;
		this.resources = resourceProvider;
	}

	public void reload() {
		ONLY_WARN_ONCE.clear();

		for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
			if (soundEvent != SoundEvents.EMPTY) {
				ResourceLocation resourceLocation = soundEvent.location();
				if (this.soundManager.getSoundEvent(resourceLocation) == null) {
					LOGGER.warn("Missing sound for event: {}", BuiltInRegistries.SOUND_EVENT.getKey(soundEvent));
					ONLY_WARN_ONCE.add(resourceLocation);
				}
			}
		}

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
			this.instanceIds.clear();
			this.instancesById.clear();
			this.activeInstances.clear();
			this.tickingSounds.clear();
			this.queuedTickableSounds.clear();
		}
	}

	public void addEventListener(SoundEventListener soundEventListener) {
		this.listeners.add(soundEventListener);
	}

	public void removeEventListener(SoundEventListener soundEventListener) {
		this.listeners.remove(soundEventListener);
	}

	public void tick(boolean bl) {
		if (NativeSoundPolicy.tickBegin(bl)) {
			this.reload();
		}

		if (!bl) {
			this.tickInGameSound();
		}

		NativeSoundPolicy.tickEnd(bl);
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
	}

	private void tickInGameSound() {
		this.queuedTickableSounds.stream().filter(SoundInstance::canPlaySound).forEach(this::play);
		this.queuedTickableSounds.clear();

		for (TickableSoundInstance tickableSoundInstance : this.tickingSounds) {
			if (!tickableSoundInstance.canPlaySound()) {
				this.stop(tickableSoundInstance);
			}

			tickableSoundInstance.tick();
			if (tickableSoundInstance.isStopped()) {
				this.stop(tickableSoundInstance);
			} else {
				this.tickingBatch.add(
					this.instanceIds.get(tickableSoundInstance), tickableSoundInstance.getVolume(), tickableSoundInstance.getPitch(),
					tickableSoundInstance.getX(), tickableSoundInstance.getY(), tickableSoundInstance.getZ()
				);
			}
		}

		this.tickingBatch.flush();
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
		} else {
			WeighedSoundEvents weighedSoundEvents = soundInstance.resolve(this.soundManager);
			ResourceLocation resourceLocation = soundInstance.getLocation();
			if (weighedSoundEvents == null) {
				if (ONLY_WARN_ONCE.add(resourceLocation)) {
					LOGGER.warn(MARKER, "Unable to play unknown soundEvent: {}", resourceLocation);
				}

				if (!SharedConstants.DEBUG_SUBTITLES) {
					return SoundEngine.PlayResult.NOT_STARTED;
				}

				weighedSoundEvents = new WeighedSoundEvents(resourceLocation, "FOR THE DEBUG!");
			}

			Sound sound = soundInstance.getSound();
			if (sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
				return SoundEngine.PlayResult.NOT_STARTED;
			} else if (sound == SoundManager.EMPTY_SOUND) {
				if (ONLY_WARN_ONCE.add(resourceLocation)) {
					LOGGER.warn(MARKER, "Unable to play empty soundEvent: {}", resourceLocation);
				}

				return SoundEngine.PlayResult.NOT_STARTED;
			} else {
				float f = soundInstance.getVolume();
				float g = Math.max(f, 1.0F) * sound.getAttenuationDistance();
				SoundSource soundSource = soundInstance.getSource();
				float pitch = soundInstance.getPitch();
				SoundInstance.Attenuation attenuation = soundInstance.getAttenuation();
				boolean bl = soundInstance.isRelative();
				if (!this.listeners.isEmpty()) {
					float j = !bl && attenuation != SoundInstance.Attenuation.NONE ? g : Float.POSITIVE_INFINITY;

					for (SoundEventListener soundEventListener : this.listeners) {
						soundEventListener.onPlaySound(soundInstance, weighedSoundEvents, j);
					}
				}

				int flags = (attenuation == SoundInstance.Attenuation.LINEAR ? NativeSoundPolicy.PLAY_FLAG_LINEAR : 0)
					| (bl ? NativeSoundPolicy.PLAY_FLAG_RELATIVE : 0)
					| (soundInstance.isLooping() ? NativeSoundPolicy.PLAY_FLAG_LOOPING : 0)
					| (sound.shouldStream() ? NativeSoundPolicy.PLAY_FLAG_STREAM : 0)
					| (soundInstance.canStartSilent() ? NativeSoundPolicy.PLAY_FLAG_CAN_START_SILENT : 0);
				long id = this.idFor(soundInstance);
				int result = NativeSoundPolicy.play(
					id, (float)soundInstance.getX(), (float)soundInstance.getY(), (float)soundInstance.getZ(), f, pitch,
					soundInstance.getDelay(), sound.getAttenuationDistance(), soundSource.ordinal(), flags,
					resourceLocation.toString(), sound.getPath().toString()
				);
				if ((result & NativeSoundPolicy.PLAY_ASSET_REQUESTS) != 0) {
					this.loadRequestedAssets();
				}

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
		}
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
