package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.sniffer.Sniffer;

/** A sniffer's digging; its rule runs in Rust. */
@Environment(EnvType.CLIENT)
public class SnifferSoundInstance extends RuledTickableSoundInstance {
	private final Sniffer sniffer;

	public SnifferSoundInstance(Sniffer sniffer) {
		super(SoundEvents.SNIFFER_DIGGING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.sniffer = sniffer;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.looping = false;
		this.delay = 0;
	}

	@Override
	public boolean canPlaySound() {
		return !this.sniffer.isSilent();
	}

	@Override
	public int soundRule() {
		return RULE_SNIFFER;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_REMOVED, this.sniffer.isRemoved());
		sample.flag(SAMPLE_HAS_TARGET, this.sniffer.getTarget() != null);
		sample.flag(SAMPLE_CAN_DIG, this.sniffer.canPlayDiggingSound());
		sample.position(this.sniffer.getX(), this.sniffer.getY(), this.sniffer.getZ());
	}
}
