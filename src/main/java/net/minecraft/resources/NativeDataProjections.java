package net.minecraft.resources;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.ChickenVariant;
import net.minecraft.world.entity.animal.CowVariant;
import net.minecraft.world.entity.animal.PigVariant;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.MoonBrightnessCheck;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnCondition;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.entity.variant.StructureCheck;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.enchantment.providers.EnchantmentsByCost;
import net.minecraft.world.item.enchantment.providers.EnchantmentsByCostWithDifficulty;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * Java projections of Rust-decoded definitions, one per registry, reading
 * fields in `content/data/registries.rs` schema order. Biomes project in
 * {@code NativeBiomes}.
 */
public final class NativeDataProjections {
	private NativeDataProjections() {
	}

	private static final com.mojang.serialization.Codec<HolderSet<net.minecraft.world.item.Item>> ITEMS = RegistryCodecs.homogeneousList(Registries.ITEM);
	private static final com.mojang.serialization.Codec<HolderSet<Enchantment>> ENCHANTMENTS = RegistryCodecs.homogeneousList(Registries.ENCHANTMENT);
	private static final com.mojang.serialization.Codec<HolderSet<net.minecraft.world.level.levelgen.structure.Structure>> STRUCTURES = RegistryCodecs.homogeneousList(Registries.STRUCTURE);
	private static final com.mojang.serialization.Codec<HolderSet<net.minecraft.world.level.biome.Biome>> BIOMES = RegistryCodecs.homogeneousList(Registries.BIOME);

	private static Component text(NativeData.Context context, Object slot) {
		return context.slot(slot, ComponentSerialization.CODEC);
	}

	public static PaintingVariant painting(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new PaintingVariant(
			(Integer)f[0],
			(Integer)f[1],
			NativeData.location(f[2]),
			context.optionalSlot(f[3], ComponentSerialization.CODEC),
			context.optionalSlot(f[4], ComponentSerialization.CODEC)
		);
	}

	public static DamageType damageType(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new DamageType(
			(String)f[0],
			NativeData.named(DamageScaling.class, f[1]),
			(Float)f[2],
			NativeData.named(DamageEffects.class, f[3]),
			NativeData.named(DeathMessageType.class, f[4])
		);
	}

	public static BannerPattern bannerPattern(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new BannerPattern(NativeData.location(f[0]), (String)f[1]);
	}

	public static JukeboxSong jukeboxSong(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new JukeboxSong(context.sound(f[0]), text(context, f[1]), (Float)f[2], (Integer)f[3]);
	}

	public static TrimPattern trimPattern(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new TrimPattern(NativeData.location(f[0]), text(context, f[1]), (Boolean)f[2]);
	}

