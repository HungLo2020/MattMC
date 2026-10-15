package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.particles.NativeParticles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValues;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Unit;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.ApplyMobEffect;
import net.minecraft.world.item.enchantment.effects.ChangeItemDamage;
import net.minecraft.world.item.enchantment.effects.DamageEntity;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.item.enchantment.effects.ExplodeEffect;
import net.minecraft.world.item.enchantment.effects.Ignite;
import net.minecraft.world.item.enchantment.effects.MultiplyValue;
import net.minecraft.world.item.enchantment.effects.PlaySoundEffect;
import net.minecraft.world.item.enchantment.effects.RemoveBinomial;
import net.minecraft.world.item.enchantment.effects.ReplaceBlock;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.item.enchantment.effects.RunFunction;
import net.minecraft.world.item.enchantment.effects.SetBlockProperties;
import net.minecraft.world.item.enchantment.effects.SetValue;
import net.minecraft.world.item.enchantment.effects.SpawnParticlesEffect;
import net.minecraft.world.item.enchantment.effects.SummonEntityEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

/**
 * Enchantment effect definitions as projections of Rust's decoded values
 * (`content/data/enchant.rs`): the effects map, its components and every
 * value, entity and location effect. Values Java still owns (requirements
 * with their context validation, damage types, attributes, game events,
 * block state providers and predicates, explosion particles) are slots its
 * codecs decoded.
 */
public final class NativeEnchantmentEffects {
	private NativeEnchantmentEffects() {
	}

	@SuppressWarnings("unchecked")
	private static <A> Codec<A> java(NativeValues.JavaCodec codec) {
		return (Codec<A>)codec.codec();
	}

	// The effects map.

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static DataComponentMap effects(Object value, NativeData.Context context) {
		DataComponentMap.Builder builder = DataComponentMap.builder();
		for (Map.Entry<String, Object> entry : NativeData.map(value).entrySet()) {
			DataComponentType type = BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getValue(ResourceLocation.withDefaultNamespace(entry.getKey()));
			builder.set(type, component(entry.getKey(), entry.getValue(), context));
		}
		return builder.build();
	}

	public static Object effectsOut(DataComponentMap map, NativeData.Builder b) {
		Map<String, Object> out = new java.util.LinkedHashMap<>();
		for (TypedDataComponent<?> typed : map) {
			if (!typed.type().isTransient()) {
				String path = BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(typed.type()).getPath();
				out.put(path, componentOut(path, typed.value(), b));
			}
		}
		return out;
	}

