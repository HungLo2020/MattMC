package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Guardian;

/** A guardian's beam charge; its attack-scale rule runs in Rust. */
@Environment(EnvType.CLIENT)
public class GuardianAttackSoundInstance extends RuledTickableSoundInstance {
	private final Guardian guardian;

	public GuardianAttackSoundInstance(Guardian guardian) {
		super(SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
		this.guardian = guardian;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.looping = true;
		this.delay = 0;
	}

	@Override
	public boolean canPlaySound() {
		return !this.guardian.isSilent();
	}

	@Override
	public int soundRule() {
		return RULE_GUARDIAN_ATTACK;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_REMOVED, this.guardian.isRemoved());
		sample.flag(SAMPLE_HAS_TARGET, this.guardian.getTarget() != null);
		sample.position(this.guardian.getX(), this.guardian.getY(), this.guardian.getZ());
		sample.value = this.guardian.getAttackAnimationScale(0.0F);
	}
}
