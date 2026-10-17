# Monster Spawner

A **Monster Spawner** (`minecraft:spawner`) repeatedly attempts to create its configured mob while an eligible player is nearby. Its cage and rotating display identify a spawn source, not a container. This guide covers the ordinary spawner; [Trial Spawners](../items/TrialSpawner.md) use a different system.

## Finding and preserving one

Checked generation code places ordinary spawners in monster rooms, selected Mineshaft corridors, Stronghold portal rooms, and Nether Fortress blaze platforms. Monster rooms choose from Zombie, Skeleton, and Spider; Mineshaft spawners create Cave Spiders, portal-room spawners [Silverfish](../mobs/Silverfish.md#spawners), and fortress platforms Blazes. These are selected source-confirmed routes, not a complete structure inventory. See [Stronghold](../structures/Stronghold.md), [Nether Fortress](../structures/NetherFortress.md), and [Cave Spider](../mobs/CaveSpider.md) for context. [Generation][rooms] · [Mineshaft][mineshaft] · [Portal room][stronghold] · [Fortress][fortress]

**Breaking one does not drop a spawner item, even with Silk Touch.** Its bundled block loot is empty. A successful Survival break with a correct, usable pickaxe can award **15–43 XP** with block drops enabled; the two random rolls mean outcomes are not uniformly distributed. Breaking it destroys that reusable spawn source. Do not mine one expecting to relocate it. [Loot][loot] · [Properties][blocks] · [Tool tag][pickaxe] · [Break reward][spawner-block] · [Mining gate][mining] · [XP rule][block]

The item is listed in the Spawn Eggs source category and can be requested through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. No bundled crafting recipe produces it. See the [item page](../items/MonsterSpawner.md) for placement and configuration. [Creative listing][creative] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

## Activation and timing

Ordinary defaults in the active server implementation are:

| Setting | Default behavior |
| --- | --- |
| Player activation | A living, nonspectator player strictly less than 16 blocks from the spawner center |
| Initial delay | 20 game ticks |
| Delay after a successful batch/reset | Randomly 200–799 game ticks, about 10–40 seconds at 20 TPS |
| Attempts per batch | Up to four; an attempt is not a guaranteed mob |
| Nearby-entity limit | Six entities of the spawned entity's exact runtime class in the checked area |
| Random spawn offset | X/Z within four blocks of the center; Y one block below, level, or one above |

The nearby-entity test uses the spawner block's bounding box expanded four blocks in every direction. It is separate from the player-activation distance. Existing mobs in that region can prevent more spawning, even when there is clear floor space elsewhere. Custom spawner data can replace these values and supply fixed positions or other spawn rules. [Server tick and defaults][base] · [Player filter][players]

When no eligible player is nearby, the server countdown pauses. The `spawnerBlocksEnabled` game rule must also be enabled; its bundled default is true. There is no ordinary redstone-input toggle in this spawner tick path. [Server tick][base] · [Server gate][server] · [Game rule][rules]

## Space, light, and unsuccessful attempts

The attempted position must pass collision, applicable spawn-placement rules, and the mob's obstruction checks. Light requirements depend on the selected mob or explicit custom spawn rules. **Torches are not a universal spawner off switch:** the current [Blaze guide](../mobs/Blaze.md) explains why that mob's ordinary registered predicate does not require darkness here.

If a batch creates at least one mob, the spawner selects a new delay. A full nearby-entity limit or certain invalid-data/addition failures also reset the delay. If all ordinary candidate positions fail their space or spawn checks, it can retry without waiting through a fresh 200–799-tick interval. The default timing table is therefore not a measured farm-output rate. [Attempt and reset paths][base]

For troubleshooting, first check player distance, the game rule, nearby mobs, clear spawn space, and the actual mob's rules. Moving existing mobs away from the local limit region can permit more attempts, but does not bypass collision or other spawn checks.

## Spawn eggs and custom setups

Using a registered spawn egg with a valid entity type on a spawner changes its configured mob through the active spawner interface, provided spawners are enabled. The egg does not need to match the mob already configured. This does not guarantee the chosen creature's space or environmental requirements will be met. Normal Survival use consumes one egg; the ordinary Creative item-use path restores the stack's original count. [Egg interaction][egg] · [Creative count restoration][creative-use]

A newly placed plain spawner has no default entity ID in its empty spawn data, so configure it rather than assuming it starts as a Pig spawner. Existing custom spawn potentials, entity data, and rules require separate inspection; changing an ID is not a promise that every custom setting is reset. [Empty spawn data][spawn-data] · [Entity assignment and potential selection][base]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked current block/entity tick wiring, activation and attempt paths, generation callers, loot/XP and mining gates, Creative registration, recipes, spawn-egg interaction, and default spawn data. No in-game spawning, lighting, mining, egg configuration, redstone, or farm test was run. Data packs, commands, game rules, and custom block-entity data can change outcomes.

The ordinary spawner's registered block entity forwards its server tick to the shared spawner implementation. [Block-entity tick path][spawner-entity] · [Block-entity registration][entity-types]

Related: [Monster Spawner item](../items/MonsterSpawner.md) · [Blaze](../mobs/Blaze.md) · [Cave Spider](../mobs/CaveSpider.md) · [Blocks](Blocks.md)

[rooms]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java
[mineshaft]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java
[stronghold]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java
[fortress]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/spawner.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L1227-L1237
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[spawner-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SpawnerBlock.java
[mining]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L293
[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1962-L1969
[base]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/BaseSpawner.java
[players]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/EntityGetter.java#L103-L115
[server]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java#L1525-L1527
[rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/GameRules.java#L241-L243
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L78
[creative-use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L379-L389
[spawner-entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/SpawnerBlockEntity.java
[entity-types]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L101
[spawn-data]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/SpawnData.java#L27-L44

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1967
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
