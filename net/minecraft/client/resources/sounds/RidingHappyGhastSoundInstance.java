package net.minecraft.client.resources.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.HappyGhast;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public class RidingHappyGhastSoundInstance extends AbstractTickableSoundInstance {
	private static final float VOLUME_MIN = 0.0F;
	private static final float VOLUME_MAX = 1.0F;
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
	public void tick() {
		if (!this.happyGhast.isRemoved() && this.player.isPassenger() && this.player.getVehicle() == this.happyGhast) {
			float f = (float)this.happyGhast.getDeltaMovement().length();
			if (f >= 0.01F) {
				this.volume = 5.0F * Mth.clampedLerp(0.0F, 1.0F, f);
			} else {
				this.volume = 0.0F;
			}
		} else {
			this.stop();
		}
	}
}
