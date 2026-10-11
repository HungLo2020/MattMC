package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.KnownPack;

public class RegistrySynchronization {
	private static final Set<ResourceKey<? extends Registry<?>>> NETWORKABLE_REGISTRIES = RegistryDataLoader.SYNCHRONIZED_REGISTRIES
		.stream()
		.map(RegistryDataLoader.RegistryData::key)
		.<ResourceKey<? extends Registry<?>>>map(key -> key)
		.collect(Collectors.toUnmodifiableSet());

	/**
	 * Packs every synchronized registry for a client that has `set`'s packs:
	 * Rust's registry snapshot decides which elements the client already
	 * knows, so only their keys are sent; the rest are encoded here.
	 */
	public static void packRegistries(
		DynamicOps<Tag> dynamicOps,
		LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess,
		Set<KnownPack> set,
		BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> biConsumer
	) {
		RegistryAccess registryAccess = layeredRegistryAccess.getAccessFrom(RegistryLayer.WORLDGEN);
		RegistryAccess snapshot = layeredRegistryAccess.compositeAccess();
		RegistryDataLoader.SYNCHRONIZED_REGISTRIES.forEach(
			registryData -> packRegistry(dynamicOps, registryData, registryAccess, snapshot, set, biConsumer)
		);
	}

	private static <T> void packRegistry(
		DynamicOps<Tag> dynamicOps,
		RegistryDataLoader.RegistryData<T> registryData,
		RegistryAccess registryAccess,
		RegistryAccess snapshot,
		Set<KnownPack> set,
		BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> biConsumer
	) {
		registryAccess.lookup(registryData.key()).ifPresent(registry -> {
			boolean[] known = NativeRegistrySnapshot.knownElements(snapshot, registry.key(), set);
			if (known.length != registry.size()) {
				throw new IllegalStateException("Registry snapshot of " + registry.key() + " is stale");
			}
			List<RegistrySynchronization.PackedRegistryEntry> list = new ArrayList<>(registry.size());
			for (int id = 0; id < known.length; id++) {
				Holder.Reference<T> reference = registry.get(id).orElseThrow();
				Optional<Tag> optional;
				if (known[id]) {
					optional = Optional.empty();
				} else {
					Tag tag = registryData.elementCodec()
						.encodeStart(dynamicOps, reference.value())
						.getOrThrow(string -> new IllegalArgumentException("Failed to serialize " + reference.key() + ": " + string));
					optional = Optional.of(tag);
				}
				list.add(new RegistrySynchronization.PackedRegistryEntry(reference.key().location(), optional));
			}
			biConsumer.accept(registry.key(), list);
		});
	}

	private static Stream<RegistryAccess.RegistryEntry<?>> ownedNetworkableRegistries(RegistryAccess registryAccess) {
		return registryAccess.registries().filter(registryEntry -> isNetworkable(registryEntry.key()));
	}

	public static Stream<RegistryAccess.RegistryEntry<?>> networkedRegistries(LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess) {
		return ownedNetworkableRegistries(layeredRegistryAccess.getAccessFrom(RegistryLayer.WORLDGEN));
	}

	public static Stream<RegistryAccess.RegistryEntry<?>> networkSafeRegistries(LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess) {
		Stream<RegistryAccess.RegistryEntry<?>> stream = layeredRegistryAccess.getLayer(RegistryLayer.STATIC).registries();
		Stream<RegistryAccess.RegistryEntry<?>> stream2 = networkedRegistries(layeredRegistryAccess);
		return Stream.concat(stream2, stream);
	}

	public static boolean isNetworkable(ResourceKey<? extends Registry<?>> resourceKey) {
		return NETWORKABLE_REGISTRIES.contains(resourceKey);
	}

	public record PackedRegistryEntry(ResourceLocation id, Optional<Tag> data) {
		public static final StreamCodec<ByteBuf, RegistrySynchronization.PackedRegistryEntry> STREAM_CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC,
			RegistrySynchronization.PackedRegistryEntry::id,
			ByteBufCodecs.TAG.apply(ByteBufCodecs::optional),
			RegistrySynchronization.PackedRegistryEntry::data,
			RegistrySynchronization.PackedRegistryEntry::new
		);
	}
}
