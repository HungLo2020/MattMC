package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Bee;

/** A bee's buzz; Rust runs its speed rule and the switch to the other loop. */
@Environment(EnvType.CLIENT)
public abstract class BeeSoundInstance extends RuledTickableSoundInstance {
	protected final Bee bee;

	public BeeSoundInstance(Bee bee, SoundEvent soundEvent, SoundSource soundSource) {
		super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
		this.bee = bee;
		this.x = (float)bee.getX();
		this.y = (float)bee.getY();
		this.z = (float)bee.getZ();
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0F;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public boolean canPlaySound() {
		return !this.bee.isSilent();
	}

	@Override
	public int soundRule() {
		return RULE_BEE;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_SWITCH, this.shouldSwitchSounds());
		sample.flag(SAMPLE_REMOVED, this.bee.isRemoved());
		sample.flag(SAMPLE_BABY, this.bee.isBaby());
		sample.position(this.bee.getX(), this.bee.getY(), this.bee.getZ());
		sample.value = (float)this.bee.getDeltaMovement().horizontalDistance();
	}

	@Override
	public void onSoundRuleSwitched() {
		Minecraft.getInstance().getSoundManager().queueTickingSound(this.getAlternativeSoundInstance());
	}

	protected abstract AbstractTickableSoundInstance getAlternativeSoundInstance();

	protected abstract boolean shouldSwitchSounds();
}
