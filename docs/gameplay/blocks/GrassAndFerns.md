# Grass and Ferns

Use **Shears** to collect Short Grass and Fern for planting. Bone Meal turns either into its two-block form, which can be sheared into **two short plants**. This provides a multiplication loop once you have one starter plant. Tall Grass is a different plant from the single-block [Tall Dry Grass](ShrubsAndDryGrass.md#tall-dry-grass). [Small registrations] · [Tall registrations] · [Grass growth]

## Forms and exact IDs

The IDs below identify both a block and its matching inventory item. The tall items exist, but shearing a tall plant returns its short counterpart. [Small items] · [Tall items] · [Display names]

| Form | Exact ID | Height | Ordinary harvest with Shears |
| --- | --- | --- | --- |
| <span id="short-grass"></span>[Short Grass](../items/ShortGrass.md) | `minecraft:short_grass` | One block | **1 Short Grass** [short_grass loot] |
| <span id="tall-grass"></span>[Tall Grass](../items/TallGrass.md) | `minecraft:tall_grass` | Two blocks | **2 Short Grass**, from one intact plant [tall_grass loot] |
| <span id="fern"></span>[Fern](../items/Fern.md) | `minecraft:fern` | One block | **1 Fern** [fern loot] |
| <span id="large-fern"></span>[Large Fern](../items/LargeFern.md) | `minecraft:large_fern` | Two blocks | **2 Fern**, from one intact plant [large_fern loot] |

## Planting and tall halves

All four use **Farmland or the dirt block tag** as ground: Grass Block, Dirt, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. Their survival check requires no light level, hydration, or nearby water. They have no placement-facing state. [Plant support] · [Soil tag] · [Grass growth] · [Two-block plant]

A tall item places the lower and upper halves together. It needs a replaceable upper cell within build height; the lower half needs soil, and the upper half needs its matching lower half. Removing either half or the supporting soil removes the unsupported remainder. There is no waterlogged state. [Tall items] · [Two-block plant] · [Placement gate]

These plants break instantly and do not collide with entities. They have no correct-tool tier requirement, but **the loot still requires Shears to recover a plant**. Silk Touch does not substitute for Shears. [Small registrations] · [Tall registrations] · [Tool gate] · [short_grass loot] · [fern loot] · [tall_grass loot] · [large_fern loot]

## Seeds and harvesting

Without Shears, each ordinary harvest has a **1-in-8 chance to produce Wheat Seeds**, otherwise no item. Short Grass and Fern start with one seed when that roll succeeds. Fortune adds a uniformly selected **0 through twice the Fortune level** to that successful drop: Fortune I gives 1–3 seeds, II gives 1–5, and III gives 1–7. Fortune changes the quantity, not the 1-in-8 success roll. [short_grass loot] · [fern loot] · [Seed Fortune]

For **Tall Grass and Large Fern**, the non-Shears outcome is **one Wheat Seed at 1-in-8 chance**, with **no Fortune multiplier**. Each tall loot branch requires the matching other half to still exist. The player-mining callback evaluates the selected half while the pair is intact, so mining either half gives the table's single plant-harvest result, not two independent rolls or four short plants. An already orphaned half does not satisfy that paired loot condition. [tall_grass loot] · [large_fern loot] · [Two-block plant] · [Mining dispatch]

## Bone Meal and a renewable supply

1. Plant Short Grass or Fern on valid ground and leave **air directly above** it
2. Use **one Bone Meal**: Short Grass becomes Tall Grass, or Fern becomes Large Fern
3. Shear either half of the intact tall plant to obtain **two short plants**, then replant

The growth target also checks that the grown plant can survive. An accepted Survival application consumes one Bone Meal; there is no extra success roll after that check. The tall forms themselves have **no direct Bone Meal action**, and these four registrations do not grow or spread by random ticks. [Grass growth] · [Bone Meal use] · [Two-block plant] · [Small registrations] · [Tall registrations]

Bone Meal on **Grass Block with air above** is another Short Grass source. It makes up to 128 candidate attempts across nearby Grass Blocks, mixing a grass route with a biome-dependent flower route. The checked grass route places Short Grass, including in Taiga; it does not select Fern from the biome's natural grass patch. Existing Short Grass candidates also have a 1-in-10 attempt to become Tall Grass when there is room. These are attempt rules, not guaranteed output counts. [Grass Block growth] · [grass_bonemeal placement] · [single_piece_of_grass feature] · [Plant feature execution]

## Finding plants and tall inventory items

These are checked examples in the Normal Overworld, whose biome selection and decoration paths run the linked features. Placement still depends on empty space, valid support, and each feature's filters. [Normal preset] · [Overworld preset] · [Overworld biomes] · [Biome feature execution] · [Placed feature execution] · [Feature registration] · [Simple feature registration] · [Patch execution] · [Plant feature execution]

- **Plains:** its grass patch supplies Short Grass, and its separate tall-grass patch supplies Tall Grass. [plains biome] · [patch_grass_plain placement] · [patch_grass feature] · [patch_tall_grass_2 placement] · [patch_tall_grass feature]
- **Taiga:** its grass patch chooses Fern with weight 4 and Short Grass with weight 1; a separate patch supplies Large Fern. These weights describe selected states, not guaranteed terrain coverage. [taiga biome] · [patch_grass_taiga_2 placement] · [patch_taiga_grass feature] · [patch_large_fern placement] · [patch_large_fern feature]
- **Wandering Trader:** a possible offer sells **1 Fern for 1 Emerald**, with **12 uses**. Five offers are selected from this group, so Fern is not guaranteed on every trader. [Trader offers] · [Offer construction] · [Trader selection] · [Offer randomization]

To obtain the **Tall Grass or Large Fern inventory item**, the checked Survival routes are village-house chests: **Savanna houses can roll Tall Grass**, and **Taiga houses can roll Large Fern**. Those same tables can also roll Short Grass or Fern respectively. Each selected plant entry gives one item; none is guaranteed in a chest. The checked village paths reach house pools containing medium-house templates bound to those chest loot tables. This is separate from harvesting a planted tall form. [Village set] · [savanna village] · [savanna houses] · [savanna chest template] · [Savanna chest loot] · [taiga village] · [taiga houses] · [taiga chest template] · [Taiga chest loot] · [Village jigsaw generation] · [Village pool expansion] · [Structure placement]

## Other uses

**Fern and Large Fern items** are the two accepted ingredients for [Fern Thatch](FloodBasaltAndFernThatch.md#fern-thatch-crafting-and-collecting); that page owns its recipe. A small Fern can also go in a [Flower Pot](FlowerPot.md#supported-plants). The grass forms and Large Fern have no matching filled-pot registration. [Fern ingredient] · [Thatch recipe] · [Pot behavior]

The [Composter](Composter.md) gives Short Grass a **30%**, Tall Grass **50%**, and either Fern size **65%** ordinary chance to raise its level. Its first accepted item in an empty composter raises the level automatically. None of these four items is in the checked standard Furnace fuel list. [Compost values] · [Compost initialization] · [Compost first layer] · [Fuel table]

Related: [Ground-cover flowers and Leaf Litter](FlowerbedsAndLeafLitter.md) · [Eyeblossoms](Eyeblossoms.md) · [Shrubs and Dry Grass](ShrubsAndDryGrass.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `20354edd390fadb09b43132d712facf4188c4119`. Checked all four block/item registrations and full loot tables, support and growth callbacks, Fortune calculation, selected biome/feature chains, trader selection, village-house loot bindings, compost/fuel values, and Fern Thatch ingredients. No in-game planting, harvesting, growth, trading, chest, or world-generation test was run. These examples are not a complete acquisition survey; data packs can change loot, recipes, tags, and features.

[Small registrations]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/Blocks.java#L701-L726
[Tall registrations]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/Blocks.java#L3334-L3359
[Grass growth]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/TallGrassBlock.java
[Small items]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/Items.java#L288-L289
[Tall items]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/Items.java#L716-L717
[Display names]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/assets/minecraft/lang/en_us.json
[short_grass loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/short_grass.json
[tall_grass loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/tall_grass.json
[fern loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/fern.json
[large_fern loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/large_fern.json
[Plant support]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/block/dirt.json
[Two-block plant]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java
[Placement gate]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/BlockItem.java#L52-L100
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Seed Fortune]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L167
[Mining dispatch]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[Bone Meal use]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[Grass Block growth]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L93
[grass_bonemeal placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/grass_bonemeal.json
[single_piece_of_grass feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/single_piece_of_grass.json
[Plant feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java
[Normal preset]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Overworld preset]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L110
[Overworld biomes]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L76-L103
[Biome feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[Placed feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L43-L69
[Feature registration]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L63-L67
[Simple feature registration]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L120
[Patch execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java
[plains biome]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/biome/plains.json
[patch_grass_plain placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/patch_grass_plain.json
[patch_grass feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/patch_grass.json
[patch_tall_grass_2 placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/patch_tall_grass_2.json
[patch_tall_grass feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/patch_tall_grass.json
[taiga biome]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/biome/taiga.json
[patch_grass_taiga_2 placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/patch_grass_taiga_2.json
[patch_taiga_grass feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/patch_taiga_grass.json
[patch_large_fern placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/patch_large_fern.json
[patch_large_fern feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/patch_large_fern.json
[Trader offers]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L750-L829
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1420-L1481
[Trader selection]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[Offer randomization]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L238
[Village set]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/structure_set/villages.json
[savanna village]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/structure/village_savanna.json
[savanna houses]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/template_pool/village/savanna/houses.json
[savanna chest template]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/structure/village/savanna/houses/savanna_medium_house_1.nbt
[Savanna chest loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/chests/village/village_savanna_house.json
[taiga village]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/structure/village_taiga.json
[taiga houses]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/houses.json
[taiga chest template]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/structure/village/taiga/houses/taiga_medium_house_1.nbt
[Taiga chest loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/chests/village/village_taiga_house.json
[Village jigsaw generation]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L139-L155
[Village pool expansion]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L319-L348
[Structure placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L294-L313
[Fern ingredient]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/item/ferns.json
[Thatch recipe]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/fern_thatch.json
[Pot behavior]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java
[Compost values]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L90-L167
[Compost initialization]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/server/Bootstrap.java#L49-L50
[Compost first layer]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L318
[Fuel table]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L39-L109
