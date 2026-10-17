# Music Disc (Pigstep)

**Music Disc (Pigstep)** (`minecraft:music_disc_pigstep`) is a playable collectible from a particular [Bastion Remnant](../structures/BastionRemnant.md) chest table. Prepare for the Nether expedition and its residents before searching for the disc. [Registration][item] · [Loot table][bastion]

## Obtaining

Search chests assigned **`minecraft:chests/bastion_other`**, the Bastion's general chest table. Its first pool makes **one weighted selection**, and Pigstep has weight **5 out of 89**, giving **5/89 (about 5.62%)** for **one disc** from that pool. No other pool in this table adds Pigstep. This chance is conditional on an untouched chest using that exact table, not a chance per Bastion. [Exact table][bastion] · [Weighted selection][pool]

The building's broad layout does not identify every chest's loot. For example, a bundled **bridge rampart** saves `bastion_other`, even though the bridge entrance uses `bastion_bridge`. The separate bridge, Hoglin-stable and treasure chest tables have no Pigstep entry. The [Bastion chest guide](../structures/BastionRemnant.md#chest-rewards) owns the layout/table distinctions. [General-table rampart][rampart] · [Bridge entrance][entrance] · [Bridge table][bridge] · [Stable table][stable] · [Treasure table][treasure]

Template placement loads the saved chest data; the chest resolves its assigned table once and clears that reference. Reopening a looted chest does not reroll Pigstep. Read [Bastion preparation](../structures/BastionRemnant.md#preparing-and-exploring) before looting: gold armor does not make theft safe or pacify Piglin Brutes. [Placement][place] · [One-time loot handling][container]

Pigstep is also listed in Creative. It is absent from the bundled Creeper disc tag, and the bundled Piglin bartering table does not contain it; collecting this disc is a chest-loot route. [Creative entry][creative] · [Creeper pool][creeper-loot] · [Disc tag][disc-tag] · [Bartering table][barter]

## Usage

Use the disc on an empty [Jukebox](../blocks/Jukebox.md#inserting-and-ejecting-discs). Its registered playable component selects song **`minecraft:pigstep`** from the loaded song registry. [Component][component] · [Song key][keys] · [Registry loading][registry] · [Insertion][insert] · [Song lookup and start][start]

The bundled song record specifies **2:29 (149 seconds)**, sound event **`minecraft:music_disc.pigstep`**, and **Comparator strength 13**. These are configured values, not measured audio duration. The active Jukebox reads the record for playback and Comparator output; the shared [song-length guide](../blocks/Jukebox.md#song-lengths-and-comparator-values) explains end padding and the separate playing signal. [Song record][song] · [Playback][player] · [Length consumer][end] · [Comparator lookup][comparator]

## Behavior

The disc stacks to **one item**. Playback does not consume it: use the occupied Jukebox to recover it. The canonical [Jukebox guide](../blocks/Jukebox.md) owns insertion/ejection, Hopper transfers, redstone and save/reload behavior. [Item properties][item] · [Disc ejection][recover]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the general chest pool, decoded template assignments, separate Bastion tables, bartering/tag exclusions and loaded song consumers. The server loads the cited loot resources through its reloadable registry and song records through world-data loading. No gameplay, loot-distribution, listening or timed-playback test was run. Data packs, later builds and already-looted terrain can differ. [Loot loading][reload] · [World loading][world-load]

Related: [Bastion Remnant](../structures/BastionRemnant.md) · [Jukebox item](Jukebox.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2341-L2359
[bastion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/bastion_other.json
[pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L63-L101
[rampart]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/bastion/bridge/ramparts/rampart_0.nbt
[entrance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/bastion/bridge/starting_pieces/entrance.nbt
[bridge]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/bastion_bridge.json
[stable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/bastion_hoglin_stable.json
[treasure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/bastion_treasure.json
[place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L285-L317
[container]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1566-L1569
[creeper-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/creeper.json
[disc-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/creeper_drop_music_discs.json
[barter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongs.java#L26-L37
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L125-L132
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L109-L119
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/pigstep.json
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java#L48-L80
[end]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSong.java#L42-L55
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L69
[recover]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L47-L61
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L70
