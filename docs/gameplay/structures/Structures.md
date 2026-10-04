# Structures

Structures give exploration a destination: a place to collect supplies, face a particular hazard, or unlock a route onward. Start with the guide for your goal, then prepare for the dimension around it.

For mapmaking with saved templates and pool connectors, see [Structure and Jigsaw Blocks](../blocks/StructureAndJigsawBlocks.md). Their operator controls are separate from finding naturally generated structures.

## Choose an expedition

| Guide | Where to search in the bundled normal world | Main reasons to visit |
| --- | --- | --- |
| [Nether Fortress](NetherFortress.md) | Nether biomes | Blazes, Nether Wart where a stalk room generates, and corridor-chest loot |
| [Bastion Remnant](BastionRemnant.md) | Nether Wastes, Crimson Forest, Soul Sand Valley and Warped Forest | Piglin/Brute encounters and chest loot, including table-specific Netherite Upgrade Template chances |
| [Pillager Outpost](PillagerOutpost.md) | Eligible normal-Overworld plains, desert, savanna, taiga, snowy and mountain biomes | Pillager/captain encounters, upper-chest rewards and optional captive residents |
| [Woodland Mansion](WoodlandMansion.md) | Dark Forest and Pale Garden in the normal Overworld | Evokers, Vindicators, possible Allays, varied room chests and a Vex template chance |
| [Stronghold](Stronghold.md) | Overworld | An End portal room, with possible libraries and other loot rooms |
| [End City](EndCity.md) | End Highlands and End Midlands on outer islands | Shulker Shells, equipment chests, and Elytra when a ship generates |
| [Shipwreck](Shipwreck.md) | Overworld oceans, Beach, and Snowy Beach | Supply/treasure chests and possible treasure maps |
| [Ocean Ruins](OceanRuins.md) | Eligible warm or cold Overworld oceans | Chest rewards, archaeology, and possible treasure maps |
| [Ocean Monument](OceanMonument.md) | Deep Ocean, Deep Cold Ocean, Deep Lukewarm Ocean, and Deep Frozen Ocean | Guardian/Elder encounters, Prismarine, Wet Sponges, gold, and a Tide template chance |
| [Buried Treasure](BuriedTreasure.md) | Beach and Snowy Beach | Heart of the Sea and other buried-chest loot |
| [Desert Pyramid](DesertPyramid.md) | Desert | Four trap-room chests, Dune templates and a separate archaeology cellar |
| [Jungle Temple](JungleTemple.md) | Jungle and Bamboo Jungle | Two chests, arrow traps, a piston puzzle and Wild templates |
| [Swamp Hut](SwampHut.md) | Swamp | Witch/Cat residents and separate piece-bound spawning routes |
| [Trial Chambers](TrialChambers.md) | Eligible normal-Overworld biomes, with Deep Dark excluded | Trial encounters, separate supply/container rewards, keys and normal or ominous Vault routes |
| [Ancient City](AncientCity.md) | Deep Dark in the normal Overworld | Sculk-aware exploration, Swift Sneak, Echo Shards, Disc Fragments and possible Ward/Silence templates |
| [Trail Ruins](TrailRuins.md) | Taiga families, Old Growth Birch Forest and Jungle in the normal Overworld | Careful Suspicious Gravel excavation for possible sherds, trim templates and the Relic disc |
| [Mineshaft](Mineshaft.md) | Eligible normal-Overworld biomes; separate Badlands variant | Track and building materials, optional chest-minecart supplies and cave-spider encounters |
| [Igloo](Igloo.md) | Snowy Plains, Snowy Taiga and Snowy Slopes in the normal Overworld | Snowy shelter and a possible basement with residents and one set of curing supplies |
| [Ruined Portal](RuinedPortal.md) | Seven placement variants across eligible Overworld and Nether biomes | Optional chest supplies and salvage, with frame repair and onward-travel preparation |
| [Monster Room](MonsterRoom.md) | Eligible Overworld and loaded Primordial Caves biomes | Cave-side spawner encounters and optional chest supplies; generated as a biome feature |

These are the exploration destinations reviewed in this section so far, not a complete inventory of MattMC. Room layouts and chest contents vary. Finding the right structure does not guarantee a particular optional room or random reward.

For fossil routes, see [Bone Block: finding fossils](../blocks/BoneBlock.md#finding-fossils) and [Dried Ghast](../blocks/DriedGhast.md).

## Finding a structure

For a Survival stronghold search, use [Eyes of Ender](Stronghold.md#finding-a-stronghold). Fortress exploration starts in the [Nether](../dimensions/Nether.md); distinguish its Nether-brick bridges and corridors from a bastion before committing to a dangerous approach.

Players with permission level 2 can use these search commands in the relevant dimension:

```text
/locate structure minecraft:fortress
/locate structure minecraft:stronghold
```

`/locate` searches the command source's current dimension. It does not travel to another dimension or place a structure on demand. Its structure result gives X and Z with `~` in place of Y; the clickable coordinate suggests a teleport using your current height. **It is not a safe arrival point.** Inspect the terrain and plan an approach rather than assuming the suggested height is clear.

A failed search is not proof that a structure does not exist anywhere. Check the dimension, world settings, applicable biomes, and exact ID first. See [Commands](../commands/Commands.md) for permission and command basics.

## When generation is eligible

New registered structure starts require the world's structure-generation option to be enabled. The generator then uses the world's loaded structure sets, placement rules, and allowed biomes. A biome being eligible means a structure may start there, not that every patch of that biome contains one.

The guides describe bundled data and the normal world preset. Data packs, custom presets, and older already-generated terrain can differ. In particular, the bundled loaded Primordial Caves dimension uses Dry Midlands, Primordial Plains and Primordial Ocean; none belongs to the fortress or stronghold allowed biome tags. See [the loaded-dimension explanation](../dimensions/PrimordialCaves.md#what-currently-generates) for how that definition takes precedence over the preset. Do not carry a fortress or stronghold search into [Primordial Caves](../dimensions/PrimordialCaves.md) expecting the default Nether or Overworld result.

Monster Rooms are biome features, included here as expedition destinations. Their decoration attempts are separate from Generate Structures, and they have no `/locate structure` target; follow [their finding and generation guidance](MonsterRoom.md). The feature/structure distinction and loaded Primordial biome list were source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04.

## Before entering

- Record your entrance and return route; mark junctions while exploring
- Bring food, tools, lighting, and spare blocks for controlled access and retreat
- Decide what you need before looting: optional rooms can justify searching another structure
- Prepare separately for the destination beyond a portal. Finding a stronghold is not preparation for the [End](../dimensions/End.md)

## Related pages

- [Dimensions](../dimensions/Dimensions.md)
- [Nether Wart crop](../blocks/NetherWart.md)
- [Brewing](../brewing/Brewing.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game structure search or exploration test was performed. Structure-specific evidence is recorded on each guide.

- [Structure-generation setting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L59), [structure-set and biome filtering](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L45-L64), and [placement and generation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L580)
- [Locate permissions, dimension scope, failure, and coordinate output](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/commands/LocateCommand.java)
- [Normal world preset, including Primordial Caves](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json), [Nether biome tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/biome/is_nether.json), and [Overworld biome tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/biome/is_overworld.json)

- [Registered-structure gate and separate feature decoration](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L381), [locate registry targets](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L69), and [Monster Room feature registration](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L103)
- [Loaded Primordial dimension](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json), [world loading](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L48), and [dimension override precedence](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L183)
