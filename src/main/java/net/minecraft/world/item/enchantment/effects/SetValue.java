package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record SetValue(LevelBasedValue value) implements EnchantmentValueEffect {
	/** The native codec's `set` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<SetValue> CODEC = (MapCodec<SetValue>)(MapCodec<?>)EnchantmentValueEffect.CODEC.dispatchCase("minecraft:set");

	@Override
	public float process(int i, RandomSource randomSource, float f) {
		return this.value.calculate(i);
	}

	@Override
	public MapCodec<SetValue> codec() {
		return CODEC;
	}
}
