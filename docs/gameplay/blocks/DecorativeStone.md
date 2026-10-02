# Granite, Diorite, Andesite and Calcite

Granite (`minecraft:granite`), Diorite (`minecraft:diorite`) and Andesite (`minecraft:andesite`) provide decorative alternatives to ordinary Stone. Each has a polished finish and shaped building forms. **Calcite** (`minecraft:calcite`) is another pale building block, with a different acquisition route and no processed shape family in the checked registry. [Block and item registrations][blocks] [items]

## Mining and drops

Use an **unbroken pickaxe**, including a Wooden Pickaxe, for every ordinary block and shape covered on this page. All require a correct tool for drops, appear in the pickaxe mining tag and have no higher material-tier restriction. Hand breaking does not collect them. [Registration][blocks] · [Tool tags][pickaxe] [wood-denials] [stone-tier] [iron-tier] [diamond-tier] · [Tool checks][tool-material] [player-tool] [broken-tool] · [Server break path][break-dispatch]

Full blocks, stairs and walls drop **one matching item**; single slabs drop **one slab**, and double slabs drop **two matching slabs**. Silk Touch is unnecessary and does not change the finish. Fortune does not increase these building-block drops. Explosion survival/decay conditions remain separate. [Full-block loot][loot-granite] [loot-polished-granite] [loot-diorite] [loot-polished-diorite] [loot-andesite] [loot-polished-andesite] [loot-calcite] · [Shape examples][loot-granite-stairs] [loot-diorite-wall] · [Slab tables][loot-granite-slab] [loot-polished-granite-slab] [loot-diorite-slab] [loot-polished-diorite-slab] [loot-andesite-slab] [loot-polished-andesite-slab]

## Finding granite, diorite and andesite

Search stone terrain in the normal Overworld. As a checked example, the **Plains** biome includes both upper and lower deposit features for all three materials. Their configured features replace eligible blocks in the base-stone tag with the corresponding material. These are natural deposits, not a promise that every chunk contains each stone. [Normal preset][presets] · [Plains feature list][plains] · [Deposit definitions][deposit-granite] [deposit-diorite] [deposit-andesite] · [Replacement tag][base-stone] · [Feature registration and placement][features] [ore-feature] [placed-feature] [biome-generation]

The checked lower placements choose origins from **Y=0 through Y=60**; the upper placements choose origins from **Y=64 through Y=128**. Those are candidate origin ranges, not exact limits of every generated clump or a best mining-height chart. [Granite placements][placement-granite-lower] [placement-granite-upper] · [Diorite placements][placement-diorite-lower] [placement-diorite-upper] · [Andesite placements][placement-andesite-lower] [placement-andesite-upper]

## Crafting the base and polished blocks

These recipes provide another route when the right ingredients are available. The Cobblestone ingredient is the ordinary `minecraft:cobblestone` item; the recipes do not use a tag that also accepts Cobbled Deepslate.

| Ingredients and arrangement | Result |
| --- | --- |
| 2 Cobblestone and 2 Nether Quartz in a 2 × 2 checkerboard, matching ingredients diagonally opposite | 2 Diorite |
| 1 Diorite + 1 Nether Quartz, shapeless | 1 Granite |
| 1 Diorite + 1 Cobblestone, shapeless | 2 Andesite |
| 4 Granite in a 2 × 2 square | 4 Polished Granite |
| 4 Diorite in a 2 × 2 square | 4 Polished Diorite |
| 4 Andesite in a 2 × 2 square | 4 Polished Andesite |

[Diorite][crafting-diorite] · [Granite][crafting-granite] · [Andesite][crafting-andesite] · [Polishing][crafting-polished-granite] [crafting-polished-diorite] [crafting-polished-andesite]

Polishing is a crafting or stonecutting operation. No smelting recipe targets these decorative-stone forms in the checked bundled recipe set. Mining a polished block gives that polished block, rather than undoing the recipe. [Recipe loading][recipes] · [Polished loot][loot-polished-granite] [loot-polished-diorite] [loot-polished-andesite]

## Polished and shaped variants

Each base stone has stairs, slabs and walls. Its polished finish has stairs and slabs, **but no polished wall**. No brick or chiseled Granite/Diorite/Andesite blocks are registered in this snapshot. Links below identify the exact item forms; a dash means the form is absent. [Registry][blocks] [items]

