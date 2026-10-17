package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface EnchantmentLocationBasedEffect {
	/** Rust decodes, validates and encodes LocationBasedEffects (`content/data/enchant.rs`); each registered type is one of its cases. */
	net.minecraft.resources.NativeValues.ValueCodec<EnchantmentLocationBasedEffect> CODEC = net.minecraft.resources.NativeValues.codec(
		net.minecraft.resources.NativeValues.Kind.ENCHANTMENT_LOCATION_EFFECT, net.minecraft.world.item.enchantment.NativeEnchantmentEffects::locationEffect, net.minecraft.world.item.enchantment.NativeEnchantmentEffects::locationEffectOut
	);

	/**
	 * Location effect types: the native codec's cases, in registry order.
	 * Entity effects share their classes, but each registry holds its own codec's cases.
	 */
	static MapCodec<? extends EnchantmentLocationBasedEffect> bootstrap(Registry<MapCodec<? extends EnchantmentLocationBasedEffect>> registry) {
		Registry.register(registry, "all_of", AllOf.LocationBasedEffects.CODEC);
		Registry.register(registry, "apply_mob_effect", CODEC.dispatchCase("minecraft:apply_mob_effect"));
		Registry.register(registry, "attribute", EnchantmentAttributeEffect.CODEC);
		Registry.register(registry, "change_item_damage", CODEC.dispatchCase("minecraft:change_item_damage"));
		Registry.register(registry, "damage_entity", CODEC.dispatchCase("minecraft:damage_entity"));
		Registry.register(registry, "explode", CODEC.dispatchCase("minecraft:explode"));
		Registry.register(registry, "ignite", CODEC.dispatchCase("minecraft:ignite"));
		Registry.register(registry, "play_sound", CODEC.dispatchCase("minecraft:play_sound"));
		Registry.register(registry, "replace_block", CODEC.dispatchCase("minecraft:replace_block"));
		Registry.register(registry, "replace_disk", CODEC.dispatchCase("minecraft:replace_disk"));
		Registry.register(registry, "run_function", CODEC.dispatchCase("minecraft:run_function"));
		Registry.register(registry, "set_block_properties", CODEC.dispatchCase("minecraft:set_block_properties"));
		Registry.register(registry, "spawn_particles", CODEC.dispatchCase("minecraft:spawn_particles"));
		return Registry.register(registry, "summon_entity", CODEC.dispatchCase("minecraft:summon_entity"));
	}

	void onChangedBlock(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3, boolean bl);

	default void onDeactivated(EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3, int i) {
	}

	MapCodec<? extends EnchantmentLocationBasedEffect> codec();
}
