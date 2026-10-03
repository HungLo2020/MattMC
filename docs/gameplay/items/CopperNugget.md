# Copper Nugget

Copper Nugget (`minecraft:copper_nugget`) is a crafting ingredient. It is registered as an ordinary inventory item with no block-placement action. [Registration][item] · [Item factory][factory] · [Use handler][use]

## Obtaining

Craft **one Copper Ingot into nine Copper Nuggets**, with no required arrangement. See [Copper Ingot](CopperIngot.md#obtaining) for metal processing and storage conversions. [Nugget recipe][nuggets]

The ordinary [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can also provide this item in Creative, subject to its cursor, space, and feature checks. [Listing][listing]

## Usage

- Nine Nuggets in a 3 × 3 square make **one Copper Ingot**. [Recipe][ingot]
- Place a Copper Ingot between two Nuggets in a vertical column to make **one Copper Chain**. [Recipe][chain]
- Surround a **Copper Torch** with eight Nuggets to make **one Copper Lantern**. [Recipe][lantern]

## Behavior

Follow the [Chain placement](../blocks/CopperBarsChainsAndLanterns.md#copper-chain-orientation-and-support), [Lantern support](../blocks/CopperBarsChainsAndLanterns.md#copper-lantern-floor-or-ceiling-light), and [fixture collection rules](../blocks/CopperBarsChainsAndLanterns.md#collecting-and-moving-fixtures) for those crafted outputs. Nuggets themselves do not inherit those blocks' oxidation, light, or mining behavior.

## Notes

Source-reviewed at `9363264a1615b1c23931f39e1189cda0cc088490` on 2026-10-02. Selected bundled routes are described here; data packs can change recipes, tags, and loot. No gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L2283-L2283
[factory]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L2792-L2797
[use]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Item.java#L164-L196
[listing]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1793
[nuggets]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/crafting/copper_nugget.json
[ingot]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/crafting/copper_ingot_from_nuggets.json
[chain]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/crafting/copper_chain.json
[lantern]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/crafting/copper_lantern.json
