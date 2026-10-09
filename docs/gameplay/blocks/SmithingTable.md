# Smithing Table

A Smithing Table opens the workstation menu for equipment upgrades and armor trims. Its block and item ID is `minecraft:smithing_table`.

## Crafting and collecting

At a [Crafting Table](CraftingTable.md), arrange **two Iron Ingots above four planks-tag items** in a two-column, three-row pattern:

| Left column | Right column |
| --- | --- |
| Iron Ingot | Iron Ingot |
| Planks | Planks |
| Planks | Planks |

The recipe produces **one Smithing Table**. Leave the unused column empty. Ingredients in the planks positions must belong to `minecraft:planks`; similarly named integrated wood blocks are not automatically valid substitutes.

The table's loot returns one table under ordinary harvesting conditions, subject to explosion survival. It belongs to the axe mining tag, but its registration does **not** require a correct tool for drops.

## Opening and using the menu

Place the table and interact with it. Its three inputs are **template, base equipment, and addition material**, from left to right, with an output slot to their right. See the [smithing guide](../smithing/Smithing.md) for verified upgrade and trim examples, input costs, and item-preservation details.

The working slots are not permanent storage: closing the menu clears the input container. Store spare equipment and templates in a [Chest](Chest.md).

## Toolsmith job site

A Smithing Table is the job-site block for a **Toolsmith Villager**. Its registered
point of interest has one claim slot; placing a table near a villager does not
by itself prove that the villager has claimed it. Keep the claimed table
accessible for work. The player's upgrade/trim menu is a separate use of the
same block. [Registered job-site states][toolsmith-site] · [Profession binding][toolsmith-profession]

Use [Villager employment and changing jobs](../mobs/Villager.md#employment-and-changing-jobs)
for eligibility and profession retention, and [Trading restocks](../trading/Trading.md#stock-and-restocking)
for the work/stock conditions. Those shared guides own the detailed rules;
removing a workstation is not a general way to reset an experienced villager's
profession or offers.

## Related pages

- [Smithing guide](../smithing/Smithing.md)
- [Armor trims: patterns, acquisition, and copying](../mechanics/ArmorTrims.md)
- [Smithing Table item](../items/SmithingTable.md)
- [Netherite Upgrade Smithing Template](../items/SmithingTemplateNetheriteUpgrade.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not an in-game test. Data packs can change recipes, tags, and loot; later builds can change behavior. Natural generation was not reviewed for this page. The Toolsmith job-site mapping was separately source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`; no villager or restocking gameplay test was run.

- [Table recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/smithing_table.json)
- [Planks tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/planks.json)
- [Block registration and tool requirement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5370-L5374)
- [Player tool-for-drops check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657)
- [Axe mining tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/axe.json)
- [Block loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/smithing_table.json)
- [Opening the menu](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/SmithingTableBlock.java)
- [Menu layout and recipe lookup](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/SmithingMenu.java)
- [Menu closing and input cleanup](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/ItemCombinerMenu.java)

[toolsmith-site]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L132
[toolsmith-profession]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L122
