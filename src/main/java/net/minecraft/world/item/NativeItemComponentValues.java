package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValues.JavaCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

/** Projections for content/data/item_components.rs; holds no bootstrap state. */
public final class NativeItemComponentValues {
	private NativeItemComponentValues() {}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <E> E leaf(Object value, NativeData.Context context, JavaCodec kind) {
		return (E)context.slot(value, (Codec)kind.codec());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object leaf(Object value, NativeData.Builder builder, JavaCodec kind) {
		return builder.slot(value, (Codec)kind.codec());
	}

	public static Holder<SoundEvent> sound(Object value, NativeData.Context context) {
		return leaf(value, context, JavaCodec.SOUND_EVENT);
	}

	public static Object sound(Holder<SoundEvent> value, NativeData.Builder builder) {
		return leaf(value, builder, JavaCodec.SOUND_EVENT);
	}

	public static Repairable repairable(Object value, NativeData.Context context) {
		return new Repairable(leaf(NativeData.record(value)[0], context, JavaCodec.ITEMS));
	}

	public static Object repairable(Repairable value, NativeData.Builder builder) {
		return new Object[]{leaf(value.items(), builder, JavaCodec.ITEMS)};
	}

	public static Enchantable enchantable(Object value, NativeData.Context context) {
		return new Enchantable((Integer)NativeData.record(value)[0]);
	}

	public static Object enchantable(Enchantable value, NativeData.Builder builder) {
		return new Object[]{value.value()};
	}

	public static DamageResistant damageResistant(Object value, NativeData.Context context) {
		return new DamageResistant(TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse((String)NativeData.record(value)[0])));
	}

	public static Object damageResistant(DamageResistant value, NativeData.Builder builder) {
		return new Object[]{value.types().location().toString()};
	}

	public static ArmorTrim trim(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new ArmorTrim(leaf(f[0], context, JavaCodec.TRIM_MATERIAL), leaf(f[1], context, JavaCodec.TRIM_PATTERN));
	}

	public static Object trim(ArmorTrim value, NativeData.Builder builder) {
		return new Object[]{leaf(value.material(), builder, JavaCodec.TRIM_MATERIAL), leaf(value.pattern(), builder, JavaCodec.TRIM_PATTERN)};
	}

	public static InstrumentComponent instrument(Object value, NativeData.Context context) {
		return new InstrumentComponent(NativeItemComponentValues.<EitherHolder<Instrument>>leaf(value, context, JavaCodec.INSTRUMENT_HOLDER));
	}

	public static Object instrument(InstrumentComponent value, NativeData.Builder builder) {
		return leaf(value.instrument(), builder, JavaCodec.INSTRUMENT_HOLDER);
	}

	public static JukeboxPlayable jukebox(Object value, NativeData.Context context) {
		return new JukeboxPlayable(leaf(value, context, JavaCodec.JUKEBOX_HOLDER));
	}

	public static Object jukebox(JukeboxPlayable value, NativeData.Builder builder) {
		return leaf(value.song(), builder, JavaCodec.JUKEBOX_HOLDER);
	}

	public static ProvidesTrimMaterial providesTrim(Object value, NativeData.Context context) {
		return new ProvidesTrimMaterial(NativeItemComponentValues.<EitherHolder<TrimMaterial>>leaf(value, context, JavaCodec.PROVIDED_TRIM_MATERIAL));
	}

	public static Object providesTrim(ProvidesTrimMaterial value, NativeData.Builder builder) {
		return leaf(value.material(), builder, JavaCodec.PROVIDED_TRIM_MATERIAL);
	}

	public static BannerPatternLayers bannerPatterns(Object value, NativeData.Context context) {
		List<BannerPatternLayers.Layer> layers = new ArrayList<>();
		for (Object entry : NativeData.list(value)) {
			Object[] f = NativeData.record(entry);
			layers.add(new BannerPatternLayers.Layer(leaf(f[0], context, JavaCodec.BANNER_PATTERN), NativeData.named(DyeColor.class, f[1])));
		}
		return new BannerPatternLayers(List.copyOf(layers));
	}

	public static Object bannerPatterns(BannerPatternLayers value, NativeData.Builder builder) {
		List<Object> layers = new ArrayList<>();
		for (var layer : value.layers()) layers.add(new Object[]{leaf(layer.pattern(), builder, JavaCodec.BANNER_PATTERN), layer.color().getSerializedName()});
		return layers;
	}

	public static FoodProperties food(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		return new FoodProperties((Integer)f[0], (Float)f[1], (Boolean)f[2]);
	}

