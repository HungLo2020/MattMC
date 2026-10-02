# Moss and Pale Moss

Moss Blocks can turn suitable nearby ground into more moss when you apply Bone Meal. Their carpets and hanging forms have different support, growth, and harvesting rules. This guide covers the five registered moss blocks below; [Hanging Roots and Spore Blossom](HangingRootsAndSporeBlossom.md) covers the other ceiling plants.

## Registered forms and harvesting

Each block has its own matching inventory item. None of these five registrations requires a particular tool tier for drops, but **Pale Hanging Moss has its own loot condition**. The four block/carpet forms are in the hoe mining tag. [Ordinary registrations][reg-moss] · [Pale registrations][reg-pale] · [Items][items] · [Hoe tag][hoe] · [Player tool gate][gate]

| Block ID | Ordinary Survival recovery | Bone Meal effect |
| --- | --- | --- |
| `minecraft:moss_block` | 1 Moss Block, including by hand [loot][loot-moss_block] | Attempts a green moss patch |
| `minecraft:moss_carpet` | 1 Moss Carpet, including by hand [loot][loot-moss_carpet] | No carpet growth callback |
| `minecraft:pale_moss_block` | 1 Pale Moss Block, including by hand [loot][loot-pale_moss_block] | Attempts a pale moss patch |
| `minecraft:pale_moss_carpet` | 1 Pale Moss Carpet from the bottom piece; **nothing from its upper wall extension** [loot][loot-pale_moss_carpet] | Extends eligible wall covering above the bottom piece |
| `minecraft:pale_hanging_moss` | 1 per harvested segment with **Shears or Silk Touch**; otherwise nothing [loot][loot-pale_hanging_moss] | Extends the connected strand downward by one block |

Silk Touch is unnecessary for the two full blocks and two carpets; Fortune does not increase any of these listed yields. Breaking a support or washing a plant away supplies no harvesting tool, so it cannot collect Pale Hanging Moss. Normal item drops also follow `doTileDrops`, with the loot tables' separate explosion handling where present. [Mining dispatch][break] · [Tool-free drops and game rule][drop] · [Support loss][support-drop]

## Moss Blocks and spreading

**`moss_block`** and **`pale_moss_block`** are full blocks with hardness 0.1, no gravity or special support requirement, and no waterlogged state. A hoe speeds mining. They remain full blocks beside water; they do not turn into a water-containing form. [Registrations][reg-moss] · [Pale registration][reg-pale] · [Shared block class][feature-block] · [Hoe tag][hoe] · [Fluid occupancy][flow-hold]

Use Bone Meal on either full block with **air immediately above it**. There is no light, biome, or water-neighbor requirement in this callback. An accepted Survival application consumes one Bone Meal and attempts the corresponding configured patch, even if the surrounding layout yields no useful change. Existing moss does not spread simply because time passes: neither registration enables random ticking. [Growth dispatch][feature-block] · [Consumption][meal] · [Registrations][reg-moss] [reg-pale]

Both patches replace eligible ground with their own moss type. The replacement tag contains the six ordinary Overworld base stones (Stone, Granite, Diorite, Andesite, Tuff, Deepslate), the dirt-tag blocks, and Cave Vines. The actual patch also needs a suitable exposed, sturdy surface, so tag membership alone does not make every placement convertible. Cobblestone, stone bricks, ores, and wood are absent. Because **both moss blocks are in the dirt tag**, either color's patch can replace the other. [Replacement tag][replace] · [Base stones][base-stone] · [Dirt members][dirt] · [Cave Vines members][vines] · [Placement checks][growth]

The Bone Meal configuration samples each horizontal half-width as 2 or 3 blocks after the feature's added margin: a possible **5 × 5 to 7 × 7** footprint with corners skipped and other outer-edge positions attempted at 75%. It searches up and down for usable surfaces within its configured vertical range of 5 and replaces one ground layer. Obstructions and ineligible blocks leave gaps; this is not a guaranteed filled square. [Green configuration][feature-moss_patch_bonemeal] · [Pale configuration][feature-pale_moss_patch_bonemeal] · [Patch algorithm][growth]

