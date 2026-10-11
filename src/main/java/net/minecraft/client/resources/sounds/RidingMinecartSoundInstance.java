package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;

/** The inside-the-minecart sound (above or under water); rule in Rust. */
@Environment(EnvType.CLIENT)
public class RidingMinecartSoundInstance extends RuledTickableSoundInstance {
	private final Player player;
	private final AbstractMinecart minecart;
	private final boolean underwaterSound;

	public RidingMinecartSoundInstance(Player player, AbstractMinecart abstractMinecart, boolean bl) {
		super(bl ? SoundEvents.MINECART_INSIDE_UNDERWATER : SoundEvents.MINECART_INSIDE, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.player = player;
		this.minecart = abstractMinecart;
		this.underwaterSound = bl;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0F;
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
		return RULE_RIDING_MINECART;
	}

	@Override
	public int soundRuleParameter() {
		return this.underwaterSound ? 1 : 0;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_REMOVED, this.minecart.isRemoved());
		sample.flag(SAMPLE_RIDING, this.player.isPassenger() && this.player.getVehicle() == this.minecart);
		sample.flag(SAMPLE_UNDERWATER, this.player.isUnderWater());
		sample.flag(SAMPLE_OFF_RAILS, !this.minecart.isOnRails() && this.minecart.getBehavior() instanceof NewMinecartBehavior);
		sample.value = (float)this.minecart.getDeltaMovement().horizontalDistance();
	}
}