	private static Object component(String path, Object value, NativeData.Context context) {
		return switch (path) {
			case "damage_protection", "damage", "smash_damage_per_fallen_block", "knockback", "armor_effectiveness" ->
				conditionals(value, context, NativeValues.JavaCodec.CONDITION_ENCHANTED_DAMAGE, NativeEnchantmentEffects::valueEffect);
			case "item_damage", "ammo_use", "projectile_piercing", "block_experience", "repair_with_xp" ->
				conditionals(value, context, NativeValues.JavaCodec.CONDITION_ENCHANTED_ITEM, NativeEnchantmentEffects::valueEffect);
			case "projectile_spread", "projectile_count", "trident_return_acceleration", "fishing_time_reduction", "fishing_luck_bonus", "mob_experience" ->
				conditionals(value, context, NativeValues.JavaCodec.CONDITION_ENCHANTED_ENTITY, NativeEnchantmentEffects::valueEffect);
			case "damage_immunity" -> conditionals(value, context, NativeValues.JavaCodec.CONDITION_ENCHANTED_DAMAGE, (v, c) -> DamageImmunity.INSTANCE);
			case "post_attack" -> targeted(value, context);
			case "hit_block" -> conditionals(value, context, NativeValues.JavaCodec.CONDITION_HIT_BLOCK, NativeEnchantmentEffects::entityEffect);
			case "tick", "projectile_spawned" -> conditionals(value, context, NativeValues.JavaCodec.CONDITION_ENCHANTED_ENTITY, NativeEnchantmentEffects::entityEffect);
			case "location_changed" -> conditionals(value, context, NativeValues.JavaCodec.CONDITION_ENCHANTED_LOCATION, NativeEnchantmentEffects::locationEffect);
			case "attributes" -> {
				List<EnchantmentAttributeEffect> list = new ArrayList<>();
				for (Object item : NativeData.list(value)) {
					list.add(attribute(NativeData.record(item), context));
				}
				yield com.google.common.collect.ImmutableList.copyOf(list);
			}
			case "equipment_drops" -> {
				List<TargetedConditionalEffect<EnchantmentValueEffect>> list = new ArrayList<>();
				for (Object item : NativeData.list(value)) {
					Object[] f = NativeData.record(item);
					list.add(new TargetedConditionalEffect<>(
						target(f[0]), EnchantmentTarget.VICTIM, valueEffect(f[1], context), requirements(f[2], context, NativeValues.JavaCodec.CONDITION_ENCHANTED_DAMAGE)
					));
				}
				yield com.google.common.collect.ImmutableList.copyOf(list);
			}
			case "crossbow_charge_time", "trident_spin_attack_strength" -> valueEffect(value, context);
			case "crossbow_charging_sounds" -> {
				List<CrossbowItem.ChargingSounds> list = new ArrayList<>();
				for (Object item : NativeData.list(value)) {
					Object[] f = NativeData.record(item);
					list.add(new CrossbowItem.ChargingSounds(optionalSound(f[0], context), optionalSound(f[1], context), optionalSound(f[2], context)));
				}
				yield com.google.common.collect.ImmutableList.copyOf(list);
			}
			case "trident_sound" -> {
				List<Holder<SoundEvent>> list = new ArrayList<>();
				for (Object item : NativeData.list(value)) {
					list.add(sound(context, item));
				}
				yield com.google.common.collect.ImmutableList.copyOf(list);
			}
			case "prevent_equipment_drop", "prevent_armor_change" -> Unit.INSTANCE;
			default -> throw new IllegalArgumentException("Unknown enchantment effect component " + path);
		};
	}

	@SuppressWarnings("unchecked")
	private static Object componentOut(String path, Object value, NativeData.Builder b) {
		return switch (path) {
			case "damage_protection", "damage", "smash_damage_per_fallen_block", "knockback", "armor_effectiveness", "item_damage", "ammo_use",
				"projectile_piercing", "block_experience", "repair_with_xp", "projectile_spread", "projectile_count", "trident_return_acceleration",
				"fishing_time_reduction", "fishing_luck_bonus", "mob_experience" ->
				conditionalsOut((List<ConditionalEffect<EnchantmentValueEffect>>)value, b, codecFor(path), NativeEnchantmentEffects::valueEffectOut);
			case "damage_immunity" -> conditionalsOut((List<ConditionalEffect<DamageImmunity>>)value, b, codecFor(path), (v, bb) -> new Object[0]);
			case "post_attack" -> {
				List<Object> out = new ArrayList<>();
				for (TargetedConditionalEffect<EnchantmentEntityEffect> effect : (List<TargetedConditionalEffect<EnchantmentEntityEffect>>)value) {
					out.add(new Object[]{
						effect.enchanted().getSerializedName(), effect.affected().getSerializedName(), entityEffectOut(effect.effect(), b),
						requirementsOut(effect.requirements(), b, codecFor(path))
					});
				}
				yield out;
			}
			case "hit_block", "tick", "projectile_spawned" ->
				conditionalsOut((List<ConditionalEffect<EnchantmentEntityEffect>>)value, b, codecFor(path), NativeEnchantmentEffects::entityEffectOut);
			case "location_changed" ->
				conditionalsOut((List<ConditionalEffect<EnchantmentLocationBasedEffect>>)value, b, codecFor(path), NativeEnchantmentEffects::locationEffectOut);
			case "attributes" -> {
				List<Object> out = new ArrayList<>();
				for (EnchantmentAttributeEffect effect : (List<EnchantmentAttributeEffect>)value) {
					out.add(attributeOut(effect, b));
				}
				yield out;
			}
			case "equipment_drops" -> {
				List<Object> out = new ArrayList<>();
				for (TargetedConditionalEffect<EnchantmentValueEffect> effect : (List<TargetedConditionalEffect<EnchantmentValueEffect>>)value) {
					out.add(new Object[]{effect.enchanted().getSerializedName(), valueEffectOut(effect.effect(), b), requirementsOut(effect.requirements(), b, codecFor(path))});
				}
				yield out;
			}
			case "crossbow_charge_time", "trident_spin_attack_strength" -> valueEffectOut((EnchantmentValueEffect)value, b);
			case "crossbow_charging_sounds" -> {
				List<Object> out = new ArrayList<>();
				for (CrossbowItem.ChargingSounds sounds : (List<CrossbowItem.ChargingSounds>)value) {
					out.add(new Object[]{
						sounds.start().map(s -> b.slot(s, SoundEvent.CODEC)).orElse(null), sounds.mid().map(s -> b.slot(s, SoundEvent.CODEC)).orElse(null), sounds.end().map(s -> b.slot(s, SoundEvent.CODEC)).orElse(null)
					});
				}
				yield out;
			}
			case "trident_sound" -> {
				List<Object> out = new ArrayList<>();
				for (Holder<SoundEvent> sound : (List<Holder<SoundEvent>>)value) {
					out.add(b.slot(sound, SoundEvent.CODEC));
				}
				yield out;
			}
			case "prevent_equipment_drop", "prevent_armor_change" -> new Object[0];
			default -> throw new IllegalArgumentException("Unknown enchantment effect component " + path);
		};
	}

