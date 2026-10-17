# Mushrooms

**Brown and Red Mushrooms** are small, spreading plants that can become huge mushrooms with Bone Meal. Their huge forms supply **Brown Mushroom Blocks, Red Mushroom Blocks, and Mushroom Stems** for building. Keep some small mushrooms before harvesting a farm: stems do not return planting material, and cap drops can be empty. [Small mushroom registry] · [Huge block registry] · [Huge growth and recovery] · [brown_mushroom_block loot] · [red_mushroom_block loot] · [mushroom_stem loot]

This guide covers the five ordinary mushroom blocks below. [Crimson and Warped Fungi](NetherFungi.md) have their own growth rules; [Flower Pot](FlowerPot.md) owns the two potted mushroom forms.

## Registered forms

Each row has a matching item with the same ID. [Small mushroom items] · [Huge block items]

| Form and exact ID | Practical role | Ordinary harvest | Silk Touch harvest |
| --- | --- | --- | --- |
| <span id="brown-mushroom"></span>[Brown Mushroom](../items/BrownMushroom.md), `minecraft:brown_mushroom` | Small plant; grows a brown cap; emits light level **1** | **1 Brown Mushroom** | Same single mushroom. [Small mushroom registry] · [brown_mushroom loot] |
| <span id="red-mushroom"></span>[Red Mushroom](../items/RedMushroom.md), `minecraft:red_mushroom` | Small plant; grows a red cap; no emitted light | **1 Red Mushroom** | Same single mushroom. [Small mushroom registry] · [red_mushroom loot] |
| <span id="brown-mushroom-block"></span>[Brown Mushroom Block](../items/BrownMushroomBlock.md), `minecraft:brown_mushroom_block` | Full cap building block | **0–2 Brown Mushrooms** | **1 Brown Mushroom Block**. [brown_mushroom_block loot] |
| <span id="red-mushroom-block"></span>[Red Mushroom Block](../items/RedMushroomBlock.md), `minecraft:red_mushroom_block` | Full cap building block | **0–2 Red Mushrooms** | **1 Red Mushroom Block**. [red_mushroom_block loot] |
| <span id="mushroom-stem"></span>[Mushroom Stem](../items/MushroomStem.md), `minecraft:mushroom_stem` | Shared full stem building block | **Nothing** | **1 Mushroom Stem**. [mushroom_stem loot] |

## Planting, light, and water

Small mushrooms have two support routes:

- **Mycelium, Podzol, Crimson Nylium, or Warped Nylium:** the bundled `#minecraft:mushroom_grow_block` tag allows survival at any light level
- **Other solid-render blocks:** the mushroom's position must have **raw brightness below 13**. The support test uses a fully occluding rendered block, rather than simply any upward-facing surface. Dim Stone is a useful example; a plantable small mushroom on it still cannot make a huge mushroom there

The light test uses raw skylight without subtracting nighttime dimming, so waiting for night alone does not make an exposed bright-sky position valid. [Small mushroom support] · [Raw light calculation]

The placed item checks survival, and neighboring block updates recheck it. Removing support or making the checked position invalid can remove the plant. The spread routine separately checks every candidate position. There is no small-mushroom age or maturation stage. [Small mushroom support] · [Mushroom support tag] · [Solid support definition] · [Placement survival] · [Plant neighbor survival] · [Small spread] · [Huge mushroom height and clearance]

Small mushrooms have no collision and no waterlogged state. **Water that flows into them replaces them and runs their normal one-mushroom drop table.** Huge caps and stems are full collision blocks with no waterlogged state; they can be used as solid building blocks without a stem or soil beneath them. [Small mushroom registry] · [Water occupancy] · [Water replacement] · [Water drops] · [brown_mushroom loot] · [red_mushroom loot] · [Huge block registry] · [Huge face properties] · [Full block shape]

## Small mushroom spreading