| Full block | Stairs | Slab | Wall |
| --- | --- | --- | --- |
| [Granite](../items/Granite.md) | [Stairs](../items/GraniteStairs.md) | [Slab](../items/GraniteSlab.md) | [Wall](../items/GraniteWall.md) |
| [Polished Granite](../items/PolishedGranite.md) | [Stairs](../items/PolishedGraniteStairs.md) | [Slab](../items/PolishedGraniteSlab.md) | — |
| [Diorite](../items/Diorite.md) | [Stairs](../items/DioriteStairs.md) | [Slab](../items/DioriteSlab.md) | [Wall](../items/DioriteWall.md) |
| [Polished Diorite](../items/PolishedDiorite.md) | [Stairs](../items/PolishedDioriteStairs.md) | [Slab](../items/PolishedDioriteSlab.md) | — |
| [Andesite](../items/Andesite.md) | [Stairs](../items/AndesiteStairs.md) | [Slab](../items/AndesiteSlab.md) | [Wall](../items/AndesiteWall.md) |
| [Polished Andesite](../items/PolishedAndesite.md) | [Stairs](../items/PolishedAndesiteStairs.md) | [Slab](../items/PolishedAndesiteSlab.md) | — |

### Shaped crafting

Use the [shared stair, slab and wall crafting patterns](Stone.md#crafting-yields): six blocks in a 1/2/3 stair pattern, three blocks across one row for slabs, or two rows of three for walls. Use the **matching full-block finish** in each checked recipe.

| Matching full-block material | 6 → 4 stairs | 3 → 6 slabs | 6 → 6 walls |
| --- | --- | --- | --- |
| Granite | [Recipe][crafting-granite-stairs] | [Recipe][crafting-granite-slab] | [Recipe][crafting-granite-wall] |
| Polished Granite | [Recipe][crafting-polished-granite-stairs] | [Recipe][crafting-polished-granite-slab] | — |
| Diorite | [Recipe][crafting-diorite-stairs] | [Recipe][crafting-diorite-slab] | [Recipe][crafting-diorite-wall] |
| Polished Diorite | [Recipe][crafting-polished-diorite-stairs] | [Recipe][crafting-polished-diorite-slab] | — |
| Andesite | [Recipe][crafting-andesite-stairs] | [Recipe][crafting-andesite-slab] | [Recipe][crafting-andesite-wall] |
| Polished Andesite | [Recipe][crafting-polished-andesite-stairs] | [Recipe][crafting-polished-andesite-slab] | — |

### Stonecutting choices

The [Stonecutter](Stonecutter.md) consumes one input for the selected result. A raw decorative stone can become its polished block or polished shape directly. Stonecutting gives **one stair per block**, compared with **four stairs per six blocks** in the Crafting Table; slabs retain the two-per-block yield. [Menu selection and consumption][stonecutter]

| One input block | Available outputs and count per operation |
| --- | --- |
| Granite | [2 × Granite Slab][stonecutting-granite-slab-from-granite-stonecutting]; [1 × Granite Stairs][stonecutting-granite-stairs-from-granite-stonecutting]; [1 × Granite Wall][stonecutting-granite-wall-from-granite-stonecutting]; [1 × Polished Granite][stonecutting-polished-granite-from-granite-stonecutting]; [2 × Polished Granite Slab][stonecutting-polished-granite-slab-from-granite-stonecutting]; [1 × Polished Granite Stairs][stonecutting-polished-granite-stairs-from-granite-stonecutting] |
| Polished Granite | [2 × Polished Granite Slab][stonecutting-polished-granite-slab-from-polished-granite-stonecutting]; [1 × Polished Granite Stairs][stonecutting-polished-granite-stairs-from-polished-granite-stonecutting] |
| Diorite | [2 × Diorite Slab][stonecutting-diorite-slab-from-diorite-stonecutting]; [1 × Diorite Stairs][stonecutting-diorite-stairs-from-diorite-stonecutting]; [1 × Diorite Wall][stonecutting-diorite-wall-from-diorite-stonecutting]; [1 × Polished Diorite][stonecutting-polished-diorite-from-diorite-stonecutting]; [2 × Polished Diorite Slab][stonecutting-polished-diorite-slab-from-diorite-stonecutting]; [1 × Polished Diorite Stairs][stonecutting-polished-diorite-stairs-from-diorite-stonecutting] |
| Polished Diorite | [2 × Polished Diorite Slab][stonecutting-polished-diorite-slab-from-polished-diorite-stonecutting]; [1 × Polished Diorite Stairs][stonecutting-polished-diorite-stairs-from-polished-diorite-stonecutting] |
| Andesite | [2 × Andesite Slab][stonecutting-andesite-slab-from-andesite-stonecutting]; [1 × Andesite Stairs][stonecutting-andesite-stairs-from-andesite-stonecutting]; [1 × Andesite Wall][stonecutting-andesite-wall-from-andesite-stonecutting]; [1 × Polished Andesite][stonecutting-polished-andesite-from-andesite-stonecutting]; [2 × Polished Andesite Slab][stonecutting-polished-andesite-slab-from-andesite-stonecutting]; [1 × Polished Andesite Stairs][stonecutting-polished-andesite-stairs-from-andesite-stonecutting] |
| Polished Andesite | [2 × Polished Andesite Slab][stonecutting-polished-andesite-slab-from-polished-andesite-stonecutting]; [1 × Polished Andesite Stairs][stonecutting-polished-andesite-stairs-from-polished-andesite-stonecutting] |

## Calcite

Calcite is a **full block only** in this registry. There is no registered Polished Calcite, Calcite stair, slab, wall, brick or chiseled form. The bundled recipe scan found no crafting, smelting or stonecutting recipe producing Calcite and no recipe using it as a named ingredient; gather and place it as a building material. Its loot returns one Calcite with a correct pickaxe, without Silk Touch. [Registry][blocks] · [Item][items] · [Loot][loot-calcite]

Two checked natural routes are useful:

- **Amethyst geodes:** Calcite is the middle shell material around the amethyst-bearing interior. The Plains biome includes the placed Amethyst Geode feature, which points to this configuration, and the registered GeodeFeature uses its middle-layer provider when placing the shell. [Biome entry][plains] · [Placement][geode-placement] · [Shell configuration][geode-config] · [Active feature][features] [geode-feature]
- **Stony Peaks:** the normal Overworld surface rules select Calcite for a noise-dependent part of this biome's stone surface. This produces another collection route without requiring a geode; it does not mean every white or rocky mountain block is Calcite. [Surface configuration][overworld] · [Preset][presets] · [Surface execution][terrain] [surface] [native-surface]

## Placement and properties

All full blocks covered here use ordinary Block placement: they have **no player-selected axis or facing**, including polished blocks and Calcite. They stay placed when the block beneath is removed. Their stairs, slabs and walls use the [shared shape, corner and waterlogging rules](Stone.md#placing-shaped-blocks); those shape states are chosen again when an item is placed. [Registered classes][blocks] · [Base behavior][properties] · [Shape implementations][slabs] [stairs] [walls]

| Material | Hardness | Blast resistance |
| --- | ---: | ---: |
| Granite, Diorite and Andesite, including polished and shaped forms | 1.5 | 6 |
| Calcite | 0.75 | 0.75 |

All use the bass-drum note-block instrument. Hardness is a block property, not a break time in seconds. Calcite's single-argument strength setting supplies both its hardness and resistance. [Registrations][blocks] · [Copied and single-value properties][properties]

These materials are not automatically interchangeable in recipes merely because they are stone. For example, the ordinary Furnace material tag accepts Cobblestone, Blackstone and Cobbled Deepslate, without any of the materials on this page. [Checked material tag][materials] · [Furnace recipe ownership](Furnace.md#crafting-and-mining)

## Sources and verification

Source-reviewed on **2026-10-02** at `2f6c6d4689df9796912eea87cf9def80fc320ee1`. Checked active registrations and tool/drop dispatch, all 22 covered block loot tables, exact recipes, shape classes, biome-to-feature generation routes, and Calcite surface/geode placement. No in-game mining, crafting, placement or world-generation test was run. The deposit ranges describe configuration, not measured abundance.

Related: [Granite item](../items/Granite.md) · [Diorite item](../items/Diorite.md) · [Andesite item](../items/Andesite.md) · [Calcite item](../items/Calcite.md) · [Tuff](Tuff.md) · [Stone](Stone.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/Items.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-granite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/granite.json
[loot-polished-granite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_granite.json
[loot-diorite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/diorite.json
[loot-polished-diorite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_diorite.json
[loot-andesite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/andesite.json
[loot-polished-andesite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_andesite.json
[loot-calcite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/calcite.json
[loot-granite-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/granite_stairs.json
[loot-diorite-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/diorite_wall.json
[loot-granite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/granite_slab.json
[loot-polished-granite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_granite_slab.json
[loot-diorite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/diorite_slab.json
[loot-polished-diorite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_diorite_slab.json
[loot-andesite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/andesite_slab.json
[loot-polished-andesite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_andesite_slab.json
[presets]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/presets/WorldPresets.java#L120-L135
[plains]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/biome/plains.json#L22-L45
[deposit-granite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_granite.json
[deposit-diorite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_diorite.json
[deposit-andesite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_andesite.json
[base-stone]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/base_stone_overworld.json
[features]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[ore-feature]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java
[placed-feature]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java
[biome-generation]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[placement-granite-lower]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_granite_lower.json
[placement-granite-upper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_granite_upper.json
[placement-diorite-lower]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_diorite_lower.json
[placement-diorite-upper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_diorite_upper.json
[placement-andesite-lower]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_andesite_lower.json
[placement-andesite-upper]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_andesite_upper.json
[crafting-diorite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/diorite.json
[crafting-granite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/granite.json
[crafting-andesite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/andesite.json
[crafting-polished-granite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_granite.json
[crafting-polished-diorite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_diorite.json
[crafting-polished-andesite]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_andesite.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[crafting-granite-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/granite_stairs.json
[crafting-granite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/granite_slab.json
[crafting-granite-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/granite_wall.json
[crafting-polished-granite-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_granite_stairs.json
[crafting-polished-granite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_granite_slab.json
[crafting-diorite-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/diorite_stairs.json
[crafting-diorite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/diorite_slab.json
[crafting-diorite-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/diorite_wall.json
[crafting-polished-diorite-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_diorite_stairs.json
[crafting-polished-diorite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_diorite_slab.json
[crafting-andesite-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/andesite_stairs.json
[crafting-andesite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/andesite_slab.json
[crafting-andesite-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/andesite_wall.json
[crafting-polished-andesite-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_andesite_stairs.json
[crafting-polished-andesite-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_andesite_slab.json
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-granite-slab-from-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/granite_slab_from_granite_stonecutting.json
[stonecutting-granite-stairs-from-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/granite_stairs_from_granite_stonecutting.json
[stonecutting-granite-wall-from-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/granite_wall_from_granite_stonecutting.json
[stonecutting-polished-granite-from-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_granite_from_granite_stonecutting.json
[stonecutting-polished-granite-slab-from-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_granite_slab_from_granite_stonecutting.json
[stonecutting-polished-granite-stairs-from-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_granite_stairs_from_granite_stonecutting.json
[stonecutting-polished-granite-slab-from-polished-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_granite_slab_from_polished_granite_stonecutting.json
[stonecutting-polished-granite-stairs-from-polished-granite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_granite_stairs_from_polished_granite_stonecutting.json
[stonecutting-diorite-slab-from-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/diorite_slab_from_diorite_stonecutting.json
[stonecutting-diorite-stairs-from-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/diorite_stairs_from_diorite_stonecutting.json
[stonecutting-diorite-wall-from-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/diorite_wall_from_diorite_stonecutting.json
[stonecutting-polished-diorite-from-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_diorite_from_diorite_stonecutting.json
[stonecutting-polished-diorite-slab-from-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_diorite_slab_from_diorite_stonecutting.json
[stonecutting-polished-diorite-stairs-from-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_diorite_stairs_from_diorite_stonecutting.json
[stonecutting-polished-diorite-slab-from-polished-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_diorite_slab_from_polished_diorite_stonecutting.json
[stonecutting-polished-diorite-stairs-from-polished-diorite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_diorite_stairs_from_polished_diorite_stonecutting.json
[stonecutting-andesite-slab-from-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/andesite_slab_from_andesite_stonecutting.json
[stonecutting-andesite-stairs-from-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/andesite_stairs_from_andesite_stonecutting.json
[stonecutting-andesite-wall-from-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/andesite_wall_from_andesite_stonecutting.json
[stonecutting-polished-andesite-from-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_andesite_from_andesite_stonecutting.json
[stonecutting-polished-andesite-slab-from-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_andesite_slab_from_andesite_stonecutting.json
[stonecutting-polished-andesite-stairs-from-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_andesite_stairs_from_andesite_stonecutting.json
[stonecutting-polished-andesite-slab-from-polished-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_andesite_slab_from_polished_andesite_stonecutting.json
[stonecutting-polished-andesite-stairs-from-polished-andesite-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_andesite_stairs_from_polished_andesite_stonecutting.json
[geode-placement]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/amethyst_geode.json
[geode-config]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/amethyst_geode.json
[geode-feature]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java
[overworld]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[terrain]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java
[surface]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java
[native-surface]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java
[properties]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[slabs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/StairBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WallBlock.java
[materials]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json
