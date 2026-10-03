# Copper Trapdoor

`minecraft:copper_trapdoor` places a **single-block hinged panel**. Its top/bottom setting locates the closed panel within that block space. [Item binding][item] · [Block registration][block] · [Shape][shape]

## Obtaining

Craft **four Copper Ingots in a 2 × 2 square into one Copper Trapdoor**. This recipe produces the unaffected, unwaxed form. [Recipe][recipe]

The ordinary [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can also provide this item in Creative, subject to its cursor, space, and feature checks. [Listing][listing]

## Usage

It **opens by hand and responds to redstone**, including at the fully oxidized stage. It can be waterlogged. See [Copper doors and trapdoors](../blocks/CopperConstruction.md#doors-and-trapdoors) for placement, power changes, and Wind Charge controls. [Copper settings][type] · [Control and placement][control]

## Behavior

While placed and unwaxed, it can progress through Exposed and Weathered to Oxidized. Wax it with Honeycomb to preserve its finish. See [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing) and [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping); hold secondary use, normally sneak, to avoid opening it during the interaction. [Stage map][weather] · [Wax pair][wax]

## Notes

For ordinary Survival collection, use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe**. Wooden and Golden Pickaxes fail the bundled tier requirement. Correct-tool mining returns **one matching current variant** without needing Silk Touch; explosion recovery is conditional. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Drop gate][block] · [Exact loot][loot]

Source-reviewed at `9363264a1615b1c23931f39e1189cda0cc088490` on 2026-10-02. Selected bundled routes are described here; data packs can change recipes, tags, and loot. No gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L1102-L1102
[block]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/Blocks.java#L6271-L6280
[listing]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L461
[shape]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L45-L75
[control]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L92-L165
[type]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L47-L64
[loot]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/loot_table/blocks/copper_trapdoor.json
[weather]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L38-L40
[wax]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/HoneycombItem.java#L51-L54
[recipe]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/crafting/copper_trapdoor.json
