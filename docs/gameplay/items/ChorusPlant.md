# Chorus Plant

**Chorus Plant** (`minecraft:chorus_plant`) is the branching stem block. Its registered item appears in Creative, but ordinary harvesting yields **0–1 Chorus Fruit per stem**, not a stem item, even with Silk Touch. [Item registration][item] · [Creative entry][creative] · [Stem loot][loot]

## Obtaining

Its ordinary Natural Blocks entry is available through the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative; that insertion route is separate from the harvesting rules below.

For Survival, [find wild Chorus in End Highlands](../blocks/Chorus.md#finding-wild-chorus), collect flowers directly, then [plant a flower on End Stone to grow stems](../blocks/Chorus.md#planting-and-growth). The checked recipe bundle has no recipe producing Chorus Plant, and its ordinary loot has no stem self-drop. [Stem loot][loot] · [Flower growth][growth]

## Usage

Harvest stems for [Chorus Fruit](ChorusFruit.md), or keep the placed plant as a source of future flowers and fruit. **Collect flowers before cutting the supporting stems:** flowers lost through ordinary plant collapse do not supply the replanting item. [Harvesting guide](../blocks/Chorus.md#harvest-flowers-before-stems)

## Behavior

Stems connect to nearby Chorus stems and flowers, but still need valid support. They can break in a chain after support removal, and water replaces them rather than waterlogging them. See [shape, support and water](../blocks/Chorus.md#shape-support-and-water) for the exact rules. [Connections and support][stem]

## Notes

The stem item is distinct from [Chorus Flower](ChorusFlower.md). A Creative inventory entry is not evidence of a Survival stem-item acquisition route. Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`; no in-game acquisition, placement or harvest test was run.

Related: [Chorus guide](../blocks/Chorus.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L469-L470
[creative]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L940-L946
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/chorus_plant.json#L1-L30
[growth]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java#L63-L99
[stem]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/ChorusPlantBlock.java#L61-L109
