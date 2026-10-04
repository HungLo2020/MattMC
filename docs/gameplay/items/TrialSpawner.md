# Trial Spawner

The **Trial Spawner item** (`minecraft:trial_spawner`) places the block used for finite normal and ominous trials. Its active encounters, participants, rewards and cooldowns are documented in the [Trial Spawner block guide](../blocks/TrialSpawner.md#trial-spawner). [Item registration][item] · [Block implementation][block]

## Obtaining

The item is listed in the **Spawn Eggs** source category and can be requested through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. No bundled crafting recipe produces it. Trial Chambers contain configured placed spawners, but **breaking one does not drop this item, even with Silk Touch**: its loot table has no pools. Mining also has no dedicated XP reward in this implementation. Do not break a found Trial Spawner expecting to relocate it. [Creative listing][creative] · [Empty block loot][loot] · [Block class][block] · [Inherited break behavior][break-default] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

The block's current hardness is 50. Its registration has no correct-tool drop requirement, and the standard pickaxe mining tag does not include it. See [Finding and preserving one](../blocks/TrialSpawner.md#finding-and-preserving-one) for the complete checked tool/loot limits. [Registration][registration] · [Pickaxe tag][pickaxe]

## Usage

A plain placed item starts **inactive and non-ominous**. Its default configuration does not name a mob, so do not assume it immediately begins a Zombie or Pig trial. A supplied spawn egg can configure the entity when `spawnerBlocksEnabled` is enabled. Ordinary Survival egg use consumes one egg; the usual Creative interaction path restores its count. [Default state][block] · [Default entity setup][entity] · [Empty spawn-data handling][empty-data] · [Egg use][egg] · [Creative handling][creative-egg]

Changing the entity resets recorded trial progress and replaces both normal and ominous spawn choices while retaining the other configuration values. Existing custom data can therefore still change counts, timing and rewards. [Reset][egg-reset] · [Configuration replacement][full-config]

## Behavior

The placed block uses nearby-player detection and a finite wave state machine. It has no storage menu or ordinary redstone on/off input. Its normal participant range defaults to 14 blocks, and a cleared encounter ejects loose rewards before returning through cooldown. Follow the canonical guide for the strict range/visibility checks and the [shared reward-table selection](../blocks/TrialSpawner.md#clearing-rewards-and-cooldown). [Player detector][players] · [State machine][states]

## Notes

- This item places `minecraft:trial_spawner`, distinct from the [Monster Spawner](MonsterSpawner.md)
- Normal and ominous settings belong to the placed spawner's configuration; the ordinary block loot does not recover that configuration
- Source-reviewed on 2026-10-02 at `053cd852a8609f4002234ce0d445d3a345b551ae`; no gameplay, crafting, placement, spawning, egg, mining or loot-frequency test was run

Related: [Trial Spawner block](../blocks/TrialSpawner.md) · [Trial Key](TrialKey.md) · [Ominous Trial Key](OminousTrialKey.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L2674
[block]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/TrialSpawnerBlock.java#L20-L62
[creative]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1960-L1969
[loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/trial_spawner.json
[break-default]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L357-L358
[registration]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L6775-L6786
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[entity]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/TrialSpawnerBlockEntity.java#L22-L92
[empty-data]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L227-L252
[egg]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L75
[creative-egg]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L379-L389
[egg-reset]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L340-L344
[full-config]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L382-L403
[players]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/PlayerDetector.java#L23-L51
[states]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L31-L146

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1968
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
