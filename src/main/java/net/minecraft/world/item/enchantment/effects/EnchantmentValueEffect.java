package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

public interface EnchantmentValueEffect {
	/** Rust decodes, validates and encodes ValueEffects (`content/data/enchant.rs`); each registered type is one of its cases. */
	net.minecraft.resources.NativeValues.ValueCodec<EnchantmentValueEffect> CODEC = net.minecraft.resources.NativeValues.codec(
		net.minecraft.resources.NativeValues.Kind.ENCHANTMENT_VALUE_EFFECT, net.minecraft.world.item.enchantment.NativeEnchantmentEffects::valueEffect, net.minecraft.world.item.enchantment.NativeEnchantmentEffects::valueEffectOut
	);

	static MapCodec<? extends EnchantmentValueEffect> bootstrap(Registry<MapCodec<? extends EnchantmentValueEffect>> registry) {
		Registry.register(registry, "add", AddValue.CODEC);
		Registry.register(registry, "all_of", AllOf.ValueEffects.CODEC);
		Registry.register(registry, "multiply", MultiplyValue.CODEC);
		Registry.register(registry, "remove_binomial", RemoveBinomial.CODEC);
		return Registry.register(registry, "set", SetValue.CODEC);
	}

	float process(int i, RandomSource randomSource, float f);

	MapCodec<? extends EnchantmentValueEffect> codec();
}