	/** The requirements codec a component's conditions use (its loot context). */
	private static NativeValues.JavaCodec codecFor(String path) {
		return switch (path) {
			case "item_damage", "ammo_use", "projectile_piercing", "block_experience", "repair_with_xp" -> NativeValues.JavaCodec.CONDITION_ENCHANTED_ITEM;
			case "projectile_spread", "projectile_count", "trident_return_acceleration", "fishing_time_reduction", "fishing_luck_bonus", "mob_experience",
				"tick", "projectile_spawned" -> NativeValues.JavaCodec.CONDITION_ENCHANTED_ENTITY;
			case "hit_block" -> NativeValues.JavaCodec.CONDITION_HIT_BLOCK;
			case "location_changed" -> NativeValues.JavaCodec.CONDITION_ENCHANTED_LOCATION;
			default -> NativeValues.JavaCodec.CONDITION_ENCHANTED_DAMAGE;
		};
	}

	@FunctionalInterface
	private interface Projector<T> {
		T project(Object value, NativeData.Context context);
	}

	@FunctionalInterface
	private interface Unprojector<T> {
		Object unproject(T value, NativeData.Builder builder);
	}

	private static <T> List<ConditionalEffect<T>> conditionals(Object value, NativeData.Context context, NativeValues.JavaCodec condition, Projector<T> effect) {
		List<ConditionalEffect<T>> list = new ArrayList<>();
		for (Object item : NativeData.list(value)) {
			Object[] f = NativeData.record(item);
			list.add(new ConditionalEffect<>(effect.project(f[0], context), requirements(f[1], context, condition)));
		}
		return com.google.common.collect.ImmutableList.copyOf(list);
	}

	private static <T> List<Object> conditionalsOut(List<ConditionalEffect<T>> value, NativeData.Builder b, NativeValues.JavaCodec condition, Unprojector<T> effect) {
		List<Object> out = new ArrayList<>();
		for (ConditionalEffect<T> conditional : value) {
			out.add(new Object[]{effect.unproject(conditional.effect(), b), requirementsOut(conditional.requirements(), b, condition)});
		}
		return out;
	}

	private static List<TargetedConditionalEffect<EnchantmentEntityEffect>> targeted(Object value, NativeData.Context context) {
		List<TargetedConditionalEffect<EnchantmentEntityEffect>> list = new ArrayList<>();
		for (Object item : NativeData.list(value)) {
			Object[] f = NativeData.record(item);
			list.add(new TargetedConditionalEffect<>(
				target(f[0]), target(f[1]), entityEffect(f[2], context), requirements(f[3], context, NativeValues.JavaCodec.CONDITION_ENCHANTED_DAMAGE)
			));
		}
		return com.google.common.collect.ImmutableList.copyOf(list);
	}

	private static Optional<LootItemCondition> requirements(Object value, NativeData.Context context, NativeValues.JavaCodec condition) {
		return context.optionalSlot(value, java(condition));
	}

