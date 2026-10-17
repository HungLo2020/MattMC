# Spruce Stairs

Spruce Stairs (`minecraft:spruce_stairs`) are the stepped building form made from [Spruce Planks](SprucePlanks.md).

## Obtaining

Use a [Crafting Table](../blocks/CraftingTable.md) to arrange **6 Spruce Planks → 4 Spruce Stairs**: rows of one, two, then three planks aligned along one side. All six slots require Spruce Planks. See the [shared construction layouts](../blocks/WoodConstruction.md#crafting-construction-shapes). [Recipe][recipe]

Ordinary Survival mining returns **one matching stair item**, with no required tool or Silk Touch. An unbroken axe mines it faster; this speed advantage is separate from [drop eligibility](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Place them upright for steps or upside down for roof edges and overhangs. They face your horizontal placement direction; the clicked face and height choose the half. Follow the [stair placement and corner guide](../blocks/WoodConstruction.md#stairs). [Placement][placement]

## Behavior

Compatible neighboring stairs can form inner or outer corners, including stairs of other materials. The placed half and corner shape do not change the matching item recovered by mining. These stairs can be waterlogged; see [water placement and bucket rules](../blocks/WoodConstruction.md#waterlogging-and-power). [Placement][placement] · [Loot][loot]

When not waterlogged, the placed stairs can be consumed by spreading fire. One Spruce Stair item supplies **300 burn ticks** as default furnace fuel; see [fire and fuel planning](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Fire registration][fire] · [Fuel table][fuel]

## Notes

* The block inherits Spruce Planks' hardness **2** and blast resistance **3**. [Stair registration][block] · [Plank properties][planks] · [Property copying][copy]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, and tags can be changed by data packs. No in-game test was run. [Item registration][item]

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/spruce_stairs.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/spruce_stairs.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L591-L591
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2632-L2632
[copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7255-L7258
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L91-L164
[planks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L143-L146
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L354-L354
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L54