On a random tick, a small mushroom has a **1-in-25 chance to enter a spread attempt**. First it counts mushrooms of its **own color** in the 9 × 3 × 9 box centered on itself, extending four blocks horizontally and one vertically. **Five or more, including the starting mushroom, cancel that attempt.** The other color does not count toward this check. [Small spread]

If density permits, the routine explores nearby positions in four short random-walk steps, then considers one final position. It places **at most one** new mushroom, only in air where that color can survive. The starting mushroom remains. This is neither a guaranteed new mushroom every 25 ticks nor a fixed growth timer, and the density box is not a hard boundary around all possible final positions. Leave valid empty planting spaces and harvest excess plants if you want continued spreading. Random spreading does not turn the small plant into a huge mushroom. [Small spread] · [Huge growth and recovery]

## Growing a huge mushroom

Use **Bone Meal on one planted Brown or Red Mushroom**. Each accepted Survival use consumes **one Bone Meal**, including a failed roll or failed placement. The mushroom has a **40% chance to attempt its matching huge feature**; the feature must still pass its own checks. On a failed feature placement the original small mushroom is restored. On success it is replaced by the huge form. No two-by-two planting arrangement is required. [Huge growth and recovery] · [Bone Meal consumption] · [Brown huge feature] · [Red huge feature]

### Ground and room

The block directly below must be in either `#minecraft:dirt` or `#minecraft:mushroom_grow_block`. Together the current tags allow **Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, Muddy Mangrove Roots, Crimson Nylium, and Warped Nylium**. Ordinary Stone is absent even though it can support a small mushroom in dim conditions. The huge feature has no extra brightness check, but the small plant still needs its own valid starting conditions. [Huge mushroom height and clearance] · [Dirt membership test] · [Soil tag] · [Mushroom support tag]

The generator selects a stem height of **4, 5, or 6 blocks**, then doubles it on a **1-in-12 roll**, giving possible stem heights **8, 10, or 12**. The top cap sits one block above the top stem block, so the whole shape reaches **5–7 or 9, 11, or 13 blocks above its supporting floor**. It also checks the world's minimum and maximum build heights. These are the checked generator's choices, not a promise that every roll can fit a particular farm. [Huge mushroom height and clearance]

| Color | Intended cap | Current clearance behavior |
| --- | --- | --- |
| Brown | A flat **7 × 7** top layer with the four corners omitted | Checks only the center column for the first four levels, then the full **7 × 7** square from offset 4 through the top. Checked cells must be air or leaves. [Brown huge feature] · [Brown cap and clearance] · [Huge mushroom height and clearance] |
| Red | A rounded cap over **four layers**, up to **5 × 5**, with a **3 × 3** top | The current caller makes the red clearance routine check the **center column only**. Cap placement then skips nonreplaceable occupied cells individually. A successful attempt can therefore leave an incomplete red cap beside obstacles. [Red huge feature] · [Red cap and clearance] · [Huge mushroom height and clearance] |

For a straightforward farm, clear the whole intended footprint and overhead space. **A 7 × 7 space for brown or 5 × 5 for red, 13 blocks high above a valid floor, accommodates the largest checked shape away from build limits.** This is a layout derived from the source; it does not remove the Bone Meal chance. Air is the simplest clearance. The initial checks accept leaves, while actual cap and stem placement can replace air or members of `#minecraft:replaceable_by_mushrooms`; that broader replacement tag does not relax the initial clearance check. [Huge mushroom height and clearance] · [Brown cap and clearance] · [Red cap and clearance] · [Mushroom replacement tag]

## Mining and building faces

Small mushrooms break instantly. Huge cap blocks and stems have hardness **0.2** and are **axe-efficient**. None of these five blocks requires a tool tier to run its loot table, so the distinction is the loot's Silk Touch branch, not a pickaxe requirement. See [Mining](../mechanics/Mining.md) for the shared tool system. [Small mushroom registry] · [Huge block registry] · [Axe mining tag] · [Axe tool assignment] · [Axe tool rules] · [Harvest gate] · [Mining loot caller]

