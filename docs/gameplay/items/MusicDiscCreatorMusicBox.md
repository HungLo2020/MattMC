# Music Disc (Creator Music Box)

**Music Disc (Creator Music Box)** (`minecraft:music_disc_creator_music_box`) is a reusable [Jukebox](../blocks/Jukebox.md) disc from assigned [Trial Chamber](../structures/TrialChambers.md) pot loot. Its recording is **Creator (Music Box)**, separate from [Creator](MusicDiscCreator.md). [Item registration][disc-item] · [Song data][song]

## Obtaining

Search generated **Decorated Pots assigned the corridor pot loot table** in Trial Chambers. The connected decor pool includes Flow, Guster, Scrape and undecorated pot templates; all four assign this table to their stored contents. A pot's face decorations are separate from its rolled contents, and a pot you craft and place yourself does not receive this loot assignment. [Decor pool][decor-pool] · [Flow][flow-pot] · [Guster][guster-pot] · [Scrape][scrape-pot] · [Undecorated pot][plain-pot] · [Generated pots](../blocks/DecoratedPot.md#generated-pots-and-saved-contents)

The table makes **one weighted contents roll**. This disc has **weight 5 out of 351**, so its chance is **5/351, about 1.42%, per assigned pot loot generation**, yielding **one disc** when chosen. This is not a chance for every decorative pot or a promise of a fixed number of pots in a chamber. [Contents table][pot-loot] · [Weighted selection][rolls] · [Default weight and quality][weights]

**Break a loot-bearing pot and collect the loose contents.** Ordinary breaking spills storage separately from the pot or its face ingredients; shattering the faces is unnecessary just to recover a stored disc. See [Decorated Pot breaking](../blocks/DecoratedPot.md#breaking-keep-the-pot-or-recover-ingredients) for the tool choices. Container access resolves the assigned loot once, so revisiting the same emptied pot does not roll again. [Lazy pot access][pot-access] · [One-time loot unpacking][unpack] · [Contents spill][spill]

The disc is also category-listed for the **Creative [inventory item browser](../mechanics/InventoryBrowser.md)**. [Category entry][creative]

## Usage

Insert the disc into an empty Jukebox to select **Creator (Music Box)** (`minecraft:creator_music_box`). The bundled song has a **configured length of 73 seconds (1:13)** and **Comparator strength 11**. These values come from its song record, not an audio measurement; the Jukebox reads that record for its Comparator output. [Song data][song] · [Playable component][component] · [Comparator consumer][comparator]

Follow [Jukebox insertion and ejection](../blocks/Jukebox.md#inserting-and-ejecting-discs) to recover the disc, and [redstone output](../blocks/Jukebox.md#redstone-output) for circuits and the separate playing signal.

## Behavior

The item **stacks to one** and carries the Creator Music Box jukebox-playable component. It is reusable, and the Jukebox keeps the inserted item after the song finishes. [Item properties][disc-item] · [Item storage][comparator] · [Song-player finish][player]

## Notes

The longer [Creator disc](MusicDiscCreator.md) has a different item ID, song record and acquisition route: ominous Vault rewards. A corridor pot roll selects its contents independently of which sherd decorates the pot.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked assigned pot templates, connected pool references, lazy loot/spill callers, exact loot weights, category entry, playable component, loaded song record and Comparator consumer. No generated-world, loot-frequency, gameplay, listening, audio-duration or redstone test was run. Shared pot and playback mechanics remain with [Decorated Pot](../blocks/DecoratedPot.md) and [Jukebox](../blocks/Jukebox.md); data packs can change defaults.

[disc-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2311-L2313
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/creator_music_box.json
[decor-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/decor.json
[flow-pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/decor/flow_pot.nbt
[guster-pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/decor/guster_pot.nbt
[scrape-pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/decor/scrape_pot.nbt
[plain-pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/decor/undecorated_pot.nbt
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/pots/trial_chambers/corridor.json
[rolls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java
[pot-access]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotBlockEntity.java#L131-L151
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[spill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1562
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L119
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java