	public static TrimMaterial trimMaterial(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		Object[] assets = NativeData.record(f[0]);
		Map<ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset>, MaterialAssetGroup.AssetInfo> overrides = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : NativeData.map(assets[1]).entrySet()) {
			overrides.put(
				ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, NativeData.location(entry.getKey())),
				new MaterialAssetGroup.AssetInfo((String)entry.getValue())
			);
		}
		MaterialAssetGroup group = new MaterialAssetGroup(new MaterialAssetGroup.AssetInfo((String)assets[0]), com.google.common.collect.ImmutableMap.copyOf(overrides));
		return new TrimMaterial(group, text(context, f[1]));
	}

	public static Instrument instrument(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new Instrument(context.sound(f[0]), (Float)f[1], (Float)f[2], text(context, f[3]));
	}

	private static Enchantment.Cost cost(Object value) {
		Object[] f = NativeData.record(value);
		return new Enchantment.Cost((Integer)f[0], (Integer)f[1]);
	}

	public static Enchantment enchantment(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		Object[] d = NativeData.record(f[1]);
		List<EquipmentSlotGroup> slots = new ArrayList<>();
		for (Object slot : NativeData.list(d[7])) {
			slots.add(NativeData.named(EquipmentSlotGroup.class, slot));
		}
		Enchantment.EnchantmentDefinition definition = new Enchantment.EnchantmentDefinition(
			context.holderSet(d[0], Registries.ITEM, ITEMS),
			context.optionalHolderSet(d[1], Registries.ITEM, ITEMS),
			(Integer)d[2],
			(Integer)d[3],
			cost(d[4]),
			cost(d[5]),
			(Integer)d[6],
			List.copyOf(slots)
		);
		return new Enchantment(
			text(context, f[0]),
			definition,
			context.holderSetOrDefault(f[2], Registries.ENCHANTMENT, ENCHANTMENTS, HolderSet.direct()),
			net.minecraft.world.item.enchantment.NativeEnchantmentEffects.effects(f[3], context)
		);
	}

	public static EnchantmentProvider enchantmentProvider(Object value, NativeData.Context context) {
		NativeData.Variant variant = (NativeData.Variant)value;
		Object[] f = variant.fields();
		return switch (variant.index()) {
			case 0 -> new EnchantmentsByCost(context.holderSet(f[0], Registries.ENCHANTMENT, ENCHANTMENTS), IntProvider.project(f[1]));
			case 1 -> new EnchantmentsByCostWithDifficulty(context.holderSet(f[0], Registries.ENCHANTMENT, ENCHANTMENTS), (Integer)f[1], (Integer)f[2]);
			default -> new SingleEnchantment(context.slot(f[0], Enchantment.CODEC), IntProvider.project(f[1]));
		};
	}

	private static SpawnPrioritySelectors selectors(Object value, NativeData.Context context) {
		List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors = new ArrayList<>();
		for (Object entry : NativeData.list(value)) {
			Object[] f = NativeData.record(entry);
			Optional<SpawnCondition> condition = Optional.ofNullable(f[0]).map(c -> condition((NativeData.Variant)c, context));
			selectors.add(new PriorityProvider.Selector<>(condition, (Integer)f[1]));
		}
		return new SpawnPrioritySelectors(List.copyOf(selectors));
	}

	private static SpawnCondition condition(NativeData.Variant variant, NativeData.Context context) {
		Object[] f = variant.fields();
		return switch (variant.index()) {
			case 0 -> new StructureCheck(context.holderSet(f[0], Registries.STRUCTURE, STRUCTURES));
			case 1 -> new MoonBrightnessCheck(bounds(f[0]));
			default -> new BiomeCheck(context.holderSet(f[0], Registries.BIOME, BIOMES));
		};
	}

	private static MinMaxBounds.Doubles bounds(Object value) {
		Object[] f = NativeData.record(value);
		Double min = (Double)f[0];
		Double max = (Double)f[1];
		if (min != null && max != null) {
			return MinMaxBounds.Doubles.between(min, max);
		} else if (min != null) {
			return MinMaxBounds.Doubles.atLeast(min);
		} else if (max != null) {
			return MinMaxBounds.Doubles.atMost(max);
		}
		return MinMaxBounds.Doubles.ANY;
	}

	private static ClientAsset.ResourceTexture texture(Object value) {
		return new ClientAsset.ResourceTexture(NativeData.location(value));
	}

	private static SpawnPrioritySelectors selectorsOrEmpty(Object[] f, int index, NativeData.Context context) {
		return f.length > index ? selectors(f[index], context) : SpawnPrioritySelectors.EMPTY;
	}

	public static CatVariant cat(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new CatVariant(texture(f[0]), selectorsOrEmpty(f, 1, context));
	}

	public static FrogVariant frog(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new FrogVariant(texture(f[0]), selectorsOrEmpty(f, 1, context));
	}

	public static WolfVariant wolf(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		Object[] a = NativeData.record(f[0]);
		return new WolfVariant(new WolfVariant.AssetInfo(texture(a[0]), texture(a[1]), texture(a[2])), selectorsOrEmpty(f, 1, context));
	}

	public static WolfSoundVariant wolfSound(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new WolfSoundVariant(context.sound(f[0]), context.sound(f[1]), context.sound(f[2]), context.sound(f[3]), context.sound(f[4]), context.sound(f[5]));
	}

	public static PigVariant pig(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new PigVariant(new ModelAndTexture<>(NativeData.named(PigVariant.ModelType.class, f[0]), NativeData.location(f[1])), selectorsOrEmpty(f, 2, context));
	}

	public static CowVariant cow(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new CowVariant(new ModelAndTexture<>(NativeData.named(CowVariant.ModelType.class, f[0]), NativeData.location(f[1])), selectorsOrEmpty(f, 2, context));
	}

	public static ChickenVariant chicken(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new ChickenVariant(new ModelAndTexture<>(NativeData.named(ChickenVariant.ModelType.class, f[0]), NativeData.location(f[1])), selectorsOrEmpty(f, 2, context));
	}

	public static ZombieNautilusVariant zombieNautilus(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new ZombieNautilusVariant(
			new ModelAndTexture<>(NativeData.named(ZombieNautilusVariant.ModelType.class, f[0]), NativeData.location(f[1])), selectorsOrEmpty(f, 2, context)
		);
	}

	// Unprojections: each object back to its decoded value, in schema field order.

	public static Object paintingOut(PaintingVariant v, NativeData.Builder b) {
		return new Object[]{v.width(), v.height(), v.assetId().toString(), b.optionalSlot(v.title(), ComponentSerialization.CODEC), b.optionalSlot(v.author(), ComponentSerialization.CODEC)};
	}

	public static Object damageTypeOut(DamageType v, NativeData.Builder b) {
		return new Object[]{v.msgId(), v.scaling().getSerializedName(), v.exhaustion(), v.effects().getSerializedName(), v.deathMessageType().getSerializedName()};
	}

	public static Object bannerPatternOut(BannerPattern v, NativeData.Builder b) {
		return new Object[]{v.assetId().toString(), v.translationKey()};
	}

	public static Object jukeboxSongOut(JukeboxSong v, NativeData.Builder b) {
		return new Object[]{b.sound(v.soundEvent()), b.slot(v.description(), ComponentSerialization.CODEC), v.lengthInSeconds(), v.comparatorOutput()};
	}

	public static Object trimPatternOut(TrimPattern v, NativeData.Builder b) {
		return new Object[]{v.assetId().toString(), b.slot(v.description(), ComponentSerialization.CODEC), v.decal()};
	}

	public static Object trimMaterialOut(TrimMaterial v, NativeData.Builder b) {
		Map<String, Object> overrides = new LinkedHashMap<>();
		v.assets().overrides().forEach((key, info) -> overrides.put(key.location().toString(), info.suffix()));
		return new Object[]{new Object[]{v.assets().base().suffix(), overrides}, b.slot(v.description(), ComponentSerialization.CODEC)};
	}

	public static Object instrumentOut(Instrument v, NativeData.Builder b) {
		return new Object[]{b.sound(v.soundEvent()), v.useDuration(), v.range(), b.slot(v.description(), ComponentSerialization.CODEC)};
	}

	private static Object costOut(Enchantment.Cost cost) {
		return new Object[]{cost.base(), cost.perLevelAboveFirst()};
	}

	public static Object enchantmentOut(Enchantment v, NativeData.Builder b) {
		Enchantment.EnchantmentDefinition d = v.definition();
		List<Object> slots = new ArrayList<>();
		d.slots().forEach(slot -> slots.add(slot.getSerializedName()));
		Object definition = new Object[]{
			b.slot(d.supportedItems(), ITEMS),
			b.optionalSlot(d.primaryItems(), ITEMS),
			d.weight(),
			d.maxLevel(),
			costOut(d.minCost()),
			costOut(d.maxCost()),
			d.anvilCost(),
			slots
		};
		return new Object[]{
			b.slot(v.description(), ComponentSerialization.CODEC),
			definition,
			b.slotWithDefault(v.exclusiveSet(), ENCHANTMENTS, HolderSet.direct()),
			net.minecraft.world.item.enchantment.NativeEnchantmentEffects.effectsOut(v.effects(), b)
		};
	}

	public static Object enchantmentProviderOut(EnchantmentProvider v, NativeData.Builder b) {
		return switch (v) {
			case EnchantmentsByCost p -> new NativeData.Variant(0, new Object[]{b.slot(p.enchantments(), ENCHANTMENTS), IntProvider.unproject(p.cost())});
			case EnchantmentsByCostWithDifficulty p -> new NativeData.Variant(1, new Object[]{b.slot(p.enchantments(), ENCHANTMENTS), p.minCost(), p.maxCostSpan()});
			case SingleEnchantment p -> new NativeData.Variant(2, new Object[]{b.slot(p.enchantment(), Enchantment.CODEC), IntProvider.unproject(p.level())});
			default -> throw new IllegalArgumentException("Unknown enchantment provider " + v);
		};
	}

	private static Object selectorsOut(SpawnPrioritySelectors selectors, NativeData.Builder b) {
		List<Object> out = new ArrayList<>();
		for (PriorityProvider.Selector<SpawnContext, SpawnCondition> selector : selectors.selectors()) {
			out.add(new Object[]{selector.condition().map(c -> conditionOut(c, b)).orElse(null), selector.priority()});
		}
		return out;
	}

	private static Object conditionOut(SpawnCondition condition, NativeData.Builder b) {
		return switch (condition) {
			case StructureCheck c -> new NativeData.Variant(0, new Object[]{b.slot(c.requiredStructures(), STRUCTURES)});
			case MoonBrightnessCheck c -> new NativeData.Variant(
				1, new Object[]{new Object[]{c.range().bounds().min().orElse(null), c.range().bounds().max().orElse(null)}}
			);
			case BiomeCheck c -> new NativeData.Variant(2, new Object[]{b.slot(c.requiredBiomes(), BIOMES)});
			default -> throw new IllegalArgumentException("Unknown spawn condition " + condition);
		};
	}

	public static Object catOut(CatVariant v, NativeData.Builder b) {
		return new Object[]{v.assetInfo().id().toString(), selectorsOut(v.spawnConditions(), b)};
	}

	public static Object frogOut(FrogVariant v, NativeData.Builder b) {
		return new Object[]{v.assetInfo().id().toString(), selectorsOut(v.spawnConditions(), b)};
	}

	public static Object wolfOut(WolfVariant v, NativeData.Builder b) {
		WolfVariant.AssetInfo a = v.assetInfo();
		return new Object[]{new Object[]{a.wild().id().toString(), a.tame().id().toString(), a.angry().id().toString()}, selectorsOut(v.spawnConditions(), b)};
	}

	public static Object wolfSoundOut(WolfSoundVariant v, NativeData.Builder b) {
		return new Object[]{b.sound(v.ambientSound()), b.sound(v.deathSound()), b.sound(v.growlSound()), b.sound(v.hurtSound()), b.sound(v.pantSound()), b.sound(v.whineSound())};
	}

	private static <T extends net.minecraft.util.StringRepresentable> Object modelOut(ModelAndTexture<T> model, SpawnPrioritySelectors selectors, NativeData.Builder b) {
		return new Object[]{model.model().getSerializedName(), model.asset().id().toString(), selectorsOut(selectors, b)};
	}

	public static Object pigOut(PigVariant v, NativeData.Builder b) {
		return modelOut(v.modelAndTexture(), v.spawnConditions(), b);
	}

	public static Object cowOut(CowVariant v, NativeData.Builder b) {
		return modelOut(v.modelAndTexture(), v.spawnConditions(), b);
	}

	public static Object chickenOut(ChickenVariant v, NativeData.Builder b) {
		return modelOut(v.modelAndTexture(), v.spawnConditions(), b);
	}

	public static Object zombieNautilusOut(ZombieNautilusVariant v, NativeData.Builder b) {
		return modelOut(v.modelAndTexture(), v.spawnConditions(), b);
	}
}
