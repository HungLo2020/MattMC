# Music Disc (Precipice)

**Music Disc (Precipice)** (`minecraft:music_disc_precipice`) is a reusable [Jukebox](../blocks/Jukebox.md) disc from normal [Trial Chamber](../structures/TrialChambers.md) rewards. Both normal Vaults and particular generated chests can use the relevant reward table. [Item registration][disc-item] · [Reward entry][unique]

## Obtaining

Use a **Trial Key at an eligible normal [Vault](../blocks/Vault.md#normal-vaults)**. The generated normal Vault template assigns the reward table containing Precipice. Follow [Vault activation and collection](../blocks/Vault.md#activating-and-using-a-vault); clearing a Trial Spawner does not directly give this disc. [Vault configuration][vault-template] · [Connected reward pool][vault-pool] · [Accepted-key reward caller][vault-use]

There is also a **chest route**: the bundled `corridor/entrance_1`, `hallway/rubble` and `hallway/rubble_chamber` templates contain a chest assigned that same normal reward table. These chests open without a key and do not use Vault player eligibility. The corridor and hallway fallback pools supply these candidate pieces; this does not promise any particular chest count in every chamber. Ordinary containers resolve their assigned loot once, so returning after a spawner cooldown does not refill them. [Entrance chest][entrance] · [Rubble chests][rubble] [rubble-chamber] · [Corridor choices][corridor-pool] · [Hallway fallback choices][fallback-pool] · [One-time container fill][unpack]

For either assigned route, the normal table has a **25% chance of one unique-table roll**. Precipice has **weight 2 out of 12** in that roll, giving **1/24, about 4.17%, per normal reward generation**, with **one disc** if selected. This chance applies once per accepted normal Vault opening or once per assigned chest fill, not to every chest, reward stack or chamber. The other reward pools do not add Precipice rolls. [Outer table][reward] · [Unique choices][unique] · [Weighted selection][rolls] [weights]

The disc is also category-listed for the **Creative [inventory item browser](../mechanics/InventoryBrowser.md)**. [Category entry][creative]

## Usage

Insert the disc into an empty Jukebox to select **Precipice** (`minecraft:precipice`). Its bundled record has a **configured length of 299 seconds (4:59)** and **Comparator strength 13**. These are configured song values, not an audio measurement; the Jukebox obtains its Comparator output from the inserted record. [Song data][song] · [Playable component][component] · [Comparator consumer][comparator]

See [Jukebox insertion and ejection](../blocks/Jukebox.md#inserting-and-ejecting-discs) for retrieving the disc, and [redstone output](../blocks/Jukebox.md#redstone-output) for circuits and the separate playing signal.

## Behavior

The item **stacks to one** and carries the Precipice jukebox-playable component. It remains reusable after playback, and the Jukebox retains the inserted item when the song finishes. [Item properties][disc-item] · [Item storage][comparator] · [Song-player finish][player]

## Notes

[Creator](MusicDiscCreator.md) is the disc in the ominous reward family. Bringing an omen to a normal Vault does not change its configured rewards; follow [normal versus ominous rewards](../blocks/Vault.md#normal-versus-ominous-rewards) and [Vault player history](../blocks/Vault.md#player-history-and-persistence) before spending keys.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked assigned Vault/chest templates and pool routes, active reward/container callers, nested loot weights, category entry, playable component, loaded song record and Comparator consumer. No world-generation, gameplay, loot-frequency, listening, audio-duration or redstone test was run. Data packs and custom container/Vault assignments can change these defaults. [Trial Chambers](../structures/TrialChambers.md), [Vault](../blocks/Vault.md) and [Jukebox](../blocks/Jukebox.md) own shared operation.

[disc-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2353-L2355
[unique]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_unique.json
[reward]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward.json
[vault-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/reward/vault.nbt
[vault-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/reward/all.json
[vault-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L329
[entrance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/corridor/entrance_1.nbt
[rubble]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/hallway/rubble.nbt
[rubble-chamber]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/hallway/rubble_chamber.nbt
[corridor-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/corridor.json
[fallback-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/hallway/fallback.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[rolls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1565
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/precipice.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L119
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java
