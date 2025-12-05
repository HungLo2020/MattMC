package net.minecraft.world.entity.variant;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.MinMaxBounds;

public record MoonBrightnessCheck(MinMaxBounds.Doubles range) implements SpawnCondition {
	public static final MapCodec<MoonBrightnessCheck> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(MinMaxBounds.Doubles.CODEC.fieldOf("range").forGetter(MoonBrightnessCheck::range)).apply(instance, MoonBrightnessCheck::new)
	);

	public boolean test(SpawnContext spawnContext) {
		return this.range.matches(spawnContext.level().getLevel().getMoonBrightness());
	}

	@Override
	public MapCodec<MoonBrightnessCheck> codec() {
		return MAP_CODEC;
	}
}
