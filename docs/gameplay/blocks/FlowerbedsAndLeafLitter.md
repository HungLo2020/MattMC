# Flowerbeds and Leaf Litter

**Pink Petals, Wildflowers, and Leaf Litter** fill a ground cell in up to **four horizontal segments**. Add matching items to increase coverage. Pink Petals and Wildflowers also multiply through Bone Meal; Leaf Litter instead has a smelting route from leaf blocks. [Cover registrations] · [Segment placement] · [Flowerbed growth] · [Leaf Litter smelting]

## Forms and exact IDs

These are block IDs and matching inventory item IDs. Ordinary mining returns **one matching item per occupied segment**, so a full cell yields four. Shears and Silk Touch are not needed, and Fortune has no multiplier in these loot tables. Explosions can reduce the recovered count. [Cover items] · [Display names] · [Tool gate]

| Ground cover | Exact ID | Amount property | Ordinary loot |
| --- | --- | --- | --- |
| <span id="pink-petals"></span>[Pink Petals](../items/PinkPetals.md) | `minecraft:pink_petals` | `flower_amount`, 1–4 | **1–4 Pink Petals** [pink_petals loot] |
| <span id="wildflowers"></span>[Wildflowers](../items/Wildflowers.md) | `minecraft:wildflowers` | `flower_amount`, 1–4 | **1–4 Wildflowers** [wildflowers loot] |
| <span id="leaf-litter"></span>[Leaf Litter](../items/LeafLitter.md) | `minecraft:leaf_litter` | `segment_amount`, 1–4 | **1–4 Leaf Litter** [leaf_litter loot] |

## Ground, coverage, and facing

**Pink Petals and Wildflowers** need **Farmland or the dirt block tag**: Grass Block, Dirt, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. **Leaf Litter instead needs a sturdy upper face on the block below**. That is a surface-shape test, not the flowerbeds' soil list; it allows suitable solid building blocks. No light, soil moisture, or nearby-water check is part of these survival rules. [Plant support] · [Soil tag] · [Flowerbed growth] · [Leaf Litter support]

A new cell starts with one segment and faces **opposite your horizontal facing direction**. Use the same item on that cell, without secondary use, to add one segment until it reaches four. Added segments preserve its facing; their shapes extend around the cell counterclockwise from the first segment. The amount increases coverage across the ground, not vertical layers. You cannot mix these three species in one block cell. [Segment placement] · [Flowerbed growth] · [Leaf Litter support]

All three have no entity collision and a zero default breaking hardness. The flowerbed outline is **3/16 block high** and Leaf Litter's is **1/16**; these are selection shapes, not platforms to stand on. None has a waterlogged state. Losing required support removes the cover. Leaf Litter is also registered as replaceable, so a different block can be placed into its cell. [Cover registrations] · [Block defaults] · [Segment placement] · [Flowerbed growth] · [Leaf Litter support] · [Plant support] · [Replacement rules]

## Bone Meal

On either **Pink Petals or Wildflowers**, one accepted Survival use consumes one Bone Meal and:

- Adds **one segment** if the amount is below four
- Drops **one matching item** if the amount is already four, leaving the full flowerbed in place

This direct action does not need an empty neighboring cell. It works at every coverage amount and has no extra random success roll. **Leaf Litter has no direct Bone Meal action**, and none of these three registrations has random-tick growth or natural self-spread. [Flowerbed growth] · [Leaf Litter support] · [Bone Meal use] · [Cover registrations]

Bone Meal on suitable **Grass Block** can also generate Pink Petals in Cherry Grove, or Wildflowers in Birch Forest and Meadow. Those biomes include the linked `flower` features, which Grass Block's flower selection recognizes. Its flower branch is a 1-in-8 choice at eligible empty candidate cells, after which further placement checks apply; it does not guarantee a particular number of flowerbeds. Leaf Litter's `random_patch` is outside that flower filter. [Grass Block growth] · [Flower feature filter] · [flower_cherry feature] · [wildflowers_birch_forest feature] · [wildflowers_meadow feature] · [patch_leaf_litter feature]

## Finding and making ground cover

The following natural routes are wired into the Normal Overworld biome and feature lists. Filters and plant survival still determine successful individual placements. [Normal preset] · [Overworld preset] · [Overworld biomes] · [Biome feature execution] · [Placed feature execution] · [Feature registration] · [Simple feature registration] · [Patch execution] · [Plant feature execution]

- **Pink Petals:** Cherry Grove's `flower_cherry` feature selects petal coverage and facing states. [cherry_grove biome] · [flower_cherry placement] · [flower_cherry feature]
- **Wildflowers:** Birch Forest and Meadow have their own Wildflowers features, selecting coverage and facing states. [birch_forest biome] · [wildflowers_birch_forest placement] · [wildflowers_birch_forest feature] · [meadow biome] · [wildflowers_meadow placement] · [wildflowers_meadow feature]
- **Leaf Litter:** Dark Forest includes a Leaf Litter patch feature. This is one checked natural route, not a claim that every tree drops litter. [dark_forest biome] · [patch_leaf_litter placement] · [patch_leaf_litter feature]