Without Silk Touch, each cap rolls an integer from **−6 through 2**, then clamps negative results to zero. For ordinary mining, that means **7/9 chance of no small mushroom, 1/9 of one, and 1/9 of two**. Both colors use this rule. **Fortune does not change these tables. Shears have no special recovery branch.** Silk Touch level I or above gives the intact cap block instead; stems need it to drop anything at all. [brown_mushroom_block loot] · [red_mushroom_block loot] · [mushroom_stem loot] · [Uniform loot count] · [Inclusive count bounds] · [Set loot count] · [Clamp loot count]

Caps and stems have six independent outer-face flags rather than a log-style axis. When you place a normal item, faces touching the **same exact block type** become interior faces; exposed faces start as exterior faces. Brown cap, red cap, and stem are three different types for this test. The model definitions use the matching outer texture for a true face and the shared mushroom interior for a false face. [Huge face placement] · [Huge face properties] · [brown_mushroom_block faces] · [red_mushroom_block faces] · [mushroom_stem faces]

**Removing an adjacent matching block does not restore the remaining block's exterior face.** A matching neighbor turns that side inward, and removing it leaves the state as it was. To reset a block's appearance, recover it with Silk Touch and replace it with the desired neighbors absent. The normal Silk Touch loot does not copy its six old face flags into the item. Naturally generated caps and stems also receive specific face states from their feature, so a collected block need not look identical after replacement. [Huge face placement] · [Generic shape update] · [brown_mushroom_block loot] · [red_mushroom_block loot] · [mushroom_stem loot] · [Brown huge feature] · [Red huge feature]

## Checked acquisition routes

The following are verified examples, not an exhaustive list of structures, trades, or every biome containing mushrooms. The Normal preset selects the Overworld and Nether biome presets; those selections include Mushroom Fields, Dark Forest, and Nether Wastes. Biome features pass through the active placed/configured-feature execution paths. Local support, clearance, filters, and random choices still control what appears. [Normal world preset] · [Biome preset execution] · [Overworld selections] · [Mushroom Fields selection] · [Biome feature execution] · [Placed feature execution] · [Configured feature execution] · [Feature registrations]

| Route | Checked result and limitation |
| --- | --- |
| **Mushroom Fields** | Its feature list includes a huge-mushroom selector, plus brown and red small-mushroom patches. The huge selector chooses red or brown with a random boolean; feature success still depends on placement. [mushroom_fields biome] · [mushroom_island_vegetation placement] · [mushroom_island_vegetation feature] · [Boolean feature selection] · [brown_mushroom_taiga placement] · [red_mushroom_taiga placement] |
| **Dark Forest** | Its vegetation selector can choose huge brown or red mushrooms among its tree choices. The listed chance checks run in order, so they are not independent percentages of all finished trees and mushrooms. [dark_forest biome] · [dark_forest_vegetation placement] · [dark_forest_vegetation feature] · [Ordered feature selection] |
| **Nether Wastes** | Both ordinary small colors are wired through Nether mushroom patch placements. Those patches still require air and the small mushroom's survival rule; this does not make them Crimson or Warped Fungi. [nether_wastes biome] · [brown_mushroom_nether placement] · [red_mushroom_nether placement] · [patch_brown_mushroom feature] · [patch_red_mushroom feature] · [Patch execution] · [Small feature survival] |
| **Shearing an adult Mooshroom** | Unbroken Shears collect **five mushrooms matching its red or brown variant** and convert that animal into an ordinary Cow. This consumes the Mooshroom as that resource source; it is not a repeatable shearing cycle on the same animal. [Mooshroom](../mobs/Mooshroom.md) is the mob entry. [Mooshroom shearing interaction] · [Mooshroom conversion] · [Mooshroom shear loot] · [Brown Mooshroom yield] · [Red Mooshroom yield] |

