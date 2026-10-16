package net.minecraft.world.item;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.NativeLibraryLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.NativeContentIdentities;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Rust-owned item definitions (`content/item`): identity, block bindings,
 * description prefix, crafting remainder, required features and default
 * components. Components arrive in their save encoding (SNBT) and are decoded
 * with each component type's own codec, so they are exactly the values a save
 * would load. Java supplies only each item's behavior factory.
 */
public final class NativeItems {
	private NativeItems() {
	}

	/** One component: its type, SNBT as the catalog stores it, and that SNBT as Rust parsed it. */
	public record ComponentValue(String type, String snbt, Tag tag) {
		String key() {
			return this.type + '\0' + this.snbt;
		}
	}

	public record Definition(
		String name,
		@Nullable String block,
		boolean blockPrefix,
		@Nullable String remainder,
		List<String> requiredFeatures,
		List<String> aliases,
		List<NativeItems.ComponentValue> components
	) {
		public String descriptionId() {
			return (this.blockPrefix ? "block" : "item") + ".minecraft." + this.name;
		}
	}

	private static final class Tables {
		static final List<NativeItems.ComponentValue> COMMON_SOURCE = new ArrayList<>();
		static final List<NativeItems.Definition> DEFINITIONS = read(COMMON_SOURCE);
	}

	private static final MethodHandle COMPONENT_TAGS = NativeLibraryLoader.downcallHandle(
		"mattmc_rust", "mattmc_item_component_tags", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
	);

	/** The catalog's component values as Rust parsed them (`content/item/snbt.rs`), in table order. */
	private static ByteBuffer componentTags() {
		try (Arena arena = Arena.ofConfined()) {
			long size = (long)COMPONENT_TAGS.invokeExact(MemorySegment.NULL, 0L);
			if (size < 0) {
				throw new IllegalStateException("The Rust item catalog has malformed SNBT");
			}
			MemorySegment segment = arena.allocate(Math.max(1, size));
			if ((long)COMPONENT_TAGS.invokeExact(segment, size) != size) {
				throw new IllegalStateException("Rust item component table changed size");
			}
			// A direct-buffer copy: heap segments would pollute hot segment-access profiles.
			byte[] table = new byte[(int)size];
			segment.asByteBuffer().get(table);
			return ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot read Rust item component values", e);
		}
	}

	private static Tag tag(ByteBuffer in) {
		return switch (in.get()) {
			case 1 -> ByteTag.valueOf(in.get());
			case 2 -> ShortTag.valueOf(in.getShort());
			case 3 -> IntTag.valueOf(in.getInt());
			case 4 -> LongTag.valueOf(in.getLong());
			case 5 -> FloatTag.valueOf(in.getFloat());
			case 6 -> DoubleTag.valueOf(in.getDouble());
			case 7 -> {
				byte[] bytes = new byte[in.getInt()];
				in.get(bytes);
				yield new ByteArrayTag(bytes);
			}
			case 8 -> StringTag.valueOf(string(in));
			case 9 -> {
				ListTag list = new ListTag();
				for (int i = in.getInt(); i > 0; i--) {
					list.add(tag(in));
				}
				yield list;
			}
			case 10 -> {
				CompoundTag compound = new CompoundTag();
				for (int i = in.getInt(); i > 0; i--) {
					String key = string(in);
					compound.put(key, tag(in));
				}
				yield compound;
			}
			case 11 -> {
				int[] ints = new int[in.getInt()];
				for (int i = 0; i < ints.length; i++) {
					ints[i] = in.getInt();
				}
				yield new IntArrayTag(ints);
			}
			case 12 -> {
				long[] longs = new long[in.getInt()];
				for (int i = 0; i < longs.length; i++) {
					longs[i] = in.getLong();
				}
				yield new LongArrayTag(longs);
			}
			default -> throw new IllegalStateException("Unknown Rust NBT tag");
		};
	}

	private static String string(ByteBuffer in) {
		byte[] bytes = new byte[in.getInt()];
		in.get(bytes);
		return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
	}

	private static List<NativeItems.Definition> read(List<NativeItems.ComponentValue> common) {
		ByteBuffer tags = componentTags();
		List<NativeItems.Definition> definitions = new ArrayList<>();
		List<String> aliases = null;
		List<NativeItems.ComponentValue> components = null;
		String[] head = null;
		for (String line : NativeContentIdentities.table(3)) {
			String[] parts = line.split("\t", 3);
			switch (parts[0]) {
				case "K" -> common.add(new NativeItems.ComponentValue(parts[1], parts[2], tag(tags)));
				case "I" -> {
					if (head != null) {
						definitions.add(definition(head, aliases, components));
					}
					head = line.split("\t");
					aliases = new ArrayList<>();
					components = new ArrayList<>();
				}
				case "A" -> aliases.add(parts[1]);
				case "C" -> components.add(new NativeItems.ComponentValue(parts[1], parts[2], tag(tags)));
				default -> throw new IllegalStateException("Unknown Rust item table line " + line);
			}
		}
		if (head != null) {
			definitions.add(definition(head, aliases, components));
		}
		return List.copyOf(definitions);
	}

