# Smithing Table

Smithing Table is the placeable item for the [Smithing Table block](../blocks/SmithingTable.md), the workstation for equipment upgrades and armor trims. Its ID is `minecraft:smithing_table`.

## Obtaining and use

Craft the table at a Crafting Table, or collect an existing table. See the [block page](../blocks/SmithingTable.md#crafting-and-collecting) for the complete recipe, accepted plank ingredients, and harvesting rules.

Place the item, then interact with the block to open its three-input menu. Carrying the table does not apply an upgrade by itself. The [smithing guide](../smithing/Smithing.md) explains the required template, base equipment, and addition material, including a verified Diamond Pickaxe-to-Netherite Pickaxe recipe.

## Related pages

- [Smithing Table: recipe and placed behavior](../blocks/SmithingTable.md)
- [Smithing guide](../smithing/Smithing.md)
- [Netherite Upgrade Smithing Template](SmithingTemplateNetheriteUpgrade.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not an in-game test; data packs and later builds can change recipes and behavior.

- [Block-item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L2443)
- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/smithing_table.json)
- [Opening the placed table](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/SmithingTableBlock.java)
