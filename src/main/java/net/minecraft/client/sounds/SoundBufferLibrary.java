package net.minecraft.client.sounds;

import com.google.common.collect.Maps;
import net.blaze3d.audio.NativeAudioAsset;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

@Environment(EnvType.CLIENT)
public class SoundBufferLibrary {
	private final ResourceProvider resourceManager;
	private final Map<ResourceLocation, CompletableFuture<NativeAudioAsset>> cache = Maps.<ResourceLocation, CompletableFuture<NativeAudioAsset>>newHashMap();
	private volatile long assetGeneration = 1L;

	public SoundBufferLibrary(ResourceProvider resourceProvider) {
		this.resourceManager = resourceProvider;
	}

	public CompletableFuture<NativeAudioAsset> getCompleteAsset(ResourceLocation resourceLocation) {
		long generation = this.assetGeneration;
		return (CompletableFuture<NativeAudioAsset>)this.cache.computeIfAbsent(resourceLocation, resourceLocationx -> CompletableFuture.supplyAsync(() -> {
			try (InputStream inputStream = this.resourceManager.open(resourceLocationx)) {
				byte[] encoded = inputStream.readAllBytes();
				if (generation != this.assetGeneration) {
					throw new IOException("Skipped stale native audio asset registration for " + resourceLocationx);
				}

				return NativeAudioAsset.create(encoded, resourceLocationx.toString(), generation);
			} catch (IOException var5) {
				throw new CompletionException(var5);
			}
		}, Util.nonCriticalIoPool()));
	}

	public CompletableFuture<AudioStream> getStream(ResourceLocation resourceLocation, boolean bl) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				InputStream inputStream = this.resourceManager.open(resourceLocation);
				return (AudioStream)(bl ? new LoopingAudioStream(JOrbisAudioStream::new, inputStream) : new JOrbisAudioStream(inputStream));
			} catch (IOException var4) {
				throw new CompletionException(var4);
			}
		}, Util.nonCriticalIoPool());
	}

	public void clear() {
		long oldGeneration = this.assetGeneration++;
		this.cache.values().forEach(completableFuture -> completableFuture.thenAccept(NativeAudioAsset::close));
		this.cache.clear();
		NativeAudioAsset.destroyGeneration(oldGeneration);
	}

	public CompletableFuture<?> preload(Collection<Sound> collection) {
		List<CompletableFuture<?>> futures = new ArrayList<>();
		for (Sound sound : collection) {
			futures.add(this.getCompleteAsset(sound.getPath()));
		}

		return CompletableFuture.allOf((CompletableFuture[])futures.toArray(CompletableFuture[]::new));
	}
}
