package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import org.junit.jupiter.api.Test;

/** Rust's registry snapshot against the Java answers it replaced. */
final class NativeRegistrySnapshotTest {
	private static final KnownPack CORE = new KnownPack("minecraft", "core", "test");
	private static final ResourceKey<Registry<String>> WORDS = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("test", "words"));
	private static final ResourceKey<Registry<String>> LOOSE = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("test", "loose"));

	private static MappedRegistry<String> words() {
		MappedRegistry<String> registry = new MappedRegistry<>(WORDS, Lifecycle.stable());
		for (int index = 0; index < 64; index++) {
			RegistrationInfo info = index % 3 == 0 ? new RegistrationInfo(Optional.of(CORE), Lifecycle.stable()) : RegistrationInfo.BUILT_IN;
			String word = "word_" + index;
			registry.register(ResourceKey.create(WORDS, ResourceLocation.fromNamespaceAndPath(index % 2 == 0 ? "test" : "other", word)), word, info);
		}
		registry.freeze();
		return registry;
	}

	private static void bind(MappedRegistry<String> registry, int tags, int stride) {
		Map<TagKey<String>, List<Holder<String>>> loaded = new HashMap<>();
		for (int tag = 0; tag < tags; tag++) {
			List<Holder<String>> members = new ArrayList<>();
			for (int id = tag; id < registry.size(); id += stride + tag) {
				members.add(registry.get(id).orElseThrow());
			}
			loaded.put(TagKey.create(WORDS, ResourceLocation.fromNamespaceAndPath("test", "tag_" + tag)), members);
		}
		registry.prepareTagReload(new TagLoader.LoadResult<>(WORDS, loaded)).apply();
	}

	/** Frozen's `TagNetworkSerialization.serializeToNetwork` map. */
	private static Map<ResourceLocation, IntList> reference(Registry<String> registry) {
		Map<ResourceLocation, IntList> map = new HashMap<>();
		registry.getTags().forEach(named -> {
			IntList ids = new IntArrayList();
			named.forEach(holder -> ids.add(registry.getId(holder.value())));
			map.put(named.key().location(), ids);
		});
		return map;
	}

	@Test
	void identityKnownPacksAndTagsMatchJava() {
		MappedRegistry<String> words = words();
		bind(words, 40, 2);
		MappedRegistry<String> loose = new MappedRegistry<>(LOOSE, Lifecycle.stable());
		// Unfrozen, so the loose registry's tags stay unbound (freezing the access would bind them empty).
		RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(List.of(words, loose));

		for (Holder.Reference<String> reference : words.listElements().toList()) {
			assertEquals(words.getId(reference.value()), NativeRegistrySnapshot.id(access, WORDS, reference.key().location()));
		}
		assertEquals(-1, NativeRegistrySnapshot.id(access, WORDS, ResourceLocation.fromNamespaceAndPath("test", "missing")));

		for (Set<KnownPack> client : List.of(Set.of(CORE), Set.<KnownPack>of(), Set.of(new KnownPack("test", "other", "1")))) {
			boolean[] expected = new boolean[words.size()];
			for (int id = 0; id < expected.length; id++) {
				Holder.Reference<String> reference = words.get(id).orElseThrow();
				expected[id] = words.registrationInfo(reference.key()).flatMap(RegistrationInfo::knownPackInfo).filter(client::contains).isPresent();
			}
			assertArrayEquals(expected, NativeRegistrySnapshot.knownElements(access, WORDS, client));
		}

		Map<ResourceLocation, IntList> tags = NativeRegistrySnapshot.networkTags(access, WORDS);
		assertEquals(reference(words), tags);
		assertEquals(List.copyOf(reference(words).keySet()), List.copyOf(tags.keySet()), "payload iteration order");
		assertSame(tags, NativeRegistrySnapshot.networkTags(access, WORDS), "a generation's answers are kept");
		assertEquals("Tags not bound", assertThrows(IllegalStateException.class, () -> NativeRegistrySnapshot.networkTags(access, LOOSE)).getMessage());

		bind(words, 7, 5);
		Map<ResourceLocation, IntList> retagged = NativeRegistrySnapshot.networkTags(access, WORDS);
		assertEquals(reference(words), retagged, "a tag rebind publishes the new tags");
		assertEquals(List.copyOf(reference(words).keySet()), List.copyOf(retagged.keySet()));
		bind(words, 7, 5);
		assertSame(retagged, NativeRegistrySnapshot.networkTags(access, WORDS), "rebinding identical tags publishes nothing");
		Map<TagKey<String>, List<Holder<String>>> changed = new HashMap<>();
		words.getTags().forEach(named -> changed.put(named.key(), named.stream().toList()));
		TagKey<String> first = TagKey.create(WORDS, ResourceLocation.fromNamespaceAndPath("test", "tag_0"));
		changed.put(first, List.of(words.get(63).orElseThrow(), words.get(1).orElseThrow()));
		words.prepareTagReload(new TagLoader.LoadResult<>(WORDS, changed)).apply();
		Map<ResourceLocation, IntList> partly = NativeRegistrySnapshot.networkTags(access, WORDS);
		assertEquals(reference(words), partly, "changed members publish; unchanged tags keep theirs");
		assertEquals(List.copyOf(reference(words).keySet()), List.copyOf(partly.keySet()));
		assertEquals(IntList.of(63, 1), partly.get(first.location()));

		ResourceLocation nine = ResourceLocation.fromNamespaceAndPath("other", "word_9");
		assertEquals(words.getId(words.getValue(nine)), NativeRegistrySnapshot.id(access, WORDS, nine), "IDs carry over a rebind");
	}
}
