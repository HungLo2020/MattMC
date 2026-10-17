package net.minecraft.world.item;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValueTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.entity.PotDecorations;

/**
 * Item component values with native codecs (`content/data/items.rs`
 * `component_schema`), projected from and described for Rust's decoded form.
 */
final class NativeItemComponents {
	private NativeItemComponents() {
	}

	@SuppressWarnings("unchecked")
	static Object customModelData(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		List<Integer> colors = new ArrayList<>();
		for (Object color : NativeData.list(f[3])) {
			colors.add(NativeValueTypes.color(color));
		}
		return new CustomModelData(List.copyOf((List<Float>)(Object)NativeData.list(f[0])), List.copyOf((List<Boolean>)(Object)NativeData.list(f[1])),
			List.copyOf((List<String>)(Object)NativeData.list(f[2])), List.copyOf(colors));
	}

	static Object customModelData(Object value, NativeData.Builder builder) {
		CustomModelData data = (CustomModelData)value;
		List<Object> colors = new ArrayList<>();
		for (int color : data.colors()) {
			colors.add(NativeValueTypes.color(color));
		}
		return new Object[]{new ArrayList<Object>(data.floats()), new ArrayList<Object>(data.flags()), new ArrayList<Object>(data.strings()), colors};
	}

	static Object useCooldown(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new UseCooldown((Float)f[0], Optional.ofNullable(f[1]).map(NativeData::location));
	}

	static Object useCooldown(Object value, NativeData.Builder builder) {
		UseCooldown cooldown = (UseCooldown)value;
		return new Object[]{cooldown.seconds(), cooldown.cooldownGroup().map(ResourceLocation::toString).orElse(null)};
	}

	static Object weapon(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new Weapon((Integer)f[0], (Float)f[1]);
	}

	static Object weapon(Object value, NativeData.Builder builder) {
		Weapon weapon = (Weapon)value;
		return new Object[]{weapon.itemDamagePerAttack(), weapon.disableBlockingForSeconds()};
	}