Once you have a small starter mushroom, [spreading](#small-mushroom-spreading) and [huge growth](#growing-a-huge-mushroom) are the checked renewable routes. Keep starter stock because cap harvesting can return zero and stem harvesting never returns small mushrooms.

## Crafting, food, and decoration

The small mushrooms are **ingredients and placeable plants, not directly edible food items**. The three huge building blocks are also plain block items. No bundled recipe in the checked recipe tree produces any of these five items, and no cap/stem recipe unpacks them into small mushrooms. [Small mushroom items] · [Huge block items] · [Plain block item registration]

| Use | Exact checked mushroom requirement |
| --- | --- |
| [Mushroom Stew](../items/MushroomStew.md) | **1 Brown Mushroom + 1 Red Mushroom + 1 Bowl → 1 Mushroom Stew**, shapeless; fits the inventory crafting grid. [mushroom_stew recipe] |
| [Rabbit Stew](../items/RabbitStew.md) | **1 Brown Mushroom or 1 Red Mushroom**, with **1 Baked Potato, 1 Cooked Rabbit, 1 Bowl, and 1 Carrot → 1 Rabbit Stew**. These are two explicit shapeless recipes with five ingredients, requiring a crafting table. [rabbit_stew_from_brown_mushroom recipe] · [rabbit_stew_from_red_mushroom recipe] |
| [Suspicious Stew](../items/SuspiciousStew.md) | The flower recipes use **both** small colors. The existing stew page and [ordinary flower table](Flowers.md#small-flower-variants) own the recipe/effect comparison; use those pages to choose the flower. [suspicious_stew_from_dandelion recipe] |
| [Fermented Spider Eye](../items/FermentedSpiderEye.md#crafting) | The existing recipe specifically needs **Brown Mushroom**. Red Mushroom does not substitute. The linked ingredient page and [Brewing](../brewing/Brewing.md) own its recipe and potion uses. [fermented_spider_eye recipe] |

The crafting execution matches the named ingredients and consumes them for the output. [Shapeless recipe execution] · [Ingredient consumption] The shared food rules belong to [Hunger, saturation, and healing](../mechanics/Hunger.md).

Both small colors can go in a [Flower Pot](FlowerPot.md). [Composting](Composter.md) accepts small mushrooms and stems at **65%**, and cap blocks at **85%**, for an ordinary level-increase roll; the first accepted item in an empty composter raises the level automatically. [Potted mushroom registry] · [Small and stem composting] · [Cap composting] · [Composter success]

## Sources and verification

Source-reviewed on **2026-10-02** at `1d7b3bbf88ccad2196a6de69317d934e7c3aeced`. Checked all five block/item registrations and complete block loot tables, the exact support/mining/replacement tags, active small-spread and huge-growth callbacks, face-state definitions, twenty recipe files referencing the family, and the selected biome-feature and Mooshroom-shearing routes. The face models were read from the pinned Git tree. No in-game growth, mining, crafting, placement, lighting, or world-generation test was run. Data packs can change tags, recipes, loot, and configured features.

[Small mushroom registry]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Blocks.java#L1060-L1084
[Huge block registry]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Blocks.java#L2292-L2306
[Huge growth and recovery]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L91-L119
[brown_mushroom_block loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/brown_mushroom_block.json
[red_mushroom_block loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/red_mushroom_block.json
[mushroom_stem loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/mushroom_stem.json
[Small mushroom items]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L360-L361
[Huge block items]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L550-L552
[brown_mushroom loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/brown_mushroom.json
[red_mushroom loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/red_mushroom.json
[Small mushroom support]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L77-L89
[Raw light calculation]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/lighting/LevelLightEngine.java#L148-L152
[Mushroom support tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/mushroom_grow_block.json
[Solid support definition]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L505-L509
[Placement survival]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/BlockItem.java#L130-L141
[Plant neighbor survival]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L27-L40
[Small spread]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L47-L75
[Huge mushroom height and clearance]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/AbstractHugeMushroomFeature.java#L18-L95
[Water occupancy]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[Water replacement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[Water drops]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Huge face properties]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/HugeMushroomBlock.java#L89-L92
[Full block shape]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[Bone Meal consumption]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[Brown huge feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/huge_brown_mushroom.json
[Red huge feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/huge_red_mushroom.json
[Dirt membership test]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L197-L199
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/dirt.json
[Brown cap and clearance]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/HugeBrownMushroomFeature.java#L16-L61
[Red cap and clearance]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/HugeRedMushroomFeature.java#L16-L69
[Mushroom replacement tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/replaceable_by_mushrooms.json
[Axe mining tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[Axe tool assignment]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[Axe tool rules]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[Harvest gate]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Mining loot caller]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[Uniform loot count]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java#L29-L31
[Inclusive count bounds]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/util/Mth.java#L138-L140
[Set loot count]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/storage/loot/functions/SetItemCountFunction.java#L46-L51
[Clamp loot count]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/storage/loot/functions/LimitCount.java#L36-L41
[Huge face placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/HugeMushroomBlock.java#L33-L67
[brown_mushroom_block faces]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/assets/minecraft/blockstates/brown_mushroom_block.json
[red_mushroom_block faces]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/assets/minecraft/blockstates/red_mushroom_block.json
[mushroom_stem faces]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/assets/minecraft/blockstates/mushroom_stem.json
[Generic shape update]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L150-L161
[Normal world preset]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[Biome preset execution]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L38-L110
[Overworld selections]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L73-L81
[Mushroom Fields selection]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L189-L194
[Biome feature execution]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Placed feature execution]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[Configured feature execution]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L26
[Feature registrations]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L74-L85
[mushroom_fields biome]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[mushroom_island_vegetation placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/placed_feature/mushroom_island_vegetation.json
[mushroom_island_vegetation feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/mushroom_island_vegetation.json
[Boolean feature selection]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/RandomBooleanSelectorFeature.java#L17-L26
[brown_mushroom_taiga placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/placed_feature/brown_mushroom_taiga.json
[red_mushroom_taiga placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/placed_feature/red_mushroom_taiga.json
[dark_forest biome]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/biome/dark_forest.json
[dark_forest_vegetation placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/placed_feature/dark_forest_vegetation.json
[dark_forest_vegetation feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/dark_forest_vegetation.json
[Ordered feature selection]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/RandomSelectorFeature.java#L17-L30
[nether_wastes biome]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[brown_mushroom_nether placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/placed_feature/brown_mushroom_nether.json
[red_mushroom_nether placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/placed_feature/red_mushroom_nether.json
[patch_brown_mushroom feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/patch_brown_mushroom.json
[patch_red_mushroom feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/patch_red_mushroom.json
[Patch execution]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java#L15-L38
[Small feature survival]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L17-L43
[Mooshroom shearing interaction]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L119-L126
[Mooshroom conversion]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L175-L192
[Mooshroom shear loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/shearing/mooshroom.json
[Brown Mooshroom yield]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/shearing/mooshroom/brown.json
[Red Mooshroom yield]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/shearing/mooshroom/red.json
[Plain block item registration]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L2750-L2783
[mushroom_stew recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/mushroom_stew.json
[rabbit_stew_from_brown_mushroom recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_brown_mushroom.json
[rabbit_stew_from_red_mushroom recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_red_mushroom.json
[suspicious_stew_from_dandelion recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_dandelion.json
[fermented_spider_eye recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/fermented_spider_eye.json
[Shapeless recipe execution]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L91
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[Potted mushroom registry]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Blocks.java#L2706-L2711
[Small and stem composting]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L137-L139
[Cap composting]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L173-L174
[Composter success]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
