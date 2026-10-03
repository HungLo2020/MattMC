# Mining tools and drops

Breaking a block, breaking it quickly, and receiving its expected drop are separate questions. MattMC uses block mining tags, tool-material restrictions, loot tables, and enchantment conditions together.

Use [Pickaxes and Shovels](PickaxesAndShovels.md) for exact per-item recipes, material repair, path/campfire actions, and recycling.

Use [Haste and Mining Fatigue](../effects/MiningEffects.md) for effect multipliers, their interaction with Conduit Power, and separate attack-recharge changes.

## Choose the tool family first

Tools have rules for their intended block groups, such as pickaxe-mineable stone or axe-mineable wood. A block that requires a correct tool can fail its drop check even when its loot table names an item. If no matching correct-for-drops rule is found, the tool rule returns false.

This matters for imported content: [Amber](../blocks/Amber.md) and [Limestone](../blocks/Limestone.md) have documented mining-tag gaps. [Ambersol](../blocks/Ambersol.md) now has its pickaxe and stone-tier tags after the merged [#779 fix](https://github.com/HungLo2020/MattMC/issues/779); use its current harvesting guide. A familiar appearance alone is not proof that a particular tool works.

## Material values

These are baseline tool-material settings before enchantments or other item-specific behavior:

| Material | Durability | Matching-tool mining-speed value |
| --- | ---: | ---: |
| Wood | 59 | 2 |
| Stone | 131 | 4 |
| Copper | 190 | 5 |
| Iron | 250 | 6 |
| Gold | 32 | 12 |
| Diamond | 1,561 | 8 |
| Netherite | 2,031 | 9 |

Speed is an internal multiplier, not a block-break time or harvest-tier ranking. Gold's high speed does not give it Iron's drop permissions. Copper exists as its own material in this MattMC snapshot; do not substitute a pre-copper vanilla chart.

## Tier restrictions

The bundled incorrect-tool tags produce these restrictions, in addition to choosing the right tool family:

- **Wood and Gold:** denied drops for blocks requiring Stone, Iron, or Diamond tier.
- **Stone and Copper:** denied for Iron- or Diamond-tier blocks.
- **Iron:** denied for Diamond-tier blocks.
- **Diamond and Netherite:** their incorrect-tool tags are empty, but a matching tool-family rule is still required.

Examples of the requirement tags include Iron/Copper/Lapis Ores at Stone tier; Diamond, Emerald, Gold, and Redstone Ores at Iron tier; and Obsidian/Ancient Debris at Diamond tier. These are selected examples, not a complete block list.

## Loot and enchantments

Use [Efficiency, Fortune and Silk Touch](../enchanting/MiningEnchantments.md) for supported tools, the active mining-speed bonus and source-specific loot choices.

[Stone](../blocks/Stone.md) illustrates Silk Touch selecting Stone instead of Cobblestone. [Coal](../items/Coal.md) illustrates Silk Touch selecting ore and Fortune modifying the ordinary coal branch. Do not generalize those exact drop counts or enchantment rules to every block.

When a drop surprises you, check tool family, material restriction, correct-tool requirement, loot conditions, and current data-pack changes separately. A missing integration tag is a documentation caveat until the gameplay code/data is actually repaired.

See [Durability and repair](Durability.md) when a previously suitable tool stops giving expected drops: fully damaged gear is retained as a broken stack and fails the normal correct-tool check.

## Related pages

- [Stone](../blocks/Stone.md)
- [Coal](../items/Coal.md)
- [Pewen tool caveats](../blocks/Pewen.md#recipes-and-tools-that-need-caution)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No timed growth, harvesting, or tool test was performed in-game.

- [Material stats and tool rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/ToolMaterial.java)
- [Correct-drop matching](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/component/Tool.java)
- [Player correct-tool check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L656)
- [Wood restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json)
- [Stone restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json)
- [Copper restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json)
- [Iron restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json)
- [Gold restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json)
- [Diamond restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json)
- [Netherite restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/incorrect_for_netherite_tool.json)
- [Stone-tier blocks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json)
- [Iron-tier blocks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json)
- [Diamond-tier blocks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json)
