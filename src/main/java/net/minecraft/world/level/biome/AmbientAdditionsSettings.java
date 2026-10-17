package net.minecraft.world.level.biome;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

public class AmbientAdditionsSettings {
	private final Holder<SoundEvent> soundEvent;
	private final double tickChance;

	public AmbientAdditionsSettings(Holder<SoundEvent> holder, double d) {
		this.soundEvent = holder;
		this.tickChance = d;
	}

	public Holder<SoundEvent> getSoundEvent() {
		return this.soundEvent;
	}

	public double getTickChance() {
		return this.tickChance;
	}
}
