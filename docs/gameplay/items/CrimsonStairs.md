# Crimson Stairs

Crimson Stairs (`minecraft:crimson_stairs`) are the stepped building form made from [Crimson Planks](CrimsonPlanks.md).

## Obtaining

Use a [Crafting Table](../blocks/CraftingTable.md) to arrange **6 Crimson Planks → 4 Crimson Stairs**: rows of one, two, then three planks aligned along one side. All six slots require Crimson Planks. See the [shared construction layouts](../blocks/WoodConstruction.md#crafting-construction-shapes). [Recipe][recipe]

Ordinary Survival mining returns **one matching stair item**, with no required tool or Silk Touch. An unbroken axe mines it faster; this speed advantage is separate from [drop eligibility](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Place them upright for steps or upside down for roof edges and overhangs. They face your horizontal placement direction; the clicked face and height choose the half. Follow the [stair placement and corner guide](../blocks/WoodConstruction.md#stairs). [Placement][placement]

## Behavior

Compatible neighboring stairs can form inner or outer corners, including stairs of other materials. The placed half and corner shape do not change the matching item recovered by mining. These stairs can be waterlogged; see [water placement and bucket rules](../blocks/WoodConstruction.md#waterlogging-and-power). [Placement][placement] · [Loot][loot]

Crimson Stairs are excluded from default furnace fuel. Their placed blocks have no ordinary fire-consumption entry. See [fire, lava, and fuel distinctions](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Fire table][fire] · [Fuel removal][fuel] · [Excluded items][nonflammable]

## Notes

* The block inherits Crimson Planks' hardness **2** and blast resistance **3**. [Stair registration][block] · [Plank properties][planks] · [Property copying][copy]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, and tags can be changed by data packs. No in-game test was run. [Item registration][item]

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_stairs.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/crimson_stairs.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L601-L601
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5678-L5678
[copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7255-L7258
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L91-L164
[planks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5582-L5585
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L106-L145
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L303-L515
[nonflammable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