	private static Object requirementsOut(Optional<LootItemCondition> value, NativeData.Builder b, NativeValues.JavaCodec condition) {
		return value.map(c -> (Object)b.slot(c, condition.codec())).orElse(null);
	}

	private static Holder<SoundEvent> sound(NativeData.Context context, Object value) {
		return context.slot(value, SoundEvent.CODEC);
	}

	private static EnchantmentTarget target(Object value) {
		return NativeData.named(EnchantmentTarget.class, value);
	}

	private static Optional<Holder<SoundEvent>> optionalSound(Object value, NativeData.Context context) {
		return value == null ? Optional.empty() : Optional.of(sound(context, value));
	}

	// Value effects (`enchantment_value_effect_type` order).

	public static EnchantmentValueEffect valueEffect(Object value, NativeData.Context context) {
		NativeData.Variant variant = (NativeData.Variant)value;
		Object[] f = variant.fields();
		return switch (variant.index()) {
			case 0 -> new AddValue(LevelBasedValue.project(f[0]));
			case 1 -> {
				List<EnchantmentValueEffect> effects = new ArrayList<>();
				for (Object item : NativeData.list(f[0])) {
					effects.add(valueEffect(item, context));
				}
				yield new AllOf.ValueEffects(com.google.common.collect.ImmutableList.copyOf(effects));
			}
			case 2 -> new MultiplyValue(LevelBasedValue.project(f[0]));
			case 3 -> new RemoveBinomial(LevelBasedValue.project(f[0]));
			default -> new SetValue(LevelBasedValue.project(f[0]));
		};
	}

	public static Object valueEffectOut(EnchantmentValueEffect value, NativeData.Builder b) {
		return switch (value) {
			case AddValue v -> new NativeData.Variant(0, new Object[]{LevelBasedValue.unproject(v.value())});
			case AllOf.ValueEffects v -> {
				List<Object> effects = new ArrayList<>();
				v.effects().forEach(e -> effects.add(valueEffectOut(e, b)));
				yield new NativeData.Variant(1, new Object[]{effects});
			}
			case MultiplyValue v -> new NativeData.Variant(2, new Object[]{LevelBasedValue.unproject(v.factor())});
			case RemoveBinomial v -> new NativeData.Variant(3, new Object[]{LevelBasedValue.unproject(v.chance())});
			case SetValue v -> new NativeData.Variant(4, new Object[]{LevelBasedValue.unproject(v.value())});
			default -> throw new IllegalArgumentException("Unknown enchantment value effect " + value);
		};
	}

	// Entity and location effects. Location effects add `attribute` third;
	// entity effect indexes shift past it.

	public static EnchantmentEntityEffect entityEffect(Object value, NativeData.Context context) {
		NativeData.Variant variant = (NativeData.Variant)value;
		int index = variant.index();
		if (index == 0) {
			List<EnchantmentEntityEffect> effects = new ArrayList<>();
			for (Object item : NativeData.list(variant.fields()[0])) {
				effects.add(entityEffect(item, context));
			}
			return new AllOf.EntityEffects(com.google.common.collect.ImmutableList.copyOf(effects));
		}
		return (EnchantmentEntityEffect)shared(index < 2 ? index : index + 1, variant.fields(), context);
	}

	public static EnchantmentLocationBasedEffect locationEffect(Object value, NativeData.Context context) {
		NativeData.Variant variant = (NativeData.Variant)value;
		Object[] f = variant.fields();
		return switch (variant.index()) {
			case 0 -> {
				List<EnchantmentLocationBasedEffect> effects = new ArrayList<>();
				for (Object item : NativeData.list(f[0])) {
					effects.add(locationEffect(item, context));
				}
				yield new AllOf.LocationBasedEffects(com.google.common.collect.ImmutableList.copyOf(effects));
			}
			case 2 -> attribute(f, context);
			default -> shared(variant.index(), f, context);
		};
	}

