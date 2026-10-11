package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/** A sound following an entity until it is removed (rule in Rust). */
@Environment(EnvType.CLIENT)
public class EntityBoundSoundInstance extends RuledTickableSoundInstance {
	private final Entity entity;

	public EntityBoundSoundInstance(SoundEvent soundEvent, SoundSource soundSource, float f, float g, Entity entity, long l) {
		super(soundEvent, soundSource, RandomSource.create(l));
		this.volume = f;
		this.pitch = g;
		this.entity = entity;
		this.x = (float)this.entity.getX();
		this.y = (float)this.entity.getY();
		this.z = (float)this.entity.getZ();
	}

	@Override
	public boolean canPlaySound() {
		return !this.entity.isSilent();
	}

	@Override
	public int soundRule() {
		return RULE_ENTITY_BOUND;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_REMOVED, this.entity.isRemoved());
		sample.position(this.entity.getX(), this.entity.getY(), this.entity.getZ());
	}
}
