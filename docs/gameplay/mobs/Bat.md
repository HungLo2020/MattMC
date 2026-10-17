# Bat

The **Bat** is a harmless flying ambient mob. Look for it in dark spaces below the surface; it rests under blocks, has **6 health points (3 hearts)**, and provides no normal item or experience reward. [Health][bat-health] · [Loot table][bat-loot]

## Obtaining

Bats use ordinary biome-based spawning. Both [Dripstone Caves][bat-list] and [Lush Caves][bat-lush] include bats in their ambient spawn lists; the [Deep Dark][deep-dark] does not. The game actually selects these lists through its [natural-spawn caller][biome-caller] and [biome/structure lookup][biome-selection].

A natural spawn needs all of these conditions:

- A position **below the local surface height**, rather than a fixed sea-level cutoff
- A block below it in the bat-spawnable tag: the bundled tag contains **Stone, Granite, Diorite, Andesite, Tuff, and Deepslate**
- A valid floor and clear, fluid-free spawning spaces, plus the normal collision, player-distance, and mob-cap checks
- A successful light roll: normally only local raw brightness **0–3** can pass, with an additional **50% rejection** before that roll. From **October 20 through November 3**, using the host's local calendar, brightness **0–6** can pass and the extra rejection is removed. Brighter allowed values have a lower success chance

These are spawn conditions, not a guarantee that a bat will appear. [Bat predicate][bat-code] · [Floor tag][bat-tag] / [contents][stone-tag] · [Placement registration][bat-placement] / [ground checks][ground] · [Natural-spawn checks][natural-spawning] / [caps][natural-cap]

A [Bat Spawn Egg](../items/BatSpawnEgg.md) also creates a bat. See the [Inventory Browser](../mechanics/InventoryBrowser.md) for MattMC's item-insertion route. [Egg registration][bat-egg] / [use][egg-use]

## Behavior

Bats fly between nearby positions and can hang beneath a supporting block. A resting bat takes flight if the block above stops supporting it, a visible player approaches within its **4-block detection range**, or it is hurt. Visibility and line of sight still affect player detection. [Resting and flight][bat-code] · [Detection range][bat-senses] · [Targeting checks][targeting]

There are no attack, taming, or breeding routines in the bat's behavior. Bats cannot be leashed, do not push other entities, and skip fall-damage handling. [Bat behavior][bat-code] · [Ambient-mob leash rule][ambient] · [Push handling][bat-health]

## Drops

The bundled Bat loot table has **no item pools**. Its normal experience reward is **0**: it keeps the mob's zero reward rather than assigning a positive one. Looting therefore adds no normal bat drop. [Loot][bat-loot] · [Default reward][mob-defaults] / [reward calculation][mob-xp]

## Notes

- Entity ID: `minecraft:bat`; category: ambient
- Registered size: **0.5 blocks wide × 0.9 blocks tall**. [Registration][bat-registry]
- Spawn lists and block tags can be changed by data packs; this page describes the bundled data

Source-reviewed **2026-10-02** at commit `1d7e3e91f2a2694339f78b8673993e98d496ca5f`; no in-game test was performed. Biome JSON is read by the [registry loader][biome-loader] using its [registry directory scope][loader-scope]. The death-loot caller reads the [active loot registry][loot-xp-caller], populated by the [JSON loot loader][loader].

Related: [Phantom](Phantom.md), [Mobs](Mobs.md), [Bat Spawn Egg](../items/BatSpawnEgg.md).

[bat-egg]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/Items.java#L1801-L1801
[egg-use]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L102
[natural-cap]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L102-L125
[bat-code]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ambient/Bat.java#L133-L253
[bat-health]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ambient/Bat.java#L89-L104
[bat-registry]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/EntityType.java#L301-L303
[bat-list]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/worldgen/biome/dripstone_caves.json#L103-L111
[bat-lush]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json#L101-L109
[deep-dark]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json#L96-L105
[bat-tag]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/tags/block/bats_spawnable_on.json#L1-L5
[stone-tag]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/tags/block/base_stone_overworld.json#L1-L10
[bat-placement]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L104-L109
[ground]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L24-L42
[natural-spawning]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L227-L287
[biome-caller]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[biome-selection]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[bat-loot]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/entities/bat.json#L1-L4
[ambient]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ambient/AmbientCreature.java#L7-L15
[mob-xp]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L289-L306
[mob-defaults]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L118-L160
[bat-senses]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ambient/Bat.java#L33-L49
[targeting]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L60-L92
[loot-xp-caller]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[loader]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[biome-loader]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
[loader-scope]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/resources/FileToIdConverter.java#L19-L37
