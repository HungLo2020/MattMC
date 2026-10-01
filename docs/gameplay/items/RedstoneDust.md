# Redstone Dust

Redstone Dust is registered as `minecraft:redstone`. Placing it creates [redstone wire](../blocks/RedstoneDust.md) for carrying signals.

## Obtaining

The Redstone Ore loot table selects the ore block with Silk Touch. Otherwise it selects **4–5 Redstone Dust** before the Fortune bonus and explosion decay. Correct-tool requirements still apply; the bundled mining tiers place Redstone Ore in the Iron-required group.

This page covers that verified ore source, not every trade, chest, or mob-drop route.

## Packing and use

- Nine Redstone Dust filling a 3 × 3 grid craft **one Block of Redstone**.
- One Block of Redstone in a shapeless recipe returns **nine Redstone Dust**.
- Place dust on valid support to make wire; see the block guide for signal attenuation and connections.

The item and placed wire have different registry IDs. Use the item ID for inventory acquisition, and do not assume a pile of dust powers itself without a source.

## Related pages

- [Wire behavior](../blocks/RedstoneDust.md)
- [Mining tools and tiers](../mechanics/Mining.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No circuit simulation or in-game timing test was run; feature flags can select different wire evaluators.

- [Ore loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/redstone_ore.json)
- [Iron-tier requirement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json)
- [Block crafting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/redstone_block.json)
- [Block unpacking](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/redstone.json)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
