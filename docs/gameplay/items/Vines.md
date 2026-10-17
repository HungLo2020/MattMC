# Vines

**Vines** are the item form of `minecraft:vine`. The [Vines family guide](../blocks/Vines.md#vine) distinguishes them from the separately registered Nether and Cave Vines. [Vine and Lichen items]

## Obtaining

Mine an ordinary Vine block with **Shears** for **one Vine item**, regardless of the number of attached faces. Bare hands and Silk Touch without Shears do not recover it. Jungle's registered vine feature is one checked starter source; see [acquisition examples](../blocks/Vines.md#source-backed-places-to-collect-starters). [Loot vine] · [Vine source biome] · [Vine placed feature] · [Vine configured feature]

## Usage

Place Vines on suitable wall or ceiling support for decoration or climbing. They can hang from matching faces of Vines above. Ordinary Vines are used in the [Stone guide's mossy recipes](../blocks/Stone.md#mossy-variants); **one Vine plus one Paper** also crafts **one Bordure Indented Banner Pattern**. [Ordinary Vine support] · [Ordinary Vine placement] · [Climbable block tag] · [Vine-to-mossy-cobblestone recipe] · [Vine-to-mossy-bricks recipe] · [Indented-border pattern recipe]

## Behavior

Their natural spread depends on `doVinesSpread`, random ticks, space, support, and the [limited density check](../blocks/Vines.md#ordinary-vines). Bone Meal does not directly grow ordinary Vines. They have no waterlogged state; washing them away does not satisfy the Shears-only loot condition. [Ordinary Vine spread] · [Vine density check] · [Ordinary Vine faces] · [Bone Meal use and consumption] · [Water-driven block drops] · [Loot vine]

## Notes

For item recovery, shear the segments before removing their support. Nether vines have different Silk Touch and Fortune rules, and Glow Berries plant the Cave Vine family. [Compare harvesting and growth](../blocks/Vines.md).

## Sources and verification

Source-reviewed on **2026-10-02** at `88d85ee594bc770fa362129690eadb127272dc20`. Checked the item mapping, full Shears-only loot, three ingredient recipes, ordinary-Vine support/spread and water paths, and the selected Jungle feature chain. No in-game placement, climbing, growth, harvesting, crafting, eating, or generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Loot vine]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/vine.json
[Vine and Lichen items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L559-L560
[Ordinary Vine faces]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L27-L50
[Ordinary Vine support]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L79-L165
[Ordinary Vine placement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L281-L309
[Ordinary Vine spread]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L167-L244
[Vine density check]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L246-L279
[Bone Meal use and consumption]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[Climbable block tag]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/climbable.json
[Water-driven block drops]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Vine source biome]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/jungle.json
[Vine placed feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/vines.json
[Vine configured feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/vines.json
[Vine-to-mossy-cobblestone recipe]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_vine.json
[Vine-to-mossy-bricks recipe]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_vine.json
[Indented-border pattern recipe]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/bordure_indented_banner_pattern.json
