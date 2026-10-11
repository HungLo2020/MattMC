package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.HappyGhast;
import net.minecraft.world.entity.player.Player;

/** Wind while riding a happy ghast; its speed rule runs in Rust. */
@Environment(EnvType.CLIENT)
public class RidingHappyGhastSoundInstance extends RuledTickableSoundInstance {
	private final Player player;
	private final HappyGhast happyGhast;

	public RidingHappyGhastSoundInstance(Player player, HappyGhast happyGhast) {
		super(SoundEvents.HAPPY_GHAST_RIDING, happyGhast.getSoundSource(), SoundInstance.createUnseededRandom());
		this.player = player;
		this.happyGhast = happyGhast;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0F;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public int soundRule() {
		return RULE_RIDING_HAPPY_GHAST;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_REMOVED, this.happyGhast.isRemoved());
		sample.flag(SAMPLE_RIDING, this.player.isPassenger() && this.player.getVehicle() == this.happyGhast);
		sample.value = (float)this.happyGhast.getDeltaMovement().length();
	}
}