	/** The cases entity and location effects share, by location index. */
	private static EnchantmentLocationBasedEffect shared(int index, Object[] f, NativeData.Context context) {
		return switch (index) {
			case 1 -> new ApplyMobEffect(
				context.slot(f[0], NativeEnchantmentEffects.<HolderSet<MobEffect>>java(NativeValues.JavaCodec.MOB_EFFECTS)),
				LevelBasedValue.project(f[1]), LevelBasedValue.project(f[2]), LevelBasedValue.project(f[3]), LevelBasedValue.project(f[4])
			);
			case 3 -> new ChangeItemDamage(LevelBasedValue.project(f[0]));
			case 4 -> new DamageEntity(LevelBasedValue.project(f[0]), LevelBasedValue.project(f[1]), context.slot(f[2], NativeEnchantmentEffects.<Holder<DamageType>>java(NativeValues.JavaCodec.DAMAGE_TYPE)));
			case 5 -> new ExplodeEffect(
				(Boolean)f[0],
				context.optionalSlot(f[1], NativeEnchantmentEffects.<Holder<DamageType>>java(NativeValues.JavaCodec.DAMAGE_TYPE)),
				Optional.ofNullable(f[2]).map(LevelBasedValue::project),
				context.optionalSlot(f[3], NativeEnchantmentEffects.<HolderSet<net.minecraft.world.level.block.Block>>java(NativeValues.JavaCodec.BLOCKS)),
				vec3(f[4]),
				LevelBasedValue.project(f[5]),
				(Boolean)f[6],
				NativeData.named(Level.ExplosionInteraction.class, f[7]),
				NativeParticles.project(f[8], context),
				NativeParticles.project(f[9], context),
				context.slotOrDefault(f[10], NativeEnchantmentEffects.<WeightedList<net.minecraft.core.particles.ExplosionParticleInfo>>java(NativeValues.JavaCodec.EXPLOSION_PARTICLES), WeightedList.of()),
				sound(context, f[11])
			);
			case 6 -> new Ignite(LevelBasedValue.project(f[0]));
			case 7 -> new PlaySoundEffect(sound(context, f[0]), FloatProvider.project(f[1]), FloatProvider.project(f[2]));
			case 8 -> new ReplaceBlock(
				vec3i(f[0]),
				context.optionalSlot(f[1], NativeEnchantmentEffects.<BlockPredicate>java(NativeValues.JavaCodec.BLOCK_PREDICATE)),
				context.slot(f[2], NativeEnchantmentEffects.<BlockStateProvider>java(NativeValues.JavaCodec.BLOCK_STATE_PROVIDER)),
				context.optionalSlot(f[3], NativeEnchantmentEffects.<Holder<GameEvent>>java(NativeValues.JavaCodec.GAME_EVENT))
			);
			case 9 -> new ReplaceDisk(
				LevelBasedValue.project(f[0]),
				LevelBasedValue.project(f[1]),
				vec3i(f[2]),
				context.optionalSlot(f[3], NativeEnchantmentEffects.<BlockPredicate>java(NativeValues.JavaCodec.BLOCK_PREDICATE)),
				context.slot(f[4], NativeEnchantmentEffects.<BlockStateProvider>java(NativeValues.JavaCodec.BLOCK_STATE_PROVIDER)),
				context.optionalSlot(f[5], NativeEnchantmentEffects.<Holder<GameEvent>>java(NativeValues.JavaCodec.GAME_EVENT))
			);
			case 10 -> new RunFunction(NativeData.location(f[0]));
			case 11 -> new SetBlockProperties(
				new BlockItemStateProperties(stringMap(f[0])),
				vec3i(f[1]),
				context.optionalSlot(f[2], NativeEnchantmentEffects.<Holder<GameEvent>>java(NativeValues.JavaCodec.GAME_EVENT))
			);
			case 12 -> new SpawnParticlesEffect(
				NativeParticles.project(f[0], context), position(f[1]), position(f[2]), velocity(f[3]), velocity(f[4]), FloatProvider.project(f[5])
			);
			case 13 -> new SummonEntityEffect(context.slot(f[0], NativeEnchantmentEffects.<HolderSet<EntityType<?>>>java(NativeValues.JavaCodec.ENTITY_TYPES)), (Boolean)f[1]);
			default -> throw new IllegalArgumentException("Unknown enchantment effect case " + index);
		};
	}

