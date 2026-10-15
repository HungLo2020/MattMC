package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record AddValue(LevelBasedValue value) implements EnchantmentValueEffect {
	/** The native codec's `add` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<AddValue> CODEC = (MapCodec<AddValue>)(MapCodec<?>)EnchantmentValueEffect.CODEC.dispatchCase("minecraft:add");

	@Override
	public float process(int i, RandomSource randomSource, float f) {
		return f + this.value.calculate(i);
	}

	@Override
	public MapCodec<AddValue> codec() {
		return CODEC;
	}
}
