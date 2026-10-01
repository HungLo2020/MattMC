# Pewen Log

Pewen Log is an axis-oriented wood block item registered as `minecraft:pewen_log`. It is the main trunk material placed by the integrated Pewen tree feature.

## Obtaining and use

A successfully grown Pewen tree places log blocks; their loot table specifies one log item. The block does not require a special tool for drops, although normal axe-speed support is not established because the checked axe-mineable tag lacks Pewen entries. Creative also lists the item.

Four Pewen Logs in a 2 × 2 square have a shaped recipe for three [Pewen Wood](PewenWood.md). However, the bundled log-to-planks recipe has an ingredient-format mismatch, and the standard axe stripping map has no Pewen conversion. Do not assume either conversion works just because the destination blocks exist.

See the [Pewen family guide](../blocks/Pewen.md) for growth, building recipes, and source limitations.

## Related pages

- [Pewen Planks](PewenPlanks.md)
- [Pewen Sapling](PewenSapling.md)
- [Items](Items.md)

## Sources and verification

Reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source/data review only; no in-game placement, growth, harvesting, or crafting test.

- [Log registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L6974-L6982)
- [Log drops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/pewen_log.json)
- [Wood recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_wood.json)
- [Planks recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_planks.json)
- [Axe mappings](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/AxeItem.java)
