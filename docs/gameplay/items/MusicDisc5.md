# Music Disc (5)

**Music Disc (5)** (`minecraft:music_disc_5`) is crafted from the [Disc Fragments](DiscFragment.md) found in Ancient City loot. Save **nine fragments** to make a playable disc for a [Jukebox](../blocks/Jukebox.md). [Registration][item] · [Recipe][recipe]

## Obtaining

### Crafting from fragments

Put **one Disc Fragment in each of the nine slots of a Crafting Table** to make **one Music Disc (5)**. The recipe is shapeless, but its nine separate ingredients require the full crafting grid; a stack of nine in one slot is not the same input. Crafting consumes the fragments. [Exact recipe][recipe] · [Recipe loading][recipe-load] · [Ingredient matching][shapeless] · [Ingredient consumption][consume]

Collect the fragments from ordinary [Ancient City chests](../structures/AncientCity.md#ordinary-city-chests). The [Disc Fragment page](DiscFragment.md#obtaining) owns the exact loot quantities and odds. A fragment selection gives only part of the recipe, and finding one city or chest does not guarantee a complete disc.

No reverse recipe that turns the finished disc back into fragments is bundled. Plan to keep the finished disc; playback is reusable. [Recipe inventory][recipes]

### Other availability

The finished disc is listed in Creative. It is absent from the bundled Creeper music-disc tag, so the ordinary skeleton-caused Creeper disc drop does not supply it. [Creative entry][creative] · [Creeper disc pool][creeper-loot] · [Allowed discs][disc-tag]

## Usage

Use the disc on an empty [Jukebox](../blocks/Jukebox.md#inserting-and-ejecting-discs). Its registered playable component selects song **`minecraft:5`** from the loaded song registry. [Component][component] · [Song key][keys] · [Registry loading][registry] · [Insertion][insert] · [Song lookup and start][start]

The bundled song record specifies **2:58 (178 seconds)**, sound event **`minecraft:music_disc.5`**, and **Comparator strength 15**. These are configured values, not measured audio duration. The active Jukebox reads the record for playback and Comparator output; the shared [song-length guide](../blocks/Jukebox.md#song-lengths-and-comparator-values) explains end padding and the separate playing signal. [Song record][song] · [Playback][player] · [Length consumer][end] · [Comparator lookup][comparator]

## Behavior

The disc stacks to **one item**. Playback does not consume it: use the occupied Jukebox to recover it. The canonical [Jukebox guide](../blocks/Jukebox.md) owns insertion/ejection, Hopper transfers, redstone and save/reload behavior. [Item properties][item] · [Disc ejection][recover]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the loaded nine-ingredient recipe, item/song mapping and consumers, plus the relevant recipe and loot resource inventory. The server loads the cited loot resources through its reloadable registry and song records through world-data loading. No gameplay, loot-distribution, listening or timed-playback test was run. Data packs, later builds and already-looted terrain can differ. [Loot loading][reload] · [World loading][world-load]

Related: [Disc Fragment](DiscFragment.md) · [Ancient City](../structures/AncientCity.md) · [Jukebox item](Jukebox.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2341-L2359
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/music_disc_5.json
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L59-L89
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L59-L82
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L114
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1566-L1569
[creeper-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/creeper.json
[disc-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/creeper_drop_music_discs.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongs.java#L26-L37
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L125-L132
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L109-L119
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/5.json
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java#L48-L80
[end]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSong.java#L42-L55
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L69
[recover]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L47-L61
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L70
