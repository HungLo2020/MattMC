package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record RemoveBinomial(LevelBasedValue chance) implements EnchantmentValueEffect {
	/** The native codec's `remove_binomial` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<RemoveBinomial> CODEC = (MapCodec<RemoveBinomial>)(MapCodec<?>)EnchantmentValueEffect.CODEC.dispatchCase("minecraft:remove_binomial");

	@Override
	public float process(int i, RandomSource randomSource, float f) {
		float g = this.chance.calculate(i);
		int j = 0;

		for (int k = 0; k < f; k++) {
			if (randomSource.nextFloat() < g) {
				j++;
			}
		}

		return f - j;
	}

	@Override
	public MapCodec<RemoveBinomial> codec() {
		return CODEC;
	}
}
