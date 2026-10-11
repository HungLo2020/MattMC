package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Wind while gliding; its fade-in and speed rule runs in Rust. */
@Environment(EnvType.CLIENT)
public class ElytraOnPlayerSoundInstance extends RuledTickableSoundInstance {
	public static final int DELAY = 20;
	private final LocalPlayer player;

	public ElytraOnPlayerSoundInstance(LocalPlayer localPlayer) {
		super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = localPlayer;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.1F;
	}

	@Override
	public int soundRule() {
		return RULE_ELYTRA;
	}

	@Override
	public void sampleSoundRule(RuledSoundInstance.Sample sample) {
		sample.flag(SAMPLE_REMOVED, this.player.isRemoved());
		sample.flag(SAMPLE_FALL_FLYING, this.player.isFallFlying());
		sample.position(this.player.getX(), this.player.getY(), this.player.getZ());
		sample.value = (float)this.player.getDeltaMovement().lengthSqr();
	}
}