	static Object tooltipDisplay(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		ReferenceLinkedOpenHashSet<DataComponentType<?>> hidden = new ReferenceLinkedOpenHashSet<>();
		for (Object path : NativeData.list(f[1])) {
			hidden.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ResourceLocation.withDefaultNamespace((String)path)));
		}
		return new TooltipDisplay((Boolean)f[0], hidden);
	}

	static Object tooltipDisplay(Object value, NativeData.Builder builder) {
		TooltipDisplay display = (TooltipDisplay)value;
		List<Object> hidden = new ArrayList<>();
		for (DataComponentType<?> type : display.hiddenComponents()) {
			ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
			if (key == null || !key.getNamespace().equals("minecraft")) {
				throw new IllegalArgumentException("Unregistered component: " + type);
			}
			hidden.add(key.getPath());
		}
		return new Object[]{display.hideTooltip(), hidden};
	}

	static Object lodestone(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		Optional<GlobalPos> target = Optional.ofNullable(f[0]).map(t -> {
			Object[] pos = NativeData.record(t);
			return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, NativeData.location(pos[0])), NativeValueTypes.blockPos(pos[1]));
		});
		return new LodestoneTracker(target, (Boolean)f[1]);
	}

	static Object lodestone(Object value, NativeData.Builder builder) {
		LodestoneTracker tracker = (LodestoneTracker)value;
		Object target = tracker.target()
			.map(pos -> (Object)new Object[]{pos.dimension().location().toString(), NativeValueTypes.blockPos(pos.pos())})
			.orElse(null);
		return new Object[]{target, tracker.tracked()};
	}

	static Object containerLoot(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new SeededContainerLoot(ResourceKey.create(Registries.LOOT_TABLE, NativeData.location(f[0])), ((NativeData.JavaNumber)f[1]).value().longValue());
	}

	static Object containerLoot(Object value, NativeData.Builder builder) {
		SeededContainerLoot loot = (SeededContainerLoot)value;
		return new Object[]{loot.lootTable().location().toString(), new NativeData.JavaNumber(loot.seed())};
	}

	private static IntArrayList ints(Object value) {
		IntArrayList ints = new IntArrayList();
		for (Object v : NativeData.list(value)) {
			ints.add((int)(Integer)v);
		}
		return ints;
	}

	static Object explosion(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new FireworkExplosion(NativeData.named(FireworkExplosion.Shape.class, f[0]), ints(f[1]), ints(f[2]), (Boolean)f[3], (Boolean)f[4]);
	}

	static Object explosion(Object value, NativeData.Builder builder) {
		FireworkExplosion e = (FireworkExplosion)value;
		return new Object[]{e.shape().getSerializedName(), new ArrayList<Object>(e.colors()), new ArrayList<Object>(e.fadeColors()), e.hasTrail(), e.hasTwinkle()};
	}

	static Object fireworks(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		List<FireworkExplosion> explosions = new ArrayList<>();
		for (Object e : NativeData.list(f[1])) {
			explosions.add((FireworkExplosion)explosion(e, context));
		}
		return new Fireworks((Integer)f[0], List.copyOf(explosions));
	}

	static Object fireworks(Object value, NativeData.Builder builder) {
		Fireworks fireworks = (Fireworks)value;
		List<Object> explosions = new ArrayList<>();
		for (FireworkExplosion e : fireworks.explosions()) {
			explosions.add(explosion(e, builder));
		}
		return new Object[]{fireworks.flightDuration(), explosions};
	}

	static Object writableBook(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		List<Filterable<String>> pages = new ArrayList<>();
		for (Object page : NativeData.list(f[0])) {
			Object[] p = NativeData.record(((NativeData.Variant)page).fields()[0]);
			pages.add(new Filterable<>((String)p[0], Optional.ofNullable((String)p[1])));
		}
		return new WritableBookContent(List.copyOf(pages));
	}

	static Object writableBook(Object value, NativeData.Builder builder) {
		List<Object> pages = new ArrayList<>();
		for (Filterable<String> page : ((WritableBookContent)value).pages()) {
			pages.add(new NativeData.Variant(0, new Object[]{new Object[]{page.raw(), page.filtered().orElse(null)}}));
		}
		return new Object[]{pages};
	}

	@SuppressWarnings("unchecked")
	static Object blockState(Object value, NativeData.Context context) {
		return new BlockItemStateProperties(com.google.common.collect.ImmutableMap.copyOf((Map<String, String>)(Object)NativeData.map(value)));
	}

	static Object blockState(Object value, NativeData.Builder builder) {
		return new LinkedHashMap<String, Object>(((BlockItemStateProperties)value).properties());
	}

	static Object container(Object value, NativeData.Context context) {
		List<Object> slots = NativeData.list(value);
		if (slots.isEmpty()) {
			return ItemContainerContents.EMPTY;
		}
		int size = (Integer)NativeData.record(slots.getLast())[0] + 1;
		List<ItemStack> items = new ArrayList<>(java.util.Collections.nCopies(size, ItemStack.EMPTY));
		for (Object slot : slots) {
			Object[] s = NativeData.record(slot);
			items.set((Integer)s[0], NativeItemStacks.project(s[1], context));
		}
		return ItemContainerContents.fromItems(items);
	}

	static Object container(Object value, NativeData.Builder builder) {
		List<Object> slots = new ArrayList<>();
		List<ItemStack> items = ((ItemContainerContents)value).stream().toList();
		for (int i = 0; i < items.size(); i++) {
			if (!items.get(i).isEmpty()) {
				slots.add(new Object[]{i, NativeItemStacks.unproject(items.get(i), builder)});
			}
		}
		return slots;
	}

	static Object potDecorations(Object value, NativeData.Context context) {
		List<Object> ids = NativeData.list(value);
		return new PotDecorations(item(ids.get(0)), item(ids.get(1)), item(ids.get(2)), item(ids.get(3)));
	}

	static Object potDecorations(Object value, NativeData.Builder builder) {
		List<Object> ids = new ArrayList<>();
		for (Item item : ((PotDecorations)value).ordered()) {
			ids.add(BuiltInRegistries.ITEM.getId(item));
		}
		return ids;
	}

	private static Item item(Object id) {
		return BuiltInRegistries.ITEM.byId((Integer)id);
	}

	static Object charged(Object value, NativeData.Context context) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Object stack : NativeData.list(value)) {
			stacks.add(NativeItemStacks.project(stack, context));
		}
		return ChargedProjectiles.of(stacks);
	}

	static Object charged(Object value, NativeData.Builder builder) {
		List<Object> stacks = new ArrayList<>();
		for (ItemStack stack : ((ChargedProjectiles)value).getItems()) {
			stacks.add(NativeItemStacks.unproject(stack, builder));
		}
		return stacks;
	}
}
