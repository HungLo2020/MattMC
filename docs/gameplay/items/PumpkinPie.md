# Pumpkin Pie

Pumpkin Pie (`minecraft:pumpkin_pie`) is a **handheld food**. Its ordinary item registration adds food and consumption components; using it does not place a food block. [Registration][item] · [Item factory][factory] · [Food components][food-component] · [Use handler][use]

## Obtaining

Combine **one Pumpkin, one Sugar, and one egg from the egg tag** in any arrangement to make **one Pumpkin Pie**. The bundled tag accepts Egg, Blue Egg, and Brown Egg. No furnace step is part of this recipe. [Recipe][recipe] · [Egg tag][eggs]

The ordinary [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can also provide this item in Creative, subject to its cursor, space, and feature checks. [Listing][listing]

## Usage

Hold use to eat a pie. One serving supplies **8 hunger points** and **4.8 saturation before caps**. Compare other foods in the [Food reference](FoodReference.md#other-registered-foods). [Food values][food] · [Saturation formula][saturation] · [Caps][caps]

## Behavior

It uses the ordinary food-consumption rules, including the normal hunger check. See [consumption defaults and exceptions](FoodReference.md#reading-the-values) and [Hunger](../mechanics/Hunger.md) for eating, saturation, and healing; the food values alone do not promise an immediate heal.

## Notes

Source-reviewed at `9363264a1615b1c23931f39e1189cda0cc088490` on 2026-10-02. Selected bundled routes are described here; data packs can change recipes, tags, and loot. No gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L2105-L2105
[factory]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L2792-L2797
[use]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Item.java#L164-L196
[listing]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1741
[recipe]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/crafting/pumpkin_pie.json
[eggs]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/tags/item/eggs.json
[food]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/food/Foods.java#L35
[food-component]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Item.java#L366-L371
[saturation]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[caps]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
