# Glow Lichen

**Glow Lichen** is the item form of `minecraft:glow_lichen`. Its [block guide](../blocks/GlowLichen.md) covers surface spreading, waterlogging, light, and natural-source restrictions. [Vine and Lichen items]

## Obtaining

Mine with **Shears** for **one item per attached face** of the block. A six-face block therefore yields six under ordinary mining. Silk Touch on another tool does not meet the Shears-only condition, and Fortune does not multiply the count. [Loot glow_lichen] · [Loot count addition]

## Usage

Place it on a supported wall, ceiling, or floor face. Additional items can add vacant supported faces within the same cell. A placed patch emits **light level 7** regardless of face count, and can preserve source Water through waterlogging. [Lichen placement and added faces] · [Lichen faces and water state] · [Full-face attachment checks] · [Glow Lichen registration] · [Lichen light and Bone Meal]

## Behavior

Bone Meal spreads a new face when suitable space and support are available; it does not duplicate a loose item directly. Glow Lichen has no autonomous random-tick spread in the checked registration and is **not climbable**. Use the [propagation guide](../blocks/GlowLichen.md#bone-meal-spreading) to set up suitable surfaces. [Glow Lichen registration] · [Lichen light and Bone Meal] · [One-face spread search] · [Climbable block tag]

## Notes

Shear it before dismantling its support. The guide's [checked natural example](../blocks/GlowLichen.md#a-checked-natural-source) distinguishes the generation rock list from broader player placement. No producing crafting or smelting recipe was found.

## Sources and verification

Source-reviewed on **2026-10-02** at `88d85ee594bc770fa362129690eadb127272dc20`. Checked the registration, complete face-count loot, tool/climbing tags, placement, light, water, Bone Meal spreading, and recipe absence. Shared mechanics remain in the block guide. No in-game placement, climbing, growth, harvesting, crafting, eating, or generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Loot glow_lichen]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/glow_lichen.json
[Glow Lichen registration]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L2391-L2403
[Vine and Lichen items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L559-L560
[Climbable block tag]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/climbable.json
[Full-face attachment checks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L251-L259
[Lichen faces and water state]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L109-L172
[Lichen placement and added faces]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L175-L216
[Lichen light and Bone Meal]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GlowLichenBlock.java#L14-L53
[One-face spread search]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceSpreader.java#L13-L43
[Loot count addition]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/storage/loot/functions/SetItemCountFunction.java#L45-L49
