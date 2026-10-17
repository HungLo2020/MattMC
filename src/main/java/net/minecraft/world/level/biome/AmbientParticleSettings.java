package net.minecraft.world.level.biome;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;

public class AmbientParticleSettings {
	private final ParticleOptions options;
	private final float probability;

	public AmbientParticleSettings(ParticleOptions particleOptions, float f) {
		this.options = particleOptions;
		this.probability = f;
	}

	float probability() {
		return this.probability;
	}

	public ParticleOptions getOptions() {
		return this.options;
	}

	public boolean canSpawn(RandomSource randomSource) {
		return randomSource.nextFloat() <= this.probability;
	}
}
