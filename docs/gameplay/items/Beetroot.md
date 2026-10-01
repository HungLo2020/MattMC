# Beetroot

Beetroot is a harvested food and crafting ingredient registered as `minecraft:beetroot`. Grow it from [Beetroot Seeds](BeetrootSeeds.md); the harvested Beetroot itself does not plant a crop.

## Obtaining

Harvest a mature [Beetroot crop](../blocks/RootCrops.md). A mature plant's loot includes one Beetroot and a separate seed drop. Breaking an immature plant gives a seed instead of food.

For a starter crop, Beetroot Seeds are possible loot in dungeon chests and abandoned mineshaft chest minecarts. They are random loot entries, not guaranteed contents. Plant the seeds on [Farmland](../blocks/Farmland.md).

MattMC's empty-hand and hoe harvest controls reset mature plants to age 0 without spending a replacement seed. The [root-crop guide](../blocks/RootCrops.md) covers harvesting limits and Beetroot's distinctive growth and bone-meal behavior.

## Food and selected uses

- Eating one Beetroot provides **1 hunger point and 1.2 saturation**, subject to the player's food limits. Two hunger points equal one hunger-bar icon.
- Combine **six Beetroots and one Bowl** in a shapeless crafting recipe to make **one [Beetroot Soup](BeetrootSoup.md)**. Soup provides **6 hunger points and 7.2 saturation**, stacks to one, and returns a Bowl when consumed.
- One Beetroot crafts into **one [Red Dye](RedDye.md)** in a shapeless recipe.
- Beetroots are accepted food for [Pigs](../mobs/Pig.md), useful for attracting and breeding them.

Keep the distinction between Beetroots for food and seeds for planting when sorting your harvest.

## Related pages

- [Root crops](../blocks/RootCrops.md)
- [Beetroot Seeds](BeetrootSeeds.md)
- [Carrot](Carrot.md)
- [Potato](Potato.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No harvesting, crafting, feeding, or eating behavior was tested in-game.

- [Crop loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/beetroots.json) and [item, seed, and soup registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Dungeon chest loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json) and [mineshaft loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/abandoned_mineshaft.json)
- [Soup recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/beetroot_soup.json) and [Red Dye recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_beetroot.json)
- [Food values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java), [saturation calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java), and [food limits](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java)
- [Pig food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/pig_food.json) and [Pig food and attraction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Pig.java)
