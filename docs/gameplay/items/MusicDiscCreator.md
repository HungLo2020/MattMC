# Music Disc (Creator)

**Music Disc (Creator)** (`minecraft:music_disc_creator`) is a reusable [Jukebox](../blocks/Jukebox.md) disc found among ominous [Trial Chamber](../structures/TrialChambers.md) rewards. It is a separate item and song from [Creator Music Box](MusicDiscCreatorMusicBox.md). [Item registration][disc-item] · [Reward entry][unique]

## Obtaining

Use an **Ominous Trial Key at an eligible ominous [Vault](../blocks/Vault.md#ominous-vaults)**. The generated ominous Vault template assigns the reward table that can select this disc. Finding a chamber, clearing a Trial Spawner, or possessing a key does not itself award Creator. Follow the [Vault guide](../blocks/Vault.md#activating-and-using-a-vault) for activation and collection. [Assigned configuration][vault-template] · [Connected reward pool][vault-pool] · [Accepted-key reward caller][vault-use]

The bundled table has a **75% chance of one unique-table roll**. Creator has **weight 1 out of 10** in that roll, giving **3/40 = 7.5% per ominous reward generation**, with **one disc** if selected. This is a chance per accepted opening, not per ejected stack or chamber. The other reward pools do not add Creator rolls. The cycling display does not reserve the next reward. [Outer table][reward] · [Unique choices][unique] · [Weight/roll calculation][rolls] [weights] · [Preview and actual selection][vault-roll]

In ordinary play, look for another eligible Vault after an opening; waiting for a Trial Spawner cooldown does not reset [Vault player history](../blocks/Vault.md#player-history-and-persistence). Creator is also category-listed for the **Creative [inventory item browser](../mechanics/InventoryBrowser.md)**. [Category entry][creative]

## Usage

Insert the disc into an empty Jukebox to select **Creator** (`minecraft:creator`). Its bundled record has a **configured length of 176 seconds (2:56)** and **Comparator strength 12**. These are song-data values, not a measured audio duration. The Jukebox reads the inserted song's Comparator value. [Song data][song] · [Playable component][component] · [Song lookup and Comparator consumer][comparator]

Use [Jukebox insertion and ejection](../blocks/Jukebox.md#inserting-and-ejecting-discs) to recover the disc, and [redstone output](../blocks/Jukebox.md#redstone-output) for circuits. The shared guide explains playback end padding and the separate playing signal.

## Behavior

The registered item **stacks to one** and carries the Creator jukebox-playable component. Playback leaves the disc available for reuse; when a song finishes, the inserted item remains in the Jukebox. [Item properties][disc-item] · [Item storage][comparator] · [Song-player finish][player]

## Notes

For the related **Creator (Music Box)** recording, search the assigned Trial Chamber pot loot described on [its item page](MusicDiscCreatorMusicBox.md). [Precipice](MusicDiscPrecipice.md) belongs to the normal reward family.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the placed Vault assignment and reward caller, nested loot weights, category entry, registered playable component, loaded song record and Comparator consumer. No gameplay, loot-frequency, listening, audio-duration or redstone test was run. Data packs and modified Vault configurations can change these defaults. Shared operation remains with [Vault](../blocks/Vault.md) and [Jukebox](../blocks/Jukebox.md).

[disc-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2308-L2310
[unique]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_unique.json
[reward]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json
[vault-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/reward/ominous_vault.nbt
[vault-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/reward/ominous_vault.json
[vault-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L273
[vault-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L284-L329
[rolls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1564
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/creator.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L119
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java
