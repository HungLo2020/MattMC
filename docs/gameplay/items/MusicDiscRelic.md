# Music Disc (Relic)

**Music Disc (Relic)** (`minecraft:music_disc_relic`) is an archaeology collectible from [Trail Ruins](../structures/TrailRuins.md). Bring a serviceable [Brush](Brush.md) and preserve suspicious blocks while excavating. [Registration][item] · [Rare loot][rare]

## Obtaining

Complete brushing on **Suspicious Gravel assigned `minecraft:archaeology/trail_ruins_rare`**. That table makes **one roll among 12 equally weighted entries**, including **one Relic disc**, so its chance is **1/12 (about 8.33%) conditional on that rare table**. This is not the chance per arbitrary Suspicious Gravel block, per ordinary Gravel block or per entire ruin. [Rare table][rare] · [Default weights][weights] · [Selection][pool]

The Trail Ruins houses-archaeology processor assigns the rare table to selected replacement blocks; the configured building and tower pools use that processor. Other suspicious blocks receive the **common** table, which has no Relic entry. Both assignments begin as the same undusted Suspicious Gravel, so appearance alone does not reveal a rare-table block. The [Trail Ruins guide](../structures/TrailRuins.md#which-gravel-can-hold-rare-finds) owns the generation limits and which pieces use each processor. [Loot assignment][processor] · [Tower pool][tower] · [Building pool][buildings] · [Saved loot data][append] · [Common table][common]

**Brush the block while it is supported; do not mine it to collect the disc.** Follow [safe excavation](../structures/TrailRuins.md#prepare-and-excavate-without-losing-finds) and [Brush archaeology](Brush.md#archaeology-basics). The first successful brushing step resolves and stores the assigned result; completion releases it and replaces the block with ordinary Gravel. Interrupting and retrying does not reroll that reward. [Loot resolution and completion][brush] · [Empty block loot][gravel-loot]

Relic is also listed in Creative. It is absent from the bundled Creeper music-disc tag, so the ordinary Creeper disc-drop route does not supply it. [Creative entry][creative] · [Creeper pool][creeper-loot] · [Disc tag][disc-tag]

## Usage

Use the disc on an empty [Jukebox](../blocks/Jukebox.md#inserting-and-ejecting-discs). Its registered playable component selects song **`minecraft:relic`** from the loaded song registry. [Component][component] · [Song key][keys] · [Registry loading][registry] · [Insertion][insert] · [Song lookup and start][start]

The bundled song record specifies **3:38 (218 seconds)**, sound event **`minecraft:music_disc.relic`**, and **Comparator strength 14**. These are configured values, not measured audio duration. The active Jukebox reads the record for playback and Comparator output; the shared [song-length guide](../blocks/Jukebox.md#song-lengths-and-comparator-values) explains end padding and the separate playing signal. [Song record][song] · [Playback][player] · [Length consumer][end] · [Comparator lookup][comparator]

## Behavior

The disc stacks to **one item**. Playback does not consume it: use the occupied Jukebox to recover it. The canonical [Jukebox guide](../blocks/Jukebox.md) owns insertion/ejection, Hopper transfers, redstone and save/reload behavior. [Item properties][item] · [Disc ejection][recover]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked rare/common archaeology tables, processor/pool assignment and Brush consumption, plus item/song mapping and consumers. The server loads the cited loot resources through its reloadable registry and song records through world-data loading. No gameplay, loot-distribution, listening or timed-playback test was run. Data packs, later builds and already-looted terrain can differ. [Loot loading][reload] · [World loading][world-load]

Related: [Trail Ruins](../structures/TrailRuins.md) · [Brush](Brush.md) · [Jukebox item](Jukebox.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2341-L2359
[rare]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_rare.json
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L46-L55
[pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L63-L101
[processor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_houses_archaeology.json
[tower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/tower.json
[buildings]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/buildings.json
[append]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/rule/blockentity/AppendLoot.java#L11-L32
[common]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_common.json
[brush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L147
[gravel-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/suspicious_gravel.json
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1566-L1569
[creeper-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/creeper.json
[disc-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/creeper_drop_music_discs.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongs.java#L26-L37
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L125-L132
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L109-L119
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/relic.json
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java#L48-L80
[end]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSong.java#L42-L55
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L69
[recover]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L47-L61
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L70
