package net.minecraft.sounds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

public record Music(Holder<SoundEvent> event, int minDelay, int maxDelay, boolean replaceCurrentMusic) {
	public static final Codec<Music> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				SoundEvent.CODEC.fieldOf("sound").forGetter(music -> music.event),
				Codec.INT.fieldOf("min_delay").forGetter(music -> music.minDelay),
				Codec.INT.fieldOf("max_delay").forGetter(music -> music.maxDelay),
				Codec.BOOL.fieldOf("replace_current_music").forGetter(music -> music.replaceCurrentMusic)
			)
			.apply(instance, Music::new)
	);
}
