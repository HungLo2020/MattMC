package net.minecraft.tags;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.NativeRegistrySnapshot;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;

public class TagNetworkSerialization {
	/** Each network-safe registry's tags as ID lists, from Rust's registry snapshot. */
	public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializeTagsToNetwork(
		LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess
	) {
		RegistryAccess snapshot = layeredRegistryAccess.compositeAccess();
		Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> result = new HashMap<>();
		RegistrySynchronization.networkSafeRegistries(layeredRegistryAccess).forEach(registryEntry -> {
			Map<ResourceLocation, IntList> tags = NativeRegistrySnapshot.networkTags(snapshot, registryEntry.key());
			if (!tags.isEmpty()) {
				result.put(registryEntry.key(), new TagNetworkSerialization.NetworkPayload(tags));
			}
		});
		return result;
	}

	static <T> TagLoader.LoadResult<T> deserializeTagsFromNetwork(Registry<T> registry, TagNetworkSerialization.NetworkPayload networkPayload) {
		ResourceKey<? extends Registry<T>> resourceKey = registry.key();
		Map<TagKey<T>, List<Holder<T>>> map = new HashMap();
		networkPayload.tags.forEach((resourceLocation, intList) -> {
			TagKey<T> tagKey = TagKey.create(resourceKey, resourceLocation);
			List<Holder<T>> list = intList.intStream()
				.mapToObj(registry::get)
				.flatMap(Optional::stream)
				.map(ref -> (Holder<T>) ref)
				.collect(Collectors.toUnmodifiableList());
			map.put(tagKey, list);
		});
		return new TagLoader.LoadResult<>(resourceKey, map);
	}

	public static final class NetworkPayload {
		public static final TagNetworkSerialization.NetworkPayload EMPTY = new TagNetworkSerialization.NetworkPayload(Map.of());
		final Map<ResourceLocation, IntList> tags;

		NetworkPayload(Map<ResourceLocation, IntList> map) {
			this.tags = map;
		}

		public void write(FriendlyByteBuf friendlyByteBuf) {
			friendlyByteBuf.writeMap(this.tags, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeIntIdList);
		}

		public static TagNetworkSerialization.NetworkPayload read(FriendlyByteBuf friendlyByteBuf) {
			return new TagNetworkSerialization.NetworkPayload(friendlyByteBuf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readIntIdList));
		}

		public boolean isEmpty() {
			return this.tags.isEmpty();
		}

		public int size() {
			return this.tags.size();
		}

		public <T> TagLoader.LoadResult<T> resolve(Registry<T> registry) {
			return TagNetworkSerialization.deserializeTagsFromNetwork(registry, this);
		}
	}
}