	public static Object food(FoodProperties value, NativeData.Builder builder) {
		return new Object[]{value.nutrition(), value.saturation(), value.canAlwaysEat()};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static ItemAttributeModifiers attributes(Object value, NativeData.Context context) {
		List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
		for (Object entry : NativeData.list(value)) {
			Object[] f = NativeData.record(entry);
			Object[] modifier = NativeData.record(f[1]);
			entries.add(new ItemAttributeModifiers.Entry(leaf(f[0], context, JavaCodec.ATTRIBUTE),
				new AttributeModifier(ResourceLocation.parse((String)modifier[0]), (Double)modifier[1], NativeData.named(AttributeModifier.Operation.class, modifier[2])),
				NativeData.named(EquipmentSlotGroup.class, f[2]),
				(ItemAttributeModifiers.Display)context.slotOrDefault(f[3], (Codec)JavaCodec.ITEM_ATTRIBUTE_DISPLAY.codec(), ItemAttributeModifiers.Display.attributeModifiers())));
		}
		return new ItemAttributeModifiers(List.copyOf(entries));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object attributes(ItemAttributeModifiers value, NativeData.Builder builder) {
		List<Object> entries = new ArrayList<>();
		for (var entry : value.modifiers()) {
			var modifier = entry.modifier();
			entries.add(new Object[]{leaf(entry.attribute(), builder, JavaCodec.ATTRIBUTE),
				new Object[]{modifier.id().toString(), modifier.amount(), modifier.operation().getSerializedName()}, entry.slot().getSerializedName(),
				builder.slotWithDefault(entry.display(), (Codec)JavaCodec.ITEM_ATTRIBUTE_DISPLAY.codec(), ItemAttributeModifiers.Display.attributeModifiers())});
		}
		return entries;
	}

	public static PotionContents potion(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(((NativeData.Variant)value).fields()[0]);
		List<MobEffectInstance> effects = new ArrayList<>();
		for (Object effect : NativeData.list(f[2])) effects.add(leaf(effect, context, JavaCodec.MOB_EFFECT_INSTANCE));
		return new PotionContents(Optional.ofNullable(f[0]).map(v -> NativeItemComponentValues.<Holder<Potion>>leaf(v, context, JavaCodec.POTION_HOLDER)),
			Optional.ofNullable((Integer)f[1]), List.copyOf(effects), Optional.ofNullable((String)f[3]));
	}

	public static Object potion(PotionContents value, NativeData.Builder builder) {
		List<Object> effects = new ArrayList<>();
		for (var effect : value.customEffects()) effects.add(leaf(effect, builder, JavaCodec.MOB_EFFECT_INSTANCE));
		return new NativeData.Variant(0, new Object[]{new Object[]{
			value.potion().map(v -> leaf(v, builder, JavaCodec.POTION_HOLDER)).orElse(null), value.customColor().orElse(null), effects, value.customName().orElse(null)}});
	}

	public static SuspiciousStewEffects stew(Object value, NativeData.Context context) {
		List<SuspiciousStewEffects.Entry> entries = new ArrayList<>();
		for (Object entry : NativeData.list(value)) {
			Object[] f = NativeData.record(entry);
			entries.add(new SuspiciousStewEffects.Entry(leaf(f[0], context, JavaCodec.MOB_EFFECT), (Integer)f[1]));
		}
		return new SuspiciousStewEffects(List.copyOf(entries));
	}

	public static Object stew(SuspiciousStewEffects value, NativeData.Builder builder) {
		List<Object> entries = new ArrayList<>();
		for (var entry : value.effects()) entries.add(new Object[]{leaf(entry.effect(), builder, JavaCodec.MOB_EFFECT), entry.duration()});
		return entries;
	}

	private static Object[] filterable(Object value) {
		return NativeData.record(((NativeData.Variant)value).fields()[0]);
	}

	public static WrittenBookContent writtenBook(Object value, NativeData.Context context) {
		Object[] f = NativeData.record(value);
		Object[] title = filterable(f[0]);
		List<Filterable<Component>> pages = new ArrayList<>();
		for (Object page : NativeData.list(f[3])) {
			Object[] p = filterable(page);
			pages.add(new Filterable<>(leaf(p[0], context, JavaCodec.BOOK_PAGE), Optional.ofNullable(p[1]).map(v -> leaf(v, context, JavaCodec.BOOK_PAGE))));
		}
		return new WrittenBookContent(new Filterable<>((String)title[0], Optional.ofNullable((String)title[1])),
			(String)f[1], (Integer)f[2], List.copyOf(pages), (Boolean)f[4]);
	}

	public static Object writtenBook(WrittenBookContent value, NativeData.Builder builder) {
		List<Object> pages = new ArrayList<>();
		for (var page : value.pages()) pages.add(new NativeData.Variant(0, new Object[]{new Object[]{
			leaf(page.raw(), builder, JavaCodec.BOOK_PAGE), page.filtered().map(v -> leaf(v, builder, JavaCodec.BOOK_PAGE)).orElse(null)}}));
		return new Object[]{new NativeData.Variant(0, new Object[]{new Object[]{value.title().raw(), value.title().filtered().orElse(null)}}),
			value.author(), value.generation(), pages, value.resolved()};
	}

	public static Bees bees(Object value, NativeData.Context context) {
		List<BeehiveBlockEntity.Occupant> bees = new ArrayList<>();
		for (Object entry : NativeData.list(value)) {
			Object[] f = NativeData.record(entry);
			bees.add(new BeehiveBlockEntity.Occupant(NativeItemComponentValues.<TypedEntityData<EntityType<?>>>leaf(f[0], context, JavaCodec.BEE_ENTITY_DATA), (Integer)f[1], (Integer)f[2]));
		}
		return new Bees(List.copyOf(bees));
	}

	public static Object bees(Bees value, NativeData.Builder builder) {
		List<Object> bees = new ArrayList<>();
		for (var bee : value.bees()) bees.add(new Object[]{leaf(bee.entityData(), builder, JavaCodec.BEE_ENTITY_DATA), bee.ticksInHive(), bee.minTicksInHive()});
		return bees;
	}
}
