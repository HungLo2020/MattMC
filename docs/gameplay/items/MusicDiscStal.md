# Music Disc (Stal)

**Music Disc (Stal)** (`minecraft:music_disc_stal`) is a reusable disc for a [Jukebox](../blocks/Jukebox.md). It plays the bundled `stal` song and gives a Comparator reading of **8** while inserted. Each inventory stack holds **one disc**. [Item registration][item] · [Song data][song] · [Comparator lookup][comparator]

## Obtaining

### Creeper kills

With **`doMobLoot` enabled**, have a [Skeleton](../mobs/Skeleton.md), Stray or Bogged land the **final arrow hit** on a [Creeper](../mobs/Creeper.md) before it explodes. Putting the Creeper between yourself and the archer is a source-derived way to attempt this; it is not a tested farm design. The arrow keeps its owner's attacker attribution, which the Creeper's death-loot table checks. [Bow attack][skeleton-bow] · [Arrow damage][arrow-hit] · [Server damage dispatch][damage] · [Death and attacker context][death] · [Mob-loot gate][mob-loot-rule]

A qualifying kill selects **one disc from 12 equally weighted choices**, giving **1/12 (about 8.33%) for Stal per qualifying Creeper death**. The disc pool has one roll and no Looting multiplier or player-kill requirement; Looting changes the separate Gunpowder pool. Repeated kills can repeat the same disc and do not guarantee a complete collection. [Creeper loot][creeper-loot] · [Twelve-disc tag][disc-tag] · [Tag expansion][tag-roll] · [Default weights][weights] · [Selection][pool]

The test is the **attacker on the fatal damage source**, not any earlier hit or any projectile. A player-owned or ownerless arrow does not satisfy it. The bundled `#minecraft:skeletons` attacker tag contains Skeleton, Stray, Bogged, Wither Skeleton and Skeleton Horse. A Wither Skeleton's direct killing attack can satisfy the same check; the tag does not make one target Creepers automatically. Skeleton Horse membership alone does not establish an ordinary attacking route. A Creeper's own detonation skips ordinary death loot. [Eligible attacker types][killers] · [Fatal-source context][death] · [Wither Skeleton attack][wither-hit] · [Horse behavior][horse] · [Detonation][explosion]

### Creative and other sources

The disc is listed in the Creative menu. No crafting recipe or chest-loot entry for Stal is bundled in the reviewed resources, including the bundled optional data packs. Its Creeper-tag membership does not imply the chest sources of other discs. [Creative entry][creative] · [Recipes][recipes] · [Loot tables][loot-tables] · [Optional packs][optional-packs]

## Usage

Use the disc on an empty [Jukebox](../blocks/Jukebox.md#inserting-and-ejecting-discs). Its `minecraft:jukebox_playable` component points to the song below in the loaded Jukebox song registry. [Item mapping][item] · [Component][component] [component-id] · [Song key][keys] · [Registry loading][load] · [Insertion][insertion] · [Song lookup and start][song-consumer]

| Property | Bundled value |
| --- | --- |
| Song ID | `minecraft:stal` |
| Sound event | `minecraft:music_disc.stal` |
| Configured length | **2:30 (150 seconds)** |
| Comparator strength | **8** |

These are the song record's configured values, **not a measured audio duration**. The Jukebox reads the record for playback and Comparator output. For the separate playback-end padding and ordinary playing signal, see [song lengths and Comparator values](../blocks/Jukebox.md#song-lengths-and-comparator-values). [Song record][song] · [Playback consumer][song-player] · [End condition][duration] · [Comparator consumer][comparator] [analog]

## Behavior

Playing the disc does not consume it. Use the occupied Jukebox to eject and recover it; a naturally finished song leaves the disc inside. [Ejection][ejection] · [Playback completion][song-player]

The canonical [Jukebox guide](../blocks/Jukebox.md) covers disc recovery, insertion/ejection, automatic transfers, redstone, nearby-mob reactions and save/reload limits. Those are shared Jukebox behaviors; this item's distinct data is its song and Comparator value.

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/song registration, loaded song data and consumers, resource filenames, recipe results and loot entries, and the active Creeper damage-to-loot chain. No gameplay, disc-farming, chest-looting, listening or timed playback test was run. Server data packs and later builds can differ. No audio, lyrics or artwork were copied.

Related: [Jukebox item](Jukebox.md) · [Creeper](../mobs/Creeper.md#drops-and-special-rewards) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2326-L2328
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1558
[creeper-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/creeper.json
[disc-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/creeper_drop_music_discs.json
[killers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/entity_type/skeletons.json
[death]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1528
[mob-loot-rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[tag-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/TagEntry.java#L44-L69
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L46-L55
[pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L63-L101
[skeleton-bow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L162-L207
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L377-L421
[damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1784
[wither-hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/WitherSkeleton.java#L94-L106
[horse]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonHorse.java#L62-L64
[explosion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L230-L239
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongs.java#L21
[load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L125-L132
[song-consumer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L109-L119
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L69
[song-player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java#L48-L80
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSong.java#L42-L50
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/stal.json
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[component-id]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L254-L256
[insertion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[analog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/JukeboxBlock.java#L96-L104
[ejection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L48-L61
[loot-tables]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[optional-packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks
