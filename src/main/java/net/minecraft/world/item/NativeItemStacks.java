package net.minecraft.world.item;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.NativeText;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValueTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.ItemLore;

/**
 * Item stacks and component patches as projections of Rust's decoded values
 * (`content/data/items.rs`). Components whose codecs are native project
 * here; the rest were decoded by Java's codecs while Rust decoded.
 */
public final class NativeItemStacks {
	private static final MethodHandle NATIVE_COMPONENTS = NativeLibraryLoader.downcallHandle(
		"mattmc_rust", "mattmc_item_native_components", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
	);
	private static final Map<DataComponentType<?>, Native> NATIVE = natives();
	/**
	 * Native components whose encodings vanilla caches (`cacheEncoding`): they
	 * encode through the type's cache-wrapped codec, so an equal value reuses
	 * its first encoding exactly as before.
	 */
	private static final Set<DataComponentType<?>> CACHED = cached();

	private NativeItemStacks() {
	}

	/** A component value's projection and description. */
	private record Native(BiFunction<Object, NativeData.Context, Object> project, BiFunction<Object, NativeData.Builder, Object> unproject) {
	}

	/** Adapts a typed projection without initializing component registries at codec construction. */
	@SuppressWarnings("unchecked")
	private static <E> Native nativeValue(NativeData.Projector<E> projector, NativeData.Unprojector<E> unprojector) {
		return new Native(projector::project, (value, builder) -> unprojector.unproject((E)value, builder));
	}

	private static Native same() {
		return new Native((value, context) -> value, (value, builder) -> value);
	}

	private static Native unit() {
		return new Native((value, context) -> Unit.INSTANCE, (value, builder) -> new Object[0]);
	}

	private static Native component() {
		return new Native(NativeText::project, (value, builder) -> NativeText.unproject((Component)value, builder));
	}

	private static Native location() {
		return new Native((value, context) -> NativeData.location(value), (value, builder) -> value.toString());
	}