	public static Object entityEffectOut(EnchantmentEntityEffect value, NativeData.Builder b) {
		if (value instanceof AllOf.EntityEffects all) {
			List<Object> effects = new ArrayList<>();
			all.effects().forEach(e -> effects.add(entityEffectOut(e, b)));
			return new NativeData.Variant(0, new Object[]{effects});
		}
		NativeData.Variant shared = sharedOut(value, b);
		int index = shared.index();
		return new NativeData.Variant(index < 2 ? index : index - 1, shared.fields());
	}

	public static Object locationEffectOut(EnchantmentLocationBasedEffect value, NativeData.Builder b) {
		return switch (value) {
			case AllOf.LocationBasedEffects all -> {
				List<Object> effects = new ArrayList<>();
				all.effects().forEach(e -> effects.add(locationEffectOut(e, b)));
				yield new NativeData.Variant(0, new Object[]{effects});
			}
			case EnchantmentAttributeEffect attribute -> new NativeData.Variant(2, attributeOut(attribute, b));
			default -> sharedOut(value, b);
		};
	}

	private static NativeData.Variant sharedOut(EnchantmentLocationBasedEffect value, NativeData.Builder b) {
		return switch (value) {
			case ApplyMobEffect v -> new NativeData.Variant(1, new Object[]{
				b.slot(v.toApply(), NativeValues.JavaCodec.MOB_EFFECTS.codec()), LevelBasedValue.unproject(v.minDuration()), LevelBasedValue.unproject(v.maxDuration()),
				LevelBasedValue.unproject(v.minAmplifier()), LevelBasedValue.unproject(v.maxAmplifier())
			});
			case ChangeItemDamage v -> new NativeData.Variant(3, new Object[]{LevelBasedValue.unproject(v.amount())});
			case DamageEntity v -> new NativeData.Variant(4, new Object[]{
				LevelBasedValue.unproject(v.minDamage()), LevelBasedValue.unproject(v.maxDamage()), b.slot(v.damageType(), NativeValues.JavaCodec.DAMAGE_TYPE.codec())
			});
			case ExplodeEffect v -> new NativeData.Variant(5, new Object[]{
				v.attributeToUser(),
				v.damageType().map(d -> (Object)b.slot(d, NativeValues.JavaCodec.DAMAGE_TYPE.codec())).orElse(null),
				v.knockbackMultiplier().map(LevelBasedValue::unproject).orElse(null),
				v.immuneBlocks().map(s -> (Object)b.slot(s, NativeValues.JavaCodec.BLOCKS.codec())).orElse(null),
				List.of(v.offset().x, v.offset().y, v.offset().z),
				LevelBasedValue.unproject(v.radius()),
				v.createFire(),
				v.blockInteraction().getSerializedName(),
				NativeParticles.unproject(v.smallParticle(), b),
				NativeParticles.unproject(v.largeParticle(), b),
				b.slotWithDefault(v.blockParticles(), NativeValues.JavaCodec.EXPLOSION_PARTICLES.codec(), WeightedList.of()),
				b.slot(v.sound(), SoundEvent.CODEC)
			});
			case net.minecraft.world.item.enchantment.effects.Ignite v -> new NativeData.Variant(6, new Object[]{LevelBasedValue.unproject(v.duration())});
			case PlaySoundEffect v -> new NativeData.Variant(7, new Object[]{b.slot(v.soundEvent(), SoundEvent.CODEC), FloatProvider.unproject(v.volume()), FloatProvider.unproject(v.pitch())});
			case ReplaceBlock v -> new NativeData.Variant(8, new Object[]{
				vec3iOut(v.offset()),
				v.predicate().map(p -> (Object)b.slot(p, NativeValues.JavaCodec.BLOCK_PREDICATE.codec())).orElse(null),
				b.slot(v.blockState(), NativeValues.JavaCodec.BLOCK_STATE_PROVIDER.codec()),
				v.triggerGameEvent().map(g -> (Object)b.slot(g, NativeValues.JavaCodec.GAME_EVENT.codec())).orElse(null)
			});
			case ReplaceDisk v -> new NativeData.Variant(9, new Object[]{
				LevelBasedValue.unproject(v.radius()),
				LevelBasedValue.unproject(v.height()),
				vec3iOut(v.offset()),
				v.predicate().map(p -> (Object)b.slot(p, NativeValues.JavaCodec.BLOCK_PREDICATE.codec())).orElse(null),
				b.slot(v.blockState(), NativeValues.JavaCodec.BLOCK_STATE_PROVIDER.codec()),
				v.triggerGameEvent().map(g -> (Object)b.slot(g, NativeValues.JavaCodec.GAME_EVENT.codec())).orElse(null)
			});
			case RunFunction v -> new NativeData.Variant(10, new Object[]{v.function().toString()});
			case SetBlockProperties v -> new NativeData.Variant(11, new Object[]{
				new java.util.LinkedHashMap<String, Object>(v.properties().properties()),
				vec3iOut(v.offset()),
				v.triggerGameEvent().map(g -> (Object)b.slot(g, NativeValues.JavaCodec.GAME_EVENT.codec())).orElse(null)
			});
			case SpawnParticlesEffect v -> new NativeData.Variant(12, new Object[]{
				NativeParticles.unproject(v.particle(), b), positionOut(v.horizontalPosition()), positionOut(v.verticalPosition()),
				velocityOut(v.horizontalVelocity()), velocityOut(v.verticalVelocity()), FloatProvider.unproject(v.speed())
			});
			case SummonEntityEffect v -> new NativeData.Variant(13, new Object[]{b.slot(v.entityTypes(), NativeValues.JavaCodec.ENTITY_TYPES.codec()), v.joinTeam()});
			default -> throw new IllegalArgumentException("Unknown enchantment effect " + value);
		};
	}

