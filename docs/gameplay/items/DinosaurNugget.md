# Dinosaur Nugget

Dinosaur Nugget is an edible item registered as `minecraft:dinosaur_nugget`. It attracts [Tremorsaurus](../mobs/Tremorsaurus.md) and [Vallumraptor](../mobs/Vallumraptor.md), but their normal breeding/growth food checks accept it only while tame.

## Obtaining

Dinosaur Nuggets are explicitly listed in Creative food items. With command permission, the registered item can also be given using its ID.

**A Survival supply is not established.** No Dinosaur Nugget recipe or loot entry was found in the checked bundled data, and the reviewed Java uses do not establish an item-production route. Do not assume that Dinosaur Chops can be crafted into nuggets or that killing a Tremorsaurus drops them.

## Eating

Eating one restores **3 hunger points** (one and a half drumstick icons) and adds **1.8 saturation**, subject to the normal cap at current hunger. Its food properties do not make it always edible or attach a special effect.

See [Hunger and healing](../mechanics/Hunger.md) for the difference between hunger and saturation.

## Using with dinosaurs

Holding a nugget can attract either species, but it does not tame one or override its combat goals. After ownership is established, feeding an eligible adult enters love mode and feeding a baby speeds growth.

The current Tremorsaurus breeding path lays a **Turtle Egg** placeholder rather than its own species egg. Vallumraptor breeding returns a Dragon Egg placeholder. See the [Tremorsaurus guide](../mobs/Tremorsaurus.md#breeding-and-drops) and [Vallumraptor guide](../mobs/Vallumraptor.md#owner-commands-and-breeding) before spending food on a breeding setup.

## Related pages

- [Tremorsaurus](../mobs/Tremorsaurus.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game eating, attraction, breeding, or item-acquisition test was run.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1685) and [Creative food listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1734-L1735)
- [Food properties](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java#L50), [saturation calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32), and [food application](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java)
- [Tremorsaurus attraction, food eligibility, and placeholder egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java) and [ordinary animal feeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157)
- [Bundled recipes](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe) and [loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table)

- [Vallumraptor food and placeholder egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/VallumraptorEntity.java)
