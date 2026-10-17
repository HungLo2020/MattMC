# Prismarine Shard

**Prismarine Shard** (`minecraft:prismarine_shard`) is a crafting ingredient. It is registered as an ordinary item, not a placeable block, and is different from [Prismarine Crystals](PrismarineCrystals.md). [Registration][shard-item]

## Obtaining

Both [Guardians](../mobs/Guardian.md) and [Elder Guardians](../mobs/ElderGuardian.md) have a Shard death-loot pool that starts at **0–2 Shards**. A zero result is possible: neither mob guarantees a Shard on each kill. The tables are connected to the registered entities through their normal death-loot path. [Guardian table][guardian-loot] · [Elder table][elder-loot] · [Registrations][guardian-registration] [Elder registration][elder-registration] · [Loot naming and dispatch][entity-loot-name] [Entity loot][entity-loot] [Loot dispatch][loot-dispatch]

An eligible attacker's Looting adds a count bonus ranging from **0 to the Looting level**, with the function rounding a level-scaled random value. Thus Looting III permits up to **5 Shards**, without guaranteeing five or even one. The Shard pool itself has no player-kill condition; this is separate from other rewards in the same tables. These are source-derived bounds, not measured drop rates. [Shard pools][guardian-loot] [Elder loot][elder-loot] · [Looting function][looting]

Monuments provide the verified encounter route: see [Ocean exploration](../biomes/Oceans.md#structures-and-exploration) and the [Prismarine family's acquisition section](../blocks/Prismarine.md#ocean-monument-route). Breaking Prismarine blocks returns the corresponding block item, not Shards. Sea Lantern's ordinary mining reward is **Crystals**, also not Shards. [Prismarine loot][loot-prismarine] · [Sea Lantern loot][sea-loot]

## Usage

Use Shards for the three [Prismarine full-block finishes](../blocks/Prismarine.md#crafting-full-blocks), or combine them with Crystals for a [Sea Lantern](../blocks/LuminousBlocks.md#sea-lantern). Those canonical block guides contain the exact recipes. The family shapes are made from the finished full blocks, rather than directly from Shards. [Full-block recipes][craft-prismarine] [Craft prismarine bricks][craft-prismarine_bricks] [Craft dark prismarine][craft-dark_prismarine] · [Lantern recipe][sea-recipe]

## Behavior

A Shard does not place Prismarine or count as a Conduit frame block. Craft or collect the appropriate full block first, then follow the [Conduit frame guide](../blocks/Conduit.md#build-a-valid-frame). [Plain-item registration][shard-item] · [Exact frame blocks][conduit]

## Notes

The checked recipe set has no reverse recipe that recovers Shards from the constructed family blocks. Keep any Shards needed for another finish or Sea Lantern before spending them on a build.

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. Checked item registration, entity-to-loot dispatch, Shard counts, Looting and the four direct Shard crafting recipes. No combat, farming, drop-rate or crafting test was run.

Related: [Prismarine](Prismarine.md) · [Dark Prismarine](DarkPrismarine.md) · [Prismarine Crystals](PrismarineCrystals.md) · [Sea Lantern](SeaLantern.md) · [Items](Items.md)

[shard-item]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L2120-L2121
[guardian-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/entities/guardian.json#L1-L176
[elder-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json#L1-L205
[guardian-registration]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/EntityType.java#L735-L743
[elder-registration]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/EntityType.java#L520-L528
[entity-loot-name]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/Entity.java#L3922-L3924
[loot-dispatch]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[looting]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[loot-prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine.json#L1-L21
[sea-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/sea_lantern.json#L1-L71
[craft-prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine.json#L1-L15
[craft-prismarine_bricks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/prismarine_bricks.json#L1-L19
[craft-dark_prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/dark_prismarine.json#L1-L17
[sea-recipe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/sea_lantern.json#L1-L17
[conduit]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L42
