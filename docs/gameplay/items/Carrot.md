# Carrot

A Carrot is both food and the planting item for a carrot crop. It is registered as `minecraft:carrot`.

## Obtaining and planting

Harvest mature [Carrot crops](../blocks/RootCrops.md). MattMC lets you use an empty hand or a hoe to harvest mature plants and reset them without replanting; the crop guide covers growth, drop counts, and the hoe's same-crop area limit.

Shipwreck supply-chest loot can also include Carrots, providing a possible starter crop. This is a random loot entry, not a guaranteed find in every shipwreck.

Use a Carrot on [Farmland](../blocks/Farmland.md) to plant it. There is no separate Carrot Seed item.

## Food and selected uses

- Eating a Carrot provides **3 hunger points** and **3.6 saturation**, subject to the player's food limits. Two hunger points equal one hunger-bar icon.
- Surround **one Carrot with eight Gold Nuggets** in a crafting table to make **one [Golden Carrot](GoldenCarrot.md)**.
- Place a **Fishing Rod diagonally above-left of a Carrot** in the crafting grid to make **one [Carrot on a Stick](CarrotOnAStick.md)**.
- Carrots are accepted food for [Pigs](../mobs/Pig.md), useful for attracting and breeding them.

- Carrots are also the bundled taming food for [Kangaroos](../mobs/Kangaroo.md#taming-and-owner-commands); the taming sequence requires repeated feeding, not a guaranteed first-Carrot success.

These are selected verified uses, not a complete recipe or animal-food list.

## Related pages

- [Root crops](../blocks/RootCrops.md)
- [Potato](Potato.md)
- [Beetroot](Beetroot.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No harvesting, crafting, feeding, or eating behavior was tested in-game.

- [Item and planting registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Shipwreck supply loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/shipwreck_supply.json)
- [Food values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java), [saturation calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java), and [food limits](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java)
- [Golden Carrot recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/golden_carrot.json) and [Carrot on a Stick recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/carrot_on_a_stick.json)
- [Pig food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/pig_food.json) and [Pig food and attraction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Pig.java)

- [Kangaroo taming-food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/kangaroo_tameables.json)
