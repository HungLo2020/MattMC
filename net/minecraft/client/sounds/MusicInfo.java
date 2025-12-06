package net.minecraft.client.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record MusicInfo(@Nullable Music music, float volume) {
	public MusicInfo(Music music) {
		this(music, 1.0F);
	}

	public boolean canReplace(SoundInstance soundInstance) {
		return this.music == null
			? false
			: this.music.replaceCurrentMusic() && !((SoundEvent)this.music.event().value()).location().equals(soundInstance.getLocation());
	}
}
