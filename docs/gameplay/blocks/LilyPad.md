# Lily Pad

**Lily Pad** (`minecraft:lily_pad`) is a thin, colliding surface plant. It can make stepping places over suitable Water, but a Boat entering its block breaks it. It has its own placement rules, separate from [Dripleaves](Dripleaves.md), [Kelp](Kelp.md), and [Seagrass](Seagrass.md). [Lily Pad registry] · [Lily support and collision] · [Generic collision shape]

## Placement and support

Use the [Lily Pad item](../items/LilyPad.md) while targeting the surface. Its item-use path raycasts for **source fluid** and attempts placement in the cell above the hit block, then applies the normal block-item placement checks. [Lily Pad item] · [Lily surface placement] · [Block item survival]

The support predicate requires **source-type Water in the block below, or an IceBlock**, and **empty fluid in the pad's own cell**. This allows ordinary Ice and Frosted Ice. Packed Ice and Blue Ice use other block classes and do not qualify through the ice branch. The water check reads the supporting block's fluid, so water held in a waterlogged block can also meet it. Flowing Water uses a different fluid type and does not meet this source-water support test. [Lily support and collision] · [Water source and flowing types] · [Normal Ice registry] · [Frosted Ice registry] · [Frosted Ice inheritance] · [Packed Ice registry] · [Blue Ice registry]

A Lily Pad has no waterlogged state and no age or facing property. If a neighboring update finds its support invalid, the plant is removed. Its collision shape is **14/16 × 14/16 blocks wide and 1.5/16 high**, leaving a narrow margin around the edge. It has no Big Dripleaf tilt cycle. [Lily support and collision] · [Plant neighbor survival] · [Generic collision shape]

## Harvesting and growth

Lily Pads break instantly and drop **one Lily Pad**, including by hand. No required-tool flag, Shears condition, Silk Touch alternative, or Fortune multiplier appears in this registration and loot table. When an **AbstractBoat**, including a Boat, enters the block on the server, the plant explicitly destroys itself with drops enabled. That uses the ordinary one-pad loot table. [Lily Pad registry] · [lily_pad loot] · [Tool gate] · [Lily support and collision] · [Entity inside dispatch] · [Support-loss drops]

**Bone Meal does not grow or duplicate Lily Pads**, and they have no random-tick propagation. The block has no Bone Meal interface or growth callback, and the registration does not enable random ticks. No bundled crafting or smelting recipe produces this item. [Lily support and collision] · [Lily Pad registry] · [Bone Meal consumption]

## Checked acquisition routes

**Swamp and Mangrove Swamp** both include `patch_waterlily`. Its placed feature uses the surface heightmap, then a small patch generator attempts Lily Pads in air and checks their survival. The Normal Overworld selector includes both biomes, and the active feature execution consumes these data. These are verified natural sources, not a promise of pads on every water surface. [swamp biome] · [mangrove_swamp biome] · [patch_waterlily placement] · [patch_waterlily feature] · [Patch placement] · [Small feature placement] · [Normal preset] · [Overworld preset] · [Overworld preset builder] · [Swamp selections] · [Biome feature dispatch] · [Placed feature dispatch] · [Configured feature dispatch]

A [Wandering Trader](../mobs/WanderingTrader.md) can sell **5 Lily Pads for 1 Emerald**, with **two uses** of that listing. It is one option in the group from which five offers are selected randomly, so it is not guaranteed on each trader. This page checks these natural/trader routes rather than every possible loot source. [Trader offers] · [Offer construction] · [Trader group selection] · [Random listing selection]

## Other uses

Lily Pads have a **65%** ordinary chance to raise a [Composter's](Composter.md) level; the first accepted item in an empty composter raises the level automatically. The checked recipe tree contains no recipes using Lily Pad as an ingredient. [Lily composting] · [Composter success]

## Sources and verification

Source-reviewed on **2026-10-02** at `96e5604a6abaec697de2004b1ba9775e303bfba7`. Checked the block/item registration, full loot table, surface-targeting item, support/ice/fluid predicates, collision and Boat destruction, composting, selected biome-feature routes, recipe absence, and active trader offer selection. No in-game placement, Boat collision, harvesting, trading, or world-generation test was run. Data packs can change loot, recipes, and features.

[Lily Pad registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L2443-L2447
[Lily support and collision]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/WaterlilyBlock.java#L18-L51
[Generic collision shape]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L330
[Lily Pad item]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L572
[Lily surface placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/PlaceOnWaterBlockItem.java#L17-L27
[Block item survival]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/BlockItem.java#L130-L141
[Water source and flowing types]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L138-L166
[Normal Ice registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L1930-L1942
[Frosted Ice registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L4307-L4317
[Frosted Ice inheritance]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/FrostedIceBlock.java#L22-L24
[Packed Ice registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L3282-L3285
[Blue Ice registry]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5175-L5177
[Plant neighbor survival]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L27-L40
[lily_pad loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/lily_pad.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Entity inside dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/Entity.java#L1175-L1205
[Support-loss drops]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/Level.java#L262-L284
[Bone Meal consumption]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[swamp biome]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[mangrove_swamp biome]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json
[patch_waterlily placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/placed_feature/patch_waterlily.json
[patch_waterlily feature]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/configured_feature/patch_waterlily.json
[Patch placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java#L15-L38
[Small feature placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L17-L43
[Normal preset]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Overworld preset]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L79
[Overworld preset builder]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L106-L109
[Swamp selections]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L439-L463
[Biome feature dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Placed feature dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[Configured feature dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L26
[Trader offers]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L815-L828
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Trader group selection]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[Random listing selection]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L234
[Lily composting]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L127
[Composter success]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
