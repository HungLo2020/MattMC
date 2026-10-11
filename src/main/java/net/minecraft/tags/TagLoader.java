package net.minecraft.tags;

import net.logging.LogUtils;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class TagLoader<T> {
	private static final Logger LOGGER = LogUtils.getLogger();
	final TagLoader.ElementLookup<T> elementLookup;
	private final String directory;

	public TagLoader(TagLoader.ElementLookup<T> elementLookup, String string) {
		this.elementLookup = elementLookup;
		this.directory = string;
	}

	/**
	 * Reads this directory's tag file stacks; Rust parses them (`TagFile`,
	 * `replace`, strict JSON). Unreadable files are logged here.
	 */
	public TagLoader.Loaded load(ResourceManager resourceManager) {
		FileToIdConverter fileToIdConverter = FileToIdConverter.json(this.directory);
		NativeTags.Records records = new NativeTags.Records();
		for (Entry<ResourceLocation, List<Resource>> entry : fileToIdConverter.listMatchingResourceStacks(resourceManager).entrySet()) {
			ResourceLocation resourceLocation = (ResourceLocation)entry.getKey();
			ResourceLocation resourceLocation2 = fileToIdConverter.fileToId(resourceLocation);
			for (Resource resource : entry.getValue()) {
				byte[] bytes;
				try (InputStream inputStream = resource.open()) {
					bytes = inputStream.readAllBytes();
				} catch (Exception var17) {
					LOGGER.error("Couldn't read tag list {} from {} in data pack {}", resourceLocation2, resourceLocation, resource.sourcePackId(), var17);
					continue;
				}
				records.add(resourceLocation, resourceLocation2, resource.sourcePackId(), bytes);
			}
		}
		return new TagLoader.Loaded(NativeTags.load(records));
	}

	/**
	 * Resolves loaded tags in `DependencySorter` order (Rust), looking each
	 * element up in Java's call order; tags missing required references are
	 * logged and left out.
	 */
	public Map<ResourceLocation, List<T>> build(TagLoader.Loaded loaded) {
		List<NativeTags.Lookup> lookups = NativeTags.plan(loaded.handle);
		Object[] values = new Object[lookups.size()];
		boolean[] found = new boolean[lookups.size()];
		for (int index = 0; index < values.length; index++) {
			NativeTags.Lookup lookup = lookups.get(index);
			values[index] = this.elementLookup.get(lookup.id(), lookup.required()).orElse(null);
			found[index] = values[index] != null;
		}
		Map<ResourceLocation, List<T>> map = new HashMap();
		for (NativeTags.Built built : NativeTags.resolve(loaded.handle, found)) {
			if (built.values() == null) {
				LOGGER.error("Couldn't load tag {} as it is missing following references: {}", built.id(), String.join(", ", built.missing()));
				continue;
			}
			SequencedSet<T> sequencedSet = new LinkedHashSet();
			for (int value : built.values()) {
				sequencedSet.add((T)values[value]);
			}
			map.put(built.id(), List.copyOf(sequencedSet));
		}
		return map;
	}

	/** Parsed tag files owned by Rust until built. */
	public static final class Loaded {
		private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
		final long handle;

		Loaded(long handle) {
			this.handle = handle;
			CLEANER.register(this, () -> NativeTags.release(handle));
		}
	}

	public static <T> void loadTagsFromNetwork(TagNetworkSerialization.NetworkPayload networkPayload, WritableRegistry<T> writableRegistry) {
		networkPayload.resolve(writableRegistry).tags.forEach(writableRegistry::bindTag);
	}

	public static List<Registry.PendingTags<?>> loadTagsForExistingRegistries(ResourceManager resourceManager, RegistryAccess registryAccess) {
		return registryAccess.registries()
			.map(registryEntry -> loadPendingTags(resourceManager, registryEntry.value()))
			.flatMap(Optional::stream)
			.collect(Collectors.toUnmodifiableList());
	}

	public static <T> void loadTagsForRegistry(ResourceManager resourceManager, WritableRegistry<T> writableRegistry) {
		ResourceKey<? extends Registry<T>> resourceKey = writableRegistry.key();
		TagLoader<Holder<T>> tagLoader = new TagLoader<>(TagLoader.ElementLookup.fromWritableRegistry(writableRegistry), Registries.tagsDirPath(resourceKey));
		tagLoader.build(tagLoader.load(resourceManager))
			.forEach((resourceLocation, list) -> writableRegistry.bindTag(TagKey.create(resourceKey, resourceLocation), list));
	}

	private static <T> Map<TagKey<T>, List<Holder<T>>> wrapTags(ResourceKey<? extends Registry<T>> resourceKey, Map<ResourceLocation, List<Holder<T>>> map) {
		return (Map<TagKey<T>, List<Holder<T>>>)map.entrySet()
			.stream()
			.collect(Collectors.toUnmodifiableMap(entry -> TagKey.create(resourceKey, (ResourceLocation)entry.getKey()), Entry::getValue));
	}

	private static <T> Optional<Registry.PendingTags<T>> loadPendingTags(ResourceManager resourceManager, Registry<T> registry) {
		ResourceKey<? extends Registry<T>> resourceKey = registry.key();
		TagLoader<Holder<T>> tagLoader = new TagLoader<>(
			(TagLoader.ElementLookup<Holder<T>>)TagLoader.ElementLookup.fromFrozenRegistry(registry), Registries.tagsDirPath(resourceKey)
		);
		TagLoader.LoadResult<T> loadResult = new TagLoader.LoadResult<>(resourceKey, wrapTags(registry.key(), tagLoader.build(tagLoader.load(resourceManager))));
		return loadResult.tags().isEmpty() ? Optional.empty() : Optional.of(registry.prepareTagReload(loadResult));
	}

	public static List<HolderLookup.RegistryLookup<?>> buildUpdatedLookups(RegistryAccess.Frozen frozen, List<Registry.PendingTags<?>> list) {
		List<HolderLookup.RegistryLookup<?>> list2 = new ArrayList();
		frozen.registries().forEach(registryEntry -> {
			Registry.PendingTags<?> pendingTags = findTagsForRegistry(list, registryEntry.key());
			list2.add(pendingTags != null ? pendingTags.lookup() : registryEntry.value());
		});
		return list2;
	}

	@Nullable
	private static Registry.PendingTags<?> findTagsForRegistry(List<Registry.PendingTags<?>> list, ResourceKey<? extends Registry<?>> resourceKey) {
		for (Registry.PendingTags<?> pendingTags : list) {
			if (pendingTags.key() == resourceKey) {
				return pendingTags;
			}
		}

		return null;
	}

	public interface ElementLookup<T> {
		Optional<? extends T> get(ResourceLocation resourceLocation, boolean bl);

		static <T> TagLoader.ElementLookup<? extends Holder<T>> fromFrozenRegistry(Registry<T> registry) {
			return (resourceLocation, bl) -> registry.get(resourceLocation);
		}

		static <T> TagLoader.ElementLookup<Holder<T>> fromWritableRegistry(WritableRegistry<T> writableRegistry) {
			HolderGetter<T> holderGetter = writableRegistry.createRegistrationLookup();
			return (resourceLocation, bl) -> ((HolderGetter<T>)(bl ? holderGetter : writableRegistry)).get(ResourceKey.create(writableRegistry.key(), resourceLocation));
		}
	}

	public record LoadResult<T>(ResourceKey<? extends Registry<T>> key, Map<TagKey<T>, List<Holder<T>>> tags) {
	}

}
