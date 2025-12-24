package net.minecraft.client.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;

@Environment(EnvType.CLIENT)
public interface SoundEventListener {
	void onPlaySound(SoundInstance soundInstance, WeighedSoundEvents weighedSoundEvents, float f);
}
