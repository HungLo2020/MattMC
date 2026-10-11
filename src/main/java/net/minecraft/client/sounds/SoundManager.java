package net.minecraft.client.sounds;

import net.blaze3d.audio.ListenerTransform;
import net.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SoundManager extends SimplePreparableReloadListener<SoundManager.Preparations> {
	public static final ResourceLocation EMPTY_SOUND_LOCATION = ResourceLocation.withDefaultNamespace("empty");
	public static final Sound EMPTY_SOUND = new Sound(EMPTY_SOUND_LOCATION, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, false, 16);
	public static final ResourceLocation INTENTIONALLY_EMPTY_SOUND_LOCATION = ResourceLocation.withDefaultNamespace("intentionally_empty");
	public static final WeighedSoundEvents INTENTIONALLY_EMPTY_SOUND_EVENT = new WeighedSoundEvents(INTENTIONALLY_EMPTY_SOUND_LOCATION, null);
	public static final Sound INTENTIONALLY_EMPTY_SOUND = new Sound(
		INTENTIONALLY_EMPTY_SOUND_LOCATION, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, false, 16
	);
	static final Logger LOGGER = LogUtils.getLogger();
	private static final String SOUNDS_PATH = "sounds.json";
	private static final AtomicLong REGISTRY_GENERATION = new AtomicLong();
	/** A built registry awaiting apply; a newer prepare discards an unapplied one. */
	private static final AtomicLong PENDING_REGISTRY = new AtomicLong();
	/** Event views of the installed Rust registry (empty Optional: no such event). */
	private final Map<ResourceLocation, Optional<WeighedSoundEvents>> registry = new ConcurrentHashMap<>();
	private Set<ResourceLocation> availableSounds = Set.of();
	private final SoundEngine soundEngine;
	private final Map<ResourceLocation, Resource> soundCache = new HashMap();

	public SoundManager(Options options, MusicManager musicManager) {
		this.soundEngine = new SoundEngine(musicManager, this, options, ResourceProvider.fromMap(this.soundCache));
	}

	protected SoundManager.Preparations prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		SoundManager.Preparations preparations = new SoundManager.Preparations();
		Zone zone = profilerFiller.zone("list");

		try {
			preparations.listResources(resourceManager);
		} catch (Throwable var17) {
			if (zone != null) {
				try {
					zone.close();
				} catch (Throwable var14) {
					var17.addSuppressed(var14);
				}
			}

			throw var17;
		}

		if (zone != null) {
			zone.close();
		}

		for (String string : resourceManager.getNamespaces()) {
			try (Zone zone2 = profilerFiller.zone(string)) {
				for (Resource resource : resourceManager.getResourceStack(ResourceLocation.fromNamespaceAndPath(string, "sounds.json"))) {
					profilerFiller.push(resource.sourcePackId());

					try (Reader reader = resource.openAsReader()) {
						preparations.resources.add(new NativeSoundPolicy.Resource(string, resource.sourcePackId(), reader.readAllAsString()));
					} catch (IOException | RuntimeException var19) {
						LOGGER.warn("Invalid {} in resourcepack: '{}'", "sounds.json", resource.sourcePackId(), var19);
					}

					profilerFiller.pop();
				}
			}
		}

		preparations.build();
		return preparations;
	}

	protected void apply(SoundManager.Preparations preparations, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		preparations.apply(this.soundCache);
		this.registry.clear();
		this.availableSounds = NativeSoundPolicy.eventNames().stream().map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet());
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			for (ResourceLocation resourceLocation : this.availableSounds) {
				WeighedSoundEvents weighedSoundEvents = this.getSoundEvent(resourceLocation);
				if (!ComponentUtils.isTranslationResolvable(weighedSoundEvents.getSubtitle()) && BuiltInRegistries.SOUND_EVENT.containsKey(resourceLocation)) {
					LOGGER.error("Missing subtitle {} for sound event: {}", weighedSoundEvents.getSubtitle(), resourceLocation);
				}
			}
		}

		if (LOGGER.isDebugEnabled()) {
			for (ResourceLocation resourceLocationx : this.availableSounds) {
				if (!BuiltInRegistries.SOUND_EVENT.containsKey(resourceLocationx)) {
					LOGGER.debug("Not having sound event for: {}", resourceLocationx);
				}
			}
		}

		this.soundEngine.reload();
	}

	public List<String> getAvailableSoundDevices() {
		return this.soundEngine.getAvailableSoundDevices();
	}

	public ListenerTransform getListenerTransform() {
		return this.soundEngine.getListenerTransform();
	}

	@Nullable
	public WeighedSoundEvents getSoundEvent(ResourceLocation resourceLocation) {
		return this.registry.computeIfAbsent(resourceLocation, location -> {
			NativeSoundPolicy.EventInfo info = NativeSoundPolicy.lookupEvent(location.toString());
			return info == null ? Optional.empty() : Optional.of(WeighedSoundEvents.registered(location, info.subtitle()));
		}).orElse(null);
	}

	public Collection<ResourceLocation> getAvailableSounds() {
		return this.availableSounds;
	}

	public void queueTickingSound(TickableSoundInstance tickableSoundInstance) {
		this.soundEngine.queueTickingSound(tickableSoundInstance);
	}

	public SoundEngine.PlayResult play(SoundInstance soundInstance) {
		return this.soundEngine.play(soundInstance);
	}

	public void playDelayed(SoundInstance soundInstance, int i) {
		this.soundEngine.playDelayed(soundInstance, i);
	}

	public void updateSource(Camera camera) {
		this.soundEngine.updateSource(camera);
	}

	public void pauseAllExcept(SoundSource... soundSources) {
		this.soundEngine.pauseAllExcept(soundSources);
	}

	public void stop() {
		this.soundEngine.stopAll();
	}

	public void destroy() {
		this.soundEngine.destroy();
	}

	public void emergencyShutdown() {
		this.soundEngine.emergencyShutdown();
	}

	public void tick(boolean bl) {
		this.soundEngine.tick(bl);
	}

	public void resume() {
		this.soundEngine.resume();
	}

	public void updateSourceVolume(SoundSource soundSource) {
		this.soundEngine.updateCategoryVolume(soundSource);
	}

	public void stop(SoundInstance soundInstance) {
		this.soundEngine.stop(soundInstance);
	}

	public void setVolume(SoundInstance soundInstance, float f) {
		this.soundEngine.setVolume(soundInstance, f);
	}

	public boolean isActive(SoundInstance soundInstance) {
		return this.soundEngine.isActive(soundInstance);
	}

	public void addListener(SoundEventListener soundEventListener) {
		this.soundEngine.addEventListener(soundEventListener);
	}

	public void removeListener(SoundEventListener soundEventListener) {
		this.soundEngine.removeEventListener(soundEventListener);
	}

	public void stop(@Nullable ResourceLocation resourceLocation, @Nullable SoundSource soundSource) {
		this.soundEngine.stop(resourceLocation, soundSource);
	}

	public String getDebugString() {
		return this.soundEngine.getDebugString();
	}

	public void reload() {
		this.soundEngine.reload();
	}

	/**
	 * The reload's `sounds.json` texts and sound file listing; Rust parses them
	 * into a registry off the main thread, installed on apply.
	 */
	@Environment(EnvType.CLIENT)
	protected static class Preparations {
		final List<NativeSoundPolicy.Resource> resources = new ArrayList<>();
		private Map<ResourceLocation, Resource> soundCache = Map.of();
		private long handle;

		void listResources(ResourceManager resourceManager) {
			this.soundCache = Sound.SOUND_LISTER.listMatchingResources(resourceManager);
		}

		void build() {
			String files = this.soundCache.keySet().stream().map(ResourceLocation::toString).collect(Collectors.joining("\n"));
			this.handle = NativeSoundPolicy.buildRegistry(REGISTRY_GENERATION.incrementAndGet(), this.resources, files);
			long abandoned = PENDING_REGISTRY.getAndSet(this.handle);
			if (abandoned != 0L) {
				NativeSoundPolicy.discardRegistry(abandoned);
			}
		}

		public void apply(Map<ResourceLocation, Resource> map2) {
			for (String warning : NativeSoundPolicy.registryWarnings(this.handle)) {
				SoundManager.LOGGER.warn(warning);
			}
			if (PENDING_REGISTRY.compareAndSet(this.handle, 0L)) {
				NativeSoundPolicy.installRegistry(this.handle);
			}
			this.handle = 0L;
			map2.clear();
			map2.putAll(this.soundCache);
		}
	}
}
