package net.minecraft.world.level.biome;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class AmbientMoodSettings {
	public static final AmbientMoodSettings LEGACY_CAVE_SETTINGS = new AmbientMoodSettings(SoundEvents.AMBIENT_CAVE, 6000, 8, 2.0);
	private final Holder<SoundEvent> soundEvent;
	private final int tickDelay;
	private final int blockSearchExtent;
	private final double soundPositionOffset;

	public AmbientMoodSettings(Holder<SoundEvent> holder, int i, int j, double d) {
		this.soundEvent = holder;
		this.tickDelay = i;
		this.blockSearchExtent = j;
		this.soundPositionOffset = d;
	}

	public Holder<SoundEvent> getSoundEvent() {
		return this.soundEvent;
	}

	public int getTickDelay() {
		return this.tickDelay;
	}

	public int getBlockSearchExtent() {
		return this.blockSearchExtent;
	}

	public double getSoundPositionOffset() {
		return this.soundPositionOffset;
	}
}
