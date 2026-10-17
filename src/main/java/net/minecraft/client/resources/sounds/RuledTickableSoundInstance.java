package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Base of the vanilla tickables whose rule runs in Rust. Subclasses set their
 * starting fields and sample their entity; `tick()` does nothing because the
 * engine never calls it for a ruled instance.
 */
@Environment(EnvType.CLIENT)
public abstract class RuledTickableSoundInstance extends AbstractTickableSoundInstance implements RuledSoundInstance {
	protected RuledTickableSoundInstance(SoundEvent soundEvent, SoundSource soundSource, RandomSource randomSource) {
		super(soundEvent, soundSource, randomSource);
	}

	@Override
	public final void tick() {
	}

	@Override
	public float ruleBaseVolume() {
		return this.volume;
	}

	@Override
	public float ruleBasePitch() {
		return this.pitch;
	}

	@Override
	public void markSoundRuleStopped() {
		this.stop();
	}
}
