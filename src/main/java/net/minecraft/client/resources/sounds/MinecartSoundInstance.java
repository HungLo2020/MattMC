package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;

/** A rolling minecart's sound; its speed-to-volume rule runs in Rust. */
@Environment(EnvType.CLIENT)
public class MinecartSoundInstance extends RuledTickableSoundInstance {
	private final AbstractMinecart minecart;

	public MinecartSoundInstance(AbstractMinecart abstractMinecart) {
		super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.minecart = abstractMinecart;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0F;
		this.x = (float)abstractMinecart.getX();
		this.y = (float)abstractMinecart.getY();
		this.z = (float)abstractMinecart.getZ();
	}

	@Override
	public boolean canPlaySound() {
		return !this.minecart.isSilent();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public int soundRule() {
		return RULE_MINECART;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_REMOVED, this.minecart.isRemoved());
		sample.flag(SAMPLE_RUNS_NORMALLY, this.minecart.level().tickRateManager().runsNormally());
		sample.flag(SAMPLE_OFF_RAILS, !this.minecart.isOnRails() && this.minecart.getBehavior() instanceof NewMinecartBehavior);
		sample.position(this.minecart.getX(), this.minecart.getY(), this.minecart.getZ());
		sample.value = (float)this.minecart.getDeltaMovement().horizontalDistance();
	}
}
