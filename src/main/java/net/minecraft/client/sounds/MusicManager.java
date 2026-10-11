package net.minecraft.client.sounds;

import com.mojang.serialization.Codec;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.OptionEnum;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Situational music. Rust (`audio/policy/music.rs`) owns the current track,
 * the song delay, fades, the music random and the toast state; Java reports
 * {@link Minecraft#getSituationalMusic()} each tick and shows or hides the
 * "now playing" toast when Rust asks.
 */
@Environment(EnvType.CLIENT)
public class MusicManager {
	private final Minecraft minecraft;

	public MusicManager(Minecraft minecraft) {
		this.minecraft = minecraft;
		NativeSoundClient.musicReset(minecraft.options.musicFrequency().get().ordinal());
	}

	private static String eventName(Music music) {
		return ((SoundEvent)music.event().value()).location().toString();
	}

	private void apply(int actions) {
		if ((actions & NativeSoundClient.MUSIC_HIDE_TOAST) != 0) {
			this.minecraft.getToastManager().hideNowPlayingToast();
		}

		if ((actions & NativeSoundClient.MUSIC_SHOW_TOAST) != 0) {
			this.minecraft.getToastManager().showNowPlayingToast();
		}
	}

	public void tick() {
		MusicInfo musicInfo = this.minecraft.getSituationalMusic();
		Music music = musicInfo.music();
		this.apply(
			music == null
				? NativeSoundClient.musicTick(null, 0, 0, false, musicInfo.volume())
				: NativeSoundClient.musicTick(eventName(music), music.minDelay(), music.maxDelay(), music.replaceCurrentMusic(), musicInfo.volume())
		);
	}

	public void startPlaying(MusicInfo musicInfo) {
		this.apply(NativeSoundClient.musicStart(eventName(musicInfo.music()), musicInfo.volume()));
	}

	public void showNowPlayingToastIfNeeded() {
		this.apply(NativeSoundClient.musicToastIfNeeded());
	}

	public void stopPlaying(Music music) {
		this.apply(NativeSoundClient.musicStop(eventName(music)));
	}

	public void stopPlaying() {
		this.apply(NativeSoundClient.musicStop(null));
	}

	public boolean isPlayingMusic(Music music) {
		return NativeSoundClient.musicIsPlaying(eventName(music));
	}

	@Nullable
	public String getCurrentMusicTranslationKey() {
		String sound = NativeSoundClient.musicCurrentSound();
		return sound == null ? null : ResourceLocation.parse(sound).toShortLanguageKey();
	}

	public void setMinutesBetweenSongs(MusicManager.MusicFrequency musicFrequency) {
		Music music = this.minecraft.getSituationalMusic().music();
		if (music == null) {
			NativeSoundClient.musicSetFrequency(musicFrequency.ordinal(), null, 0, 0);
		} else {
			NativeSoundClient.musicSetFrequency(musicFrequency.ordinal(), eventName(music), music.minDelay(), music.maxDelay());
		}
	}

	/** The music frequency option; Rust holds each value's delays by ordinal. */
	@Environment(EnvType.CLIENT)
	public static enum MusicFrequency implements OptionEnum, StringRepresentable {
		DEFAULT(20),
		FREQUENT(10),
		CONSTANT(0);

		public static final Codec<MusicManager.MusicFrequency> CODEC = StringRepresentable.fromEnum(MusicManager.MusicFrequency::values);
		private static final String KEY_PREPEND = "options.music_frequency.";
		private final int id;
		private final String key;

		private MusicFrequency(final int j) {
			this.id = j;
			this.key = "options.music_frequency." + this.name().toLowerCase();
		}

		public int getId() {
			return this.id;
		}

		public String getKey() {
			return this.key;
		}

		public String getSerializedName() {
			return this.name();
		}
	}
}