After a qualifying ground position, each patch has a 60% vegetation attempt. Green moss chooses among Moss Carpet, Short Grass, Tall Grass, Azalea, and Flowering Azalea. Pale moss chooses Pale Moss Carpet, Short Grass, or Tall Grass; it does **not** produce Azalea or Pale Hanging Moss. These are weighted, placement-dependent results, not a fixed harvest per application. See [Saplings and Azaleas](SaplingsAndAzaleas.md#azalea-and-flowering-azalea) for growing the resulting shrubs. [Green vegetation][feature-moss_vegetation] · [Pale vegetation][feature-pale_moss_vegetation] · [Vegetation placement][simple-feature]

## Moss Carpet

**`minecraft:moss_carpet`** is a one-sixteenth-block-high floor covering. It needs a **non-air block directly below**, rather than a full sturdy top face. Removing that support makes it break on its shape update. It has no waterlogged state and no Bone Meal or automatic-spreading callback. [Carpet class][carpet]

Craft **two Moss Blocks side by side → three Moss Carpets**. The 2 × 1 recipe fits the inventory grid. There is no reverse recipe returning a block from carpets. [Recipe][recipe-moss_carpet]

## Pale Moss Carpet

**`minecraft:pale_moss_carpet`** has a floor piece and optional side covering. The floor needs a non-air block below and adds low covering against neighboring faces that provide a full support or collision face. Its horizontal floor collision is only one-sixteenth block high; the side covering is not a full blocking wall. [Carpet support and shapes][pale-carpet] · [Side attachment rule][attach]

On placement, the block may also create covering in the **one block immediately above**: each available upper side is independently chosen with a 50% roll. The upper space must be replaceable or an existing upper Pale Moss Carpet, and an eligible upper face must connect to the corresponding side below. Bone Meal on the **bottom piece** fills the eligible upper sides when that would change them. It does not create a spreading carpet field or a second floor layer. [Placement, upper-piece creation, and Bone Meal][pale-carpet]

The upper piece has no collision and survives only over a bottom Pale Moss Carpet, with at least one supported side remaining. Changes to the surrounding walls update the side shapes; losing the bottom support removes the covering. **Only the bottom piece has an item drop**, so clipping off the upper decoration does not multiply carpet items. [State updates][pale-carpet] · [Bottom-only loot][loot-pale_moss_carpet]

Craft **two Pale Moss Blocks side by side → three Pale Moss Carpets**, also in the inventory grid. Ordinary Moss Blocks do not substitute in this recipe. [Recipe][recipe-pale_moss_carpet]

## Pale Hanging Moss

**`minecraft:pale_hanging_moss`** hangs from a full support/collision face above or from another Pale Hanging Moss segment. It has no collision, waterlogging, climbing callback, or random growth tick. Its bottom segment uses a tip shape; connected segments above use the longer shape. [Hanging Moss class][hanging-moss] · [Attachment test][attach] · [Registration][reg-pale] · [Climbable tag][climbable]

Apply Bone Meal to **any segment in the connected strand** while there is air below its bottom. The callback finds the bottom and adds exactly one segment. It does not grow into water or another occupied block. There is no shears-use growth-stopping interaction here: Shears collect it when you break it. [Downward growth][hanging-moss]

When support disappears, the block schedules a check **one game tick later**, then destroys itself if still unsupported. This is a scheduled support check, not random growth; unsupported lower segments can subsequently lose their supports too. Break each segment with Shears or Silk Touch before removing its ceiling if you want to recover the strand. [Support callback][hanging-moss] · [Scheduled tick dispatch][tick] · [Loot][loot-pale_hanging_moss]

## Water and recovery

The carpets and Pale Hanging Moss cannot store water. When flowing water can reach their block space, the fluid code can replace these thin or non-colliding blocks and requests their ordinary tool-free drops. Moss Carpet and the bottom Pale Moss Carpet can therefore drop their items; Pale Hanging Moss and upper carpet pieces do not. Water does not convert green moss into pale moss or revive a lost plant. For a waterloggable ceiling decoration, use [Hanging Roots](HangingRootsAndSporeBlossom.md#hanging-roots). [Fluid eligibility][flow-hold] · [Thin-block solidity][solid] [aabb] · [Replacement][flow] · [Water drops][water-drops] · [Loot table distinctions](#registered-forms-and-harvesting)

## Finding a starting supply

In the bundled **normal world preset**, the Overworld uses the active Overworld biome list, which includes Lush Caves and Pale Garden. Lush Caves installs the floor-moss placement and its Moss Block/vegetation configuration. Pale Garden installs pale ground patches and its Pale Oak selector. These are verified generation routes, not a promise that every cave or tree has every decoration. [Normal preset][normal] · [Preset dispatch][preset] · [Biome selection][biomes] · [Lush Caves][biome-lush_caves] · [Moss placement][placed-lush_caves_vegetation] [feature-moss_patch] · [Pale Garden][biome-pale_garden] · [Pale patch][placed-pale_moss_patch] [feature-pale_moss_patch]

The Pale Garden selector reaches `pale_oak` and `pale_oak_creaking`; both have the Pale Moss decorator, which can add ground moss and hanging strands beneath logs and leaves. **Player-grown Pale Oak uses `pale_oak_bonemeal`, whose decorator list is empty**, including when saplings grow by random ticks. Growing those saplings is therefore not this natural-tree moss route. Keep tree planting rules in [Saplings and Azaleas](SaplingsAndAzaleas.md#ordinary-sapling-comparison). [Selector][placed-pale_garden_vegetation] [feature-pale_garden_vegetation] · [Selected placements][placed-pale_oak_checked] [placed-pale_oak_creaking_checked] · [Natural trees][feature-pale_oak] [feature-pale_oak_creaking] · [Decorator][pale-decorator] · [Planted selection][grower] [feature-pale_oak_bonemeal]

Wandering Trader offers also include **1 Emerald → 2 Moss Blocks**, **1 Emerald → 2 Pale Moss Blocks**, and **1 Emerald → 3 Pale Hanging Moss**. The block offers allow five uses each; the hanging-moss offer allows four. Offers are randomly selected, so an individual trader may have none of them. Once you have a starter, the Bone Meal routes above can make more without requiring the original biome. [Offer entries][trades] · [Offer quantities][trade-count] · [Active trader selection][trader-use] [trade-choice]

## Crafting and composting

Besides the two carpet recipes, **one Moss Block plus one Cobblestone → one Mossy Cobblestone**, and **one Moss Block plus one Stone Bricks → one Mossy Stone Bricks**. Both are shapeless. Pale Moss Block is not an interchangeable ingredient. [Recipes][recipe-mossy_cobblestone_from_moss_block] [recipe-mossy_stone_bricks_from_moss_block] · [Stone construction guide](Stone.md#mossy-variants)

The bundled recipes contain no recipe creating either full moss block or Pale Hanging Moss. For composting, the two full blocks each have a 65% configured chance, and both carpets and Pale Hanging Moss each have 30%; the composter's first accepted item advances an empty composter directly. [Composter entries and processing][compost]

## Small starting patch

A source-based example, **not gameplay-tested**: put one Moss Block in the center of a flat 5 × 5 Dirt platform, leave the space above open, and use Bone Meal on the Moss Block. Recover some converted Moss Blocks by hand or hoe while retaining a starter, then craft carpets from pairs. Expect uneven conversion and random vegetation. For Pale Hanging Moss, put a strand beneath a solid ceiling, leave air below it, Bone Meal the strand, and harvest the added segment with Shears. [Patch checks][growth] · [Growth callbacks][feature-block] [hanging-moss]

## Related pages

- [Hanging Roots and Spore Blossom](HangingRootsAndSporeBlossom.md)
- [Rooted Dirt](SoilSandAndGravel.md#rooted-dirt-and-hanging-roots)
- [Pale Oak and other saplings](SaplingsAndAzaleas.md)
- [Primordial Plants](PrimordialPlants.md), which keeps Fiddlehead, Cycad, and Archaic Vine behavior

## Sources and verification

Checked against the pinned MattMC source and bundled resources on **2026-10-02**. Registrations, active callbacks, loot, recipe inputs, and generation chains were inspected. The examples are source-based; no gameplay test is claimed.

[reg-moss]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6590-L6614
[reg-pale]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6825-L6849
[items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L359-L379
[hoe]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot-moss_block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/moss_block.json
[loot-moss_carpet]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/moss_carpet.json
[loot-pale_moss_block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/pale_moss_block.json
[loot-pale_moss_carpet]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/pale_moss_carpet.json
[loot-pale_hanging_moss]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/pale_hanging_moss.json
[break]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[drop]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[support-drop]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Block.java#L213-L233
[feature-block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java
[flow-hold]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L429
[meal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/BoneMealItem.java#L37-L86
[replace]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/moss_replaceable.json
[base-stone]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/base_stone_overworld.json
[dirt]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/dirt.json
[vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/cave_vines.json
[growth]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/VegetationPatchFeature.java
[feature-moss_patch_bonemeal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch_bonemeal.json
[feature-pale_moss_patch_bonemeal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_patch_bonemeal.json
[feature-moss_vegetation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/moss_vegetation.json
[feature-pale_moss_vegetation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_vegetation.json
[simple-feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java
[carpet]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/CarpetBlock.java
[recipe-moss_carpet]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/moss_carpet.json
[pale-carpet]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MossyCarpetBlock.java
[attach]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L251-L260
[recipe-pale_moss_carpet]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/pale_moss_carpet.json
[hanging-moss]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/HangingMossBlock.java
[climbable]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/climbable.json
[tick]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L771
[solid]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L482-L500
[aabb]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/phys/AABB.java#L271-L276
[flow]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L266-L278
[water-drops]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[normal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[preset]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[biomes]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[biome-lush_caves]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[placed-lush_caves_vegetation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/lush_caves_vegetation.json
[feature-moss_patch]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch.json
[biome-pale_garden]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/pale_garden.json
[placed-pale_moss_patch]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/pale_moss_patch.json
[feature-pale_moss_patch]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_patch.json
[placed-pale_garden_vegetation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/pale_garden_vegetation.json
[feature-pale_garden_vegetation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/pale_garden_vegetation.json
[placed-pale_oak_checked]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/pale_oak_checked.json
[placed-pale_oak_creaking_checked]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/pale_oak_creaking_checked.json
[feature-pale_oak]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak.json
[feature-pale_oak_creaking]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_creaking.json
[pale-decorator]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/PaleMossDecorator.java
[grower]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[feature-pale_oak_bonemeal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_bonemeal.json
[trades]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L810-L827
[trade-count]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1480
[trader-use]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[recipe-mossy_cobblestone_from_moss_block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_moss_block.json
[recipe-mossy_stone_bricks_from_moss_block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_moss_block.json
[compost]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
