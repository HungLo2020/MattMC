package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

public record JukeboxSong(Holder<SoundEvent> soundEvent, Component description, float lengthInSeconds, int comparatorOutput) {
	public static final Codec<JukeboxSong> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.JUKEBOX_SONG, false, net.minecraft.resources.NativeDataProjections::jukeboxSong, net.minecraft.resources.NativeDataProjections::jukeboxSongOut);
	public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxSong> DIRECT_STREAM_CODEC = StreamCodec.composite(
		SoundEvent.STREAM_CODEC,
		JukeboxSong::soundEvent,
		ComponentSerialization.STREAM_CODEC,
		JukeboxSong::description,
		ByteBufCodecs.FLOAT,
		JukeboxSong::lengthInSeconds,
		ByteBufCodecs.VAR_INT,
		JukeboxSong::comparatorOutput,
		JukeboxSong::new
	);
	public static final Codec<Holder<JukeboxSong>> CODEC = RegistryFixedCodec.create(Registries.JUKEBOX_SONG);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<JukeboxSong>> STREAM_CODEC = ByteBufCodecs.holder(Registries.JUKEBOX_SONG, DIRECT_STREAM_CODEC);
	private static final int SONG_END_PADDING_TICKS = 20;

	public int lengthInTicks() {
		return Mth.ceil(this.lengthInSeconds * 20.0F);
	}

	public boolean hasFinished(long l) {
		return l >= this.lengthInTicks() + 20;
	}

	public static Optional<Holder<JukeboxSong>> fromStack(HolderLookup.Provider provider, ItemStack itemStack) {
		JukeboxPlayable jukeboxPlayable = itemStack.get(DataComponents.JUKEBOX_PLAYABLE);
		return jukeboxPlayable != null ? jukeboxPlayable.song().unwrap(provider) : Optional.empty();
	}
}
