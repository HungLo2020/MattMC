# Spruce Wood

Spruce Wood carries Spruce Log bark across all six faces, so an exposed beam end keeps the bark finish. The matching [log](SpruceLog.md), Spruce Wood, and [Stripped Spruce Wood](StrippedSpruceWood.md) are separate items. [Bark model][model] · [Face mapping][faces]

## Obtaining

Craft **four [Spruce Logs](SpruceLog.md) in a 2 × 2 square** to receive **three Spruce Wood**. The recipe requires ordinary Spruce Logs; it does not accept other log families or stripped logs. See [timber crafting choices](../blocks/TreeLogsAndRoots.md#crafting-choices) for the shared layouts and conversion tradeoff. [Recipe][wood-recipe]

With normal block drops enabled, ordinary Survival mining of a placed Spruce Wood returns **one Spruce Wood**, including when broken by hand. An **unbroken axe** speeds collection; Silk Touch is unnecessary and Fortune adds no extra wood. [Mining and placement](../blocks/TreeLogsAndRoots.md#mining-and-placement) covers the shared harvest rules. [Loot][loot] · [Axe tag][axe-tag] · [Harvest eligibility][harvest]

## Usage

One Spruce Wood crafts into **four [Spruce Planks](SprucePlanks.md)** through the `minecraft:spruce_logs` ingredient tag. If planks are the goal, crafting wood first turns four logs into only twelve planks; converting those logs directly gives sixteen. [Plank inputs][plank-tag] · [Plank recipe][plank-recipe]

It also supplies **300 furnace burn ticks**, or can be smelted as the ingredient for **one [Charcoal](Charcoal.md#making-charcoal)** using a separate fuel supply. Follow [Tree logs and roots: fire and fuel](../blocks/TreeLogsAndRoots.md#fire-and-fuel) for shared fuel choices and processing details. [Fuel value][fuel] · [Burnable-log families][burnable-logs] · [Charcoal recipe][charcoal]

## Behavior

The placed block keeps an **axis** despite having bark on every face: placement on a top or bottom face gives a vertical axis, while a side face gives that horizontal axis. [Placement rule][axis]

Use an unbroken axe on it to make **[Stripped Spruce Wood](StrippedSpruceWood.md)**, preserving that axis. See [stripping](../blocks/TreeLogsAndRoots.md#stripping) for tool wear and shield interaction. Placed Spruce Wood is flammable; stripping it does not remove that property. [Stripping pair][stripping] · [Axis retention][axis-retention] · [Fire entries][fire]

## Notes

* Item and placed-block ID: `minecraft:spruce_wood`. [Item registration][item] · [Block registration][block]
* Available in the **Building Blocks** Creative tab. [Creative entry][creative]
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game crafting, mining, rendering, or fire test was run. Resource packs can change its appearance; data packs and server settings can change recipes, loot, and fire behavior.

[model]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/models/block/spruce_wood.json
[faces]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/models/block/cube_column.json
[wood-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/spruce_wood.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/spruce_wood.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[plank-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/spruce_logs.json
[plank-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/spruce_planks.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L51
[charcoal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/charcoal.json
[burnable-logs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[axis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L57
[stripping]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L55
[axis-retention]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L132
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L383-L400
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L458-L462
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L258-L258
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L92-L94
