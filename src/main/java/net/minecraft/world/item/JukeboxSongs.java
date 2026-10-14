package net.minecraft.world.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public interface JukeboxSongs {
	ResourceKey<JukeboxSong> THIRTEEN = create("13");
	ResourceKey<JukeboxSong> CAT = create("cat");
	ResourceKey<JukeboxSong> BLOCKS = create("blocks");
	ResourceKey<JukeboxSong> CHIRP = create("chirp");
	ResourceKey<JukeboxSong> FAR = create("far");
	ResourceKey<JukeboxSong> MALL = create("mall");
	ResourceKey<JukeboxSong> MELLOHI = create("mellohi");
	ResourceKey<JukeboxSong> STAL = create("stal");
	ResourceKey<JukeboxSong> STRAD = create("strad");
	ResourceKey<JukeboxSong> WARD = create("ward");
	ResourceKey<JukeboxSong> ELEVEN = create("11");
	ResourceKey<JukeboxSong> WAIT = create("wait");
	ResourceKey<JukeboxSong> PIGSTEP = create("pigstep");
	ResourceKey<JukeboxSong> OTHERSIDE = create("otherside");
	ResourceKey<JukeboxSong> FIVE = create("5");
	ResourceKey<JukeboxSong> RELIC = create("relic");
	ResourceKey<JukeboxSong> PRECIPICE = create("precipice");
	ResourceKey<JukeboxSong> CREATOR = create("creator");
	ResourceKey<JukeboxSong> CREATOR_MUSIC_BOX = create("creator_music_box");
	ResourceKey<JukeboxSong> TEARS = create("tears");
	ResourceKey<JukeboxSong> LAVA_CHICKEN = create("lava_chicken");

	private static ResourceKey<JukeboxSong> create(String string) {
		return ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.withDefaultNamespace(string));
	}
}
