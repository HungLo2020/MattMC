package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record MultiplyValue(LevelBasedValue factor) implements EnchantmentValueEffect {
	/** The native codec's `multiply` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<MultiplyValue> CODEC = (MapCodec<MultiplyValue>)(MapCodec<?>)EnchantmentValueEffect.CODEC.dispatchCase("minecraft:multiply");

	@Override
	public float process(int i, RandomSource randomSource, float f) {
		return f * this.factor.calculate(i);
	}

	@Override
	public MapCodec<MultiplyValue> codec() {
		return CODEC;
	}
}