A possible **Wandering Trader** offer sells **1 Wildflowers for 1 Emerald**, with **12 uses**. The trader chooses five offers from that group, so this offer may be absent. [Trader offers] · [Offer construction] · [Trader selection] · [Offer randomization]

**Smelt one item in the leaves tag into one Leaf Litter** in a [Furnace](Furnace.md#fuel-planning). The exact recipe takes **200 ticks** at normal Furnace speed and assigns **0.1 recipe XP**. The current ingredient tag includes Oak, Spruce, Birch, Jungle, Acacia, Cherry, Dark Oak, Pale Oak, Mangrove, Azalea, and Flowering Azalea Leaves. Collect actual leaf-block items for this input; loose saplings are not the recipe ingredient. [Leaf Litter smelting] · [Leaves ingredient]

## Dye, Bees, compost, and fuel

A shapeless craft turns **one Pink Petals into one Pink Dye**, or **one Wildflowers into one Yellow Dye**. These recipes consume the input item. The checked recipes give neither flowerbed nor Leaf Litter a Suspicious Stew recipe. [Pink dye] · [Yellow dye] · [Recipe execution] · [Ingredient consumption]

Pink Petals and Wildflowers are in the **Bee food** and **Bee-attractive block** tags. They can therefore be used as ordinary Bee food and pollination plants, subject to the Bee's usual conditions. Leaf Litter is absent from both tags. The [Bee guide](../mobs/Bee.md#flowers-and-pollination) owns those conditions. [Bee food] · [Bee plants] · [Bee feeding] · [Bee attraction]

All three items have a **30%** ordinary chance to increase a [Composter](Composter.md) level; the first accepted item in an empty composter succeeds automatically. **Leaf Litter is also Furnace fuel for 100 burn ticks per item**, half the fuel for one uninterrupted 200-tick recipe. Pink Petals and Wildflowers are absent from the checked standard fuel list. [Compost values] · [Compost initialization] · [Compost first layer] · [Fuel table] · [Server fuel] · [Furnace fuel]

Related: [Grass and Ferns](GrassAndFerns.md) · [Small and tall flowers](Flowers.md) · [Eyeblossoms](Eyeblossoms.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `20354edd390fadb09b43132d712facf4188c4119`. Checked three registrations, item IDs/display names, full loot tables, support/segmentation/Bone Meal code, selected natural feature chains, the trader offer, recipe tree, dye/smelting recipes, tags, compost values, and active Furnace fuel wiring. No in-game placement, Bone Meal, harvesting, crafting, trading, fuel, or world-generation test was run. Data packs can change these recipes, loot, tags, and features.

[Cover registrations]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/Blocks.java#L6595-L6609
[Segment placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/SegmentableBlock.java
[Flowerbed growth]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/FlowerBedBlock.java
[Leaf Litter smelting]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/smelting/leaf_litter.json
[Cover items]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/Items.java#L371-L373
[Display names]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/assets/minecraft/lang/en_us.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[pink_petals loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/pink_petals.json
[wildflowers loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/wildflowers.json
[leaf_litter loot]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/loot_table/blocks/leaf_litter.json
[Plant support]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/block/dirt.json
[Leaf Litter support]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/LeafLitterBlock.java
[Block defaults]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L994-L1004
[Replacement rules]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L261-L267
[Bone Meal use]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[Grass Block growth]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L93
[Flower feature filter]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L45-L58
[flower_cherry feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/flower_cherry.json
[wildflowers_birch_forest feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/wildflowers_birch_forest.json
[wildflowers_meadow feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/wildflowers_meadow.json
[patch_leaf_litter feature]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/configured_feature/patch_leaf_litter.json
[Normal preset]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Overworld preset]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L110
[Overworld biomes]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L76-L103
[Biome feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[Placed feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L43-L69
[Feature registration]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L63-L67
[Simple feature registration]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L120
[Patch execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java
[Plant feature execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java
[cherry_grove biome]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/biome/cherry_grove.json
[flower_cherry placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/flower_cherry.json
[birch_forest biome]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/biome/birch_forest.json
[wildflowers_birch_forest placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/wildflowers_birch_forest.json
[meadow biome]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/biome/meadow.json
[wildflowers_meadow placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/wildflowers_meadow.json
[dark_forest biome]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/biome/dark_forest.json
[patch_leaf_litter placement]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/worldgen/placed_feature/patch_leaf_litter.json
[Trader offers]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L750-L829
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1420-L1481
[Trader selection]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[Offer randomization]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L238
[Leaves ingredient]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/item/leaves.json
[Pink dye]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_pink_petals.json
[Yellow dye]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_wildflowers.json
[Recipe execution]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/inventory/ResultSlot.java
[Bee food]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/item/bee_food.json
[Bee plants]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[Bee feeding]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/animal/Bee.java#L570-L590
[Bee attraction]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L677
[Compost values]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L90-L167
[Compost initialization]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/server/Bootstrap.java#L49-L50
[Compost first layer]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L318
[Fuel table]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L39-L109
[Server fuel]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/server/MinecraftServer.java#L335-L343
[Furnace fuel]: https://github.com/HungLo2020/MattMC/blob/20354edd390fadb09b43132d712facf4188c4119/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L156-L180
