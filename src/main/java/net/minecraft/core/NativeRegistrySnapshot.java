package net.minecraft.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Rust's identity snapshot of a frozen registry set (`content/registry`),
 * keyed by a stable access such as `LayeredRegistryAccess.compositeAccess()`:
 * element network IDs, each element's known pack, and tags as ID lists.
 *
 * A set publishes its elements and tags on first query. When registries
 * rebind their tags, the next query publishes just those registries' tags;
 * elements, IDs and the rest carry over in Rust. Each publish is a new
 * generation, and queries name the generation they expect. Registry
 * synchronization asks Rust which elements a client already knows and
 * which IDs each network tag holds; Java keeps immutable CPU views until the
 * registry they came from changes. Queued payloads pin their original IDs.
 */
public final class NativeRegistrySnapshot {
	private static final Cleaner CLEANER = Cleaner.create();
	private static final long STALE = -2L;
	/** A member head referring to a previous tag's members (`content/registry/ffi.rs`). */
	private static final int PREVIOUS = 1 << 31;
	/** A name head announcing a new name. */
	private static final int NEW_NAME = -1;
	private static final AtomicInteger NEXT_CONTEXT = new AtomicInteger();
	/** Guards every snapshot structure below. */
	private static final Object LOCK = new Object();
	private static final Map<RegistryAccess, Slot> PUBLISHED = new WeakHashMap<>();
	/** Each registry's last tag rebind, on the epoch clock. */
	private static final Map<Registry<?>, Long> TAG_VERSIONS = new WeakHashMap<>();
	private static long epoch;
	private static final MethodHandle PUBLISH = handle("registry_publish", ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG);
	private static final MethodHandle RETAG = handle("registry_retag", ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ID = handle("registry_id", ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle KNOWN = handle("registry_known_elements", ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_registry_release",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
	private static final MethodHandle BUFFER_FREE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_buffer_free",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

	private NativeRegistrySnapshot() {
	}

	private static MethodHandle handle(String name, ValueLayout result, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.of(result, arguments));
	}

	/** `registry` rebound its tags: snapshots holding it publish them on their next query. */
	public static void tagsChanged(Registry<?> registry) {
		synchronized (LOCK) {
			TAG_VERSIONS.put(registry, ++epoch);
		}
	}

	/** One registry set's Rust context, its registries in publish order, and its answers so far. */
	private static final class Slot {
		final int context;
		final List<Registry<?>> registries;
		final Map<ResourceKey<? extends Registry<?>>, Integer> indices = new HashMap<>();
		long generation;
		/** The epoch whose tags are published. */
		long epoch;
		final List<List<ResourceLocation>> tagNames;
		/** The member IDs last sent per tag, to send only what changed. */
		final List<int[][]> sentIds;
		/** Java's reason a registry's tags cannot go over the network, or null. */
		final String[] unserializable;
		final List<Map<ResourceLocation, IntList>> networkTags;
		/** Known masks never change: elements are fixed once a set freezes. */
		final List<Map<Set<KnownPack>, boolean[]>> knownElements;

		Slot(int context, List<Registry<?>> registries) {
			this.context = context;
			this.registries = registries;
			for (int index = 0; index < registries.size(); index++) {
				this.indices.put(registries.get(index).key(), index);
			}
			int size = registries.size();
			this.tagNames = new ArrayList<>(Collections.nCopies(size, null));
			this.sentIds = new ArrayList<>(Collections.nCopies(size, null));
			this.unserializable = new String[size];
			this.networkTags = new ArrayList<>(Collections.nCopies(size, null));
			this.knownElements = new ArrayList<>(size);
			for (int index = 0; index < size; index++) {
				this.knownElements.add(new HashMap<>());
			}
		}

		int index(ResourceKey<? extends Registry<?>> registry) {
			Integer index = this.indices.get(registry);
			if (index == null) {
				throw new IllegalStateException("Registry snapshot has no registry " + registry.location());
			}
			return index;
		}
	}

	/** A growable little-endian record buffer. */
	private static final class Writer {
		private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
		byte[] bytes = new byte[1 << 16];
		int size;

		void reserve(int extra) {
			if (this.size + extra > this.bytes.length) {
				this.bytes = Arrays.copyOf(this.bytes, Math.max(this.bytes.length * 2, this.size + extra));
			}
		}

		void u32(int value) {
			this.reserve(4);
			INT.set(this.bytes, this.size, value);
			this.size += 4;
		}

		void setU32(int at, int value) {
			INT.set(this.bytes, at, value);
		}

		void text(String text) {
			byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
			this.u32(utf8.length);
			this.reserve(utf8.length);
			System.arraycopy(utf8, 0, this.bytes, this.size, utf8.length);
			this.size += utf8.length;
		}

		void location(ResourceLocation location) {
			this.text(location.getNamespace());
			this.text(location.getPath());
		}

		void pack(KnownPack pack) {
			this.text(pack.namespace());
			this.text(pack.id());
			this.text(pack.version());
		}

		MemorySegment copy(Arena arena) {
			MemorySegment segment = arena.allocate(Math.max(1, this.size));
			MemorySegment.copy(this.bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, this.size);
			return segment;
		}
	}

	private static <T> void writeElements(Writer writer, Registry<T> registry, Map<KnownPack, Integer> packs) {
		writer.location(registry.key().location());
		int size = registry.size();
		writer.u32(size);
		for (int id = 0; id < size; id++) {
			Holder.Reference<T> reference = registry.get(id).orElseThrow(() -> new IllegalStateException("Registry " + registry.key() + " has a gap"));
			writer.location(reference.key().location());
			Optional<KnownPack> pack = registry.registrationInfo(reference.key()).flatMap(RegistrationInfo::knownPackInfo);
			writer.u32(pack.map(known -> packs.computeIfAbsent(known, key -> packs.size())).orElse(-1));
		}
	}

	/** Java's reason a registry's tags cannot be sent. */
	private static final class Unserializable extends RuntimeException {
		Unserializable(String message) {
			super(message, null, false, false);
		}
	}

	/**
	 * A registry's tags as `TagNetworkSerialization` walked them, or Java's
	 * reason they cannot be sent. `previous[i]` is the index of the previously
	 * sent tag with the same name, or -1; an unchanged tag shares that tag's
	 * member array.
	 */
	private record Walk(List<ResourceLocation> names, int[][] ids, int[] previous, @Nullable String unserializable) {
	}

	private static <T> Walk walk(Slot slot, int index) {
		@SuppressWarnings("unchecked")
		Registry<T> registry = (Registry<T>)slot.registries.get(index);
		List<ResourceLocation> sentNames = slot.tagNames.get(index);
		int[][] sent = slot.sentIds.get(index);
		Map<ResourceLocation, Integer> sentIndex = new HashMap<>();
		List<ResourceLocation> names = new ArrayList<>();
		List<int[]> ids = new ArrayList<>();
		IntArrayList previous = new IntArrayList();
		try {
			registry.getTags().forEach(named -> {
				ResourceLocation name = named.key().location();
				int position = names.size();
				int earlier = -1;
				if (sent != null) {
					if (position < sentNames.size() && sentNames.get(position).equals(name)) {
						earlier = position;
					} else {
						if (sentIndex.isEmpty()) {
							for (int tag = 0; tag < sentNames.size(); tag++) {
								sentIndex.put(sentNames.get(tag), tag);
							}
						}
						earlier = sentIndex.getOrDefault(name, -1);
					}
				}
				int size = named.size();
				int[] base = earlier >= 0 && sent[earlier].length == size ? sent[earlier] : null;
				// Compare in place; copy only when a member differs.
				int[] members = base != null ? base : new int[size];
				int member = 0;
				for (Holder<T> holder : named) {
					if (holder.kind() != Holder.Kind.REFERENCE) {
						throw new Unserializable("Can't serialize unregistered value " + holder);
					}
					int id = registry.getId(holder.value());
					if (members == base && base[member] != id) {
						members = Arrays.copyOf(base, size);
					}
					members[member++] = id;
				}
				names.add(name);
				ids.add(members);
				previous.add(earlier);
			});
		} catch (Unserializable | IllegalStateException failure) {
			// `getTags` throws "Tags not bound" for a registry whose tags never loaded.
			return new Walk(List.of(), new int[0][], new int[0], failure.getMessage());
		}
		return new Walk(names, ids.toArray(int[][]::new), previous.toIntArray(), null);
	}

	/** Whether `walk` matches what Rust already holds for the registry at `index`. */
	private static boolean published(Slot slot, int index, Walk walk) {
		int[][] sent = slot.sentIds.get(index);
		if (sent == null || sent.length != walk.ids().length || !Objects.equals(walk.unserializable(), slot.unserializable[index])) {
			return false;
		}
		for (int tag = 0; tag < sent.length; tag++) {
			if (walk.previous()[tag] != tag || walk.ids()[tag] != sent[tag]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Writes the registry at `index`'s tags (see `read_tags` in
	 * `content/registry/ffi.rs`): unchanged members and names refer to the
	 * previously sent tags. Returns the slot update to apply once Rust accepts
	 * the publish. Network lists borrow the accepted Rust generation's storage.
	 */
	private static Runnable writeTags(Writer writer, Slot slot, int index, Walk walk) {
		List<ResourceLocation> names = walk.names();
		int[][] sent = slot.sentIds.get(index);
		boolean sameNames = sent != null && names.equals(slot.tagNames.get(index));
		writer.u32(sameNames ? 0 : 1);
		writer.u32(names.size());
		for (int tag = 0; tag < names.size(); tag++) {
			int earlier = walk.previous()[tag];
			int[] members = walk.ids()[tag];
			if (earlier >= 0 && members == sent[earlier]) {
				writer.u32(PREVIOUS | earlier);
				continue;
			}
			writer.u32(members.length);
			writer.reserve(4 * members.length);
			for (int member : members) {
				writer.u32(member);
			}
		}
		if (!sameNames) {
			for (int tag = 0; tag < names.size(); tag++) {
				int earlier = walk.previous()[tag];
				if (earlier >= 0) {
					writer.u32(earlier);
				} else {
					writer.u32(NEW_NAME);
					writer.location(names.get(tag));
				}
			}
		}
		List<ResourceLocation> sentNames = sameNames ? slot.tagNames.get(index) : List.copyOf(names);
		return () -> {
			slot.tagNames.set(index, sentNames);
			slot.sentIds.set(index, walk.ids());
			slot.unserializable[index] = walk.unserializable();
			slot.networkTags.set(index, null);
		};
	}

	private static long tagVersion(Registry<?> registry) {
		Long version = TAG_VERSIONS.get(registry);
		return version == null ? 0L : version;
	}

	/** Publishes `slot`'s registry set, or the tags rebound since its last publish. */
	private static void publish(RegistryAccess access, Slot slot) {
		long published = slot.epoch;
		boolean first = slot.generation == 0L;
		Writer writer = new Writer();
		List<Runnable> updates = new ArrayList<>();
		long generation;
		try (Arena arena = Arena.ofConfined()) {
			if (first) {
				Map<KnownPack, Integer> packs = new LinkedHashMap<>();
				Writer registries = new Writer();
				registries.u32(slot.registries.size());
				for (int index = 0; index < slot.registries.size(); index++) {
					writeElements(registries, slot.registries.get(index), packs);
					updates.add(writeTags(registries, slot, index, walk(slot, index)));
				}
				writer.u32(packs.size());
				packs.keySet().forEach(writer::pack);
				writer.reserve(registries.size);
				System.arraycopy(registries.bytes, 0, writer.bytes, writer.size, registries.size);
				writer.size += registries.size;
				generation = (long)PUBLISH.invokeExact(slot.context, writer.copy(arena), (long)writer.size);
			} else {
				writer.u32(0);
				int changed = 0;
				for (int index = 0; index < slot.registries.size(); index++) {
					if (tagVersion(slot.registries.get(index)) <= published) {
						continue;
					}
					// A rebind often binds the same tags again: send only registries that changed.
					Walk walk = walk(slot, index);
					if (!published(slot, index, walk)) {
						writer.u32(index);
						updates.add(writeTags(writer, slot, index, walk));
						changed++;
					}
				}
				if (changed == 0) {
					slot.epoch = epoch;
					return;
				}
				writer.setU32(0, changed);
				generation = (long)RETAG.invokeExact(slot.context, slot.generation, writer.copy(arena), (long)writer.size);
			}
		} catch (Throwable throwable) {
			throw failure(throwable);
		}
		if (generation == 0L || generation == STALE) {
			throw new IllegalStateException("Rust rejected the registry snapshot: " + generation);
		}
		updates.forEach(Runnable::run);
		if (first) {
			int context = slot.context;
			CLEANER.register(access, () -> release(context));
		}
		slot.generation = generation;
		slot.epoch = epoch;
	}

	private static void release(int context) {
		try {
			RELEASE.invokeExact(context);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Release registry snapshot failed", throwable);
		}
	}

	/** `access`'s slot with current tags, publishing first if needed. Call holding `LOCK`. */
	private static Slot current(RegistryAccess access) {
		Slot slot = PUBLISHED.get(access);
		if (slot == null) {
			List<Registry<?>> registries = access.registries().<Registry<?>>map(RegistryAccess.RegistryEntry::value).toList();
			slot = new Slot(NEXT_CONTEXT.getAndIncrement(), registries);
			PUBLISHED.put(access, slot);
		}
		if (slot.generation == 0L || slot.epoch != epoch) {
			publish(access, slot);
		}
		return slot;
	}

	private static IllegalStateException failure(Throwable throwable) {
		return throwable instanceof IllegalStateException state ? state : new IllegalStateException("Registry snapshot query failed", throwable);
	}

	static void check(ResourceKey<? extends Registry<?>> registry, long status) {
		if (status != 0L) {
			throw new IllegalStateException(
				status == STALE ? "Registry snapshot generation retired during a query" : "Registry snapshot has no registry " + registry.location()
			);
		}
	}

	private static MemorySegment utf8(Arena arena, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		return bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
	}

	private static long length(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

	/** An element's network ID in `access`'s snapshot, or -1. */
	public static int id(RegistryAccess access, ResourceKey<? extends Registry<?>> registry, ResourceLocation element) {
		synchronized (LOCK) {
			Slot slot = current(access);
			ResourceLocation key = registry.location();
			try (Arena arena = Arena.ofConfined()) {
				long id = (long)ID.invokeExact(slot.context, slot.generation, utf8(arena, key.getNamespace()), length(key.getNamespace()),
					utf8(arena, key.getPath()), length(key.getPath()), utf8(arena, element.getNamespace()), length(element.getNamespace()),
					utf8(arena, element.getPath()), length(element.getPath()));
				if (id == STALE) {
					check(registry, id);
				}
				return (int)id;
			} catch (Throwable throwable) {
				throw failure(throwable);
			}
		}
	}

	/**
	 * `RegistrySynchronization`'s known-pack check: per element ID, whether
	 * its known pack is one the client has (its data need not be sent).
	 * The result is shared; callers must not modify it.
	 */
	public static boolean[] knownElements(RegistryAccess access, ResourceKey<? extends Registry<?>> registry, Set<KnownPack> known) {
		synchronized (LOCK) {
			Slot slot = current(access);
			int index = slot.index(registry);
			Map<Set<KnownPack>, boolean[]> cache = slot.knownElements.get(index);
			boolean[] cached = cache.get(known);
			if (cached != null) {
				return cached;
			}
			Writer packs = new Writer();
			known.forEach(packs::pack);
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
				MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
				long status = (long)KNOWN.invokeExact(slot.context, slot.generation, index, packs.copy(arena), (long)packs.size, pointer, length);
				check(registry, status);
				long size = length.get(ValueLayout.JAVA_LONG, 0);
				MemorySegment mask = pointer.get(ValueLayout.ADDRESS, 0).reinterpret(size);
				boolean[] knownElements = new boolean[(int)size];
				try {
					for (int element = 0; element < knownElements.length; element++) {
						knownElements[element] = mask.get(ValueLayout.JAVA_BYTE, element) != 0;
					}
				} finally {
					BUFFER_FREE.invokeExact(mask, size);
				}
				cache.put(Set.copyOf(known), knownElements);
				return knownElements;
			} catch (Throwable throwable) {
				throw failure(throwable);
			}
		}
	}

	/**
	 * A registry's tags as network ID lists (`TagNetworkSerialization`'s
	 * payload map), throwing Java's error when they cannot be sent. The map
	 * is shared and unmodifiable.
	 */
	public static Map<ResourceLocation, IntList> networkTags(RegistryAccess access, ResourceKey<? extends Registry<?>> registry) {
		synchronized (LOCK) {
			Slot slot = current(access);
			int index = slot.index(registry);
			if (slot.unserializable[index] != null) {
				throw new IllegalStateException(slot.unserializable[index]);
			}
			Map<ResourceLocation, IntList> cached = slot.networkTags.get(index);
			if (cached == null) {
				cached = decodeTags(slot, index, registry);
				slot.networkTags.set(index, cached);
			}
			return cached;
		}
	}

	private static Map<ResourceLocation, IntList> decodeTags(Slot slot, int index, ResourceKey<? extends Registry<?>> registry) {
		List<ResourceLocation> names = slot.tagNames.get(index);
		if (names.isEmpty()) {
			return Map.of();
		}
		Map<ResourceLocation, IntList> tags = new HashMap<>();
		IntList[] lists = NativeRegistryTagView.acquire(slot.context, slot.generation, index, registry, names.size());
		for (int tag = 0; tag < names.size(); tag++) {
			// Insertion in publish order keeps Java's payload iteration order.
			tags.put(names.get(tag), lists[tag]);
		}
		return Collections.unmodifiableMap(tags);
	}
}