	private static EnchantmentAttributeEffect attribute(Object[] f, NativeData.Context context) {
		return new EnchantmentAttributeEffect(
			NativeData.location(f[0]),
			context.slot(f[1], NativeEnchantmentEffects.<Holder<Attribute>>java(NativeValues.JavaCodec.ATTRIBUTE)),
			LevelBasedValue.project(f[2]),
			NativeData.named(AttributeModifier.Operation.class, f[3])
		);
	}

	private static Object[] attributeOut(EnchantmentAttributeEffect effect, NativeData.Builder b) {
		return new Object[]{
			effect.id().toString(), b.slot(effect.attribute(), NativeValues.JavaCodec.ATTRIBUTE.codec()), LevelBasedValue.unproject(effect.amount()),
			effect.operation().getSerializedName()
		};
	}

	private static SpawnParticlesEffect.PositionSource position(Object value) {
		Object[] f = NativeData.record(value);
		return new SpawnParticlesEffect.PositionSource(NativeData.named(SpawnParticlesEffect.PositionSourceType.class, f[0]), (Float)f[1], (Float)f[2]);
	}

	private static Object positionOut(SpawnParticlesEffect.PositionSource source) {
		return new Object[]{source.type().getSerializedName(), source.offset(), source.scale()};
	}

	private static SpawnParticlesEffect.VelocitySource velocity(Object value) {
		Object[] f = NativeData.record(value);
		return new SpawnParticlesEffect.VelocitySource((Float)f[0], FloatProvider.project(f[1]));
	}

	private static Object velocityOut(SpawnParticlesEffect.VelocitySource source) {
		return new Object[]{source.movementScale(), FloatProvider.unproject(source.base())};
	}

	private static Vec3 vec3(Object value) {
		List<Object> l = NativeData.list(value);
		return new Vec3((Double)l.get(0), (Double)l.get(1), (Double)l.get(2));
	}

	private static Vec3i vec3i(Object value) {
		List<Object> l = NativeData.list(value);
		return new Vec3i((Integer)l.get(0), (Integer)l.get(1), (Integer)l.get(2));
	}

	private static Object vec3iOut(Vec3i v) {
		return List.of(v.getX(), v.getY(), v.getZ());
	}

	/** As `Codec.unboundedMap` builds it: an immutable map in input order. */
	private static Map<String, String> stringMap(Object value) {
		com.google.common.collect.ImmutableMap.Builder<String, String> map = com.google.common.collect.ImmutableMap.builder();
		NativeData.map(value).forEach((k, v) -> map.put(k, (String)v));
		return map.buildOrThrow();
	}
}