	private static Map<DataComponentType<?>, Native> natives() {
		Map<String, Native> byName = Map.ofEntries(
			Map.entry("break_sound", nativeValue(NativeItemComponentValues::sound, NativeItemComponentValues::sound)),
			Map.entry("repairable", nativeValue(NativeItemComponentValues::repairable, NativeItemComponentValues::repairable)),
			Map.entry("enchantable", nativeValue(NativeItemComponentValues::enchantable, NativeItemComponentValues::enchantable)),
			Map.entry("damage_resistant", nativeValue(NativeItemComponentValues::damageResistant, NativeItemComponentValues::damageResistant)),
			Map.entry("trim", nativeValue(NativeItemComponentValues::trim, NativeItemComponentValues::trim)),
			Map.entry("instrument", nativeValue(NativeItemComponentValues::instrument, NativeItemComponentValues::instrument)),
			Map.entry("jukebox_playable", nativeValue(NativeItemComponentValues::jukebox, NativeItemComponentValues::jukebox)),
			Map.entry("provides_trim_material", nativeValue(NativeItemComponentValues::providesTrim, NativeItemComponentValues::providesTrim)),
			Map.entry("banner_patterns", nativeValue(NativeItemComponentValues::bannerPatterns, NativeItemComponentValues::bannerPatterns)),
			Map.entry("food", nativeValue(NativeItemComponentValues::food, NativeItemComponentValues::food)),
			Map.entry("attribute_modifiers", nativeValue(NativeItemComponentValues::attributes, NativeItemComponentValues::attributes)),
			Map.entry("potion_contents", nativeValue(NativeItemComponentValues::potion, NativeItemComponentValues::potion)),
			Map.entry("suspicious_stew_effects", nativeValue(NativeItemComponentValues::stew, NativeItemComponentValues::stew)),
			Map.entry("written_book_content", nativeValue(NativeItemComponentValues::writtenBook, NativeItemComponentValues::writtenBook)),
			Map.entry("bees", nativeValue(NativeItemComponentValues::bees, NativeItemComponentValues::bees)),
			Map.entry("max_stack_size", same()),
			Map.entry("max_damage", same()),
			Map.entry("damage", same()),
			Map.entry("repair_cost", same()),
			Map.entry("unbreakable", unit()),
			Map.entry("intangible_projectile", unit()),
			Map.entry("glider", unit()),
			Map.entry("custom_name", component()),
			Map.entry("item_name", component()),
			Map.entry("item_model", location()),
			Map.entry("tooltip_style", location()),
			Map.entry("note_block_sound", location()),
			Map.entry("lore", new Native(
				(value, context) -> {
					List<Object> lines = NativeData.list(value);
					List<Component> projected = new ArrayList<>(lines.size());
					for (Object line : lines) {
						projected.add(NativeText.project(line, context));
					}
					return new ItemLore(projected);
				},
				(value, builder) -> {
					List<Component> lines = ((ItemLore)value).lines();
					List<Object> described = new ArrayList<>(lines.size());
					for (Component line : lines) {
						described.add(NativeText.unproject(line, builder));
					}
					return described;
				}
			)),
			Map.entry("rarity", new Native((value, context) -> NativeData.named(Rarity.class, value), (value, builder) -> ((Rarity)value).getSerializedName())),
			Map.entry("enchantment_glint_override", same()),
			Map.entry("potion_duration_scale", same()),
			Map.entry("dyed_color", new Native(
				(v, c) -> new net.minecraft.world.item.component.DyedItemColor(NativeValueTypes.color(v)),
				(v, b) -> NativeValueTypes.color(((net.minecraft.world.item.component.DyedItemColor)v).rgb())
			)),
			Map.entry("map_color", new Native(
				(v, c) -> new net.minecraft.world.item.component.MapItemColor((Integer)v), (v, b) -> ((net.minecraft.world.item.component.MapItemColor)v).rgb()
			)),
			Map.entry("map_id", new Native(
				(v, c) -> new net.minecraft.world.level.saveddata.maps.MapId((Integer)v), (v, b) -> ((net.minecraft.world.level.saveddata.maps.MapId)v).id()
			)),
			Map.entry("ominous_bottle_amplifier", new Native(
				(v, c) -> new net.minecraft.world.item.component.OminousBottleAmplifier((Integer)v),
				(v, b) -> ((net.minecraft.world.item.component.OminousBottleAmplifier)v).value()
			)),
			Map.entry("base_color", new Native((v, c) -> NativeData.named(DyeColor.class, v), (v, b) -> ((DyeColor)v).getSerializedName())),
			Map.entry("custom_model_data", new Native(NativeItemComponents::customModelData, NativeItemComponents::customModelData)),
			Map.entry("use_cooldown", new Native(NativeItemComponents::useCooldown, NativeItemComponents::useCooldown)),
			Map.entry("weapon", new Native(NativeItemComponents::weapon, NativeItemComponents::weapon)),
			Map.entry("tooltip_display", new Native(NativeItemComponents::tooltipDisplay, NativeItemComponents::tooltipDisplay)),
			Map.entry("lodestone_tracker", new Native(NativeItemComponents::lodestone, NativeItemComponents::lodestone)),
			Map.entry("container_loot", new Native(NativeItemComponents::containerLoot, NativeItemComponents::containerLoot)),
			Map.entry("firework_explosion", new Native(NativeItemComponents::explosion, NativeItemComponents::explosion)),
			Map.entry("fireworks", new Native(NativeItemComponents::fireworks, NativeItemComponents::fireworks)),
			Map.entry("writable_book_content", new Native(NativeItemComponents::writableBook, NativeItemComponents::writableBook)),
			Map.entry("block_state", new Native(NativeItemComponents::blockState, NativeItemComponents::blockState)),
			Map.entry("container", new Native(NativeItemComponents::container, NativeItemComponents::container)),
			Map.entry("pot_decorations", new Native(NativeItemComponents::potDecorations, NativeItemComponents::potDecorations)),
			Map.entry("charged_projectiles", new Native(NativeItemComponents::charged, NativeItemComponents::charged)),
			Map.entry("use_remainder", new Native(
				(v, c) -> new net.minecraft.world.item.component.UseRemainder(project(v, c)),
				(v, b) -> unproject(((net.minecraft.world.item.component.UseRemainder)v).convertInto(), b)
			))
		);
		Set<String> rust = new HashSet<>(rustNatives());
		if (!rust.equals(byName.keySet())) {
			throw new IllegalStateException("Native item components differ: Rust " + rust + ", Java " + byName.keySet());
		}
		Map<DataComponentType<?>, Native> natives = new java.util.IdentityHashMap<>();
		byName.forEach((name, n) -> natives.put(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ResourceLocation.withDefaultNamespace(name)), n));
		return natives;
	}

	private static Set<DataComponentType<?>> cached() {
		Set<DataComponentType<?>> cached = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (DataComponentType<?> type : NATIVE.keySet()) {
			ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
			if (net.minecraft.core.NativeContentIdentities.componentType(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(type), key.getPath()).cached()) {
				cached.add(type);
			}
		}
		return cached;
	}

	private static List<String> rustNatives() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(4096);
			long size = (long)NATIVE_COMPONENTS.invokeExact(out, 4096L);
			if (size > 4096) {
				out = arena.allocate(size);
				size = (long)NATIVE_COMPONENTS.invokeExact(out, size);
			}
			ByteBuffer in = out.reinterpret(size).asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
			List<String> names = new ArrayList<>();
			for (int i = in.getInt(); i > 0; i--) {
				byte[] bytes = new byte[in.getInt()];
				in.get(bytes);
				names.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
			}
			return names;
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot read native item components", e);
		}
	}

	/** {@code ItemStack.MAP_CODEC}'s record: id, count, components. */
	public static ItemStack project(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new ItemStack(item(f[0]), (Integer)f[1], patch(f[2], context));
	}

	public static Object unproject(ItemStack stack, NativeData.Builder builder) {
		return new Object[]{BuiltInRegistries.ITEM.getId(stack.getItem()), stack.getCount(), patch(stack.getComponentsPatch(), builder)};
	}

	/** {@code ItemStack.SINGLE_ITEM_CODEC}'s record: id, components; count 1. */
	public static ItemStack projectSingle(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new ItemStack(item(f[0]), 1, patch(f[1], context));
	}

	public static Object unprojectSingle(ItemStack stack, NativeData.Builder builder) {
		return new Object[]{BuiltInRegistries.ITEM.getId(stack.getItem()), patch(stack.getComponentsPatch(), builder)};
	}

	/** An item by registry ID (Rust's catalog order). */
	private static Holder<Item> item(Object id) {
		return BuiltInRegistries.ITEM.byId((Integer)id).builtInRegistryHolder();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static DataComponentPatch patch(Object value, NativeData.Context context) {
		Map<String, Object> entries = NativeData.map(value);
		if (entries.isEmpty()) {
			return DataComponentPatch.EMPTY;
		}
		DataComponentPatch.Builder builder = DataComponentPatch.builder();
		entries.forEach((path, v) -> {
			DataComponentType type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ResourceLocation.withDefaultNamespace(path));
			if (v == null) {
				builder.remove(type);
			} else {
				if (v instanceof NativeData.Variant delegated && delegated.index() == 0xFFFF) {
					// Custom DynamicOps kept the original complete component codec.
					builder.set(type, context.slot(delegated.fields()[0], type.codecOrThrow()));
					return;
				}
				Native n = NATIVE.get(type);
				builder.set(type, n != null ? n.project().apply(v, context) : context.slot(v, type.codecOrThrow()));
			}
		});
		return builder.build();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object patch(DataComponentPatch patch, NativeData.Builder builder) {
		if (patch.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> entries = new java.util.LinkedHashMap<>();
		for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
			DataComponentType type = entry.getKey();
			if (type.isTransient()) {
				continue;
			}
			String path = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).getPath();
			Optional<?> optional = entry.getValue();
			if (optional.isEmpty()) {
				entries.put(path, null);
			} else {
				Native n = CACHED.contains(type) ? null : NATIVE.get(type);
				entries.put(path, n != null ? n.unproject().apply(optional.get(), builder) : builder.slot(optional.get(), type.codecOrThrow()));
			}
		}
		return entries;
	}
}
