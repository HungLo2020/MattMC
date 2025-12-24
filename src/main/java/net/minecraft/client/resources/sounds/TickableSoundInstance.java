package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface TickableSoundInstance extends SoundInstance {
	boolean isStopped();

	void tick();
}
