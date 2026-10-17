# Music Disc (Otherside)

**Music Disc (Otherside)** (`minecraft:music_disc_otherside`) is a playable chest-loot collectible. Search eligible [Ancient City](../structures/AncientCity.md), [Stronghold](../structures/Stronghold.md) or [Monster Room](../structures/MonsterRoom.md) chests; these routes do not guarantee a disc from one structure. [Registration][item]

## Obtaining

Each selected Otherside entry awards **one disc**. The following fractions apply to **one weighted selection in the named pool**, not to the whole chest or every structure. A chest can select the same entry repeatedly. [Pool selection][pool] · [Default weights][weights]

| Chest/table | Rolls in the disc's pool | Otherside weight / pool total | Chance per selection |
| --- | --- | --- | --- |
| Ordinary Ancient City (`chests/ancient_city`) | 5–10 | 1 / 86 | About **1.16%** |
| Stronghold corridor (`chests/stronghold_corridor`) | 2–3 | 1 / 101 | About **0.99%** |
| Monster Room (`chests/simple_dungeon`) | 1–3 | 2 / 144 | **1/72**, about **1.39%** |

[City table][city] · [Corridor table][corridor] · [Monster Room table][dungeon]

The assignments matter: city templates save the ordinary city table, the stronghold's chest-corridor piece assigns its corridor table, and the room generator assigns `simple_dungeon`. **City ice-box chests and Stronghold crossing/library chests do not contain Otherside in their bundled tables.** Use the linked structure guides for layouts, optional rooms and exploration precautions. [Example city assignment][city-chest] · [Template placement][place] · [Corridor assignment][corridor-assign] · [Room assignment][dungeon-assign] · [Ice box][ice] · [Crossing][crossing] · [Library][library]

The optional [Trade Rebalance pack](../trading/Trading.md#optional-trade-rebalance) keeps Otherside's city weight **1/86** and the **5–10 rolls** when its replacement table is selected. Loot is generated once for the assigned container; reopening a looted chest does not reroll it. [Optional city table][rebalance] · [Container handling][container]

Otherside is also listed in Creative. It is absent from the bundled Creeper music-disc tag, so the ordinary Creeper disc-drop route does not award it. [Creative entry][creative] · [Creeper pool][creeper-loot] · [Disc tag][disc-tag]

## Usage

Use the disc on an empty [Jukebox](../blocks/Jukebox.md#inserting-and-ejecting-discs). Its registered playable component selects song **`minecraft:otherside`** from the loaded song registry. [Component][component] · [Song key][keys] · [Registry loading][registry] · [Insertion][insert] · [Song lookup and start][start]

The bundled song record specifies **3:15 (195 seconds)**, sound event **`minecraft:music_disc.otherside`**, and **Comparator strength 14**. These are configured values, not measured audio duration. The active Jukebox reads the record for playback and Comparator output; the shared [song-length guide](../blocks/Jukebox.md#song-lengths-and-comparator-values) explains end padding and the separate playing signal. [Song record][song] · [Playback][player] · [Length consumer][end] · [Comparator lookup][comparator]

## Behavior

The disc stacks to **one item**. Playback does not consume it: use the occupied Jukebox to recover it. The canonical [Jukebox guide](../blocks/Jukebox.md) owns insertion/ejection, Hopper transfers, redstone and save/reload behavior. [Item properties][item] · [Disc ejection][recover]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked all three chest tables and assignments, the optional city replacement, Creeper tag exclusion and loaded song consumers. The server loads the cited loot resources through its reloadable registry and song records through world-data loading. No gameplay, loot-distribution, listening or timed-playback test was run. Data packs, later builds and already-looted terrain can differ. [Loot loading][reload] · [World loading][world-load]

Related: [Jukebox item](Jukebox.md) · [Ancient City](../structures/AncientCity.md) · [Stronghold](../structures/Stronghold.md) · [Monster Room](../structures/MonsterRoom.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2341-L2359
[pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L63-L101
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L46-L55
[city]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[corridor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/stronghold_corridor.json
[dungeon]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json
[city-chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L285-L317
[corridor-assign]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L277-L280
[dungeon-assign]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java#L90-L114
[ice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city_ice_box.json
[crossing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/stronghold_crossing.json
[library]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/stronghold_library.json
[rebalance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json
[container]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1566-L1569
[creeper-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/creeper.json
[disc-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/creeper_drop_music_discs.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongs.java#L26-L37
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L125-L132
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L109-L119
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/otherside.json
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java#L48-L80
[end]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSong.java#L42-L55
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L69
[recover]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L47-L61
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L70
