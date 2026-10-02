# Oxidized Copper Trapdoor

`minecraft:oxidized_copper_trapdoor` places a **single-block hinged panel**. Its top/bottom setting locates the closed panel within that block space. [Item binding][item] · [Block registration][block] · [Shape][shape]

## Obtaining

Let an unwaxed [Copper Trapdoor](CopperTrapdoor.md) progress through Exposed and Weathered to **Oxidized**, then collect it with the tool described below. The checked ordinary recipe set has no direct crafting output for this unwaxed stage. See [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing). [Stage map][weather]

The ordinary [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can also provide this item in Survival and Creative, subject to its cursor, space, and feature checks. [Listing][listing]

## Usage

It **opens by hand and responds to redstone**, including at the fully oxidized stage. It can be waterlogged. See [Copper doors and trapdoors](../blocks/CopperConstruction.md#doors-and-trapdoors) for placement, power changes, and Wind Charge controls. [Copper settings][type] · [Control and placement][control]

## Behavior

This is the final oxidation stage, but its unwaxed finish can still be changed by an axe or lightning. Honeycomb produces **Waxed Oxidized Copper Trapdoor**. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) and [lightning cleaning](../blocks/CopperConstruction.md#lightning-cleaning); hold secondary use, normally sneak, for wax/scrape interactions. [Stage map][weather] · [Wax pair][wax]

## Notes

For ordinary Survival collection, use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe**. Wooden and Golden Pickaxes fail the bundled tier requirement. Correct-tool mining returns **one matching current variant** without needing Silk Touch; explosion recovery is conditional. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Drop gate][block] · [Exact loot][loot]

Source-reviewed at `9363264a1615b1c23931f39e1189cda0cc088490` on 2026-10-02. Selected bundled routes are described here; data packs can change recipes, tags, and loot. No gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L1105-L1105
[block]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/Blocks.java#L6286-L6290
[listing]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L494
[shape]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L45-L75
[control]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L92-L165
[type]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L47-L64
[loot]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_trapdoor.json
[weather]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L38-L40
[wax]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/HoneycombItem.java#L51-L54