	private static NativeItems.Definition definition(String[] head, List<String> aliases, List<NativeItems.ComponentValue> components) {
		return new NativeItems.Definition(
			head[1],
			head[2].equals("-") ? null : head[2],
			head[3].equals("1"),
			head[4].equals("-") ? null : head[4],
			List.of(head[5].split(",")),
			List.copyOf(aliases),
			List.copyOf(components)
		);
	}

	/** Every item definition, by registry ID. */
	public static List<NativeItems.Definition> definitions() {
		return Tables.DEFINITIONS;
	}

	/** The definition registered at {@code id}; it must be the item {@code key}. */
	public static NativeItems.Definition definition(int id, ResourceKey<Item> key) {
		List<NativeItems.Definition> definitions = Tables.DEFINITIONS;
		if (id < 0 || id >= definitions.size() || !key.location().equals(ResourceLocation.withDefaultNamespace(definitions.get(id).name()))) {
			throw new IllegalStateException("Item " + key.location() + " registered at ID " + id + " differs from the Rust catalog");
		}
		return definitions.get(id);
	}

	/** The constructor input for {@code definition}: its description, remainder and features. */
	public static Item.Properties properties(NativeItems.Definition definition, ResourceKey<Item> key) {
		Item remainder = null;
		if (definition.remainder() != null) {
			ResourceLocation location = ResourceLocation.withDefaultNamespace(definition.remainder());
			if (!BuiltInRegistries.ITEM.containsKey(location)) {
				throw new IllegalStateException("Item " + key.location() + " has unregistered remainder " + location);
			}
			remainder = BuiltInRegistries.ITEM.getValue(location);
		}
		List<ResourceLocation> features = definition.requiredFeatures().stream().map(ResourceLocation::parse).toList();
		FeatureFlagSet requiredFeatures = FeatureFlags.REGISTRY.fromNames(features, unknown -> {
			throw new IllegalStateException("Item " + key.location() + " requires unknown feature " + unknown);
		});
		return new Item.Properties(definition.descriptionId(), remainder, requiredFeatures);
	}

	/**
	 * Decodes and binds every item's default components. Runs once, when all
	 * built-in registries have their contents and before they freeze: codecs
	 * may name any built-in entry, and tag references must exist before freezing.
	 */
	public static void bindComponents() {
		BuiltInRegistries.bindValuesBeforeFreeze();
		NativeItemState.bindDefaultRoles();
		List<NativeItems.Definition> definitions = Tables.DEFINITIONS;
		if (BuiltInRegistries.ITEM.size() != definitions.size()) {
			throw new IllegalStateException(BuiltInRegistries.ITEM.size() + " items registered, but the Rust catalog defines " + definitions.size());
		}
		DataComponentMap common = decodeAll(Tables.COMMON_SOURCE).build();
		// Items share one immutable value per distinct component type and SNBT.
		// The distinct values decode in parallel: registry lookups serialize in
		// SerialGetter, and tag sets live in identity maps, so the result does
		// not depend on decode order. Values that build item stacks of items not
		// yet bound decode below, in registry order, as serial decoding did.
		Map<String, NativeItems.ComponentValue> unique = new java.util.LinkedHashMap<>();
		for (NativeItems.Definition definition : definitions) {
			for (NativeItems.ComponentValue component : definition.components()) {
				unique.putIfAbsent(component.key(), component);
			}
		}
		Map<String, Object> decoded = new java.util.concurrent.ConcurrentHashMap<>();
		unique.values().parallelStream().forEach(component -> {
			try {
				decoded.put(component.key(), decode(componentType(component.type()), component));
			} catch (Item.UnboundComponentsException later) {
				// Decoded in the loop below, after the items it names.
			}
		});
		for (int id = 0; id < definitions.size(); id++) {
			NativeItems.Definition definition = definitions.get(id);
			Item item = BuiltInRegistries.ITEM.byId(id);
			DataComponentMap.Builder builder = DataComponentMap.builder().addAll(common);
			for (NativeItems.ComponentValue component : definition.components()) {
				DataComponentType<?> type = componentType(component.type());
				setUnchecked(builder, type, decoded.computeIfAbsent(component.key(), key -> decode(type, component)));
			}
			DataComponentMap components = builder
				.set(DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable(definition.descriptionId()))
				.set(DataComponents.ITEM_MODEL, ResourceLocation.withDefaultNamespace(definition.name()))
				.build();
			if (components.has(DataComponents.DAMAGE) && components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
				throw new IllegalStateException("Item minecraft:" + definition.name() + " cannot have both durability and be stackable");
			}
			item.bindComponents(components);
		}
	}

