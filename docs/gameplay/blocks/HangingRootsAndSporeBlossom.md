# Hanging Roots and Spore Blossom

These two ceiling plants have different recovery and water rules: **Hanging Roots need Shears to collect and can hold water; Spore Blossom drops by hand and cannot survive in water**. Both are registered, non-colliding, instantly breakable blocks with matching inventory items. [Roots registration][reg-roots] · [Blossom registration][reg-spore] · [Items][items]

## Hanging Roots

**`minecraft:hanging_roots`** needs a block with a **sturdy full downward face immediately above**. Rooted Dirt is one valid support, but is not the only one. Roots cannot hang in a chain from other Hanging Roots, and the block has no climbing behavior. If its ceiling support changes and no longer qualifies, it breaks. [Support and state updates][roots] · [Climbable tag][climbable]

Harvest the placed plant with **Shears → one Hanging Roots**. Its loot condition specifically checks for Shears: a different Silk Touch tool does not substitute, and Fortune adds no quantity. Breaking the support, ordinary hand breaking, or a tool-free destruction path gives no item. The block itself has no required-tool-tier gate; the restriction comes from this loot table. Normal drops still obey `doTileDrops`. [Loot][loot-hanging_roots] · [Registration][reg-roots] · [Mining gate][gate] · [Drop dispatch][break] [drop]

Hanging Roots has a waterlogged state. Placing it in **source Water** records water in the block; the placement check compares the fluid type to `Fluids.WATER`, rather than accepting every flowing-water state. A Water Bucket can fill the placed roots, and an empty Bucket can recover that stored Water while leaving a supported plant in place. Waterlogging does not grow or duplicate the roots. [Placement and stored fluid][roots] · [Bucket interaction implementation][waterlogged]

The reproducible supply route belongs to [Rooted Dirt](SoilSandAndGravel.md#rooted-dirt-and-hanging-roots): use Bone Meal on Rooted Dirt with air directly below, then shear the roots it creates. The soil remains, so clearing the space allows another application. **Applying Bone Meal to Hanging Roots itself does nothing to that plant.** Hoeing Rooted Dirt instead directly produces a Hanging Roots item while converting the soil to Dirt; follow the linked soil guide for that separate transaction. [Rooted Dirt callback][rooted] · [Bone Meal dispatch][meal] · [Roots class][roots]

Lush Caves also installs the `rooted_azalea_tree` placement. Its root-system configuration supplies Hanging Roots, and the active feature attempts them in air beneath valid supports after its tree/root placement succeeds. This verifies a natural route without treating every Azalea tree grown by a player as the same cave root system. See [Saplings and Azaleas](SaplingsAndAzaleas.md#azalea-and-flowering-azalea) for the planted shrub's different tree route. [Lush Caves][biome-lush_caves] · [Placed root system][placed-rooted_azalea_tree] · [Configuration][feature-rooted_azalea_tree] · [Feature callbacks][roots-feature]

## Spore Blossom

**`minecraft:spore_blossom`** attaches to the ceiling: the block above must support its downward **center**, and the blossom's position must not contain Water. Its support test differs from Hanging Roots' full-face test. It has no floor or wall orientation, waterlogged state, Bone Meal callback, or random-tick spreading route. Removing the required ceiling makes it break. [Blossom class][spore]

Break it by hand or with an ordinary tool to recover **one Spore Blossom**. Shears and Silk Touch are unnecessary, and Fortune adds no quantity. Flowing Water can replace the non-colliding plant and requests ordinary tool-free loot; the item can drop if normal block drops are enabled. [Loot][loot-spore_blossom] · [Registration][reg-spore] · [Flow eligibility][flow-hold] · [Replacement and drops][flow] [water-drops] [drop]

The blossom emits falling and drifting spore particles through its client animation callback. That callback does not grow crops, convert ground, or generate more blossoms. MattMC nevertheless includes this plant in its **bee-attractive block tag** and the item in **bee food**: it can serve the corresponding Bee attraction and feeding systems. See [Bees](../mobs/Bee.md#flowers-and-pollination) for their full rules. [Particle behavior][spore] · [Attraction tag][bee-attractive] [bee-target] · [Food tag and callback][bee-food] [bee]

Find it through the active **Lush Caves** generation route: the biome lists the Spore Blossom placement, which searches upward for suitable cave ceilings and invokes its simple-block configuration. The feature still checks the blossom's survival rule. Lush Caves is selected by the Overworld biome source in the bundled normal preset. There is no bundled crafting recipe or plant-duplication callback for Spore Blossom. [Normal preset][normal] · [Preset dispatch][preset] [biomes] · [Biome][biome-lush_caves] · [Placement][placed-spore_blossom] · [Configuration][feature-spore_blossom] · [Final survival check][simple-feature] · [Block callbacks][spore]

## Crafting, composting, and reuse

Neither Hanging Roots nor Spore Blossom has a bundled crafting recipe. Their reliable recovery methods are the harvesting rules above. Hanging Roots has a **30%** configured composting chance and Spore Blossom **65%**; the first accepted item advances an empty composter directly. Composting consumes the item. [Composter entries and processing][compost]

A source-based example, **not gameplay-tested**: suspend a Rooted Dirt block with air beneath it, apply Bone Meal to the soil, and break the resulting roots with Shears. Repeat after clearing the root position. For a ceiling garden, place a collected Spore Blossom under a suitable dry ceiling, with waterloggable Hanging Roots nearby where water is wanted. [Root creation][rooted] · [Plant support and water rules][roots] [spore]

## Related pages

- [Moss and Pale Moss](MossAndPaleMoss.md), including Pale Hanging Moss's different growth and Silk Touch rule
- [Rooted Dirt](SoilSandAndGravel.md#rooted-dirt-and-hanging-roots)
- [Saplings and Azaleas](SaplingsAndAzaleas.md)
- [Primordial Plants](PrimordialPlants.md), for the imported plants' separate interactions

## Sources and verification

Checked against pinned MattMC registrations, recipes, loot, tags, active callbacks, and generation resources on **2026-10-02**. No gameplay test or guaranteed world-generation yield is claimed.

[reg-roots]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6636-L6650
[reg-spore]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6569-L6573
[items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L359-L379
[roots]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/HangingRootsBlock.java
[climbable]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/climbable.json
[loot-hanging_roots]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/hanging_roots.json
[gate]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[drop]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[rooted]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/RootedDirtBlock.java
[meal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/BoneMealItem.java#L37-L86
[biome-lush_caves]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[placed-rooted_azalea_tree]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/rooted_azalea_tree.json
[feature-rooted_azalea_tree]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/rooted_azalea_tree.json
[roots-feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/RootSystemFeature.java
[spore]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/SporeBlossomBlock.java
[loot-spore_blossom]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/spore_blossom.json
[flow-hold]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L429
[flow]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L266-L278
[water-drops]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[bee-attractive]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[bee-target]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L677
[bee-food]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/item/bee_food.json
[bee]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/animal/Bee.java#L587-L590
[normal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[preset]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[biomes]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[placed-spore_blossom]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/spore_blossom.json
[feature-spore_blossom]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/spore_blossom.json
[simple-feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java
[compost]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
