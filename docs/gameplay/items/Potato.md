# Potato

A Potato is the planting item for a potato crop and a food that becomes more filling when cooked. It is registered as `minecraft:potato`.

## Obtaining and planting

Harvest mature [Potato crops](../blocks/RootCrops.md). MattMC's empty-hand and hoe harvest controls drop mature loot and leave the crop planted at age 0. Shipwreck supply-chest loot can also include ordinary Potatoes; finding them is not guaranteed.

Use an ordinary Potato on [Farmland](../blocks/Farmland.md) to start a crop. [Baked Potatoes](BakedPotato.md) and [Poisonous Potatoes](PoisonousPotato.md) are different items and do not plant that crop. Set aside ordinary Potatoes before cooking if you plan to expand the field.

## Food and cooking

A raw Potato provides **1 hunger point and 0.6 saturation**. A Baked Potato provides **5 hunger points and 6 saturation**, subject to the player's food limits. Two hunger points equal one hunger-bar icon.

One ordinary Potato becomes one Baked Potato through any of these recipes:

| Cooking method | Recipe time |
| --- | --- |
| [Furnace](../blocks/Furnace.md) | 200 ticks, about 10 seconds at 20 ticks per second |
| Smoker | 100 ticks, about 5 seconds at 20 ticks per second |
| Campfire | 600 ticks, about 30 seconds at 20 ticks per second |

These recipes accept ordinary Potatoes, not Poisonous Potatoes. The mature crop's separate chance to drop a Poisonous Potato is described in the [crop guide](../blocks/RootCrops.md#what-a-harvest-yields).

Raw Potatoes are also accepted food for [Pigs](../mobs/Pig.md), useful for attracting and breeding them.

## Related pages

- [Root crops](../blocks/RootCrops.md)
- [Baked Potato](BakedPotato.md)
- [Carrot](Carrot.md)
- [Beetroot](Beetroot.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No harvesting, cooking, feeding, or eating behavior was tested in-game; cooking times are recipe values.

- [Item and planting registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java) and [shipwreck supply loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/shipwreck_supply.json)
- [Furnace recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/baked_potato.json), [Smoker recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smoking/baked_potato_from_smoking.json), and [Campfire recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/campfire_cooking/baked_potato_from_campfire_cooking.json)
- [Food values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java), [saturation calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java), and [food limits](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java)
- [Pig food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/pig_food.json) and [Pig food and attraction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Pig.java)
