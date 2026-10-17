# Redstone Randomizer

The **Redstone Randomizer item** (`minecraft:redstone_randomizer`) places the [two-output redstone component](../blocks/RedstoneRandomizer.md#redstone-randomizer). Its circuit behavior starts after placement. [Item registration][item] · [Block-item registration][block-item] · [Placement action][place]

## Obtaining

Its **Redstone Blocks** category entry can be requested through the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. No recipe was found in the inspected bundled recipe data. An existing Randomizer normally drops one item when broken in Survival, with no special tool or enchantment needed; this block-recovery behavior is separate from that browser insertion route. See [support and drops](../blocks/RedstoneRandomizer.md#obtaining-support-and-drops) for the exact limits. [Creative entry][creative] · [Recipes][recipes] · [Loot][loot] · [Block properties][registration]

## Usage

Place it on a suitable upward support face. Its input initially points toward the player who placed it, and its outputs are sideways. Follow the block guide's [orientation and wiring](../blocks/RedstoneRandomizer.md#orientation-and-wiring) before connecting a source and separate output branches. [Placement direction][placement]

## Behavior

Each recognized transition from off to on chooses one side to supply strength 15. It does not generate a clock or continuously reroll under steady power. The [placed-block guide](../blocks/RedstoneRandomizer.md#random-choices-and-update-timing) explains delay, short-pulse limits and repeated choices. [State transition and output][behavior]

## Notes

This is the item form of `minecraft:redstone_randomizer`. The held item does not store a previously selected branch through the ordinary block loot table. A placed block starts from its default state, with placement determining facing. [Loot][loot] · [Default state][state] · [Placement][placement]

Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game crafting, collection or circuit test was run.

[All items](Items.md) · [Redstone and transport catalog](../blocks/catalog/redstone.md)

[item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L999-L1003
[block-item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L2750-L2752
[place]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L83
[creative]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1283-L1296
[recipes]: https://github.com/HungLo2020/MattMC/tree/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/recipe
[loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/redstone_randomizer.json#L1-L21
[registration]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Blocks.java#L2852-L2854
[placement]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L151-L161
[behavior]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L54-L82
[state]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L19-L26