	/** Puts {@code item}'s block and aliases into {@link Item#BY_BLOCK}, after checking its block matches the definition. */
	public static void bindBlocks(NativeItems.Definition definition, Item item) {
		String actual = item instanceof BlockItem blockItem ? BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).getPath() : null;
		if (!Objects.equals(actual, definition.block())) {
			throw new IllegalStateException("Item " + definition.name() + " places " + actual + ", but the Rust catalog says " + definition.block());
		}
		if (definition.block() != null) {
			Item.BY_BLOCK.put(block(definition.block()), item);
		}
		for (String alias : definition.aliases()) {
			Item.BY_BLOCK.put(block(alias), item);
		}
	}

	private static Block block(String name) {
		ResourceLocation location = ResourceLocation.withDefaultNamespace(name);
		if (!BuiltInRegistries.BLOCK.containsKey(location)) {
			throw new IllegalStateException("Unknown block " + location + " in the Rust item catalog");
		}
		return BuiltInRegistries.BLOCK.getValue(location);
	}

	private static final RegistryOps<Tag> OPS = RegistryOps.create(NbtOps.INSTANCE, new NativeItems.DefinitionLookup());

	private static DataComponentMap.Builder decodeAll(List<NativeItems.ComponentValue> components) {
		DataComponentMap.Builder builder = DataComponentMap.builder();
		for (NativeItems.ComponentValue component : components) {
			DataComponentType<?> type = componentType(component.type());
			setUnchecked(builder, type, decode(type, component));
		}
		return builder;
	}

	@SuppressWarnings("unchecked")
	private static <T> void setUnchecked(DataComponentMap.Builder builder, DataComponentType<T> type, Object value) {
		builder.set(type, (T)value);
	}

	private static DataComponentType<?> componentType(String name) {
		// Component types register when DataComponents initializes; lookups are by name.
		Objects.requireNonNull(DataComponents.ITEM_NAME);
		DataComponentType<?> type = DataComponents.byName(name);
		if (type == null || type.isTransient()) {
			throw new IllegalStateException("Rust item component minecraft:" + name + " is not a saved component type");
		}
		return type;
	}

	private static <T> T decode(DataComponentType<T> type, NativeItems.ComponentValue component) {
		return decode(type, component.tag(), component.snbt());
	}

	/** Decodes one component value with its save codec. */
	public static <T> T decode(DataComponentType<T> type, Tag tag, String source) {
		return type.codecOrThrow().parse(OPS, tag).getOrThrow(error -> new IllegalStateException("Cannot decode " + type + " " + source + ": " + error));
	}

	/** A registration lookup for parallel decoding: lookups, which can create holders and tag sets, run one at a time. */
	private record SerialGetter<T>(HolderGetter<T> getter) implements HolderGetter<T> {
		private static final Object LOCK = new Object();

		@Override
		public Optional<net.minecraft.core.Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
			synchronized (LOCK) {
				return this.getter.get(resourceKey);
			}
		}

		@Override
		public Optional<net.minecraft.core.HolderSet.Named<T>> get(net.minecraft.tags.TagKey<T> tagKey) {
			synchronized (LOCK) {
				return this.getter.get(tagKey);
			}
		}
	}

	/**
	 * Built-in registries, through their registration lookups while they are
	 * still open. Data-driven registries do not exist yet, so references into
	 * them stay keys, as the original definitions held them.
	 */
	private static final class DefinitionLookup implements RegistryOps.RegistryInfoLookup {
		private final Map<ResourceKey<?>, Optional<RegistryOps.RegistryInfo<?>>> lookups = new HashMap<>();

		@SuppressWarnings("unchecked")
		@Override
		public synchronized <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> resourceKey) {
			return (Optional<RegistryOps.RegistryInfo<T>>)(Optional<?>)this.lookups.computeIfAbsent(resourceKey, key -> {
				Registry<?> registry = BuiltInRegistries.builtInRegistry(key.location());
				return registry == null ? Optional.empty() : Optional.of(info(registry));
			});
		}

		private static <T> RegistryOps.RegistryInfo<T> info(Registry<T> registry) {
			HolderGetter<T> getter;
			try {
				getter = BuiltInRegistries.acquireBootstrapRegistrationLookup(registry);
			} catch (IllegalStateException frozen) {
				getter = registry;
			}
			return new RegistryOps.RegistryInfo<>(registry, new NativeItems.SerialGetter<>(getter), registry.registryLifecycle());
		}

		@Override
		public boolean keepsUnresolvedKeys() {
			return true;
		}
	}
}
