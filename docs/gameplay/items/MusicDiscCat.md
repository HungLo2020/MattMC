# Music Disc (Cat)

**Music Disc (Cat)** (`minecraft:music_disc_cat`) is a reusable disc for a [Jukebox](../blocks/Jukebox.md). It plays the bundled `cat` song and gives a Comparator reading of **2** while inserted. Each inventory stack holds **one disc**. [Item registration][item] · [Song data][song] · [Comparator lookup][comparator]

## Obtaining

### Creeper kills

With **`doMobLoot` enabled**, have a [Skeleton](../mobs/Skeleton.md), Stray or Bogged land the **final arrow hit** on a [Creeper](../mobs/Creeper.md) before it explodes. Putting the Creeper between yourself and the archer is a source-derived way to attempt this; it is not a tested farm design. The arrow keeps its owner's attacker attribution, which the Creeper's death-loot table checks. [Bow attack][skeleton-bow] · [Arrow damage][arrow-hit] · [Server damage dispatch][damage] · [Death and attacker context][death] · [Mob-loot gate][mob-loot-rule]

A qualifying kill selects **one disc from 12 equally weighted choices**, giving **1/12 (about 8.33%) for Cat per qualifying Creeper death**. The disc pool has one roll and no Looting multiplier or player-kill requirement; Looting changes the separate Gunpowder pool. Repeated kills can repeat the same disc and do not guarantee a complete collection. [Creeper loot][creeper-loot] · [Twelve-disc tag][disc-tag] · [Tag expansion][tag-roll] · [Default weights][weights] · [Selection][pool]

The test is the **attacker on the fatal damage source**, not any earlier hit or any projectile. A player-owned or ownerless arrow does not satisfy it. The bundled attacker tag contains Skeleton, Stray, Bogged, Wither Skeleton and Skeleton Horse. A Wither Skeleton's direct killing attack can satisfy the same check; the tag does not make one target Creepers automatically. Skeleton Horse membership alone does not establish an ordinary attacking route. A Creeper's own detonation skips ordinary death loot. [Eligible attacker types][killers] · [Fatal-source context][death] · [Wither Skeleton attack][wither-hit] · [Horse behavior][horse] · [Detonation][explosion]

### Structure chests

Cat can also be selected by these **assigned chest tables**. Each selection of its entry produces **one disc**. The fractions below are **per weighted selection in the named pool**, not per chest or per structure; a chest makes several selections, which can repeat. [Roll selection][pool]

| Chest source | Relevant pool rolls | Cat weight / total | Table |
| --- | ---: | ---: | --- |
| [Monster Room](../structures/MonsterRoom.md#optional-chests-and-their-rewards) | 1–3 | 15 / 144 | [Simple dungeon][dungeon] |
| [Woodland Mansion](../structures/WoodlandMansion.md#chest-rewards), Chest-marker rewards | 1–3 | 15 / 127 | [Mansion][mansion] |
| [Ancient City](../structures/AncientCity.md#ordinary-city-chests), ordinary loot chests | 5–10 | 2 / 86 | [City][city] |

The dungeon generator assigns its table to successfully placed room chests; mansion Chest markers assign the mansion table. Ordinary city templates save the city table on their chests. **Fixed-inventory or empty mansion chests and Ancient City ice-box chests are different sources.** Follow the linked structure guides for where those chests occur. These are one-time supplies: unpacking the saved table clears its reference, so reopening a chest does not reroll it. [Dungeon assignment][dungeon-assign] · [Mansion assignment][mansion-assign] · [Example city chest][city-chest] · [Saved-table loading and fill][container]

The optional [Trade Rebalance pack](../trading/Trading.md#optional-trade-rebalance) replaces the ordinary city table when selected, but keeps Cat's weight **2**, the main pool total **86**, and its **5–10 rolls**. Custom data packs can change any of these tables or tags. [Bundled replacement][rebalance]

### Creative and crafting

The disc can be requested through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. No crafting recipe for this disc is bundled in the reviewed recipe resources; the recipe for Music Disc 5 does not create other tracks. [Creative entry][creative] · [Recipe resources][recipes] · [Combined browser][catalog-browser]

## Usage

Use the disc on an empty [Jukebox](../blocks/Jukebox.md#inserting-and-ejecting-discs). Its playable component resolves the song below from the loaded Jukebox song registry. [Item mapping][item] · [Song key][keys] · [Registry loading][load] · [Song lookup and start][song-consumer]

| Property | Bundled value |
| --- | --- |
| Song ID | `minecraft:cat` |
| Sound event | `minecraft:music_disc.cat` |
| Configured length | **3:05 (185 seconds)** |
| Comparator strength | **2** |

These are the song record's configured values, **not a measured audio duration**. The Jukebox reads the record for playback and Comparator output. For the separate playback-end padding and ordinary playing signal, see [song lengths and Comparator values](../blocks/Jukebox.md#song-lengths-and-comparator-values). [Song record][song] · [Playback consumer][song-player] · [End condition][duration] · [Comparator consumer][comparator]

## Behavior

The canonical [Jukebox guide](../blocks/Jukebox.md) covers disc recovery, insertion/ejection, automatic transfers, redstone, nearby-mob reactions and save/reload limits. Those are shared Jukebox behaviors; this item's distinct data is its song and Comparator value.

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/song registration, loaded song data and consumers, resource filenames and recipes, and the active Creeper damage-to-loot chain, plus the three chest tables and their assignments. No gameplay, disc-farming, chest-looting, listening or timed playback test was run. Server data packs and later builds can differ. No audio, lyrics or artwork were copied.

Related: [Jukebox item](Jukebox.md) · [Creeper](../mobs/Creeper.md#drops-and-special-rewards) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2296-L2322
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1551-L1556
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
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongs.java#L14-L19
[load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L125-L132
[song-consumer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L109-L119
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L69
[song-player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java#L48-L80
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSong.java#L42-L50
[dungeon]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json
[mansion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/woodland_mansion.json
[city]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[dungeon-assign]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java#L84-L113
[mansion-assign]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1200-L1232
[city-chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[container]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[rebalance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/cat.json
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe

[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
